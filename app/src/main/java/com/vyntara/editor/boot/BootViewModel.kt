package com.vyntara.editor.boot

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vyntara.editor.VyntaraApp
import com.vyntara.editor.capabilities.CapabilityChecker
import com.vyntara.editor.capabilities.DeviceProfile
import com.vyntara.editor.data.PreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: PreferencesRepository =
        (application as VyntaraApp).preferencesRepository

    private val checker = CapabilityChecker(application)

    private val _bootState = MutableStateFlow<BootState>(BootState.Idle)
    val bootState: StateFlow<BootState> = _bootState.asStateFlow()

    private val _cinematicPhase = MutableStateFlow(CinematicPhase.BLACK)
    val cinematicPhase: StateFlow<CinematicPhase> = _cinematicPhase.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _canSkip = MutableStateFlow(false)
    val canSkip: StateFlow<Boolean> = _canSkip.asStateFlow()

    private val _navigateToEditor = MutableStateFlow(false)
    val navigateToEditor: StateFlow<Boolean> = _navigateToEditor.asStateFlow()

    private val _deviceProfile = MutableStateFlow<DeviceProfile?>(null)
    val deviceProfile: StateFlow<DeviceProfile?> = _deviceProfile.asStateFlow()

    private var initJob: Job? = null
    private var phaseJob: Job? = null
    private var capabilitiesReady = false
    private var animationFinished = false
    private var userSkipped = false
    private var cinematicEnabled = true
    private var reduceMotion = false

    fun startBoot() {
        if (initJob?.isActive == true) return
        initJob = viewModelScope.launch {
            _bootState.value = BootState.Initializing
            _statusMessage.value = "Inicializando entorno…"

            cinematicEnabled = prefs.cinematicEnabled.first()
            val firstRun = !prefs.firstRunDone.first()

            // Accessibility: reduce motion
            reduceMotion = try {
                Settings.Global.getFloat(
                    getApplication<Application>().contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f
                ) == 0f
            } catch (_: Exception) {
                false
            }

            if (!cinematicEnabled || reduceMotion) {
                // Fast path
                _cinematicPhase.value = CinematicPhase.STATUS_HOLD
                runCapabilityCheck()
                finishWhenReady()
                return@launch
            }

            // Start cinematic phases in parallel with real work
            launchCinematicSequence(firstRun)
            runCapabilityCheck()
        }
    }

    private suspend fun runCapabilityCheck() {
        _bootState.value = BootState.CheckingCapabilities("Comprobando capacidades multimedia…")
        _statusMessage.value = "Comprobando capacidades multimedia…"

        val appVersion = try {
            val pm = getApplication<Application>().packageManager
            val info = pm.getPackageInfo(getApplication<Application>().packageName, 0)
            @Suppress("DEPRECATION")
            info.versionCode
        } catch (_: Exception) {
            1
        }

        val cached = prefs.getCachedProfile(appVersion, Build.VERSION.SDK_INT)
        if (cached != null) {
            _deviceProfile.value = cached
            _statusMessage.value = "Compatibilidad multimedia comprobada"
            _bootState.value = BootState.PreparingRenderer("Preparando renderizado…")
            delay(120) // tiny settle so UI can show the message
            _bootState.value = BootState.Ready(cached)
            capabilitiesReady = true
            tryFinish()
            return
        }

        try {
            val profile = checker.evaluate { progress ->
                _statusMessage.value = progress.message
                _bootState.value = when (progress.stage) {
                    "CODECS" -> BootState.CheckingCapabilities(progress.message)
                    "GPU", "MEMORY" -> BootState.PreparingRenderer(progress.message)
                    else -> BootState.PreparingRenderer(progress.message)
                }
            }
            prefs.saveProfile(profile, appVersion, Build.VERSION.SDK_INT)
            _deviceProfile.value = profile
            _statusMessage.value = "Motor gráfico preparado"
            _bootState.value = BootState.Ready(profile)
            capabilitiesReady = true
            tryFinish()
        } catch (e: Exception) {
            val fallback = DeviceProfile(
                androidSdk = Build.VERSION.SDK_INT,
                supportedVideoDecoders = emptyList(),
                supportedVideoEncoders = emptyList(),
                supportedAudioDecoders = emptyList(),
                supportedAudioEncoders = emptyList(),
                glEsVersion = "unknown",
                hasVulkan = false,
                maxTextureSize = 2048,
                availableMemoryMb = 0,
                previewResolutionCap = 720,
                preferHardwareDecode = false,
                preferHardwareEncode = false,
                effectQuality = DeviceProfile.EffectQuality.LOW,
                notes = listOf("Perfil seguro por error de comprobación: ${e.message}")
            )
            _deviceProfile.value = fallback
            _bootState.value = BootState.ErrorRecoverable(
                "No se pudieron comprobar todas las capacidades. Usando perfil seguro.",
                fallback
            )
            _statusMessage.value = "Perfil seguro aplicado"
            capabilitiesReady = true
            tryFinish()
        }
    }

    private fun launchCinematicSequence(firstRun: Boolean) {
        phaseJob = viewModelScope.launch {
            val scale = if (firstRun) 1f else 0.65f // shorter on subsequent launches

            // Phase A
            _cinematicPhase.value = CinematicPhase.INIT_SPACE
            delay((900 * scale).toLong())
            _canSkip.value = true

            // Phase B
            _cinematicPhase.value = CinematicPhase.CORE_FORMATION
            delay((1100 * scale).toLong())

            // Phase C
            _cinematicPhase.value = CinematicPhase.LOGO_REVEAL
            delay((1200 * scale).toLong())

            // Phase D
            _cinematicPhase.value = CinematicPhase.ENGINE_PULSE
            delay((800 * scale).toLong())

            // Phase E — hold until capabilities ready or user skips
            _cinematicPhase.value = CinematicPhase.STATUS_HOLD
            // Wait until capabilitiesReady or skip, with a soft max so we never hang forever
            var waited = 0L
            while (!capabilitiesReady && !userSkipped && waited < 8000) {
                delay(50)
                waited += 50
            }

            // Phase F
            _cinematicPhase.value = CinematicPhase.TRANSITION_OUT
            delay((600 * scale).toLong())

            animationFinished = true
            _cinematicPhase.value = CinematicPhase.FINISHED
            tryFinish()
        }
    }

    fun skipAnimation() {
        if (!_canSkip.value) return
        userSkipped = true
        phaseJob?.cancel()
        viewModelScope.launch {
            _cinematicPhase.value = CinematicPhase.STATUS_HOLD
            // Still wait for essential checks
            var waited = 0L
            while (!capabilitiesReady && waited < 5000) {
                delay(40)
                waited += 40
            }
            _cinematicPhase.value = CinematicPhase.TRANSITION_OUT
            delay(350)
            animationFinished = true
            _cinematicPhase.value = CinematicPhase.FINISHED
            tryFinish()
        }
    }

    private fun tryFinish() {
        if (capabilitiesReady && (animationFinished || userSkipped || !cinematicEnabled || reduceMotion)) {
            finishWhenReady()
        }
    }

    private fun finishWhenReady() {
        viewModelScope.launch {
            prefs.setFirstRunDone()
            _navigateToEditor.value = true
        }
    }

    fun onEditorEntered() {
        // Release any boot-only resources if needed
    }
}
