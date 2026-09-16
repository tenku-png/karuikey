package tenkupng.karuikey

import android.content.Context
import android.graphics.Typeface

/** Shared variable-font settings for the Compose settings UI and View-based keyboard. */
object KaruikeyTypeface {
    const val WIDTH = 95f

    @JvmStatic
    fun create(context: Context, weight: Int): Typeface =
        Typeface.Builder(context.assets, "roboto_flex.ttf")
            .setFontVariationSettings("'wdth' $WIDTH, 'wght' $weight")
            .build()
}
