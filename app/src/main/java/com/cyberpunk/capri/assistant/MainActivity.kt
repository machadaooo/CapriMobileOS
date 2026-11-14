package com.cyberpunk.capri.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val btn = Button(this).apply {
            text = "Ativar Capri Overlay"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    startService(Intent(this@MainActivity, CapriOverlayService::class.java))
                    Toast.makeText(this@MainActivity, "Capri ativada!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
        setContentView(btn)

        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, CapriOverlayService::class.java))
            finish()
        }
    }
}
