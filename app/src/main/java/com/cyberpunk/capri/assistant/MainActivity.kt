package com.cyberpunk.capri.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(this).apply {
            text = "CapriMobileOS Control"
            textSize = 20f
            setPadding(0, 0, 0, 40)
        }
        layout.addView(title)

        val btnPermission = Button(this).apply {
            text = "1. Conceder Permissão de Overlay"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Permissão já concedida!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(btnPermission)

        val btnStart = Button(this).apply {
            text = "2. Iniciar Capri Overlay"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startService(Intent(this@MainActivity, CapriOverlayService::class.java))
                    Toast.makeText(this@MainActivity, "Capri ativado!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Conceda a permissão primeiro!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(btnStart)

        setContentView(layout)
    }
}
