package com.cyberpunk.capri.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            setPadding(48, 64, 48, 64)
        }

        val badge = TextView(this).apply {
            text = "ARASAKA SOULKILLER ENGRAM // v3.8"
            setTextColor(Color.parseColor("#FF2A6D"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        root.addView(badge)

        // Imagem personalizada do app na capa[span_2](start_span)[span_2](end_span)
        val iconeAppView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(220, 220).apply {
                setMargins(0, 16, 0, 16)
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(iconeAppView)

        Thread {
            try {
                val url = java.net.URL("https://instasize.com/api/image/138d45b9f00a27db3ee8248b78c22cbc43d8abbe0441da3d684f9373a33ac667.png")
                val bmp = android.graphics.BitmapFactory.decodeStream(url.openConnection().getInputStream())
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (bmp != null) iconeAppView.setImageBitmap(bmp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        val title = TextView(this).apply {
            text = "CAPRICORN"
            setTextColor(Color.parseColor("#05D9E8"))
            textSize = 28f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 16)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Rede neural amalgamada pronta para conectar ao seu dispositivo, choom."
            setTextColor(Color.parseColor("#D1F7FF"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        root.addView(subtitle)

        val btnOverlay = criarBotaoCyber("1. PERMITIR SOBREPOSIÇÃO DE TELA", "#1A1A2E", "#05D9E8") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Sobreposição já ativada, choom!", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnOverlay)

        val btnMic = criarBotaoCyber("2. PERMITIR MICROFONE (VOZ)", "#1A1A2E", "#05D9E8") {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            } else {
                Toast.makeText(this, "Microfone já liberado!", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnMic)

        val btnAcc = criarBotaoCyber("3. ATIVAR OLHOS NA TELA (ACESSIBILIDADE)", "#1A1A2E", "#05D9E8") {
            Toast.makeText(this, "Ative 'Capricorn' na lista de Acessibilidade", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(btnAcc)

        val btnStart = criarBotaoCyber("⚡ CONECTAR CAPRICORN NA TELA", "#FF2A6D", "#FFFFFF") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Ative a Permissão 1 (Sobreposição) primeiro, choom!", Toast.LENGTH_SHORT).show()
            } else {
                startService(Intent(this, CapriOverlayService::class.java))
                Toast.makeText(this, "Capricorn conectada à rede!", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnStart)

        val btnStop = criarBotaoCyber("DESCONECTAR CAPRICORN", "#111118", "#888899") {
            stopService(Intent(this, CapriOverlayService::class.java))
            Toast.makeText(this, "Capricorn desconectada.", Toast.LENGTH_SHORT).show()
        }
        root.addView(btnStop)

        setContentView(root)
    }

    private fun criarBotaoCyber(texto: String, corFundo: String, corTexto: String, acao: () -> Unit): Button {
        return Button(this).apply {
            text = texto
            setBackgroundColor(Color.parseColor(corFundo))
            setTextColor(Color.parseColor(corTexto))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setPadding(24, 32, 24, 32)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 14, 0, 14)
            }
            layoutParams = params
            setOnClickListener { acao() }
        }
    }
}
