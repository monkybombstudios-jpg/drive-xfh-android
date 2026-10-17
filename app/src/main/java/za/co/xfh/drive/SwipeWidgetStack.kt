package za.co.xfh.drive

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/** Page two cockpit cards per vertical swipe without intercepting button taps. */
class SwipeWidgetStack(context:Context,private val onSwipe:(Int)->Unit):LinearLayout(context){
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private var downX=0f
    private var downY=0f
    private var tracking=false
    init{orientation=VERTICAL}
    override fun onInterceptTouchEvent(event:MotionEvent):Boolean{
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=event.x;downY=event.y;tracking=false}
            MotionEvent.ACTION_MOVE->{
                val dy=event.y-downY
                if(abs(dy)>slop*2&&abs(dy)>abs(event.x-downX)*1.3f){
                    tracking=true;parent?.requestDisallowInterceptTouchEvent(true);return true
                }
            }
        }
        return false
    }
    override fun onTouchEvent(event:MotionEvent):Boolean{
        when(event.actionMasked){
            MotionEvent.ACTION_MOVE->return tracking
            MotionEvent.ACTION_UP->{
                val dy=event.y-downY
                if(tracking&&abs(dy)>slop*3)onSwipe(if(dy<0)1 else -1)
                tracking=false;return true
            }
            MotionEvent.ACTION_CANCEL->{tracking=false;return true}
        }
        return true
    }
}
