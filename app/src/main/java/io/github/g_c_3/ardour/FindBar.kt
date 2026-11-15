package io.github.g_c_3.ardour

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

// Find in page: replaces the toolbar while open. Query, match count, previous, next, close.
class FindBar(
    context: Context,
    private val onQuery: (String) -> Unit,
    private val onStep: (Boolean) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {
    private val input = EditText(context)
    private val count = TextView(context)
    private val prev = IconButton(context, IconButton.Kind.UP)
    private val next = IconButton(context, IconButton.Kind.DOWN)

    init {
        orientation = VERTICAL
        addView(Ui.divider(context))
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.CARD)
            setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 4), 0)
        }
        input.apply {
            background = null
            hint = "Find in page"
            setHintTextColor(0xFF9A9CA6.toInt())
            setTextColor(Ui.INK)
            textSize = 16f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            highlightColor = 0x40E8451D
            setPadding(0, 0, Ui.dp(context, 8), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, c: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString().orEmpty()
                    if (text.isEmpty()) showCount(0, 0)
                    onQuery(text)
                }
            })
            setOnEditorActionListener { _, _, _ ->
                onStep(true)
                true
            }
        }
        count.apply {
            textSize = 13f
            setTextColor(Ui.MUTED)
            setPadding(0, 0, Ui.dp(context, 8), 0)
        }
        prev.contentDescription = "Previous match"
        next.contentDescription = "Next match"
        prev.setOnClickListener { onStep(false) }
        next.setOnClickListener { onStep(true) }
        val close = IconButton(context, IconButton.Kind.STOP).apply {
            contentDescription = "Close find"
            setOnClickListener { onClose() }
        }
        row.addView(input, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        row.addView(count, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        for (b in listOf(prev, next, close)) row.addView(b, LayoutParams(Ui.dp(context, 44), ViewGroup.LayoutParams.MATCH_PARENT))
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 56)))
    }

    // active is 0-based, as the engine reports it.
    fun showCount(active: Int, total: Int) {
        count.text = when {
            input.text.isNullOrEmpty() -> ""
            total == 0 -> "No matches"
            else -> "${active + 1} of $total"
        }
        prev.isEnabled = total > 0
        next.isEnabled = total > 0
    }

    fun open() {
        input.setText("")
        showCount(0, 0)
        input.requestFocus()
        input.post {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun close() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        rootView.requestFocus()
    }
}
