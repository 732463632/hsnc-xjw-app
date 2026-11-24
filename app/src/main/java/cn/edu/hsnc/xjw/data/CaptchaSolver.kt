package cn.edu.hsnc.xjw.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject

/**
 * 验证码识别：红色分离 -> 缩放到网络输入尺寸 -> 小型 CNN -> 4 个字符的概率分布。
 *
 * 权重随 APK 打包（int8 量化，加载时乘以 scale 还原为 float）。网络层结构、输入尺寸、
 * 张量顺序全部从 captcha_meta.json 读取，换模型只要换这两个资源文件，Kotlin 不用改。
 */
class CaptchaSolver(context: Context) {

    data class Result(val code: String, val confidence: Float)

    private class Tensor(val data: FloatArray)
    private class ConvLayer(val w: Tensor, val b: Tensor, val pool: Boolean, val cout: Int)

    companion object {
        const val CHARS = "2345678abcdefgmnpwxy"
        private const val HEADS = 4
        private const val NC = 20

        /** 低于此值不预填到输入框，交给用户手输 */
        const val MIN_CONFIDENCE_FILL = 0.12f
    }

    private var iw = 96
    private var ih = 32
    private val convs = ArrayList<ConvLayer>()
    private var fcW = Tensor(FloatArray(0))
    private var fcB = Tensor(FloatArray(0))
    private var headW = Tensor(FloatArray(0))
    private var headB = Tensor(FloatArray(0))
    private var fcOut = 0
    private var ready = false

    init {
        ready = runCatching { load(context) }.isSuccess
    }

    val available: Boolean get() = ready

    private fun load(ctx: Context) {
        val meta = JSONObject(
            ctx.assets.open("captcha_meta.json").bufferedReader().use { it.readText() }
        )
        val input = meta.getJSONObject("input")
        iw = input.getInt("w")
        ih = input.getInt("h")

        val order = meta.getJSONArray("order")
        val names = Array(order.length()) { order.getString(it) }
        val raw = ctx.assets.open("captcha_model.bin").use { it.readBytes() }

        // 按 order 顺序逐个张量取值，字节数与 shape 的乘积必须对得上
        val tensors = HashMap<String, Tensor>()
        var off = 0
        for (name in names) {
            val t = meta.getJSONObject("tensors").getJSONObject(name)
            val shape = t.getJSONArray("shape")
            var count = 1
            for (i in 0 until shape.length()) count *= shape.getInt(i)
            val scale = t.getDouble("scale").toFloat()
            val a = FloatArray(count) { i -> raw[off + i].toInt() * scale }
            off += count
            tensors[name] = Tensor(a)
        }

        val layers = meta.getJSONArray("layers")
        var cin = 1
        for (i in 0 until layers.length()) {
            val l = layers.getJSONObject(i)
            val tw = tensors.getValue(l.getString("w"))
            // 卷积核大小为 3x3，cout 由权重数量与输入通道数反推，避免元数据写错就直接崩
            val cout = tw.data.size / 9 / cin
            convs.add(ConvLayer(tw, tensors.getValue(l.getString("b")), l.optBoolean("pool", true), cout))
            cin = cout
        }
        fcW = tensors.getValue("fcw")
        fcB = tensors.getValue("fcb")
        headW = tensors.getValue("hw")
        headB = tensors.getValue("hb")
        require(convs.isNotEmpty() && fcW.data.isNotEmpty()) { "模型元数据不完整" }
    }

    // ---------------- 预处理 ----------------

