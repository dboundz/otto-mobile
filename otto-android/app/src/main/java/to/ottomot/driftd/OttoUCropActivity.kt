package to.ottomot.driftd

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.yalantis.ucrop.R
import com.yalantis.ucrop.UCropActivity

/**
 * uCrop's layout predates edge-to-edge: the toolbar has no status-bar / cutout padding, so the
 * crop confirm action (toolbar menu checkmark) can sit under the status bar on some devices
 * (see Yalantis/uCrop#913, #951). We request decor fitting and pad the toolbar by [statusBars]
 * insets so menu actions stay in a tappable region.
 */
class OttoUCropActivity : UCropActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, windowInsets ->
            val top = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (view.paddingTop != top) {
                view.setPadding(view.paddingLeft, top, view.paddingRight, view.paddingBottom)
            }
            windowInsets
        }
        ViewCompat.requestApplyInsets(toolbar)
    }
}
