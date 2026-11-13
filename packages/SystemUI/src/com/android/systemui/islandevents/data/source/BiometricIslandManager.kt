package com.android.systemui.islandevents.data.source

import android.hardware.biometrics.BiometricSourceType
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.islandevents.model.IslandEvent
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

open class BiometricIslandManager
@Inject
constructor(private val keyguardUpdateMonitor: KeyguardUpdateMonitor) {
    private var listening = false
    private val _biometricEvent = MutableStateFlow<IslandEvent.BiometricUnlock?>(null)
    val biometricEvent: StateFlow<IslandEvent.BiometricUnlock?> = _biometricEvent.asStateFlow()

    var onBiometricUnlock: ((IslandEvent.BiometricUnlock) -> Unit)? = null

    private fun postBiometricEvent(
        sourceType: BiometricSourceType,
        status: IslandEvent.BiometricStatus,
        message: String? = null,
    ) {
        val name =
            when (sourceType) {
                BiometricSourceType.FINGERPRINT -> "Fingerprint"
                BiometricSourceType.FACE -> "Face"
                BiometricSourceType.IRIS -> "Iris"
                else -> "Biometric"
            }
        val event =
            IslandEvent.BiometricUnlock(
                sourceType = sourceType.ordinal,
                sourceName = name,
                status = status,
                message = message,
            )
        _biometricEvent.value = event
        onBiometricUnlock?.invoke(event)
    }

    private val callback =
        object : KeyguardUpdateMonitorCallback() {
            override fun onBiometricRunningStateChanged(
                running: Boolean,
                biometricSourceType: BiometricSourceType,
            ) {
                if (running && biometricSourceType == BiometricSourceType.FACE) {
                    postBiometricEvent(biometricSourceType, IslandEvent.BiometricStatus.SCANNING, "Scanning…")
                } else if (!running && _biometricEvent.value?.status == IslandEvent.BiometricStatus.SCANNING) {
                    _biometricEvent.value = null
                }
            }

            override fun onBiometricAuthenticated(
                userId: Int,
                biometricSourceType: BiometricSourceType,
                isStrongBiometric: Boolean,
            ) {
                postBiometricEvent(biometricSourceType, IslandEvent.BiometricStatus.SUCCESS, "Unlocked")
            }

            override fun onBiometricAuthFailed(biometricSourceType: BiometricSourceType) {
                val msg = if (biometricSourceType == BiometricSourceType.FACE) "Face not recognized" else "Not recognized"
                postBiometricEvent(biometricSourceType, IslandEvent.BiometricStatus.FAILED, msg)
            }

            override fun onBiometricError(
                msgId: Int,
                errString: String,
                biometricSourceType: BiometricSourceType,
            ) {
                if (msgId != 5 && msgId != 10) {
                    postBiometricEvent(biometricSourceType, IslandEvent.BiometricStatus.ERROR, errString)
                }
            }

            override fun onBiometricHelp(
                msgId: Int,
                helpString: String,
                biometricSourceType: BiometricSourceType,
            ) {
                postBiometricEvent(biometricSourceType, IslandEvent.BiometricStatus.HELP, helpString)
            }
        }

    fun startListening() {
        if (listening) return
        listening = true
        keyguardUpdateMonitor.registerCallback(callback)
    }

    fun stopListening() {
        if (!listening) return
        listening = false
        keyguardUpdateMonitor.removeCallback(callback)
        _biometricEvent.value = null
    }

    fun clear() {
        _biometricEvent.value = null
    }
}

