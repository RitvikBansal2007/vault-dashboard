package com.vault.commandcenter.ui

import android.content.Intent
import android.os.*
import android.view.HapticFeedbackConstants
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.vault.commandcenter.databinding.ActivityLoginBinding
import com.vault.commandcenter.util.CryptoUtils

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val pin = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupKeypad()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
            )
        }
    }

    private fun setupKeypad() {
        val buttons = listOf(
            binding.btn1 to "1", binding.btn2 to "2", binding.btn3 to "3",
            binding.btn4 to "4", binding.btn5 to "5", binding.btn6 to "6",
            binding.btn7 to "7", binding.btn8 to "8", binding.btn9 to "9",
            binding.btn0 to "0"
        )

        buttons.forEach { (btn, digit) ->
            btn.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (pin.length < 4) {
                    pin.append(digit)
                    updateDots()
                    if (pin.length == 4) verifyPin(pin.toString())
                }
            }
        }

        binding.btnDel.setOnClickListener {
            if (pin.isNotEmpty()) {
                pin.deleteCharAt(pin.length - 1)
                updateDots()
            }
        }

        binding.btnClr.setOnClickListener { clearPin() }
    }

    private fun updateDots() {
        listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
            .forEachIndexed { i, dot -> dot.alpha = if (i < pin.length) 1f else 0.25f }
    }

    private fun clearPin() {
        pin.clear()
        updateDots()
        binding.tvError.text = ""
    }

    private fun verifyPin(raw: String) {
        val hashed = CryptoUtils.hashPin(raw)

        Firebase.database.reference
            .child("Vault").child("PIN")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    // Firebase stores it with uppercase O — normalise to lowercase
                    val stored = snap.getValue(String::class.java)
                        ?.lowercase() ?: ""
                    if (CryptoUtils.safeHashEquals(hashed, stored)) {
                        startActivity(Intent(this@LoginActivity,
                            DashboardActivity::class.java))
                        finish()
                    } else {
                        showError()
                    }
                }
                override fun onCancelled(e: DatabaseError) {
                    binding.tvError.text = "Network error"
                    clearPin()
                }
            })
    }

    private fun showError() {
        getSystemService(Vibrator::class.java)
            .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), -1))
        binding.tvError.text = "INCORRECT PIN"
        clearPin()
        Handler(Looper.getMainLooper()).postDelayed(
            { binding.tvError.text = "" }, 2000
        )
    }
}