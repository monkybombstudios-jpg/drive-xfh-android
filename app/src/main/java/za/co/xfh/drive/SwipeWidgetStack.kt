package za.co.xfh.drive

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/** Gesture paging independent of child touch handlers; normal taps reach buttons. */
class SwipeWidgetStack(context:Context,private val onSwipe:(Int)->Unit):LinearLayout(context){
    private val minimum=ViewConfiguration.get(context).scaledTouchSlop*5
    private var firstX=0f
    private var firstY=0f
    private var fired=false
    init{orientation=VERTICAL}
    override fun dispatchTouchEvent(event:MotionEvent):Boolean{
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{firstX=event.rawX;firstY=event.rawY;fired=false}
            MotionEvent.ACTION_MOVE->{
                val dy=event.rawY-firstY
                val dx=event.rawX-firstX
                if(!fired && abs(dy)>minimum && abs(dy)>abs(dx)*1.4f){
                    fired=true
                    val cancel=MotionEvent.obtain(event)
                    cancel.action=MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    onSwipe(if(dy<0)1 else -1)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL->{fired=false}
            MotionEvent.ACTION_UP->{if(fired){fired=false;return true}}
        }
        return super.dispatchTouchEvent(event)
    }
}
