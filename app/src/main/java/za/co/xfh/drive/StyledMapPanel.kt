package za.co.xfh.drive

import android.content.Context
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.content.pm.PackageManager
import android.Manifest
import android.widget.FrameLayout
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
    init {
        addView(view,LayoutParams(-1,-1))
        try {
            val arr=JSONArray(routeJson)
            for(i in 0 until arr.length()) {
                val p=arr.getJSONArray(i)
                points.add(LatLng(p.getDouble(0),p.getDouble(1)))
            }
        }catch(_:Exception){}
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
                val centre=here ?: points.firstOrNull() ?: LatLng(-32.98,27.87)
                // Initial 2D overview for long journeys; use 3D close up near the vehicle.
                val near=points.isEmpty() || points.size<2
                val zoom=if(near)16.0 else 8.0
                val tilt=if(near)58.0 else 45.0
                map.cameraPosition=CameraPosition.Builder()
                    .target(centre).zoom(zoom).tilt(tilt).build()
                if(here!=null)map.addMarker(MarkerOptions().position(here).title("Your location"))
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
    fun resume(){view.onStart();view.onResume()}
    fun pause(){view.onPause();view.onStop()}
    fun dispose(){pause();view.onDestroy()}
}
