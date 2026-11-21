package io.github.g_c_3.ardour

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText

// Alignment of typed text (DECISIONS.md 120): while the text, or the hint of an empty field, is shorter than the field it
// is centred and stays centred as it grows; once it fills the width it starts at the left. An address that was already
// in the field keeps the left start until the field is emptied.
class TypedAlign(private val edit: EditText) {
    private var centred = true
    private var gravityNow = -1

    init {
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) centred = true
                refresh()
            }
        })
        edit.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) refresh()
        }
    }

    // The field opens: an existing address starts at the left, an empty field is centred. Call before its text is set.
    fun reset(hasAddress: Boolean) {
        centred = !hasAddress
        refresh()
    }

    // Measures the fit again (text, width or font size changed) and sets the gravity.
    fun refresh() {
        val room = edit.width - edit.paddingLeft - edit.paddingRight
        val shown = edit.text.toString().ifEmpty { edit.hint?.toString().orEmpty() }
        val fits = room > 0 && edit.paint.measureText(shown) <= room
        val wanted = if (centred && fits) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
        if (wanted != gravityNow) {
            gravityNow = wanted
            edit.gravity = wanted
        }
    }
}
