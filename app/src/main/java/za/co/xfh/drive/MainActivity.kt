package za.co.xfh.drive

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*

class MainActivity : Activity() {
    private val base = Color.rgb(7, 14, 26)
    private val panel = Color.rgb(21, 35, 54)
    private val white = Color.rgb(239, 246, 255)
    private val subtle = Color.rgb(157, 180, 208)
    private val blue = Color.rgb(83, 185, 255)
    private val prefs by lazy { getSharedPreferences("drive_xfh", MODE_PRIVATE) }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun background(c: Int): GradientDrawable = GradientDrawable().apply {
        setColor(c); cornerRadius = dp(14).toFloat()
    }
    private fun text(s: String, size: Float = 17f, c: Int = white, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = s; textSize = size; setTextColor(c)
            gravity = Gravity.CENTER_VERTICAL
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(7), dp(4), dp(7), dp(4))
        }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = base
        window.navigationBarColor = base
        if (prefs.getBoolean("awake", true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        show("Home")
    }
    private fun tile(name: String, sub: String, glyph: String, color: Int, action: () -> Unit): View =
        column().apply {
            background = background(panel)
            setPadding(dp(10), dp(8), dp(8), dp(8))
            addView(text(glyph, 25f, color), LinearLayout.LayoutParams(-1, dp(42)))
            addView(text(name, 17f, white, true))
            addView(text(sub, 11f, subtle))
            setOnClickListener { action() }
            isClickable = true
        }
    private fun addTiles(target: LinearLayout, data: List<Triple<String, String, String>>) {
        data.chunked(3).forEach { chunk ->
            val line = row()
            chunk.forEach { (name, sub, glyph) ->
                val colors = mapOf("Navigation" to blue, "Media" to 0xffd88fff.toInt(), "Phone" to 0xff6cebb5.toInt(), "Applications" to 0xffffbc79.toInt(), "Settings" to 0xffaab2ff.toInt(), "Mirroring" to 0xff67d9dd.toInt())
                val view = tile(name, sub, glyph, colors[name] ?: blue) {
                    when (name) {
                        "Navigation" -> open(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).setPackage("com.google.android.apps.maps"), Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))
                        "Media" -> show("Media")
                        "Phone" -> open(Intent(Intent.ACTION_DIAL))
                        "Applications" -> show("Apps")
                        "Settings" -> show("Settings")
                        "Mirroring" -> launchPackage("com.mirroring.suit")
                    }
                }
                line.addView(view, LinearLayout.LayoutParams(0, dp(133), 1f).apply { setMargins(dp(5),dp(5),dp(5),dp(5)) })
            }
            target.addView(line)
        }
    }
    private fun open(intent: Intent, fallback: Intent? = null) {
        try { startActivity(intent) }
        catch (_: Exception) {
            if (fallback != null) try { startActivity(fallback) } catch (_: Exception) { toast("App not available") }
            else toast("App not available")
        }
    }
    private fun launchPackage(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) open(intent) else toast("Install or open this app on the phone first")
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun show(page: String) {
        val root = column().apply { setBackgroundColor(base) }
        setContentView(root)
        val header = row().apply {
            setPadding(dp(16), dp(9), dp(14), dp(9))
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("◉  DRIVE XFH", 20f, blue, true), LinearLayout.LayoutParams(0, dp(43), 1f))
        header.addView(text(page.uppercase(), 14f, subtle, true))
        root.addView(header)
        val scroll = ScrollView(this)
        val body = column().apply { setPadding(dp(12),dp(8),dp(12),dp(8)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        when (page) {
            "Home" -> {
                body.addView(text("YOUR DRIVE  •  CONNECTED DASHBOARD", 12f, subtle, true))
                addTiles(body, listOf(
                    Triple("Navigation","Google Maps","➤"),Triple("Media","Music and audio","♫"),Triple("Phone","Dialler","☎"),
                    Triple("Applications","Installed apps","▦"),Triple("Settings","Preferences","⚙"),Triple("Mirroring","AMirroring Suite","▣")
                ))
            }
            "Apps" -> {
                body.addView(text("INSTALLED APPLICATIONS", 15f, subtle, true))
                val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
                apps.forEach { info ->
                    val name = info.loadLabel(packageManager).toString()
                    val b = text("›  $name", 16f)
                    b.background = background(panel)
                    b.setOnClickListener { open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(info.activityInfo.packageName,info.activityInfo.name)) }
                    body.addView(b, LinearLayout.LayoutParams(-1,dp(50)).apply { setMargins(0,dp(3),0,dp(3)) })
                }
            }
            "Media" -> {
                body.addView(text("MUSIC & MEDIA", 20f, blue, true))
                body.addView(text("Choose a music app below. Media playback controls arrive in a later version.", 14f, subtle))
                listOf("com.spotify.music","com.google.android.youtube","com.google.android.apps.youtube.music").forEach { pkg ->
                    val name = when(pkg) { "com.spotify.music" -> "Spotify"; "com.google.android.youtube" -> "YouTube"; else -> "YouTube Music" }
                    val b = text("♫  $name", 18f)
                    b.background = background(panel)
                    b.setOnClickListener { launchPackage(pkg) }
                    body.addView(b, LinearLayout.LayoutParams(-1,dp(60)).apply { setMargins(0,dp(5),0,dp(5)) })
                }
            }
            "Settings" -> {
                body.addView(text("DRIVE XFH SETTINGS", 20f, blue, true))
                val check = CheckBox(this).apply {
                    text = "Keep phone awake while dashboard is open"
                    setTextColor(white)
                    isChecked = prefs.getBoolean("awake",true)
                    setOnCheckedChangeListener { _, checked ->
                        prefs.edit().putBoolean("awake",checked).apply()
                        if (checked) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                body.addView(check)
                body.addView(text("Version 0.1 • Android 13 • Landscape • Uses existing mirroring transport", 13f, subtle))
            }
        }
        val nav = row().apply { setBackgroundColor(Color.rgb(12,23,40)); setPadding(dp(8),dp(4),dp(8),dp(4)) }
        listOf("Home","Apps","Media","Settings").forEach { name ->
            val b = text(name, 14f, if (name==page) blue else subtle, true).apply { gravity = Gravity.CENTER }
            b.setOnClickListener { show(name) }
            nav.addView(b, LinearLayout.LayoutParams(0,dp(41),1f))
        }
        root.addView(nav)
    }
}
