package com.mrjshzk.riseup

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Legacy destination trampoline: redirects directly to [MainActivity] with the
 * Schedule tab open, ensuring any pending notification or shortcut lands on the
 * unified 3-tab layout.
 */
class WeekScheduleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SCHEDULE)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
