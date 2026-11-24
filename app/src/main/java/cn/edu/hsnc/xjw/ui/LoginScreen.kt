package cn.edu.hsnc.xjw.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.hsnc.xjw.data.CaptchaSolver
import cn.edu.hsnc.xjw.data.LoginOutcome
import cn.edu.hsnc.xjw.data.UrpApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .border(0.5.dp, Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = TextDim, fontSize = 14.sp)
                }
                inner()
            }
        }
    )
}

@Composable
fun LoginScreen(api: UrpApi, initialError: String? = null, onLoggedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    val solver = remember { CaptchaSolver(api.appContext) }
    var user by remember { mutableStateOf(api.lastUser) }
    var pwd by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(initialError) }
    var saveCred by remember { mutableStateOf(api.saveCredentialPref) }
    var recognized by remember { mutableStateOf(false) }
    var solving by remember { mutableStateOf(false) }

    val loadCaptcha: () -> Unit = {
        scope.launch {
            try {
                val c = withContext(Dispatchers.IO) { api.fetchCaptcha() }
                token = c.token
                val bmp = BitmapFactory.decodeByteArray(c.jpeg, 0, c.jpeg.size)
                captcha = bmp?.asImageBitmap()
                code = ""
                recognized = false
                if (solver.available) {
                    solving = true
                    val r = withContext(Dispatchers.IO) { solver.solve(c.jpeg) }
                    solving = false
                    if (r != null && r.confidence >= CaptchaSolver.MIN_CONFIDENCE_FILL) {
                        code = r.code
                        recognized = true
                    }
                }
            } catch (e: Exception) {
                error = "验证码加载失败：${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) { loadCaptcha() }

    fun submit() {
        if (user.isBlank() || pwd.isBlank() || code.isBlank()) {
            error = "学号、密码、验证码都要填写"
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    api.login(user.trim(), pwd, code.trim(), token)
                }
                when (outcome) {
                    LoginOutcome.Success -> {
                        api.saveCredentialPref = saveCred
                        if (saveCred) {
                            withContext(Dispatchers.IO) { api.saveCredential(user.trim(), pwd) }
                        } else {
                            withContext(Dispatchers.IO) { api.forgetCredential() }
                        }
                        onLoggedIn()
                    }
                    LoginOutcome.BadCaptcha -> {
                        val next = if (recognized) "识别有误，已换一张，可手动输入" else "验证码错误，请重试"
                        error = next
                        loadCaptcha()
                    }
                    is LoginOutcome.Failure -> {
                        error = outcome.message
                        code = ""
                        loadCaptcha()
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "网络异常，请检查是否连上校园网"
                loadCaptcha()
            } finally {
                loading = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(AccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Text("衡", color = Accent, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(18.dp))
        Text("衡院教务", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text("衡水学院 · 教学管理与服务平台", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(30.dp))

        DarkField(user, { user = it }, "学号", keyboardType = KeyboardType.Number)
        Spacer(Modifier.height(12.dp))
        DarkField(pwd, { pwd = it }, "密码", password = true)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                DarkField(
                    code,
                    { code = it; recognized = false },
                    if (solving) "识别中…" else "验证码",
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .width(112.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(0.5.dp, Line, RoundedCornerShape(12.dp))
                    .clickable { loadCaptcha() },
                contentAlignment = Alignment.Center
            ) {
                val bmp = captcha
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "验证码",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("点击加载", color = Color(0xFF888888), fontSize = 11.sp)
                }
            }
        }

        if (recognized) {
            Spacer(Modifier.height(6.dp))
            Text("已自动填好，可能有错，请核对或点图片换一张", color = Good, fontSize = 11.sp)
        } else if (solver.available && captcha != null && !solving) {
            Spacer(Modifier.height(6.dp))
            Text("未能识别，请手动输入", color = TextDim, fontSize = 11.sp)
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = saveCred,
                onCheckedChange = { on ->
                    saveCred = on
                    api.saveCredentialPref = on
                    // 关掉开关即刻删除本地凭据，避免文案与实际行为不符
                    if (!on) scope.launch { withContext(Dispatchers.IO) { api.forgetCredential() } }
                },
                modifier = Modifier.scale(0.82f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = TextDim,
                    uncheckedTrackColor = CardBg2,
                    uncheckedBorderColor = Line
                )
            )
            Spacer(Modifier.width(2.dp))
            Text("记住密码，登录过期自动重登", color = TextSub, fontSize = 12.sp)
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2A1A1A))
                    .border(0.5.dp, Color(0xFF6B2B2B), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(error!!, color = Color(0xFFF0B4B4), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Accent)
                .clickable(enabled = !loading) { submit() },
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text("登 录", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            if (saveCred)
                "验证码在本机离线识别，不联网。密码用系统密钥库加密后保存在本机，只提交给学校服务器；关掉上面的开关即可清除。\n验证码是本地猜的，四个字符全中有时会失手，登录前请顺手核一眼。自动登录只试一次，失败就退回这里，避免账号被锁定。"
            else
                "验证码在本机离线识别，不联网。当前不保存密码，每次需要手动登录。",
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}
