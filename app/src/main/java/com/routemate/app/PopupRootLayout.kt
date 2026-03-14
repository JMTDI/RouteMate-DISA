package com.routemate.app

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.LinearLayout

/**
 * Root layout for the overlay popup. Overrides dispatchKeyEvent so that
 * hardware KEYCODE_CALL and KEYCODE_ENDCALL are caught reliably regardless
 * of which child view currently holds focus.
 */
class PopupRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Called on KEYCODE_CALL ACTION_UP — should invoke the focused button's action. */
    var onCallKey: (() -> Unit)? = null

    /** Called on KEYCODE_ENDCALL ACTION_UP — should dismiss the popup. */
    var onEndCallKey: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_CALL -> {
                    onCallKey?.invoke()
                    return true
                }
                KeyEvent.KEYCODE_ENDCALL -> {
                    onEndCallKey?.invoke()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
