package com.few.passend.domain.util

import android.app.Activity
import android.view.WindowManager

fun Activity.enableSecureFlag() {
    window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
    )
}