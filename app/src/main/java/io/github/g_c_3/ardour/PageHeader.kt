package io.github.g_c_3.ardour

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout

// Top row of Settings and Diagnostics (DECISIONS.md 149): round back button, the page title beside it, and on Settings the
// search button at the right end. The title fades as the search field unfolds over it.
class PageHeader(context: Context, titleText: String, onBack: () -> Unit) : FrameLayout(context) {
    private val title = Ui.title(context, titleText).apply { textSize = 28f }

    init {
        addView(BackButton(context).apply {
            contentDescription = "Back"
            setOnClickListener { onBack() }
        }, LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44), Gravity.START or Gravity.CENTER_VERTICAL))
        addView(title, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            marginStart = Ui.dp(context, 56)
        })
    }

    fun attach(field: SearchField) {
        addView(field, LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44), Gravity.END or Gravity.CENTER_VERTICAL))
    }

    // 0 = search folded (title shown), 1 = unfolded (title gone).
    fun foldTitle(unfolded: Float) {
        title.alpha = 1f - (unfolded / 0.5f).coerceIn(0f, 1f)
    }
}
