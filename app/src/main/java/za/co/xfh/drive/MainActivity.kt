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
    private var page = "Home"
    private var map: MapView? = null
    private var widgetIndex=0
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
        if((page == "Map" || page == "Home") && to != page) { map?.onPause(); map?.onDetach(); map=null }
        page = to
        val outer = col().apply { setBackgroundColor(base) }
        setContentView(outer)
        if(to=="Home") { showCockpit(outer); return }
        // Interior page headers stay unchanged until their layouts are redesigned.
        val top = row().apply { setPadding(dp(18),dp(8),dp(14),dp(6)) }
        top.addView(txt("◉  DRIVE XFH",21f,blue,true),LinearLayout.LayoutParams(0,dp(44),1f))
        top.addView(txt(SimpleDateFormat("EEE d MMM  •  HH:mm",Locale.getDefault()).format(Date()),14f,dim,true))
        top.addView(txt("⚙",22f,dim).apply { setOnClickListener { render("Settings") } })
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
            item.addView(icon,FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER))
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
        stage.addView(createDock("Home"),FrameLayout.LayoutParams(-1,dp(50),Gravity.BOTTOM))
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) enableLocation()
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),100)
    }
    private val widgetNames=listOf("Radio","Mirror","Video","Now Playing","Clock","Weather")
    private fun widgetCard(heading:String,body:String,large:Boolean=false)=col().apply {
        setPadding(dp(9),dp(5),dp(8),dp(5))
        background=bg(glass2,6)
        addView(txt(heading,10f,blue,true))
        addView(txt(body,if(large)29f else 14f,bright,large))
    }
    private fun refreshWidgetClock() { if(page=="Home") drawWidgets() }
    private fun drawWidgets() {
        val holder=widgetHolder?:return
        holder.removeAllViews()
        repeat(2) { pos->
            val name=widgetNames[(widgetIndex+pos)%widgetNames.size]
            val tile=when(name) {
                "Radio"->widgetCard("RADIO","Audio player")
                "Mirror"->widgetCard("MIRROR","Phone connection")
                "Video"->widgetCard("VIDEO","Local video player")
                "Weather"->widgetCard("WEATHER","Local weather\nNot configured")
                "Clock"->widgetCard(SimpleDateFormat("EEE d MMM yyyy",Locale.getDefault()).format(Date()),
                    SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),true)
                "Now Playing"->widgetCard("NOW PLAYING",nowTitle()).apply{setOnClickListener{render("Media")}}
                else->widgetCard("SHORTCUTS","Navigation · Media\nPhone · Apps").apply{setOnClickListener{render("Apps")}}
            }
            tile.addView(txt("⛶",16f,blue,true).apply{gravity=Gravity.RIGHT;setOnClickListener{expandWidget(name)}})
            tile.setOnClickListener{expandWidget(name)}
            holder.addView(tile,LinearLayout.LayoutParams(-1,0,1f).apply {if(pos>0)topMargin=dp(5)})
        }
        val controls=row().apply {gravity=Gravity.CENTER}
        val up=txt("▲",15f,dim,true).apply {gravity=Gravity.CENTER;setOnClickListener{widgetIndex=(widgetIndex+widgetNames.size-1)%widgetNames.size;drawWidgets()}}
        val down=txt("▼",15f,dim,true).apply {gravity=Gravity.CENTER;setOnClickListener{widgetIndex=(widgetIndex+1)%widgetNames.size;drawWidgets()}}
        controls.addView(up,LinearLayout.LayoutParams(dp(40),dp(28)))
        controls.addView(txt("WIDGETS  ${widgetIndex+1}/${widgetNames.size}",10f,dim).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,dp(28),1f))
        controls.addView(down,LinearLayout.LayoutParams(dp(40),dp(28)))
        holder.addView(controls)
        var startY=0f
        holder.setOnTouchListener { _,e->
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {startY=e.y;true}
                MotionEvent.ACTION_UP -> {
                    val dy=e.y-startY
                    if(kotlin.math.abs(dy)>dp(25)) {
                        widgetIndex=(widgetIndex+if(dy<0)1 else widgetNames.size-1)%widgetNames.size
                        drawWidgets()
                    }
                    true
                }
                else->true
            }
        }
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
        stage.addView(createMap(),FrameLayout.LayoutParams(-1,-1))
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
        if(requestCode==442) {
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
        val m=createMap()
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
        target.addView(txt("Drive XFH v0.10  •  Android 13  •  iDrive-inspired prototype",12f,dim))
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
