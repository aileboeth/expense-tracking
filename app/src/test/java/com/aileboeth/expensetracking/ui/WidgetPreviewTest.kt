package com.aileboeth.expensetracking.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aileboeth.expensetracking.widget.Widgets
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the pictures the launcher's widget picker shows (some launchers, e.g. vivo, ignore previewLayout and
 * fall back to the app icon without them). Writes straight into the res folders; run after changing a widget:
 *   gradlew :app:testDebugUnitTest -Pscreenshots=true -PtestJavaHome=D:/JDK --tests "*WidgetPreviewTest*"
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetPreviewTest {
    private fun render(dir: String) {
        assumeTrue(System.getProperty("screenshots") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val data = Widgets.Data.sample()
        val out = File("src/main/res/$dir").apply { mkdirs() }
        for ((kind, size) in listOf(
            Widgets.Kind.SUMMARY to Widgets.SizeDp(320, 210),
            Widgets.Kind.TODAY to Widgets.SizeDp(150, 150),
            Widgets.Kind.QUICK to Widgets.SizeDp(320, 56),
        )) {
            val dm = ctx.resources.displayMetrics.density
            val w = (size.w * dm).toInt()
            val h = (size.h * dm).toInt()
            val parent = FrameLayout(ctx)
            val v = Widgets.preview(ctx, kind, size, data).apply(ctx, parent)
            parent.addView(v, FrameLayout.LayoutParams(w, h))
            parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            parent.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            parent.draw(Canvas(bmp))
            File(out, "widget_preview_${kind.name.lowercase()}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Config(qualifiers = "en-rUS-xhdpi") @Test fun english() = render("drawable-nodpi")
    @Config(qualifiers = "zh-rCN-xhdpi") @Test fun simplified() = render("drawable-zh-nodpi")
    @Config(qualifiers = "b+zh+Hant+TW-xhdpi") @Test fun traditional() = render("drawable-b+zh+Hant-nodpi")
}
