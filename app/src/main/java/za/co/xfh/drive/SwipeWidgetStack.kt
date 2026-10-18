package za.co.xfh.drive

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/** Detect swipes without intercepting taps on the embedded media controls. */
class SwipeWidgetStack(context:Context,private val onSwipe:(Int)->Unit):LinearLayout(context){
    private val threshold=(ViewConfiguration.get(context).scaledTouchSlop*3).coerceAtLeast((28*resources.displayMetrics.density).toInt())
    private var downX=0f
    private var downY=0f
    init{orientation=VERTICAL;isClickable=true}
    override fun dispatchTouchEvent(event:MotionEvent):Boolean{
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=event.rawX;downY=event.rawY}
            MotionEvent.ACTION_UP->{
                val dx=event.rawX-downX
                val dy=event.rawY-downY
                if(abs(dy)>=threshold&&abs(dy)>abs(dx)*1.2f){
                    val cancel=MotionEvent.obtain(event)
                    cancel.action=MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    post{onSwipe(if(dy<0)1 else -1)}
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
