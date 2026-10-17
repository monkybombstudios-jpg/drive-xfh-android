package za.co.xfh.drive

import android.content.Context
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.content.pm.PackageManager
import android.Manifest
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import org.json.JSONArray
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.expressions.Expression
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** BMW-inspired angled vector map; route is shared with the v0.13 navigation planner. */
class StyledMapPanel(context: Context, private val routeJson: String) : FrameLayout(context) {
    private val view=MapView(context)
    private var scene:MapLibreMap?=null
    private val points=mutableListOf<LatLng>()

    private var positionMarker:org.maplibre.android.annotations.Marker?=null
    private var gpsListener:android.location.LocationListener?=null
    private var gpsManager:LocationManager?=null
    private fun metres(a:LatLng,b:LatLng):Float {
        val out=FloatArray(1)
        Location.distanceBetween(a.latitude,a.longitude,b.latitude,b.longitude,out)
        return out[0]
    }
    private fun bearing(a:LatLng,b:LatLng):Double {
        val one=Location("route").apply{latitude=a.latitude;longitude=a.longitude}
        val two=Location("route").apply{latitude=b.latitude;longitude=b.longitude}
        return (one.bearingTo(two).toDouble()+360.0)%360.0
    }
    private fun updateFollow(location:Location) {
        val map=scene?:return
        if(points.size<2)return
        val here=LatLng(location.latitude,location.longitude)
        val nearest=points.indices.minByOrNull{metres(here,points[it])}?:return
        if(metres(here,points[nearest])>150f)return
        positionMarker?.position=here
        var ahead=nearest
        var distance=0f
        val targetDistance=if(location.hasSpeed())(location.speed*10f).coerceIn(90f,350f) else 150f
        while(ahead<points.lastIndex && distance<targetDistance){
            distance+=metres(points[ahead],points[ahead+1]);ahead++
        }
        val next=points[ahead]
        val angle=if(ahead>nearest)bearing(points[nearest],next) else map.cameraPosition.bearing
        val focus=LatLng((here.latitude+next.latitude)/2.0,(here.longitude+next.longitude)/2.0)
        map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder(map.cameraPosition).target(focus).bearing(angle).tilt(58.0).build()),1100)
    }
    private fun startFollow(){
        if(gpsListener!=null||points.size<2)return
        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return
        try{
            val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener=object:android.location.LocationListener{
                override fun onLocationChanged(location:Location){updateFollow(location)}
            }
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,2500L,5f,listener)
            gpsManager=manager;gpsListener=listener
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let{updateFollow(it)}
        }catch(_:Exception){}
    }
    private fun stopFollow(){
        gpsListener?.let{try{gpsManager?.removeUpdates(it)}catch(_:Exception){}}
        gpsListener=null;gpsManager=null
    }
    private fun px(n:Int)=(n*resources.displayMetrics.density).toInt()
    init {
        addView(view,LayoutParams(-1,-1))
        try {
            val arr=JSONArray(routeJson)
            for(i in 0 until arr.length()) {
                val p=arr.getJSONArray(i)
                points.add(LatLng(p.getDouble(0),p.getDouble(1)))
            }
        }catch(_:Exception){}
        val controls=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
        for((label,delta) in listOf("+" to 1.0,"−" to -1.0)) {
            val btn=TextView(context).apply {
                text=label;textSize=23f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
                background=GradientDrawable().apply{
                    setColor(Color.argb(220,27,27,30));cornerRadius=px(7).toFloat()
                }
                setOnClickListener{
                    scene?.let{m->m.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomTo(
                        (m.cameraPosition.zoom+delta).coerceIn(2.0,20.0)))}
                }
            }
            controls.addView(btn,LinearLayout.LayoutParams(px(43),px(43)).apply{bottomMargin=px(5)})
        }
        addView(controls,LayoutParams(-2,-2,Gravity.END or Gravity.BOTTOM).apply{
            rightMargin=px(11);bottomMargin=px(58)
        })
        view.onCreate(null)
        view.getMapAsync { map ->
            scene=map
            map.uiSettings.isCompassEnabled=false
            map.uiSettings.isLogoEnabled=false
            map.uiSettings.isAttributionEnabled=false
            map.setStyle("https://tiles.openfreemap.org/styles/bright") { style ->
                // The OpenFreeMap 'bright' vector tiles expose OpenMapTiles building heights.
                try {
                    val building=FillExtrusionLayer("xfh-3d-buildings","openmaptiles").apply {
                        sourceLayer="building"
                        minZoom=14f
                        setProperties(
                            fillExtrusionColor(Color.rgb(215,218,220)),
                            fillExtrusionHeight(Expression.get("render_height")),
                            fillExtrusionBase(Expression.get("render_min_height")),
                            fillExtrusionOpacity(0.88f)
                        )
                    }
                    style.addLayer(building)
                }catch(_:Exception){}
                if(points.size>1) {
                    val routeLine=LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude,it.latitude) })
                    style.addSource(GeoJsonSource("xfh-route-source",routeLine))
                    style.addLayer(LineLayer("xfh-route-outline","xfh-route-source").withProperties(
                        lineColor(Color.rgb(245,246,248)),lineWidth(11f),lineOpacity(0.95f)
                    ))
                    style.addLayer(LineLayer("xfh-route-blue","xfh-route-source").withProperties(
                        lineColor(Color.rgb(40,150,250)),lineWidth(7f),lineOpacity(0.96f)
                    ))
                    map.addMarker(MarkerOptions().position(points.last()).title("Destination"))
                }
                val here=findLocation()
                val centre=points.firstOrNull() ?: here ?: LatLng(-32.98,27.87)
                // Initial 2D overview for long journeys; use 3D close up near the vehicle.
                val zoom=18.0
                val tilt=58.0
                val next=if(points.size>1)points.firstOrNull{metres(points[0],it)>60f}?:points[1] else null
                val angle=if(next!=null)bearing(points[0],next) else 0.0
                map.cameraPosition=CameraPosition.Builder().target(centre).zoom(zoom).tilt(tilt).bearing(angle).build()
                val arrowPosition=points.firstOrNull() ?: here
                if(arrowPosition!=null) {
                    val arrow=android.graphics.Bitmap.createBitmap(96,96,android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas=android.graphics.Canvas(arrow)
                    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{
                        color=Color.rgb(35,140,244);this.style=android.graphics.Paint.Style.FILL
                        setShadowLayer(5f,0f,3f,Color.DKGRAY)
                    }
                    val shape=android.graphics.Path().apply{
                        moveTo(48f,5f);lineTo(80f,84f);lineTo(48f,67f)
                        lineTo(16f,84f);close()
                    }
                    canvas.drawPath(shape,paint)
                    val icon=org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(arrow)
                    positionMarker=map.addMarker(MarkerOptions().position(arrowPosition).title(if(points.isNotEmpty())"Trip starting point" else "Current position").icon(icon))
                }
                startFollow()
            }
        }
        view.onStart()
        view.onResume()
    }
    private fun findLocation():LatLng? {
        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
           context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return null
        return try {
            val lm=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc:Location?=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?:lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if(loc==null)null else LatLng(loc.latitude,loc.longitude)
        }catch(_:SecurityException){null}
    }
    fun resume(){view.onStart();view.onResume();startFollow()}
    fun pause(){stopFollow();view.onPause();view.onStop()}
    fun dispose(){pause();view.onDestroy()}
}
