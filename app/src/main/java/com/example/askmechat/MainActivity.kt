package com.example.askmechat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.askmechat.presentation.chat.ChatBoatFragment

/**
 * Single-activity host for the chat feature.
 *
 * An AppCompatActivity that hosts [ChatBoatFragment] — the app uses a
 * single-Activity / Fragment-based architecture so the chat screen owns
 * the full window.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ChatBoatFragment.newInstance())
                .commit()
        }
    }
}
