package com.maxworld.app

import android.content.Context
import android.os.Build
import java.security.MessageDigest
import java.security.SecureRandom

/** PIN lock for the app screen. The PIN is stored only as a salted hash. */
object AppLock {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("lock", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = p(c).getString("hash", "").orEmpty().isNotEmpty()
    fun bioEnabled(c: Context): Boolean = p(c).getBoolean("bio", false)
    fun setBio(c: Context, v: Boolean) = p(c).edit().putBoolean("bio", v).apply()

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        p(c).edit().putString("salt", salt).putString("hash", hash(salt, pin)).apply()
    }

    fun check(c: Context, pin: String): Boolean {
        val salt = p(c).getString("salt", "").orEmpty()
        val h = p(c).getString("hash", "").orEmpty()
        return h.isNotEmpty() && h == hash(salt, pin)
    }

    fun disable(c: Context) {
        p(c).edit().clear().apply()
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    /** Fingerprint / face unlock needs Android 10+ and an enrolled finger. */
    fun bioAvailable(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val bm = c.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            if (Build.VERSION.SDK_INT >= 30) {
                bm.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                    android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                bm.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
            }
        } catch (e: Exception) {
            false
        }
    }

    fun promptBio(act: android.app.Activity, onOk: () -> Unit) {
        if (Build.VERSION.SDK_INT < 28) return
        try {
            val exec = act.mainExecutor
            val prompt = android.hardware.biometrics.BiometricPrompt.Builder(act)
                .setTitle(act.getString(R.string.lock_title))
                .setNegativeButton(act.getString(R.string.cancel), exec) { _, _ -> }
                .build()
            prompt.authenticate(
                android.os.CancellationSignal(), exec,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                        onOk()
                    }
                }
            )
        } catch (e: Exception) {
        }
    }
}
