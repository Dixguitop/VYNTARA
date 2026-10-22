package com.vyntara.editor.boot

import com.vyntara.editor.capabilities.DeviceProfile

/**
 * Explicit boot controller states. The cinematic UI reacts to these;
 * they are driven by real work, not by animation timers alone.
 */
sealed class BootState {
    data object Idle : BootState()
    data object Initializing : BootState()
    data class CheckingCapabilities(val message: String) : BootState()
    data class PreparingRenderer(val message: String) : BootState()
    data class Ready(val profile: DeviceProfile) : BootState()
    data class ErrorRecoverable(val message: String, val fallback: DeviceProfile?) : BootState()
}

/**
 * Visual phase of the cinematic sequence. Independent from BootState so
 * the animation can finish gracefully even if capabilities are already ready.
 */
enum class CinematicPhase {
    BLACK,
    INIT_SPACE,       // Phase A — particles / lines appear
    CORE_FORMATION,     // Phase B — converge
    LOGO_REVEAL,      // Phase C
    ENGINE_PULSE,     // Phase D
    STATUS_HOLD,      // Phase E — show real status while waiting if needed
    TRANSITION_OUT,   // Phase F
    FINISHED
}
