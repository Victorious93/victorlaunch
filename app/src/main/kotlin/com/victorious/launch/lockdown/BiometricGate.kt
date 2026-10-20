package com.victorious.launch.lockdown

import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricGate(private val activity: FragmentActivity) {
    fun canAuthenticate(): Boolean {
        val mgr = BiometricManager.from(activity)
        val ready =
            mgr.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        return ready == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(title: String, onOk: () -> Unit, onFail: () -> Unit = {}) {
        if (!canAuthenticate()) {
            onOk()
            return
        }
        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        Handler(Looper.getMainLooper()).post { onOk() }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        Handler(Looper.getMainLooper()).post { onFail() }
                    }

                    override fun onAuthenticationFailed() {
                        Handler(Looper.getMainLooper()).post { onFail() }
                    }
                },
            )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Unlock this VictorLaunch item")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build(),
        )
    }
}
