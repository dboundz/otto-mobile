package to.ottomot.driftd.core.audio

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.annotation.RawRes
import to.ottomot.driftd.R

/** Short in-app UI tones — matches iOS [TabSoundPlayer]. */
object OttoTabSoundPlayer {
    fun playTabChange(app: Application) {
        play(app, R.raw.tab_change)
    }

    fun playUserSharing(app: Application) {
        play(app, R.raw.user_sharing)
    }

    fun playStartDrive(app: Application) {
        play(app, R.raw.start_drive)
    }

    fun playCheckpointComplete(app: Application) {
        play(app, R.raw.checkpoint_complete)
    }

    fun playRouteFinished(app: Application) {
        play(app, R.raw.route_finished)
    }

    fun playLevelUp(app: Application) {
        play(app, R.raw.level_up)
    }

    private fun play(
        app: Application,
        @RawRes resId: Int,
    ) {
        try {
            val player = MediaPlayer.create(app, resId) ?: return
            player.setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            player.setOnCompletionListener { it.release() }
            player.start()
        } catch (_: Throwable) {
        }
    }
}
