package com.filmroll.camera.capture

import com.filmroll.camera.image.ImageAdjustments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.dsl.module

/**
 * A frame that has just been shot, on its way to the editor.
 *
 * @param fileName cache file holding the capture, already the editor's working copy.
 * @param originalFileName cache file holding the same bytes untouched, so an
 *   "original format" export still has EXIF to copy from.
 * @param filmName the stock that was loaded in the viewfinder, so the editor opens
 *   on the look the shot was framed through instead of on the raw frame.
 * @param adjustments whatever was dialled in at the viewfinder.
 */
data class CapturedPhoto(
    val fileName: String,
    val originalFileName: String,
    val filmName: String?,
    val adjustments: ImageAdjustments,
)

/**
 * Hands a capture from the camera screen to the editor.
 *
 * Voyager has no return-a-result channel and the editor is the navigator root, so
 * the shot cannot simply be a constructor argument. A single-slot relay is the
 * smallest thing that works: the camera publishes before popping, the editor's
 * screen model — which is still alive underneath — picks it up and consumes it.
 *
 * [consume] is what stops a rotation or a process-death restore from re-importing
 * the same frame forever.
 */
class CaptureRelay {

    private val _pending = MutableStateFlow<CapturedPhoto?>(null)
    val pending: StateFlow<CapturedPhoto?> = _pending

    fun publish(photo: CapturedPhoto) {
        _pending.value = photo
    }

    fun consume(photo: CapturedPhoto) {
        _pending.compareAndSet(photo, null)
    }
}

val captureRelayModule = module {
    single { CaptureRelay() }
}
