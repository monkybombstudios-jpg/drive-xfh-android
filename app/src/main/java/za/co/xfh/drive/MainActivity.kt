package za.co.xfh.drive

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val base = Color.rgb(6, 12, 22)
    private val glass = Color.rgb(25, 38, 56)
    private val glass2 = Color.rgb(33, 50, 70)
    private val bright = Color.rgb(244, 248, 255)
    private val dim = Color.rgb(170, 187, 208)
    private val blue = Color.rgb(91, 179, 255)
    private val mint = Color.rgb(116, 221, 185)
    private val amber = Color.rgb(249, 193, 126)
    private val prefs by lazy { getSharedPreferences("drive_xfh", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private var page = "Home"
    private var map: MapView? = null
    private val tick = object : Runnable {
        override fun run() {
            if (page == "Home") render("Home")
            handler.postDelayed(this, 60000)
        }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 18): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(color, Color.rgb(14, 25, 41))
    ).apply { cornerRadius = dp(radius).toFloat(); setStroke(dp(1), Color.rgb(58, 77, 100)) }
    private fun txt(value: String, size: Float = 15f, color: Int = bright, heavy: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            if (heavy) typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(3), dp(6), dp(3))
        }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun col() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun lp(w: Int = -1, h: Int = -2) = LinearLayout.LayoutParams(w,h)
    private fun space(w: Int=0,h: Int=0): View = View(this).apply { layoutParams = lp(w,h) }
    private fun card(title: String, detail: String, icon: String, accent: Int, onTap: () -> Unit): LinearLayout =
        col().apply {
            background = bg(glass)
            setPadding(dp(15),dp(12),dp(12),dp(10))
            addView(txt(icon, 26f, accent))
            addView(txt(title, 19f, bright, true))
            addView(txt(detail, 12f, dim))
            setOnClickListener { onTap() }
        }
    private fun action(name: String, onTap: () -> Unit): TextView =
        txt(name, 15f, bright, true).apply {
            background = bg(glass2,10)
            gravity = Gravity.CENTER
            setPadding(dp(14),dp(10),dp(14),dp(10))
            setOnClickListener { onTap() }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        window.statusBarColor = base
        window.navigationBarColor = base
        if(prefs.getBoolean("awake",true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render("Home")
        handler.postDelayed(tick,60000)
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        map?.onDetach()
        super.onDestroy()
    }
    override fun onPause() { map?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); map?.onResume() }
    private fun render(to: String) {
        if(page == "Map" && to != "Map") { map?.onPause(); map?.onDetach(); map=null }
        page = to
        val outer = col().apply { setBackgroundColor(base) }
        setContentView(outer)
        val top = row().apply { setPadding(dp(18),dp(8),dp(14),dp(6)) }
        top.addView(txt("◉  DRIVE XFH",21f,blue,true),LinearLayout.LayoutParams(0,dp(44),1f))
        top.addView(txt(SimpleDateFormat("EEE d MMM  •  HH:mm",Locale.getDefault()).format(Date()),14f,dim,true))
        outer.addView(top)
        val content = col().apply { setPadding(dp(12),dp(4),dp(12),dp(6)) }
        if(to == "Map") {
            outer.addView(content,LinearLayout.LayoutParams(-1,0,1f))
            showMap(content)
        } else {
            val scroller = ScrollView(this).apply { isFillViewport=true }
            scroller.addView(content)
            outer.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
            when(to) {
                "Home" -> showHome(content)
                "Media" -> showMedia(content)
                "Apps" -> showApps(content)
                "Settings" -> showSettings(content)
            }
        }
        val dock = row().apply { background = bg(Color.rgb(13,25,41),0); setPadding(dp(8),dp(3),dp(8),dp(3)) }
        listOf("Home" to "⌂","Map" to "➤","Media" to "♫","Apps" to "▦","Settings" to "⚙").forEach { (name,icon)->
            val button = txt("$icon  $name",14f,if(to==name)blue else dim,true).apply {
                gravity=Gravity.CENTER; setOnClickListener { if(page!=name) render(name) }
            }
            dock.addView(button,LinearLayout.LayoutParams(0,dp(42),1f))
        }
        outer.addView(dock)
    }
    private fun showHome(target: LinearLayout) {
        val feature = row()
        val left = col().apply {
            background=bg(glass2)
            setPadding(dp(17),dp(13),dp(17),dp(13))
            addView(txt("GOOD TO SEE YOU",11f,blue,true))
            addView(txt(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),39f,bright,true))
            addView(txt(SimpleDateFormat("EEEE, d MMMM yyyy",Locale.getDefault()).format(Date()),14f,dim))
            addView(txt("Your drive, your way.",12f,mint))
        }
        feature.addView(left,LinearLayout.LayoutParams(0,dp(165),1.12f).apply { setMargins(dp(3),0,dp(5),0) })
        feature.addView(card("NOW PLAYING",nowTitle(),"♫",blue){render("Media")},LinearLayout.LayoutParams(0,dp(165),1f).apply {setMargins(dp(5),0,dp(3),0)})
        target.addView(feature)
        target.addView(txt("QUICK ACCESS",12f,dim,true))
        val actions=row()
        listOf(Triple("NAVIGATION","➤",blue),Triple("MEDIA","♫",mint),Triple("PHONE","☎",amber),Triple("APPS","▦",blue)).forEach { (name,icon,accent)->
            val tile=card(name,"$icon  Open",icon,accent){
                when(name){ "NAVIGATION"->render("Map");"MEDIA"->render("Media");"PHONE"->open(Intent(Intent.ACTION_DIAL)); else->render("Apps") }
            }
            actions.addView(tile,LinearLayout.LayoutParams(0,dp(126),1f).apply {setMargins(dp(3),dp(4),dp(3),0)})
        }
        target.addView(actions)
    }
    private fun controller(): MediaController? {
        return try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val active=manager.getActiveSessions(ComponentName(this,MediaAccessService::class.java))
            active.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING } ?: active.firstOrNull()
        } catch (_:SecurityException) {null}
    }
    private fun nowTitle():String = controller()?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Connect your music"
    private fun showMedia(target: LinearLayout) {
        val title = txt("MEDIA CENTRE",22f,blue,true)
        target.addView(title)
        val mc=controller()
        if(mc==null) {
            target.addView(txt("Enable Notification Access for Drive XFH to control active music apps without leaving the dashboard.",15f,dim))
            target.addView(action("ENABLE MEDIA ACCESS"){open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))})
            target.addView(txt("Start music in your favourite app once; then come back to Drive XFH.",12f,dim))
            return
        }
        val display=row()
        val art=mc.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?:mc.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if(art!=null) {
            display.addView(ImageView(this).apply {setImageBitmap(art);scaleType=ImageView.ScaleType.CENTER_CROP; background=bg(glass2)},
                lp(dp(140),dp(140)))
        } else {
            display.addView(txt("♫",54f,blue).apply{background=bg(glass2);gravity=Gravity.CENTER},lp(dp(140),dp(140)))
        }
        val details=col().apply {
            setPadding(dp(20),dp(12),dp(10),dp(8))
            addView(txt(nowTitle(),22f,bright,true))
            addView(txt(mc.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown artist",15f,dim))
            addView(txt(mc.packageName,11f,blue))
        }
        display.addView(details,LinearLayout.LayoutParams(0,dp(140),1f))
        target.addView(display)
        val buttons=row()
        listOf("⏮" to { mc.transportControls.skipToPrevious() },
            (if(mc.playbackState?.state==android.media.session.PlaybackState.STATE_PLAYING) "Ⅱ" else "▶") to {
                if(mc.playbackState?.state==android.media.session.PlaybackState.STATE_PLAYING) mc.transportControls.pause() else mc.transportControls.play()
            }, "⏭" to {mc.transportControls.skipToNext()}).forEach { (label,cb)->
            val b=action(label){cb();handler.postDelayed({if(page=="Media")render("Media")},350)}
            buttons.addView(b,LinearLayout.LayoutParams(0,dp(62),1f).apply {setMargins(dp(4),dp(5),dp(4),0)})
        }
        target.addView(buttons)
        target.addView(action("REFRESH NOW PLAYING"){render("Media")})
    }
    private fun showMap(target: LinearLayout) {
        val head=row()
        head.addView(txt("NAVIGATION  •  OpenStreetMap",17f,bright,true),LinearLayout.LayoutParams(0,dp(39),1f))
        head.addView(action("⌖  MY LOCATION"){moveToUser()})
        target.addView(head)
        val m=MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(-32.98,27.87))
        }
        map=m
        target.addView(m,LinearLayout.LayoutParams(-1,0,1f))
        target.addView(txt("Map tiles require data. Pan and pinch to zoom. Route guidance is planned for a later release.",11f,dim))
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) enableLocation()
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),100)
    }
    private fun enableLocation() {
        val m=map ?:return
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return
        try {
            val loc=MyLocationNewOverlay(GpsMyLocationProvider(this),m)
            loc.enableMyLocation()
            m.overlays.add(loc)
        } catch (_:Exception) {}
    }
    private fun moveToUser() {
        val m=map ?:return
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),100);return
        }
        enableLocation()
        val manager=getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        try {
            val last=manager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?:manager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if(last!=null) {m.controller.animateTo(GeoPoint(last.latitude,last.longitude));m.controller.setZoom(16.0)}
            else Toast.makeText(this,"Waiting for a location fix",Toast.LENGTH_SHORT).show()
        }catch(_:SecurityException){}
    }
    override fun onRequestPermissionsResult(req:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(req,permissions,grantResults)
        if(req==100 && grantResults.any{it==PackageManager.PERMISSION_GRANTED})enableLocation()
    }
    private fun showApps(target:LinearLayout) {
        target.addView(txt("APPLICATIONS  •  External shortcuts",19f,blue,true))
        target.addView(txt("External apps may not stay landscape. Use the built-in Media and Map pages where possible.",12f,dim))
        val installed=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        installed.forEach { info->
            val label=info.loadLabel(packageManager).toString()
            target.addView(action("↗  $label"){
                open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(info.activityInfo.packageName,info.activityInfo.name))
            },lp(-1,dp(49)))
            target.addView(space(h=dp(4)))
        }
    }
    private fun showSettings(target:LinearLayout) {
        target.addView(txt("SETTINGS",22f,blue,true))
        val check=CheckBox(this).apply {
            text="Keep the display awake during Drive XFH"
            setTextColor(bright);isChecked=prefs.getBoolean("awake",true)
            setOnCheckedChangeListener { _,flag->
                prefs.edit().putBoolean("awake",flag).apply()
                if(flag)window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        target.addView(check)
        target.addView(action("MEDIA PERMISSIONS"){open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))})
        target.addView(space(h=dp(8)))
        target.addView(action("OPEN AMIRRORING SUITE"){launchPackage("com.mirroring.suit")})
        target.addView(txt("Drive XFH v0.2  •  Android 13  •  iDrive-inspired prototype",12f,dim))
        target.addView(txt("Independent mirroring, route guidance and call integration are not implemented yet.",12f,dim))
    }
    private fun open(intent:Intent) {
        try{startActivity(intent)}catch(_:Exception){Toast.makeText(this,"No compatible application found",Toast.LENGTH_SHORT).show()}
    }
    private fun launchPackage(pkg:String) {
        val intent=packageManager.getLaunchIntentForPackage(pkg)
        if(intent!=null)open(intent)else Toast.makeText(this,"AMirroring Suite not detected",Toast.LENGTH_SHORT).show()
    }
}
