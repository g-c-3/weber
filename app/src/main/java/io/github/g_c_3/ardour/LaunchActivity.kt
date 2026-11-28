package io.github.g_c_3.ardour

import android.app.Activity
import android.content.Intent
import android.os.Bundle

// What the launcher entries (the two icon aliases) open, and nothing else (DECISIONS.md 184): it starts MainActivity and ends at
// once. When an alias is switched off, Android ends the activities that were started through it. The running browser is started
// as MainActivity itself, so it stays; only this short-lived activity ever belonged to the alias.
class LaunchActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }
}
