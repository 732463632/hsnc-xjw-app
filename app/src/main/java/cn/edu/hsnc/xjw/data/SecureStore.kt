package cn.edu.hsnc.xjw.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 里的 AES-256-GCM 密钥加密保存登录凭据。
 * 密钥由系统密钥库生成且不可导出，只有本应用能调用；卸载应用或重置密钥库即失效。
 */
object SecureStore {

    private const val PREF = "urp_secure"
    private const val FIELD = "cred"
    private const val ALIAS = "urp_cred_key"
    private const val SEP = '\u0000'

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    fun save(ctx: Context, user: String, password: String): Boolean = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = cipher.doFinal((user + SEP + password).toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ct, Base64.NO_WRAP)
        prefs(ctx).edit().putString(FIELD, payload).apply()
        true
    }.getOrDefault(false)

    fun load(ctx: Context): Pair<String, String>? = runCatching {
        val raw = prefs(ctx).getString(FIELD, null) ?: return null
        val idx = raw.indexOf(':')
        if (idx <= 0) return null
        val iv = Base64.decode(raw.substring(0, idx), Base64.NO_WRAP)
        val ct = Base64.decode(raw.substring(idx + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        val plain = String(cipher.doFinal(ct), Charsets.UTF_8)
        val i = plain.indexOf(SEP)
        if (i < 0) null else plain.substring(0, i) to plain.substring(i + 1)
    }.getOrNull()

    fun has(ctx: Context): Boolean = load(ctx) != null

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        }
    }
}
