package com.vault.commandcenter.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Gravity
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.vault.commandcenter.databinding.ActivityDashboardBinding
import com.vault.commandcenter.service.VaultMonitorService
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    // Firebase refs — using YOUR exact field names from the database
    private val refAuth   = Firebase.database.reference.child("Vault").child("Auth")
    private val refStatus = Firebase.database.reference.child("Vault").child("Status")
    private val refUser   = Firebase.database.reference.child("Vault").child("User")
    private val refPing   = Firebase.database.reference.child("Vault").child("Log_Ping")
    private val refUsers  = Firebase.database.reference.child("Vault").child("Authorized_Users")

    private lateinit var authListener: ValueEventListener
    private lateinit var statusListener: ValueEventListener
    private lateinit var userListener: ValueEventListener
    private lateinit var pingListener: ValueEventListener
    private lateinit var usersListener: ValueEventListener

    private val handler = Handler(Looper.getMainLooper())
    private var lastPingEpoch  = 0L
    private var currentUser    = ""
    private var lastUserList   = listOf<String>()

    private val flashAnim = AlphaAnimation(1f, 0.15f).apply {
        duration = 500; repeatMode = Animation.REVERSE; repeatCount = Animation.INFINITE
    }

    private val heartbeat = object : Runnable {
        override fun run() { checkHeartbeat(); handler.postDelayed(this, 10_000L) }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ContextCompat.startForegroundService(
            this, Intent(this, VaultMonitorService::class.java))

        attachListeners()
        handler.post(heartbeat)

        binding.btnSignOut.setOnClickListener { signOut() }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        refAuth.removeEventListener(authListener)
        refStatus.removeEventListener(statusListener)
        refUser.removeEventListener(userListener)
        refPing.removeEventListener(pingListener)
        refUsers.removeEventListener(usersListener)
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private fun attachListeners() {

        authListener = listener { s ->
            val v = s.getValue(String::class.java) ?: return@listener
            binding.tvAuthState.text = v
            binding.tvAuthState.setTextColor(
                Color.parseColor(if (v == "LOCKED") "#f85149" else "#3fb950"))
        }
        refAuth.addValueEventListener(authListener)

        statusListener = listener { s ->
            val v = s.getValue(String::class.java) ?: return@listener
            binding.tvVaultStatus.text = v
            val col = when {
                v.startsWith("SECURE")   -> "#3fb950"
                v.startsWith("WARNING")  -> "#d29922"
                v.startsWith("CRITICAL") -> "#f85149"
                v.startsWith("BREACH")   -> "#ff4444"
                else                     -> "#8b949e"
            }
            binding.tvVaultStatus.setTextColor(Color.parseColor(col))
            if (v.startsWith("BREACH") || v.startsWith("CRITICAL") || v.startsWith("WARNING"))
                binding.tvVaultStatus.startAnimation(flashAnim)
            else
                binding.tvVaultStatus.clearAnimation()
        }
        refStatus.addValueEventListener(statusListener)

        userListener = listener { s ->
            currentUser = s.getValue(String::class.java) ?: "None"
            binding.tvActiveUser.text = currentUser
            buildUserRows(lastUserList)   // refresh highlight
        }
        refUser.addValueEventListener(userListener)

        pingListener = listener { s ->
            lastPingEpoch = when (val raw = s.value) {
                is Long   -> raw
                is String -> raw.toLongOrNull() ?: 0L
                else      -> 0L
            }
            if (lastPingEpoch > 0L) {
                val sdf = SimpleDateFormat("HH:mm:ss  dd/MM/yy", Locale.getDefault())
                binding.tvLastPing.text = sdf.format(Date(lastPingEpoch * 1000L))
            } else {
                binding.tvLastPing.text = "Waiting for ESP32 ping…"
            }
        }
        refPing.addValueEventListener(pingListener)

        // Authorized_Users — keys are "1","2","3","4", values are plain names
        usersListener = listener { s ->
            val names = s.children
                .mapNotNull { it.getValue(String::class.java) }
            lastUserList = names
            binding.tvUserCount.text = "${names.size} users"
            binding.tvUsersEmpty.visibility =
                if (names.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            buildUserRows(names)
        }
        refUsers.addValueEventListener(usersListener)
    }

    // Tiny helper so each listener doesn't repeat the boilerplate
    private fun listener(block: (DataSnapshot) -> Unit) = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) = block(s)
        override fun onCancelled(e: DatabaseError) {}
    }

    // ── User list rendering ────────────────────────────────────────────────────

    private fun buildUserRows(names: List<String>) {
        val container = binding.layoutUserList
        container.removeAllViews()
        val dp = resources.displayMetrics.density

        names.forEachIndexed { index, name ->
            val isActive = name.equals(currentUser, ignoreCase = true)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
            }

            // Avatar circle
            val avatarSize = (30 * dp).toInt()
            val avatar = TextView(this).apply {
                text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor(if (isActive) "#0d1117" else "#58a6ff"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(if (isActive) "#3fb950" else "#0d2340"))
                }
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).also {
                    it.setMargins(0, 0, (12 * dp).toInt(), 0)
                }
            }

            // Name + subtitle column
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvName = TextView(this).apply {
                text = name
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor(if (isActive) "#3fb950" else "#e6edf3"))
            }
            val tvSub = TextView(this).apply {
                text = if (isActive) "● currently active" else "authorized"
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor(if (isActive) "#238636" else "#8b949e"))
            }

            col.addView(tvName)
            col.addView(tvSub)
            row.addView(avatar)
            row.addView(col)
            container.addView(row)

            // Divider between rows (not after the last one)
            if (index < names.size - 1) {
                container.addView(android.view.View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.setMargins(0, (3 * dp).toInt(), 0, (3 * dp).toInt()) }
                    setBackgroundColor(Color.parseColor("#21262d"))
                })
            }
        }
    }

    // ── Heartbeat ──────────────────────────────────────────────────────────────

    private fun checkHeartbeat() {
        if (lastPingEpoch == 0L) return

        val delta = System.currentTimeMillis() / 1000L - lastPingEpoch
        ObjectAnimator.ofInt(binding.progressPing, "progress",
            delta.coerceIn(0, 300).toInt()).setDuration(400).start()

        binding.progressPing.progressTintList =
            android.content.res.ColorStateList.valueOf(Color.parseColor(when {
                delta < 120  -> "#3fb950"
                delta < 240  -> "#d29922"
                else         -> "#f85149"
            }))

        if (delta > 300) {
            binding.tvHeartbeat.text = "⚠  SYSTEM OFFLINE — CONNECTION LOST"
            binding.tvHeartbeat.setTextColor(Color.parseColor("#f85149"))
            binding.tvHeartbeat.startAnimation(flashAnim)
        } else {
            binding.tvHeartbeat.text = "●  SYSTEM ONLINE"
            binding.tvHeartbeat.setTextColor(Color.parseColor("#3fb950"))
            binding.tvHeartbeat.clearAnimation()
        }
    }

    private fun signOut() {
        stopService(Intent(this, VaultMonitorService::class.java))
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}