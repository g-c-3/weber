package io.github.g_c_3.ardour

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

// A confirmation card over the page, drawn by Ardour (DECISIONS.md 38): a dimmed backdrop, a card that rises in with a
// spring, a quiet Cancel and a glowing confirm button. A tap outside the card, or Back, cancels.
class ConfirmSheet(private val activity: Activity, private val onChange: () -> Unit) {
    private var backdrop: FrameLayout? = null
    private var card: LinearLayout? = null

    val showing: Boolean get() = backdrop != null

    fun show(title: String, body: String, confirm: String, onConfirm: () -> Unit) {
        if (backdrop != null) return
        val dim = FrameLayout(activity).apply {
            background = ColorDrawable(0x66101018)
            isClickable = true
            alpha = 0f
            setOnClickListener { dismiss() }
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.CARD)
                cornerRadius = Ui.dp(activity, 26).toFloat()
                setStroke(Ui.dp(activity, 1), 0x40E8451D)
            }
            elevation = Ui.dp(activity, 14).toFloat()
            outlineSpotShadowColor = 0x70E8451D
            setPadding(Ui.dp(activity, 24), Ui.dp(activity, 24), Ui.dp(activity, 24), Ui.dp(activity, 16))
            isClickable = true
        }
        box.addView(Ui.title(activity, title).apply { textSize = 20f })
        box.addView(
            Ui.body(activity, body).apply {
                textSize = 15f
                setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 22))
            },
        )
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val cancel = TextView(activity).apply {
            text = "Cancel"
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Ui.MUTED)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(activity, 20), Ui.dp(activity, 15), Ui.dp(activity, 20), Ui.dp(activity, 15))
            isClickable = true
            setOnClickListener { dismiss() }
        }
        Ui.springPress(cancel)
        val go = Ui.pillButton(activity, confirm).apply {
            setOnClickListener {
                dismiss()
                onConfirm()
            }
        }
        row.addView(cancel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(go, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val margin = Ui.dp(activity, 16)
        dim.addView(
            box,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(margin, margin, margin, Ui.dp(activity, 28))
            },
        )
        activity.addContentView(dim, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        backdrop = dim
        card = box
        box.translationY = Ui.dp(activity, 48).toFloat()
        dim.animate().alpha(1f).setDuration(220).start()
        box.animate().translationY(0f).setDuration(460).setInterpolator(SpringInterpolator()).start()
        onChange()
    }

    fun dismiss() {
        val dim = backdrop ?: return
        val box = card
        backdrop = null
        card = null
        box?.animate()?.translationY(Ui.dp(activity, 48).toFloat())?.setDuration(220)?.start()
        dim.animate().alpha(0f).setDuration(220).withEndAction {
            (dim.parent as? ViewGroup)?.removeView(dim)
        }.start()
        onChange()
    }
}