    /** 红色像素掩码，与训练脚本 red_mask 完全一致 */
    private fun redMask(bmp: Bitmap): FloatArray {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val m = FloatArray(w * h)
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val mx = if (g > b) g else b
            m[i] = if (r - mx > 40 && r > 90) 1f else 0f
        }
        return m
    }

    /** 整数边界的区域均值缩放，与 Python 端 half-up 取整一致 */
    private fun resize(mask: FloatArray, w: Int, h: Int): FloatArray {
        val xs = IntArray(iw + 1) { ((it * w * 2 + iw) / (iw * 2)) }
        val ys = IntArray(ih + 1) { ((it * h * 2 + ih) / (ih * 2)) }
        val out = FloatArray(ih * iw)
        for (ty in 0 until ih) {
            var y0 = ys[ty]
            var y1 = ys[ty + 1]
            if (y1 <= y0) y1 = y0 + 1
            if (y1 > h) y1 = h
            for (tx in 0 until iw) {
                var x0 = xs[tx]
                var x1 = xs[tx + 1]
                if (x1 <= x0) x1 = x0 + 1
                if (x1 > w) x1 = w
                var s = 0f
                var n = 0
                for (y in y0 until y1) {
                    val base = y * w
                    for (x in x0 until x1) {
                        s += mask[base + x]
                        n++
                    }
                }
                out[ty * iw + tx] = if (n > 0) s / n else 0f
            }
        }
        return out
    }

    // ---------------- 前向 ----------------

    private fun conv(
        x: FloatArray, inC: Int, h: Int, w: Int, wt: FloatArray, bi: FloatArray, outC: Int
    ): FloatArray {
        val plane = h * w
        val out = FloatArray(outC * plane)
        for (oc in 0 until outC) {
            val wOff = oc * inC * 9
            val ob = oc * plane
            val bv = bi[oc]
            for (y in 0 until h) {
                for (xx in 0 until w) {
                    var sum = bv
                    for (ic in 0 until inC) {
                        val iOff = ic * plane
                        val wb = wOff + ic * 9
                        for (ky in -1..1) {
                            val yy = y + ky
                            if (yy < 0 || yy >= h) continue
                            val row = iOff + yy * w
                            val wrow = wb + (ky + 1) * 3
                            for (kx in -1..1) {
                                val xc = xx + kx
                                if (xc < 0 || xc >= w) continue
                                sum += x[row + xc] * wt[wrow + kx + 1]
                            }
                        }
                    }
                    out[ob + y * w + xx] = if (sum > 0f) sum else 0f   // 融合 ReLU
                }
            }
        }
        return out
    }

    private fun pool2(x: FloatArray, c: Int, h: Int, w: Int): FloatArray {
        val oh = h / 2
        val ow = w / 2
        val out = FloatArray(c * oh * ow)
        val ip = h * w
        val op = oh * ow
        for (ci in 0 until c) {
            val ib = ci * ip
            val ob = ci * op
            for (y in 0 until oh) {
                for (xx in 0 until ow) {
                    val i = ib + (y * 2) * w + xx * 2
                    var m = x[i]
                    if (x[i + 1] > m) m = x[i + 1]
                    if (x[i + w] > m) m = x[i + w]
                    if (x[i + w + 1] > m) m = x[i + w + 1]
                    out[ob + y * ow + xx] = m
                }
            }
        }
        return out
    }

    /** 成功返回识别结果；图片异常返回 null */
    fun solve(jpeg: ByteArray): Result? {
        if (!ready) return null
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        return try {
            solveBitmap(bmp)
        } finally {
            bmp.recycle()
        }
    }

    fun solveBitmap(bmp: Bitmap): Result? {
        if (!ready || bmp.width < 20 || bmp.height < 10) return null

        var x = resize(redMask(bmp), bmp.width, bmp.height)
        var c = 1
        var h = ih
        var w = iw
        for (l in convs) {
            x = conv(x, c, h, w, l.w.data, l.b.data, l.cout)
            c = l.cout
            if (l.pool) {
                x = pool2(x, c, h, w)
                h /= 2
                w /= 2
            }
        }

        val featLen = x.size
        if (fcW.data.size % featLen != 0) return null
        fcOut = fcW.data.size / featLen
        val fc = FloatArray(fcOut)
        for (i in 0 until fcOut) {
            var s = fcB.data[i]
            val base = i * featLen
            for (j in 0 until featLen) s += x[j] * fcW.data[base + j]
            fc[i] = if (s > 0f) s else 0f
        }

        val sb = StringBuilder()
        var minP = 1f
        for (head in 0 until HEADS) {
            val logits = FloatArray(NC)
            var mx = -1e9f
            for (c2 in 0 until NC) {
                var s = headB.data[head * NC + c2]
                val base = (head * NC + c2) * fcOut
                for (j in 0 until fcOut) s += fc[j] * headW.data[base + j]
                logits[c2] = s
                if (s > mx) mx = s
            }
            var sum = 0f
            for (c2 in 0 until NC) {
                val e = Math.exp((logits[c2] - mx).toDouble()).toFloat()
                logits[c2] = e
                sum += e
            }
            var bi = 0
            var bp = 0f
            for (c2 in 0 until NC) {
                val p = logits[c2] / sum
                if (p > bp) {
                    bp = p
                    bi = c2
                }
            }
            sb.append(CHARS[bi])
            if (bp < minP) minP = bp
        }
        return Result(sb.toString(), minP)
    }
}
