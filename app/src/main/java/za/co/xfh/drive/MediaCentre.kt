package za.co.xfh.drive

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.documentfile.provider.DocumentFile

/** Source-browser, local player and internet-radio view for the 800 x 480 cockpit. */
class MediaCentre(private val activity:Activity,private val selectFolder:()->Unit):LinearLayout(activity) {
    companion object {
        var player:MediaPlayer?=null
        var playingUri:Uri?=null
        var playingName=""
        var playingArtist=""
        var playingSource="Music"
        var isPlaying=false
        fun stop() { try {player?.release()}catch(_:Exception){};player=null;isPlaying=false;playingUri=null }
    }
    private val prefs=activity.getSharedPreferences("drive_xfh",Activity.MODE_PRIVATE)
    private val ink=Color.rgb(235,239,247)
    private val muted=Color.rgb(162,175,195)
    private val blue=Color.rgb(88,171,250)
    private val bg=Color.rgb(23,23,29)
    private val panel=Color.rgb(32,32,40)
    private var mode=prefs.getString("media_mode","FILES")?:"FILES"
    private var filter=prefs.getString("media_filter","ALL")?:"ALL"
    private var currentFolder:DocumentFile?=null
    private var right:LinearLayout?=null
    private var list:LinearLayout?=null
    private var video:VideoView?=null
    private var activeRadio:MediaPlayer?=null
    private fun d(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun text(s:String,size:Float=12f,color:Int=ink,bold:Boolean=false)=TextView(activity).apply{
        text=s;textSize=size;setTextColor(color);gravity=Gravity.CENTER_VERTICAL
        if(bold)typeface=Typeface.DEFAULT_BOLD
        setPadding(d(6),d(4),d(6),d(4))
        isSingleLine=true;ellipsize=android.text.TextUtils.TruncateAt.END
    }
    private fun button(s:String,click:()->Unit)=text(s,11f,ink,true).apply{
        gravity=Gravity.CENTER;backgroundTintList=null;setBackgroundColor(panel)
        setOnClickListener{click()}
    }
    init{orientation=VERTICAL;setBackgroundColor(bg);render()}
    private fun render(){
        removeAllViews()
        val bar=LinearLayout(activity).apply{orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        bar.addView(text("MEDIA",14f,blue,true),LayoutParams(d(75),d(38)))
        for(item in listOf("FILES","RADIO")){
            val label=button(item){mode=item;prefs.edit().putString("media_mode",mode).apply();render()}
            if(mode==item)label.setTextColor(blue)
            bar.addView(label,LayoutParams(d(90),d(33)).apply{rightMargin=d(5)})
        }
        bar.addView(text(if(mode=="RADIO")"STREAMING RADIO" else "LOCAL AUDIO + VIDEO",10f,muted),LayoutParams(0,d(32),1f))
        addView(bar,LayoutParams(-1,d(40)))
        val columns=LinearLayout(activity).apply{orientation=HORIZONTAL}
        val left=LinearLayout(activity).apply{orientation=VERTICAL;setBackgroundColor(Color.rgb(19,19,25))}
        val tabs=LinearLayout(activity).apply{orientation=HORIZONTAL}
        if(mode=="FILES"){
            for(name in listOf("ALL","MUSIC","VIDEO")){
                val b=button(name){filter=name;prefs.edit().putString("media_filter",name).apply();render()}
                if(name==filter)b.setTextColor(blue)
                tabs.addView(b,LayoutParams(0,d(31),1f).apply{rightMargin=d(2)})
            }
        }else{
            for(name in listOf("INTERNET","FM","AM")){
                val b=button(name){filter=name;render()}
                if(name==filter)b.setTextColor(blue)
                tabs.addView(b,LayoutParams(0,d(31),1f).apply{rightMargin=d(2)})
            }
        }
        left.addView(tabs,LayoutParams(-1,d(32)))
        val controls=LinearLayout(activity).apply{orientation=HORIZONTAL}
        controls.addView(button(if(mode=="FILES")"◀  UP" else "+ STATION"){
            if(mode=="FILES"){currentFolder=currentFolder?.parentFile;populate()}else addStation()
        },LayoutParams(0,d(29),1f))
        controls.addView(button(if(mode=="FILES")"CHOOSE FOLDER" else "REFRESH"){
            if(mode=="FILES")selectFolder()else populate()
        },LayoutParams(0,d(29),1f).apply{leftMargin=d(3)})
        left.addView(controls,LayoutParams(-1,d(30)))
        val scroll=ScrollView(activity).apply{isFillViewport=true}
        list=LinearLayout(activity).apply{orientation=VERTICAL}
        scroll.addView(list)
        left.addView(scroll,LayoutParams(-1,0,1f))
        columns.addView(left,LayoutParams(0,-1,1f))
        val pane=LinearLayout(activity).apply{
            orientation=VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setBackgroundColor(panel)
            setPadding(d(12),d(5),d(10),d(7))
        }
        right=pane
        columns.addView(pane,LayoutParams(0,-1,1f).apply{leftMargin=d(7)})
        addView(columns,LayoutParams(-1,0,1f))
        populate()
        showDetails()
    }
    private fun item(title:String,subtitle:String,selected:Boolean=false,click:()->Unit){
        val container=LinearLayout(activity).apply{
            orientation=VERTICAL;setPadding(d(5),d(2),d(5),d(2))
            setBackgroundColor(if(selected)Color.rgb(46,61,82) else bg)
            setOnClickListener{click()}
        }
        container.addView(text(title,12f,ink,true),LayoutParams(-1,d(25)))
        if(subtitle.isNotBlank())container.addView(text(subtitle,10f,muted),LayoutParams(-1,d(18)))
        list?.addView(container,LayoutParams(-1,-2))
        list?.addView(View(activity).apply{setBackgroundColor(Color.rgb(50,50,60))},LayoutParams(-1,d(1)))
    }
    private fun root():DocumentFile?{
        val url=prefs.getString("media_folder","")?:return null
        return try{DocumentFile.fromTreeUri(activity,Uri.parse(url))}catch(_:Exception){null}
    }
    private fun populate(){
        list?.removeAllViews()
        if(mode=="RADIO"){
            if(filter=="FM"||filter=="AM"){
                item("Tuner not connected","AM/FM scanning requires compatible radio hardware"){}
                item("Use INTERNET for now","Save and play streaming stations"){}
            }else{
                val stations=stations()
                if(stations.length()==0)item("No stations saved","+ STATION to add a streaming radio URL"){}
                for(i in 0 until stations.length()){
                    val st=stations.optJSONObject(i)?:continue
                    val title=st.optString("name","Station")
                    val url=st.optString("url","")
                    item(title,url,playingSource=="Radio"&&playingName==title){playStream(title,url)}
                }
            }
            return
        }
        val folder=currentFolder?:root()
        if(folder==null){item("No media folder selected","Tap CHOOSE FOLDER to allow SD card or phone access"){};return}
        val files=try{folder.listFiles().sortedWith(compareBy<DocumentFile>{!it.isDirectory}.thenBy{it.name?.lowercase()?:""})}catch(_:Exception){emptyList()}
        if(files.isEmpty())item("Folder is empty","Select a different folder"){}
        for(f in files){
            val type=f.type?:""
            val video=type.startsWith("video/")||listOf("mp4","mkv","webm","3gp").any{f.name?.endsWith("."+it,true)==true}
            val audio=type.startsWith("audio/")||listOf("mp3","aac","ogg","m4a","flac","wav").any{f.name?.endsWith("."+it,true)==true}
            if(f.isDirectory){
                item("▸  "+(f.name?:"Folder"),"Folder"){currentFolder=f;populate()}
            }else if((filter=="ALL"&&(audio||video))||(filter=="MUSIC"&&audio)||(filter=="VIDEO"&&video)){
                item(if(video)"▶  "+f.name else "♫  "+f.name,if(video)"VIDEO" else "AUDIO",playingUri==f.uri){playFile(f,video)}
            }
        }
    }
    private fun playFile(f:DocumentFile,isVideo:Boolean){
        val uri=f.uri
        try{
            releaseRadio()
            if(isVideo){
                stop()
                playingUri=uri;playingName=f.name?:"Video";playingArtist="";playingSource="Video";isPlaying=true
                showDetails()
            }else{
                stop()
                val mp=MediaPlayer()
                mp.setDataSource(activity,uri)
                mp.prepare()
                mp.start()
                player=mp;playingUri=uri;playingName=f.name?:"Audio";playingArtist="";playingSource="Music";isPlaying=true
                try{
                    val retriever=MediaMetadataRetriever()
                    retriever.setDataSource(activity,uri)
                    playingName=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?:playingName
                    playingArtist=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?:""
                    retriever.release()
                }catch(_:Exception){}
                mp.setOnCompletionListener{isPlaying=false;showDetails()}
                showDetails()
            }
            populate()
        }catch(e:Exception){Toast.makeText(activity,"Cannot play file: "+e.message,Toast.LENGTH_LONG).show()}
    }
    private fun stations()=try{org.json.JSONArray(prefs.getString("radio_stations","[]"))}catch(_:Exception){org.json.JSONArray()}
    private fun addStation(){
        val form=LinearLayout(activity).apply{orientation=VERTICAL;setPadding(d(14),d(8),d(14),d(8))}
        val name=EditText(activity).apply{hint="Station name";setSingleLine(true)}
        val url=EditText(activity).apply{hint="https://... direct audio stream";setSingleLine(true)}
        form.addView(name);form.addView(url)
        AlertDialog.Builder(activity).setTitle("Save internet radio station").setView(form)
            .setPositiveButton("SAVE"){_,_->
                val n=name.text.toString().trim();val u=url.text.toString().trim()
                if(n.isNotBlank()&&u.startsWith("https://")){
                    val arr=stations();arr.put(org.json.JSONObject().put("name",n).put("url",u))
                    prefs.edit().putString("radio_stations",arr.toString()).apply();populate()
                }else Toast.makeText(activity,"Enter a name and a direct HTTPS stream URL",Toast.LENGTH_LONG).show()
            }.setNegativeButton("CANCEL",null).show()
    }
    private fun releaseRadio(){try{activeRadio?.release()}catch(_:Exception){};activeRadio=null}
    private fun playStream(name:String,url:String){
        releaseRadio();stop()
        playingName=name;playingArtist="Internet radio";playingSource="Radio";isPlaying=false
        showDetails()
        val p=MediaPlayer();activeRadio=p
        try{
            p.setDataSource(url)
            p.setOnPreparedListener{it.start();isPlaying=true;showDetails()}
            p.setOnErrorListener{_,_,_->isPlaying=false;showDetails();true}
            p.prepareAsync()
        }catch(e:Exception){releaseRadio();Toast.makeText(activity,e.message?:"Stream unavailable",Toast.LENGTH_LONG).show()}
    }
    private fun showDetails(){
        val area=right?:return
        area.removeAllViews()
        area.addView(text(playingName.ifEmpty{"Select music, video or radio"},15f,ink,true),LayoutParams(-1,d(32)))
        area.addView(text(playingArtist.ifEmpty{playingSource},11f,muted),LayoutParams(-1,d(26)))
        val surface=FrameLayout(activity).apply{setBackgroundColor(bg)}
        if(playingSource=="Video"&&playingUri!=null){
            val v=VideoView(activity).apply{
                setVideoURI(playingUri);setOnPreparedListener{it.isLooping=false;start()}
                setOnCompletionListener{isPlaying=false}
            }
            video=v;surface.addView(v,FrameLayout.LayoutParams(-1,-1))
        }else{
            val art=ImageView(activity).apply{scaleType=ImageView.ScaleType.FIT_CENTER}
            if(playingSource=="Music"&&playingUri!=null)try{
                val r=MediaMetadataRetriever();r.setDataSource(activity,playingUri)
                val bytes=r.embeddedPicture
                if(bytes!=null)art.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.size))
                r.release()
            }catch(_:Exception){}
            surface.addView(art,FrameLayout.LayoutParams(-1,-1))
            if(art.drawable==null)surface.addView(text(if(playingSource=="Radio")"◉ RADIO" else "♫",34f,blue,true).apply{gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,-1))
        }
        area.addView(surface,LayoutParams(-1,0,1f))
        val controls=LinearLayout(activity).apply{orientation=HORIZONTAL}
        controls.addView(button("■ STOP"){
            stop();releaseRadio();playingSource="Music";playingName="";playingArtist="";showDetails()
        },LayoutParams(0,d(38),1f))
        controls.addView(button("▶ / Ⅱ"){
            if(playingSource=="Video"){
                video?.let{if(it.isPlaying)it.pause() else it.start()}
            }else{
                (if(playingSource=="Radio")activeRadio else player)?.let{
                    if(it.isPlaying){it.pause();isPlaying=false}else{it.start();isPlaying=true}
                }
            }
        },LayoutParams(0,d(38),1f).apply{leftMargin=d(5)})
        area.addView(controls,LayoutParams(-1,d(40)))
    }
    fun refreshFolder(){currentFolder=null;populate()}
    override fun onDetachedFromWindow(){
        video?.stopPlayback();video=null
        super.onDetachedFromWindow()
    }
}
