package com.cyberpunk.capri.assistant

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class CapriOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_capri, null)

        val LAYOUT_FLAG = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            LAYOUT_FLAG,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        windowManager.addView(overlayView, layoutParams)

        val txtSpeech = overlayView.findViewById<TextView>(R.id.txt_speech)
        val layoutChat = overlayView.findViewById<View>(R.id.layout_chat)
        val edtPergunta = overlayView.findViewById<EditText>(R.id.edt_pergunta)
        val btnEnviar = overlayView.findViewById<Button>(R.id.btn_enviar)
        val imgCapri = overlayView.findViewById<View>(R.id.img_capri)

        val frases = listOf(
            "Sistema online. O que mandas?",
            "Conexão neural ativa. Fala rápido.",
            "Estou de olho em tudo por aqui.",
            "Mais um dia na rede. Qual é o plano?"
        )

        imgCapri.setOnClickListener {
            if (txtSpeech.visibility == View.GONE) {
                txtSpeech.text = frases.random()
                txtSpeech.visibility = View.VISIBLE
                layoutChat.visibility = View.VISIBLE
                layoutParams?.flags = WindowManager.LayoutParams.FLAG_SHIT or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                layoutParams?.flags = 0 // Permite foco para digitar
                windowManager.updateViewLayout(overlayView, layoutParams)
            } else {
                txtSpeech.visibility = View.GONE
                layoutChat.visibility = View.GONE
            }
        }

        btnEnviar.setOnClickListener {
            val pergunta = edtPergunta.text.toString()
            if (pergunta.isNotBlank()) {
                txtSpeech.text = "Processando: \"$pergunta\"... Rede estável."
                edtPergunta.text.clear()
            }
        }

        // Lógica para arrastar a Capri pela tela
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        imgCapri.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams?.x ?: 0
                        initialY = layoutParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false // Deixa o clique passar se for um toque rápido
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams?.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, layoutParams)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
