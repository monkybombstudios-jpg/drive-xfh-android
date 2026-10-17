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
import android.view.MotionEvent
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
    private val base = Color.rgb(12, 12, 13)
    private val glass = Color.rgb(30, 30, 32)
    private val glass2 = Color.rgb(40, 40, 43)
    private val bright = Color.rgb(244, 248, 255)
    private val dim = Color.rgb(170, 187, 208)
    private val blue = Color.rgb(91, 179, 255)
    private val mint = Color.rgb(116, 221, 185)
    private val amber = Color.rgb(249, 193, 126)
    private val prefs by lazy { getSharedPreferences("drive_xfh", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private var tts:android.speech.tts.TextToSpeech?=null
    private var ttsReady=false
    private var lastSpoken=""
    private var rerouteBusy=false
    private var rerouteAt=0L
    private var offRouteCount=0
    private var turnListener:android.location.LocationListener?=null
    private var turnManager:android.location.LocationManager?=null
    private fun stopTurnListener(){turnListener?.let{try{turnManager?.removeUpdates(it)}catch(_:Exception){}};turnListener=null;turnManager=null}
    private fun speak(message:String){if(ttsReady && prefs.getBoolean("nav_voice",true))tts?.speak(message,android.speech.tts.TextToSpeech.QUEUE_FLUSH,null,"xfh-turn")}

    private var page = "Home"
    private var map: MapView? = null
    private var styledMap: StyledMapPanel? = null
    private var widgetIndex=0
    private var weatherText="Weather loading…"
    private var weatherLast=0L
    private fun refreshWeather(){
        if(android.os.SystemClock.elapsedRealtime()-weatherLast<1800000L)return
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return
        weatherLast=android.os.SystemClock.elapsedRealtime()
        Thread{
            try{
                val manager=getSystemService(LOCATION_SERVICE) as android.location.LocationManager
                val loc=manager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?:manager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)?:return@Thread
                val url="https://api.open-meteo.com/v1/forecast?latitude="+loc.latitude+"&longitude="+loc.longitude+
                    "&current=weather_code&daily=temperature_2m_min,temperature_2m_max&forecast_days=1&timezone=auto"
                val c=java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout=10000;c.readTimeout=12000
                val obj=try{org.json.JSONObject(c.inputStream.bufferedReader().use{it.readText()})}finally{c.disconnect()}
                val code=obj.getJSONObject("current").getInt("weather_code")
                val type=when(code){0->"☼ Sunny";1,2->"◐ Partly cloudy";3->"☁ Overcast";45,48->"≋ Fog";51,53,55,61,63,65,80,81,82->"☂ Rain";71,73,75,85,86->"❄ Snow";95,96,99->"ϟ Storm";else->"☁ Cloudy"}
                val daily=obj.getJSONObject("daily")
                val low=daily.getJSONArray("temperature_2m_min").getDouble(0).toInt()
                val high=daily.getJSONArray("temperature_2m_max").getDouble(0).toInt()
                val summary=type+" | "+low+"° to "+high+"°C | "+String.format(Locale.US,"%.2f, %.2f",loc.latitude,loc.longitude)
                runOnUiThread{weatherText=summary;if(page=="Home")drawWidgets()}
            }catch(_:Exception){runOnUiThread{weatherLast=0L;weatherText="Weather unavailable"}}
        }.start()
    }

    private var stageView:FrameLayout?=null
    private var widgetHolder: LinearLayout?=null
    private val tick = object : Runnable {
        override fun run() {
            if (page == "Home") refreshWidgetClock()
            handler.postDelayed(this, 60000)
        }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 18): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(color, Color.rgb(20, 20, 22))
    ).apply { cornerRadius = dp(radius).toFloat(); setStroke(dp(1), Color.rgb(65, 65, 70)) }
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
        org.maplibre.android.MapLibre.getInstance(this)
        tts=android.speech.tts.TextToSpeech(this){n->ttsReady=n==android.speech.tts.TextToSpeech.SUCCESS;if(ttsReady)tts?.language=Locale.UK}
        window.statusBarColor = base
        window.navigationBarColor = base
        if(prefs.getBoolean("awake",true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render("Home")
        handler.postDelayed(tick,60000)
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        stopTurnListener()
        tts?.stop();tts?.shutdown()
        map?.onDetach()
        styledMap?.dispose()
        super.onDestroy()
    }
    override fun onPause() { map?.onPause(); styledMap?.pause(); super.onPause() }
    override fun onResume() { super.onResume(); map?.onResume(); styledMap?.resume() }
    private fun render(to: String) {
        stopTurnListener()
        if((page == "Map" || page == "Home") && to != page) { map?.onPause(); map?.onDetach(); map=null; styledMap?.dispose();styledMap=null }
        page = to
        val outer = col().apply { setBackgroundColor(base) }
        setContentView(outer)
        if(to=="Home") { showCockpit(outer); return }
        // Interior page headers stay unchanged until their layouts are redesigned.
        val top = row().apply { setPadding(dp(18),dp(8),dp(14),dp(6)) }
        top.addView(txt("◉  DRIVE XFH",21f,blue,true),LinearLayout.LayoutParams(0,dp(44),1f))
        top.addView(txt(SimpleDateFormat("EEE d MMM  •  HH:mm",Locale.getDefault()).format(Date()),14f,dim,true))
        top.addView(txt("⚙",22f,dim).apply { setOnClickListener { render("Settings") } })
        if(to!="Map" && to!="Media") outer.addView(top)
        val content = col().apply { setPadding(dp(12),dp(4),dp(12),dp(6)) }
        if(to == "Map" || to == "Media") {
            outer.addView(content,LinearLayout.LayoutParams(-1,0,1f))
            if(to=="Map")showMap(content) else showMedia(content)
        } else {
            val scroller = ScrollView(this).apply { isFillViewport=true }
            scroller.addView(content)
            outer.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
            when(to) {
                "Media" -> showMedia(content)
                "Apps" -> showApps(content)
                "Settings" -> showSettings(content)
            }
        }
        outer.addView(createDock(to))
    }


    // Shared dock keeps identical iconography and interaction on every page.
    // Exact user-provided dock artwork, scaled to transparent 48px icon masks.
    private val dockIconImages = mapOf(
        "Map" to "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAAEgElEQVR42u2Z34tVVRTHP+fcexuCicw0J6cywvQh6CGVDCJpeggpEqEICgqCYfzxUA8iaQ1B/QMS9AMfgiCKxIciqIQCESMhCbJAm9L8NTUi0oMN08zcud8eWpsWm3PunHPnXE24Gzbn3L3XWWt911577bX2TSRxLbeUa7z1APQA9AD0APQALKjVu8Q3cR0gnJat/zOA1HrLutrIFDBXiaUqSCUSU9wr1A8sBxYDDeAvYAL4I2OVWlcTQM0pfhuwGdgIrAFuiWhngOPAV8B+4EgGjysKIAgeAHYCLwA32tzfwG9m8TlgEbAiAvUF8AbwbcZeKd4kddJr9nxK0nn922YlfSBpk6RbJSXRNzdIWifpdUln7JuW/cbok7K6dKJ83Z6v6L/2saR7MmhT67FiiySNSpqx7/dJauTQVgogKL/LBDclbYtWpuasmUTWTR0PJD3oVuMjxyPpBoDgNptN4Iy5UACWZnyT5KxKYhZH0ipJp43naCSrMgBB6DJJEyZsu81dV8IIMcgA4n5Jk2aU+8qAKGv9Pab8J5ECZXjcGykXeOw03l/ngO0YQGA0KOmypClb9qTEUge/f1HSZ+7bxL33STphIB4qugppiYTvaTthPwXGotM3yTidQ2sATeA5YA/wi8X71MX+BJgG3rSx4SrPgbACB806j5vV6vNsVr9Rh13c3+5cx0cn7PyYknRRUn8b3oVdKHx8s7nPpKSlGYyzDqF6pHyI+U+4+SQDxCGjW1/EjdKC7rPC3GcMuBgd+1lpQHCbYWCvuVrgNe7ofQ/zP9jv1Tnu2VFBs8Se510elKd8HZgFRkx5DEANmAIuzFMbjBvfgSorsj57Tuds1NjyI8C7NnYCeNXeL7gVzGvTkcxKAEy5PN9bXO49WH6LU/534FHgkrPutIHPyzxDRjtZBYAgZMLthZoLfYlTvglsBd6xsZPAI8BZYK0DlCXXA1ppz3NVAjhtS78SuCPD55vANuBtG/seeNjcJwEGbfxMG7ktc8EH7PdPReroIgBqVhJ+Y8oOuY1cc5Z/y8a/NJpzrua+3RkiLim9HuuBu6xyGytUcpZIA561+HzE4nafjY+4umCvi9t1l/uHBHBTdAbEMvYb3e5ofEG5UDho+iWdNQFP2txWp/yoo08dkLutWpOktRkAgpJDVl/8KWkgOtwWnI0GIVtMkWOSdtj7pKRnMoqRAGCD0V22hNCn54FmsaQxo9vRjXQ6cdXWIWf1n63OzVrukAc9b7SnJF3vAIT5PkkHjOag8SlcldFBMTIo6TtJ75nl8nw1KPiaKXfYjQf6JU75k5KWF3Wd0MvczLUsKowD66JI1mwTgu+McqBZe2609HoV8CvwmJ0TaZnLrrKXu4pCX7sw13KHn4/rQ8A+4HNT/gCwwcJmrfRNXYf3QkXT8Iak4xaFDks66vbPhKSX2tTLhXrSpb9ZQ2qwzKUhoR0DPgTet7nOb+UqutxtB6IBvAzcZC50FPjRucmC7kW7DWC+a/25Tq1+pQHUo42tKhS/2ivQ+4+sB6AHoKL2D+f9gX7TSvNCAAAAAElFTkSuQmCC",
        "Home" to "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAACX0lEQVR42u2ZsYoUQRCGv56Zg0PRRFwMTAzk0Fg0MTUQQU3uCXwBIxPfwNhHMDPwCRTuFYQ1EdHEQ830AnFvZn4Dq6EZZnd7d7pXR6ag6dmd7q7/r6ruqt51khizFIxcJgITgYnARCBKHFBZPzoCBSCgtr4YE4EKaIEZ8MT61r5PI5Jytcr6A0lz/ZG5pKud94NabvC3JX0x8D+tP5Z0KxWJ1MBdAOowAH3a6U8k3UtBIjX40p4fG9BWUm19G3yWpEbSIxtf2vy/RqAIADwzgLWBbHuafydJT3vW2CkBb/U9SS8M1GIFeN+aIKSed4yxMwIe/AVJrwPw7QZtYfNeStrvrJuVgN98VyS93RJ8l8SRGSOahNvyRlZYQroBvAIuA6cDE1QN7AHvgPvAh0BP0kzsF70LvDHwdYLsWpkRrgNHwE3TsxLjph5wVs/MgI/AGQNfJiw/vCe+AgfAD180pPCArybPmcWUoZ4qbd3zpkerqthtlTfrFk5Qfjfr4p8BcesiwbdLXO8ijBeloyKvrNobigzXLAQU8b6wo/A42PzOvHIRuGbPbujRtW0IrQLvY/gBMO8ZMwPe20YdRCKXB7zFf5knSiPkc0htZ36SK1/Oi3wZWFj2HGtxRRhq0BmuDcZoSZgxdCPn2AMxhJPljyKj9QeHR+4QyuWpnYRQ98eqvhywLs6XzScoIbIRWAD7kTXTshLj7Br9Mj1JCfgj8DNwB7hkIF0n2wKcAJ86FvXW/g48tISmnpgvTMe3IHsnuQ/8czIkkZURMd5knP9/eGD6g2MiMBGYCEwEJgKjlt+QV0gZTIInVwAAAABJRU5ErkJggg==",
        "Apps" to "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAACD0lEQVR42u2YvW4UMRDHf/buQgJFuggkqKMoEqLKE9Gc8jY8QR4BypSUFLRQIEVKESo+CghCsLdDMytZlnft/dSd4pGs270dW/P3zH9mbCMi7LNY9lwygAwgA8gAMoAMIAPIADKADCADGC3lAN0CMBGdRodR/ZjUUwGYfT+RpXjAAAK8Ap7rrlnPG1ugAt4CH4BjYOPMDYXuX+A18LNHLy4iEhtGfz9JXC5U96WkyTPVtwl2BMcQDnzX3a8D8V2rB3477//aPfK8JeqBX8oXRu/+CBKXznNoLeuEXRkBUAXCdNE0ahJ0mgHzxDF6tAfmrgOmw9ApmzIbABkAwCQaKWukUTdVtiP0zToh1CiRTQcHpGOdRQEcKXmLnnUeOiSvEtazawK4Aq41PRZe5mh0rc/6/gN4owa6HhDn/Y+TdnMrkSJPgEPd7a7c/lULVAU87eFAy5fbqVxI8UBrxDvgvCOE2l5oA1wCZ8B7R8cGwu0OeAF88RLAYh54BBwADwLk2wYq9eOEoje5kA1NoxJJoxI4FzQdIbR6GjXeCH3rm+P/b73Ct3gvNLRax4ySOSryUr2QHQF4J3ohCXSaUzvcWQH0ZQ1JMEgCYzUSl95BJXRjYTyS9nWm5RweGNJKnGgtkA6CWuAG+Kb14tTzjHgAa+Dj1KuVe9UL2cjx0HjVtUjgQ7NmCO2k5MvdDOC+A/gPUek3lHwP5VwAAAAASUVORK5CYII=",
        "Media" to "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAADEUlEQVR42u2Yv2sUQRTHP7N7UaKNv+KvRhAkAUFNCptoxEbBQitLO9HaSqz8L6zU2kpLEaxiEwVBRFASTCVEUEEFyY/78bXwDQzj3t3u3WY3gXvwmON2Zvb7fe/Nm/fWSWI7S8I2lxGBEYERgRGBeqWxRXA4M6YDBLRzL6zpIgsBtw30YBtVRKAf4EPANDAHLAOPbY7qCqEQcMc0DIuDBvgCMAucAvYEzxeBeSDtF05lEkh7AJ4AzgDnzMqngb3R+jbQBMaMYOWHOAS8zwDPmYWngf0Z8zuBtxLDkwKtqgj4rJEY2DngvFl4og9gP/qsk9aRRj2BB8DtnIB9eDUy7qGOjaqCgAsAXrLfTbNkL8ChpZeBd8ALYAa4VfSCbZSUcdaCGO4F+DPwBngFLAAfbS3AHdurMg9klSTOsogHsWSA54HXwCdgPVq70wiPB/+pag94y60BTw3wggFu9ki3/gC3ItCuSgJh7bIC3MgBuNs+tRVzLhjHzerKuNCKGKW2atSHhBumSKu6H1CJXix0BpKSD3HltXnZHZkryYuVngENYXlnWUqDhlDZ90ARz/vmxl9sG1slC+UB74u2A8CUkTkWeMJVTUAFwR8H7gNXjISXVlFMjSHi1pe/SUHw01Z9TgR7KNpn00LIg2hFNXynwPseGfh1K/zikNFmldMe/G7gOnAZOAr8AY70yR5+7ZS1mh1gRw8Pr5ZNwAOYNQtODngPKGqE4hIkAX4Db6MObaiLzPetk8BzGzcsjELtFbv+rCxamZ1m7OEboLvAt+B+6GMSqZ82bHyof7ImqRNpy54tSxqz+S7aJ7HxhKT3+l++SroZze2reULIl8MnzZJpFws74IuV0kmG+/2cJeAscNUyEsAHy0zfu6wdygOpjU/MUquS2oFuSGras2vRmixNcrwrt+aZlFg4zBj4bnKvgPudgW0E6oqCz0sgBDUr6aWkn5LWJa1IeibpYtHYLUuLfJ0OY/MwsAv4AfzKeF6ZFP28nmTk57CyZKsTiEvoYXqBUmTQarR24JvVUo4IjAhsN/kLPTMk4hCiZCMAAAAASUVORK5CYII="
    )
    private fun dockArtwork(name:String,selected:Boolean):View {
        if(name=="Phone") return ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_call)
            rotation = 90f
            setColorFilter(if(selected)blue else bright)
            scaleType=ImageView.ScaleType.FIT_CENTER
            setPadding(dp(2),dp(2),dp(2),dp(2))
        }
        val bytes=android.util.Base64.decode(dockIconImages[name],android.util.Base64.DEFAULT)
        val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        return ImageView(this).apply {
            setImageBitmap(bitmap)
            setColorFilter(if(selected)blue else bright,android.graphics.PorterDuff.Mode.SRC_IN)
            scaleType=ImageView.ScaleType.FIT_CENTER
            adjustViewBounds=true
        }
    }
    private fun createDock(activePage:String):LinearLayout {
        val dock=row().apply {
            gravity=Gravity.CENTER
            setPadding(dp(8),0,dp(8),0)
            background=GradientDrawable().apply {
                setColor(Color.argb(179,16,16,18))
                setStroke(dp(1),Color.argb(65,225,238,255))
            }
            elevation=dp(3).toFloat()
        }
        // Icon-only buttons avoid clipped labels on the compact 50dp dock.
        listOf("Media" to "♫","Map" to "◎","Home" to "⌂","Apps" to "▦","Phone" to "phone").forEach { (name,symbol)->
            val selected=name==activePage
            val item=FrameLayout(this).apply {
                contentDescription=name
                isClickable=true
                isFocusable=true
                setOnClickListener {
                    if(name=="Phone")open(Intent(Intent.ACTION_DIAL))
                    else if(page!=name)render(name)
                }
            }
            val icon:View=dockArtwork(name,selected)
            icon.translationY = -dp(2).toFloat()
            item.addView(icon,FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER).apply { if(name=="Apps") topMargin = -dp(5) })
            if(selected) {
                val indicator=View(this).apply {
                    background=GradientDrawable().apply {
                        setColor(blue);cornerRadius=dp(2).toFloat()
                    }
                }
                item.addView(indicator,FrameLayout.LayoutParams(dp(26),dp(2),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{
                    bottomMargin=dp(2)
                })
            }
            dock.addView(item,LinearLayout.LayoutParams(dp(65),-1))
        }
        return dock
    }
    private fun showCockpit(outer: LinearLayout) {
        val stage=FrameLayout(this)
        outer.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        stageView=stage
        showBackdrop(stage)
        showRouteGuidance(stage)
        stage.addView(View(this).apply {
            background=android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE);cornerRadius=dp(2).toFloat()
            }
            contentDescription="Change cockpit background"
            setOnClickListener{chooseBackdrop()}
        },FrameLayout.LayoutParams(dp(4),dp(65),Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
            rightMargin=dp(6)
        })
        val priorityStrip=row().apply {
            background=GradientDrawable().apply {
                setColor(Color.argb(210,20,20,22))
                cornerRadius=dp(7).toFloat()
            }
            setPadding(dp(5),0,dp(6),0)
        }
        priorityStrip.addView(txt(batteryStatus(),12f,bright,true))
        priorityStrip.addView(txt("⚙",22f,Color.WHITE,true).apply {
            gravity=Gravity.CENTER
            contentDescription="Settings"
            setOnClickListener { render("Settings") }
        },LinearLayout.LayoutParams(dp(38),dp(36)))
        stage.addView(priorityStrip,FrameLayout.LayoutParams(-2,dp(36),Gravity.RIGHT or Gravity.TOP).apply {
            rightMargin=dp(24)
            topMargin=dp(8)
        })
        val holder=col().apply {
            setPadding(dp(7),dp(7),dp(7),dp(7))
            background=GradientDrawable().apply {
                setColor(Color.argb(235,25,25,27))
                cornerRadius=dp(8).toFloat()
                setStroke(dp(1),Color.rgb(68,68,72))
            }
        }
        widgetHolder=holder
        stage.addView(holder,FrameLayout.LayoutParams(dp(250),-1,Gravity.TOP or Gravity.LEFT).apply {
            setMargins(dp(12),dp(9),0,dp(59))
        })
        drawWidgets()
        stage.addView(createDock("Home"),FrameLayout.LayoutParams(-1,dp(46),Gravity.BOTTOM))
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) enableLocation()
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),100)
    }
    private val widgetNames=listOf("Music","Radio","Podcasts","Weather","Clock","Now Playing")
    private fun centered(value:String,size:Float,color:Int=bright,bold:Boolean=false)=txt(value,size,color,bold).apply{
        gravity=Gravity.CENTER;maxLines=2
    }
    private fun widgetCard(heading:String)=col().apply{
        background=bg(glass2,8);setPadding(dp(7),dp(4),dp(7),dp(4))
        addView(centered(heading,10f,blue,true),lp(-1,dp(18)))
    }
    private fun refreshWidgetClock(){if(page=="Home")drawWidgets()}
    private fun drawWidgets(){
        refreshWeather()
        val holder=widgetHolder?:return
        holder.removeAllViews()
        val cards=SwipeWidgetStack(this){direction->
            val pages=widgetNames.size/2
            widgetIndex=((widgetIndex/2+direction+pages)%pages)*2
            drawWidgets()
        }
        holder.addView(cards,LinearLayout.LayoutParams(-1,0,1f))
        repeat(2){pos->
            val name=widgetNames[(widgetIndex+pos)%widgetNames.size]
            val tile=widgetCard(name.uppercase())
            when(name){
                "Weather"->{
                    val segments=weatherText.split(" | ")
                    val condition=segments.getOrNull(0)?:"Loading weather"
                    val icon=condition.takeWhile{!it.isLetter()}.trim().ifBlank{"☁"}
                    val description=condition.removePrefix(icon).trim()
                    tile.addView(centered(icon,29f,Color.WHITE),lp(-1,dp(36)))
                    tile.addView(centered(description,12f,bright,true),lp(-1,dp(24)))
                    tile.addView(centered(segments.getOrNull(1)?:"",12f,bright),lp(-1,dp(23)))
                    tile.addView(centered(segments.getOrNull(2)?:"Locating…",10f,dim),lp(-1,dp(21)))
                }
                "Clock"->{
                    tile.addView(centered(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),36f,bright,true),
                        LinearLayout.LayoutParams(-1,0,1f))
                    tile.addView(centered(SimpleDateFormat("EEEE d MMMM yyyy",Locale.getDefault()).format(Date()),13f,dim),
                        lp(-1,dp(32)))
                }
                else->{
                    val kind=when(name){"Music"->"Music";"Radio"->"Radio";"Podcasts"->"Podcast";else->MediaCentre.playingSource}
                    val active=MediaCentre.playingName.isNotBlank()&&(name=="Now Playing"||MediaCentre.playingSource==kind)
                    if(active&&MediaCentre.cover!=null){
                        tile.addView(ImageView(this).apply{
                            setImageBitmap(MediaCentre.cover);scaleType=ImageView.ScaleType.FIT_CENTER
                        },lp(-1,dp(49)))
                    }else{
                        tile.addView(centered(when(name){"Music"->"♫";"Radio"->"◉";"Podcasts"->"◎";else->"♫"},25f,blue),
                            lp(-1,dp(43)))
                    }
                    tile.addView(centered(if(active)MediaCentre.playingName else when(name){
                        "Music"->"Choose a track";"Radio"->"Choose a station"
                        "Podcasts"->"Choose an episode";else->"Nothing playing"
                    },12f,bright,true),lp(-1,dp(30)))
                    if(active){
                        val controls=row().apply{gravity=Gravity.CENTER}
                        for((symbol,step) in listOf("⏮" to -1,"⏯" to 0,"⏭" to 1)){
                            controls.addView(centered(symbol,18f,bright,true).apply{
                                setOnClickListener{
                                    if(step==0)MediaCentre.toggle() else MediaCentre.skip(step)
                                    handler.postDelayed({if(page=="Home")drawWidgets()},250)
                                }
                            },LinearLayout.LayoutParams(0,dp(29),1f))
                        }
                        tile.addView(controls)
                        if(kind!="Radio"&&MediaCentre.duration()>0){
                            tile.addView(SeekBar(this).apply{
                                max=100
                                progress=(100L*MediaCentre.position()/MediaCentre.duration()).toInt().coerceIn(0,100)
                                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                                    override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean){if(fromUser)MediaCentre.seek(value)}
                                    override fun onStartTrackingTouch(bar:SeekBar?){}
                                    override fun onStopTrackingTouch(bar:SeekBar?){}
                                })
                            },lp(-1,dp(22)))
                        }
                    }
                    tile.setOnClickListener{
                        prefs.edit().putString("media_mode",when(name){
                            "Radio"->"RADIO";"Podcasts"->"PODCASTS";else->"MEDIA"
                        }).apply()
                        render("Media")
                    }
                }
            }
            cards.addView(tile,LinearLayout.LayoutParams(-1,0,1f).apply{
                weight=1f;if(pos>0)topMargin=dp(6)
            })
        }
        val pageNumber=widgetIndex/2+1
        holder.addView(centered("● ".repeat(pageNumber)+"○ ".repeat(widgetNames.size/2-pageNumber),10f,dim),
            lp(-1,dp(22)))
    }

    private fun batteryStatus():String {
        val b=(getSystemService(BATTERY_SERVICE) as android.os.BatteryManager).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val gps=if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)"GPS" else "GPS?"
        return "⌖ $gps    Bluetooth    ▰ $b%"
    }
    private fun showBackdrop(stage:FrameLayout) {
        val selected=prefs.getString("backdrop","map")
        if(selected!="map" && selected!=null) {
            try {
                stage.addView(ImageView(this).apply {
                    setImageURI(Uri.parse(selected));scaleType=ImageView.ScaleType.CENTER_CROP
                },FrameLayout.LayoutParams(-1,-1))
                return
            } catch(_:Exception) {}
        }
        val vectorMap=StyledMapPanel(this,prefs.getString("route_points","[]")?:"[]")
        styledMap=vectorMap
        stage.addView(vectorMap,FrameLayout.LayoutParams(-1,-1))
    }
    private fun chooseBackdrop() {
        AlertDialog.Builder(this).setTitle("Cockpit background")
            .setItems(arrayOf("Live map","Choose photo")) { _,which ->
                if(which==0) {prefs.edit().putString("backdrop","map").apply();render("Home")}
                else try {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
                        addCategory(Intent.CATEGORY_OPENABLE);type="image/*"
                    },442)
                }catch(_:Exception){Toast.makeText(this,"No image picker",Toast.LENGTH_SHORT).show()}
            }.show()
    }
    private fun expandWidget(name:String) {
        when(name) {
            "Mirror" -> launchPackage("com.mirroring.suit")
            "Video" -> try {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE);type="video/*"
            },443)}catch(_:Exception){Toast.makeText(this,"No video picker",Toast.LENGTH_SHORT).show()}
            "Radio","Now Playing" -> render("Media")
            "Clock","Weather" -> AlertDialog.Builder(this).setTitle(name)
                .setMessage(if(name=="Clock")SimpleDateFormat("HH:mm, EEEE d MMMM yyyy",Locale.getDefault()).format(Date()) else "Weather integration coming soon.")
                .setPositiveButton("Close",null).show()
        }
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK)return
        val uri=data?.data?:return
        if(requestCode==524){
            try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
            prefs.edit().putString("media_folder",uri.toString()).apply()
            if(page=="Media")render("Media")
        } else if(requestCode==442) {
            try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
            prefs.edit().putString("backdrop",uri.toString()).apply()
            render("Home")
        } else if(requestCode==443) {
            val stage=stageView?:return
            val overlay=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
            stage.addView(overlay,FrameLayout.LayoutParams(-1,-1))
            val video=VideoView(this)
            overlay.addView(video,FrameLayout.LayoutParams(-1,-1))
            video.setMediaController(android.widget.MediaController(this).apply{setAnchorView(video)})
            overlay.addView(action("✕ CLOSE"){video.stopPlayback();stage.removeView(overlay)},
                FrameLayout.LayoutParams(-2,dp(44),Gravity.TOP or Gravity.RIGHT))
            video.setVideoURI(uri)
            video.start()
        }
    }
    private fun createMap(): MapView {
        val m=MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(-32.98,27.87))
        }
        map=m
        return m
    }
    private fun controller(): MediaController? {
        return try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val active=manager.getActiveSessions(ComponentName(this,MediaAccessService::class.java))
            active.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING } ?: active.firstOrNull()
        } catch (_:SecurityException) {null}
    }
    private fun nowTitle():String = controller()?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Connect your music"
    private fun showMedia(target:LinearLayout) {
        val centre=MediaCentre(this){
            val intent=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply{
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent,524)
        }
        target.addView(centre,LinearLayout.LayoutParams(-1,-1))
    }
    private fun findAddressSuggestions(field:EditText) {
        val query=field.text.toString().trim()
        if(query.length<3){Toast.makeText(this,"Enter at least 3 characters",Toast.LENGTH_SHORT).show();return}
        Toast.makeText(this,"Searching addresses…",Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val url="https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&limit=5&q="+
                    java.net.URLEncoder.encode(query,"UTF-8")
                val c=java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout=12000;c.readTimeout=12000
                c.setRequestProperty("User-Agent","DriveXFH/0.13 prototype")
                val results=try{
                    if(c.responseCode!=200)throw Exception("Search unavailable")
                    org.json.JSONArray(c.inputStream.bufferedReader().use{it.readText()})
                }finally{c.disconnect()}
                val titles=(0 until results.length()).map{results.getJSONObject(it).getString("display_name")}.toTypedArray()
                runOnUiThread{
                    if(titles.isEmpty())Toast.makeText(this,"No matches. Try suburb, city or postal code.",Toast.LENGTH_LONG).show()
                    else AlertDialog.Builder(this).setTitle("Choose the correct address")
                        .setItems(titles){_,which->
                            val item=results.getJSONObject(which)
                            field.setText(titles[which])
                            field.setSelection(field.length())
                            val prefix=if(field.tag=="home")"nav_home_point" else "nav_dest_point"
                            prefs.edit().putString(prefix,item.getString("lat")+","+item.getString("lon"))
                                .putString(prefix+"_label",titles[which]).apply()
                        }.show()
                }
            }catch(e:Exception){runOnUiThread{Toast.makeText(this,e.message?:"Search error",Toast.LENGTH_LONG).show()}}
        }.start()
    }
    private fun selectedCoordinates(prefix:String,label:String):GeoPoint? {
        if(prefs.getString(prefix+"_label","")!=label)return null
        return try{
            val parts=prefs.getString(prefix,"")!!.split(",")
            GeoPoint(parts[0].toDouble(),parts[1].toDouble())
        }catch(_:Exception){null}
    }
    private fun showRouteGuidance(stage:FrameLayout) {
        val steps=try{org.json.JSONArray(prefs.getString("route_steps","[]"))}catch(_:Exception){return}
        val route=savedRoutePoints()
        if(steps.length()==0 || route.size<2)return
        val guidance=txt("Route preview",12f,bright,true).apply{
            background=bg(glass,8);setPadding(dp(10),dp(6),dp(10),dp(6));maxLines=2
        }
        stage.addView(guidance,FrameLayout.LayoutParams(dp(230),dp(50),Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply{topMargin=dp(52)})
        val lm=getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val cumulative=DoubleArray(route.size)
        fun metres(a:GeoPoint,b:GeoPoint):Double {
            val out=FloatArray(1)
            android.location.Location.distanceBetween(a.latitude,a.longitude,b.latitude,b.longitude,out)
            return out[0].toDouble()
        }
        for(i in 1 until route.size)cumulative[i]=cumulative[i-1]+metres(route[i-1],route[i])
        fun closestIndex(lat:Double,lon:Double):Pair<Int,Double> {
            val at=GeoPoint(lat,lon)
            var index=0;var best=Double.MAX_VALUE
            for(i in route.indices){
                val distance=metres(at,route[i])
                if(distance<best){best=distance;index=i}
            }
            return index to best
        }
        val stepsAlong=mutableListOf<Pair<Int,String>>()
        for(i in 0 until steps.length()){
            val step=steps.getJSONObject(i)
            val point=closestIndex(step.getDouble("lat"),step.getDouble("lon")).first
            val instruction=step.optString("instruction","Continue")
            stepsAlong.add(point to instruction)
        }
        // Avoid using a stale GPS reading kilometres away from the planned route.
        val update:(android.location.Location?)->Unit={ location->
            val nearest=if(location!=null)closestIndex(location.latitude,location.longitude) else 0 to Double.MAX_VALUE
            val preview=nearest.second>250.0
            val currentIndex=if(preview)0 else nearest.first
            val next=stepsAlong.firstOrNull {it.first>currentIndex+1}
            val distance=if(next!=null)(cumulative[next.first]-cumulative[currentIndex]).coerceAtLeast(0.0) else 0.0
            val caption=next?.second ?: "Arriving at destination"
            val formatted=if(distance>=1000.0)String.format(Locale.US,"%.1f km",distance/1000.0)
                          else String.format(Locale.US,"%.0f m",distance)
            guidance.text=(if(preview)"PREVIEW · " else "")+formatted+"  •  "+caption
            if(location!=null && location.hasAccuracy() && location.accuracy<50f){
                if(nearest.second<80.0){
                    prefs.edit().putBoolean("nav_engaged",true).apply();offRouteCount=0
                    val phase=when{distance<55.0->"turn";distance<180.0->"near";distance<500.0->"far";else->""}
                    val key=(next?.first?:-1).toString()+":"+phase
                    if(phase.isNotEmpty() && key!=lastSpoken){lastSpoken=key;speak(if(phase=="turn")caption+" now" else "In "+formatted+", "+caption)}
                }else if(nearest.second>180.0 && prefs.getBoolean("nav_engaged",false) && prefs.getBoolean("nav_reroute",true)){
                    offRouteCount++
                    if(offRouteCount>=3 && !rerouteBusy && android.os.SystemClock.elapsedRealtime()-rerouteAt>45000L){offRouteCount=0;rerouteFrom(location)}
                }
            }
        }
        update(null)
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            try{
                val last=lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?:lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                update(last)
                val listener=object:android.location.LocationListener{
                    override fun onLocationChanged(location:android.location.Location){
                        if(page=="Home" && guidance.parent!=null)update(location)
                        else try{lm.removeUpdates(this)}catch(_:Exception){}
                    }
                }
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,3000L,8f,listener)
                turnListener=listener;turnManager=lm
            }catch(_:Exception){}
        }
    }
    private fun rerouteFrom(position:android.location.Location){
        val parts=prefs.getString("route_destination_point","")?.split(",")?:return
        if(parts.size!=2)return
        rerouteBusy=true;rerouteAt=android.os.SystemClock.elapsedRealtime()
        Thread{
            try{
                val url="https://router.project-osrm.org/route/v1/driving/"+position.longitude+","+position.latitude+";"+parts[1]+","+parts[0]+"?overview=full&geometries=geojson&steps=true&alternatives=true"
                val c=java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout=14000;c.readTimeout=20000
                val json=try{if(c.responseCode!=200)throw Exception("Router unavailable");org.json.JSONObject(c.inputStream.bufferedReader().use{it.readText()})}finally{c.disconnect()}
                val routes=json.getJSONArray("routes");if(routes.length()==0)throw Exception("No reroute found")
                var route=routes.getJSONObject(0)
                if(prefs.getString("nav_mode","fastest")=="shortest")for(i in 1 until routes.length()){val r=routes.getJSONObject(i);if(r.getDouble("distance")<route.getDouble("distance"))route=r}
                val coordinates=route.getJSONObject("geometry").getJSONArray("coordinates")
                val points=org.json.JSONArray()
                for(i in 0 until coordinates.length()){val p=coordinates.getJSONArray(i);points.put(org.json.JSONArray().put(p.getDouble(1)).put(p.getDouble(0)))}
                if(points.length()<2)throw Exception("Empty reroute")
                val instructions=org.json.JSONArray();val legs=route.getJSONArray("legs")
                for(i in 0 until legs.length()){val steps=legs.getJSONObject(i).getJSONArray("steps");for(j in 0 until steps.length()){
                    val step=steps.getJSONObject(j);val m=step.getJSONObject("maneuver");val p=m.getJSONArray("location")
                    instructions.put(org.json.JSONObject().put("lat",p.getDouble(1)).put("lon",p.getDouble(0)).put("instruction",(m.optString("type")+" "+m.optString("modifier")+" "+step.optString("name")).trim()))
                }}
                runOnUiThread{
                    prefs.edit().putString("route_points",points.toString()).putString("route_steps",instructions.toString())
                        .putString("route_start",position.latitude.toString()+","+position.longitude.toString()).putBoolean("nav_engaged",true).apply()
                    rerouteBusy=false;lastSpoken="";speak("Route recalculated")
                    if(page=="Home")render("Home")
                }
            }catch(_:Exception){runOnUiThread{rerouteBusy=false;Toast.makeText(this,"Unable to reroute. Check connection.",Toast.LENGTH_SHORT).show()}}
        }.start()
    }
    private fun showMap(target: LinearLayout) {
        target.addView(txt("NAVIGATION  •  ROUTE PLANNER",15f,bright,true),lp(-1,dp(27)))
        val body=row().apply{gravity=Gravity.TOP}
        target.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val panel=col().apply{background=bg(glass,8);setPadding(dp(8),dp(3),dp(8),dp(3))}
        val panelScroll=ScrollView(this).apply{isFillViewport=false;clipToPadding=false;addView(panel)}
        body.addView(panelScroll,LinearLayout.LayoutParams(dp(265),-1))
        fun compactButton(label:String,callback:()->Unit)=txt(label,11f,bright,true).apply{
            gravity=Gravity.CENTER;background=bg(glass2,7)
            setPadding(dp(5),0,dp(5),0)
            setOnClickListener{callback()}
        }
        val home=EditText(this).apply{
            setSingleLine(true);setText(prefs.getString("nav_home",""))
            hint="Home address";textSize=12f;setTextColor(bright);setHintTextColor(dim)
        }
        panel.addView(txt("HOME ADDRESS",10f,blue,true))
        panel.addView(home.apply{tag="home"},lp(-1,dp(36)))
        panel.addView(compactButton("FIND HOME ADDRESS"){findAddressSuggestions(home)},lp(-1,dp(29)))
        panel.addView(compactButton("SAVE HOME") {
            prefs.edit().putString("nav_home",home.text.toString().trim()).apply()
            Toast.makeText(this,"Home saved",Toast.LENGTH_SHORT).show()
        },lp(-1,dp(32)))
        val fromHome=CheckBox(this).apply{
            text="Start at Home (otherwise GPS)"
            textSize=11f;setTextColor(bright)
            isChecked=prefs.getBoolean("nav_from_home",false)
            setOnCheckedChangeListener{_,yes->prefs.edit().putBoolean("nav_from_home",yes).apply()}
        }
        panel.addView(fromHome)
        val destination=EditText(this).apply{
            setSingleLine(true);setText(prefs.getString("nav_draft",""))
            hint="Destination address";textSize=12f;setTextColor(bright);setHintTextColor(dim)
        }
        panel.addView(txt("DESTINATION",10f,blue,true))
        panel.addView(destination.apply{tag="destination"},lp(-1,dp(36)))
        panel.addView(compactButton("FIND DESTINATION"){findAddressSuggestions(destination)},lp(-1,dp(29)))
        val status=txt("Internet required for route calculation.",10f,dim)
        lateinit var calculate:TextView
        calculate=compactButton("CALCULATE ROUTE") {
            prefs.edit().putString("nav_draft",destination.text.toString()).apply()
            planRoute(fromHome.isChecked,home.text.toString().trim(),destination.text.toString().trim(),status,calculate)
        }
        panel.addView(txt("ROUTE PREFERENCE",10f,blue,true))
        val mode=Spinner(this).apply{
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Fastest","Shortest available alternative"))
            setSelection(if(prefs.getString("nav_mode","fastest")=="shortest")1 else 0)
            onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
                override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){prefs.edit().putString("nav_mode",if(position==1)"shortest" else "fastest").apply()}
            }
        }
        panel.addView(mode,lp(-1,dp(36)))
        panel.addView(CheckBox(this).apply{text="Voice directions";textSize=11f;setTextColor(bright);isChecked=prefs.getBoolean("nav_voice",true);setOnCheckedChangeListener{_,b->prefs.edit().putBoolean("nav_voice",b).apply()}})
        panel.addView(CheckBox(this).apply{text="Automatic rerouting";textSize=11f;setTextColor(bright);isChecked=prefs.getBoolean("nav_reroute",true);setOnCheckedChangeListener{_,b->prefs.edit().putBoolean("nav_reroute",b).apply()}})
        panel.addView(calculate,lp(-1,dp(39)))
        panel.addView(status)
        val vectorMap=StyledMapPanel(this,prefs.getString("route_points","[]")?:"[]")
        styledMap=vectorMap
        body.addView(vectorMap,LinearLayout.LayoutParams(0,-1,1f).apply{leftMargin=dp(8)})
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)enableLocation()
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),100)
    }
    private fun savedRoutePoints():List<GeoPoint> = try {
        val arr=org.json.JSONArray(prefs.getString("route_points","[]"))
        (0 until arr.length()).map { i->val p=arr.getJSONArray(i);GeoPoint(p.getDouble(0),p.getDouble(1))}
    }catch(_:Exception){emptyList()}
    private fun displaySavedRoute(m:MapView) {
        val pts=savedRoutePoints()
        if(pts.size<2)return
        m.overlays.add(org.osmdroid.views.overlay.Polyline().apply{
            setPoints(pts);outlinePaint.color=blue;outlinePaint.strokeWidth=dp(6).toFloat()
        })
        m.overlays.add(org.osmdroid.views.overlay.Marker(m).apply{
            position=pts.last();title=prefs.getString("route_destination","Destination")
            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
        })
        m.post{
            if(map===m)try{
                m.zoomToBoundingBox(org.osmdroid.util.BoundingBox.fromGeoPoints(pts),true,dp(40))
            }catch(_:Exception){}
        }
        m.invalidate()
    }
    private fun geocode(address:String):GeoPoint {
        val url="https://nominatim.openstreetmap.org/search?format=json&limit=1&q="+
            java.net.URLEncoder.encode(address,"UTF-8")
        val c=java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout=12000;c.readTimeout=12000
        c.setRequestProperty("User-Agent","DriveXFH/0.12 personal navigation prototype")
        try{
            if(c.responseCode!=200)throw Exception("Address lookup unavailable: "+c.responseCode)
            val arr=org.json.JSONArray(c.inputStream.bufferedReader().use{it.readText()})
            if(arr.length()==0)throw Exception("Address not found: "+address)
            val p=arr.getJSONObject(0)
            return GeoPoint(p.getDouble("lat"),p.getDouble("lon"))
        }finally{c.disconnect()}
    }
    private fun gpsStart():GeoPoint {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
           checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            throw Exception("Enable location access, or start from Home.")
        val manager=getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val last=try {
            manager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?:manager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
        }catch(_:SecurityException){null}
        if(last==null)throw Exception("Waiting for GPS fix. Use saved Home meanwhile.")
        return GeoPoint(last.latitude,last.longitude)
    }
    private fun planRoute(fromHome:Boolean,home:String,dest:String,status:TextView,button:TextView) {
        if(dest.isBlank()){status.text="Enter destination.";return}
        if(fromHome&&home.isBlank()){status.text="Enter Home address.";return}
        status.text="Calculating route…";button.isEnabled=false
        Thread {
            try{
                val start=if(fromHome)(selectedCoordinates("nav_home_point",home)?:geocode(home)) else gpsStart()
                if(fromHome)Thread.sleep(1200)
                val finish=selectedCoordinates("nav_dest_point",dest)?:geocode(dest)
                val url="https://router.project-osrm.org/route/v1/driving/"+
                    start.longitude+","+start.latitude+";"+finish.longitude+","+finish.latitude+
                    "?overview=full&geometries=geojson&steps=true&alternatives=true"
                val c=java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout=15000;c.readTimeout=20000
                c.setRequestProperty("User-Agent","DriveXFH/0.12 prototype")
                val payload=try{
                    if(c.responseCode!=200)throw Exception("Routing unavailable: "+c.responseCode)
                    org.json.JSONObject(c.inputStream.bufferedReader().use{it.readText()})
                }finally{c.disconnect()}
                val routes=payload.getJSONArray("routes")
                if(routes.length()==0)throw Exception("No driving route found.")
                var route=routes.getJSONObject(0)
                if(prefs.getString("nav_mode","fastest")=="shortest")for(i in 1 until routes.length()){val r=routes.getJSONObject(i);if(r.getDouble("distance")<route.getDouble("distance"))route=r}
                val coords=route.getJSONObject("geometry").getJSONArray("coordinates")
                val arr=org.json.JSONArray()
                for(i in 0 until coords.length()){
                    val p=coords.getJSONArray(i)
                    arr.put(org.json.JSONArray().put(p.getDouble(1)).put(p.getDouble(0)))
                }
                if(arr.length()<2)throw Exception("Empty route returned.")

                val instructions=org.json.JSONArray()
                val legs=route.optJSONArray("legs")
                if(legs!=null)for(li in 0 until legs.length()){
                    val steps=legs.getJSONObject(li).optJSONArray("steps")?:continue
                    for(si in 0 until steps.length()){
                        val step=steps.getJSONObject(si)
                        val maneuver=step.getJSONObject("maneuver")
                        val p=maneuver.getJSONArray("location")
                        val kind=maneuver.optString("type","continue")
                        val turn=maneuver.optString("modifier","")
                        val street=step.optString("name","")
                        val caption=(kind+" "+turn+" "+street).trim().replace("  "," ")
                        instructions.put(org.json.JSONObject().put("lat",p.getDouble(1))
                            .put("lon",p.getDouble(0)).put("instruction",caption))
                    }
                }
                val summary=String.format(Locale.US,"%.1f km · %.0f min",route.getDouble("distance")/1000.0,route.getDouble("duration")/60.0)
                runOnUiThread {
                    prefs.edit().putString("route_points",arr.toString())
                        .putString("route_destination",dest).putString("route_summary",summary)
                        .putString("route_start",start.latitude.toString()+","+start.longitude.toString())
                        .putString("route_destination_point",finish.latitude.toString()+","+finish.longitude.toString())
                        .putString("route_steps",instructions.toString())
                        .putBoolean("nav_engaged",false)
                        .putString("backdrop","map").apply()
                    Toast.makeText(this,"Route ready: "+summary,Toast.LENGTH_LONG).show()
                    render("Home")
                }
            }catch(e:Exception){
                runOnUiThread {status.text=e.message?:"Route failed. Check internet.";button.isEnabled=true}
            }
        }.start()
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
        target.addView(txt("Drive XFH v0.15  •  Android 13  •  iDrive-inspired prototype",12f,dim))
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
