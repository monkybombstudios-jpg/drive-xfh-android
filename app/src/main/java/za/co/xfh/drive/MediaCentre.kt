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
    private var mode=(prefs.getString("media_mode","MEDIA")?:"MEDIA").let{if(it=="FILES")"MEDIA" else it}
    private var filter=prefs.getString("media_filter","ALL")?:"ALL"
    private var currentFolder:DocumentFile?=null
    private var right:LinearLayout?=null
    private var list:LinearLayout?=null
    private var video:VideoView?=null
    private var activeRadio:MediaPlayer?=null
    private var radioView="SAVED"
    private val popularStations=mutableListOf<Triple<String,String,String>>()
    private val podcastEpisodes=mutableListOf<Pair<String,String>>()
    private var podcastFeed=prefs.getString("podcast_feed","")?:""
    private var podcastName=prefs.getString("podcast_show_name","")?:""
    private val podcastShows=listOf(
        "Timcast IRL" to "https://feeds.libsyn.com/574450/rss",
        "Podcast of the Lotus Eaters" to "https://feeds.soundcloud.com/users/soundcloud:users:896213575/sounds.rss",
        "The Glenn Beck Program" to "https://feeds.megaphone.fm/BMDC3567910388",
        "The Joe Rogan Experience" to "https://joeroganexp.libsyn.com/rss",
        "NPR Up First" to "https://feeds.npr.org/510318/podcast.xml"
    )
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
        for(item in listOf("MEDIA","RADIO","PODCASTS")){
            val label=button(item){mode=item;prefs.edit().putString("media_mode",mode).apply();render()}
            if(mode==item)label.setTextColor(blue)
            bar.addView(label,LayoutParams(d(90),d(33)).apply{rightMargin=d(5)})
        }

        addView(bar,LayoutParams(-1,d(40)))
        val columns=LinearLayout(activity).apply{orientation=HORIZONTAL}
        val left=LinearLayout(activity).apply{orientation=VERTICAL;setBackgroundColor(Color.rgb(19,19,25))}
        val tabs=LinearLayout(activity).apply{orientation=HORIZONTAL}
        if(mode=="MEDIA"){
            for(name in listOf("ALL","MUSIC","VIDEO")){
                val b=button(name){filter=name;prefs.edit().putString("media_filter",name).apply();render()}
                if(name==filter)b.setTextColor(blue)
                tabs.addView(b,LayoutParams(0,d(31),1f).apply{rightMargin=d(2)})
            }
        }else if(mode=="PODCASTS"){
            tabs.addView(text(if(podcastFeed.isBlank())"PODCAST LIBRARY" else podcastName.ifBlank{"PODCAST EPISODES"},11f,blue,true),LayoutParams(-1,d(31)))
        }else{
            for(tab in listOf("SAVED","POPULAR US")){
                val b=button(tab){radioView=tab;populate();if(tab=="POPULAR US"&&popularStations.isEmpty())loadUsStations()}
                if(radioView==tab)b.setTextColor(blue)
                tabs.addView(b,LayoutParams(0,d(31),1f).apply{rightMargin=d(2)})
            }
        }
        left.addView(tabs,LayoutParams(-1,d(32)))
        val controls=LinearLayout(activity).apply{orientation=HORIZONTAL}
        if(mode=="PODCASTS"){
            controls.addView(button("◀ SHOWS"){podcastFeed="";podcastName="";prefs.edit().remove("podcast_feed").remove("podcast_show_name").apply();populate()},LayoutParams(0,d(29),1f))
            controls.addView(button(if(podcastFeed.isBlank())"ADD RSS" else "REFRESH"){if(podcastFeed.isBlank())editPodcastFeed() else loadPodcasts()},LayoutParams(0,d(29),1f).apply{leftMargin=d(3)})
        }else{
            controls.addView(button(if(mode=="MEDIA")"◀  UP" else "+ STATION"){
                if(mode=="MEDIA"){currentFolder=currentFolder?.parentFile;populate()}else addStation()
            },LayoutParams(0,d(29),1f))
            controls.addView(button(if(mode=="MEDIA")"CHOOSE FOLDER" else "REFRESH"){
                if(mode=="MEDIA")selectFolder()else if(radioView=="POPULAR US")loadUsStations() else populate()
            },LayoutParams(0,d(29),1f).apply{leftMargin=d(3)})
        }
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
        if(mode=="PODCASTS" && podcastFeed.isNotBlank())loadPodcasts()
        if(mode=="RADIO"&&radioView=="POPULAR US")loadUsStations()
    }
    private fun item(title:String,subtitle:String,selected:Boolean=false,longClick:(()->Unit)?=null,click:()->Unit){
        val container=LinearLayout(activity).apply{
            orientation=VERTICAL;setPadding(d(5),d(2),d(5),d(2))
            setBackgroundColor(if(selected)Color.rgb(46,61,82) else bg)
            setOnClickListener{click()}
            if(longClick!=null)setOnLongClickListener{longClick();true}
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
        if(mode=="PODCASTS"){
            if(podcastEpisodes.isEmpty())item("Podcast episodes","Tap REFRESH to load an RSS feed"){}
            for((title,url) in podcastEpisodes)item("♫  "+title,"PODCAST · stream episode",playingName==title){playStream(title,url,"Podcast")}
            return
        }
        if(mode=="RADIO"){
            if(radioView=="POPULAR US"){
                if(popularStations.isEmpty())item("Popular US stations","Fetching live directory…"){}
                for((name,url,details) in popularStations){
                    item(name,details+" · hold to save",playingSource=="Radio"&&playingName==name,
                        longClick={saveStation(name,url)}){playStream(name,url)}
                }
            }else{
                val saved=stations()
                if(saved.length()==0)item("No stations saved","Select POPULAR US or use + STATION"){}
                for(i in 0 until saved.length()){
                    val st=saved.optJSONObject(i)?:continue
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
    private fun editPodcastFeed(){
        val input=EditText(activity).apply{setSingleLine(true);setText(podcastFeed);hint="Podcast RSS feed URL"}
        AlertDialog.Builder(activity).setTitle("Podcast RSS feed").setView(input)
            .setPositiveButton("LOAD"){_,_->
                val value=input.text.toString().trim()
                if(value.startsWith("https://")){podcastFeed=value;prefs.edit().putString("podcast_feed",value).apply();loadPodcasts()}
                else Toast.makeText(activity,"Use an HTTPS RSS feed",Toast.LENGTH_SHORT).show()
            }.setNegativeButton("CANCEL",null).show()
    }
    private fun loadPodcasts(){
        if(mode!="PODCASTS")return
        list?.removeAllViews()
        item("Loading episodes…","Fetching podcast RSS feed"){}
        Thread{
            try{
                val c=java.net.URL(podcastFeed).openConnection() as java.net.HttpURLConnection
                c.connectTimeout=12000;c.readTimeout=15000
                c.setRequestProperty("User-Agent","DriveXFH-Podcast/0.16")
                val result=mutableListOf<Pair<String,String>>()
                try{
                    if(c.responseCode!=200)throw Exception("Feed HTTP "+c.responseCode)
                    val xml=android.util.Xml.newPullParser()
                    xml.setInput(c.inputStream,"UTF-8")
                    var inItem=false;var currentTitle="";var audio=""
                    var event=xml.eventType
                    while(event!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT && result.size<60){
                        val tag=xml.name?:""
                        if(event==org.xmlpull.v1.XmlPullParser.START_TAG){
                            if(tag=="item"){inItem=true;currentTitle="";audio=""}
                            else if(inItem && tag=="title")currentTitle=xml.nextText().trim()
                            else if(inItem && tag=="enclosure")audio=xml.getAttributeValue(null,"url")?:""
                        }else if(event==org.xmlpull.v1.XmlPullParser.END_TAG && tag=="item"){
                            if(audio.startsWith("https://")||audio.startsWith("http://"))result.add((currentTitle.ifBlank{"Episode"}) to audio)
                            inItem=false
                        }
                        event=xml.next()
                    }
                }finally{c.disconnect()}
                activity.runOnUiThread{
                    if(mode=="PODCASTS"){
                        podcastEpisodes.clear();podcastEpisodes.addAll(result)
                        populate()
                        if(result.isEmpty())item("No audio episodes found","Try another RSS feed"){}
                    }
                }
            }catch(e:Exception){activity.runOnUiThread{if(mode=="PODCASTS"){list?.removeAllViews();item("Feed could not load",e.message?:"Connection error"){} }}}
        }.start()
    }
    private fun saveStation(name:String,url:String){
        val arr=stations()
        for(i in 0 until arr.length())if(arr.optJSONObject(i)?.optString("url")==url){
            Toast.makeText(activity,"Station already saved",Toast.LENGTH_SHORT).show();return
        }
        arr.put(org.json.JSONObject().put("name",name).put("url",url))
        prefs.edit().putString("radio_stations",arr.toString()).apply()
        Toast.makeText(activity,"Saved to Radio favourites",Toast.LENGTH_SHORT).show()
    }
    private fun loadUsStations(){
        if(mode!="RADIO"||radioView!="POPULAR US")return
        list?.removeAllViews();item("Loading popular US radio…","Checking Radio Browser"){}
        Thread{
            try{
                val endpoint="https://de1.api.radio-browser.info/json/stations/search?countrycode=US&hidebroken=true&order=votes&reverse=true&limit=80&is_https=true"
                val c=java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection
                c.connectTimeout=12000;c.readTimeout=15000
                c.setRequestProperty("User-Agent","DriveXFH/0.16.2 Android radio")
                val entries=mutableListOf<Triple<String,String,String>>()
                try{
                    if(c.responseCode!=200)throw Exception("Directory HTTP "+c.responseCode)
                    val data=org.json.JSONArray(c.inputStream.bufferedReader().use{it.readText()})
                    for(i in 0 until data.length()){
                        val station=data.optJSONObject(i)?:continue
                        val name=station.optString("name").trim()
                        val url=station.optString("url_resolved",station.optString("url")).trim()
                        if(name.isBlank()||!url.startsWith("https://"))continue
                        val tags=station.optString("tags").split(",").take(2).joinToString(" · ")
                        val details=listOf(station.optString("state"),tags).filter{it.isNotBlank()}.joinToString(" · ")
                        entries.add(Triple(name,url,details))
                    }
                }finally{c.disconnect()}
                activity.runOnUiThread{
                    if(mode=="RADIO"&&radioView=="POPULAR US"){
                        popularStations.clear();popularStations.addAll(entries);populate()
                        if(entries.isEmpty())item("No compatible stations found","Try refresh or add manually"){}
                    }
                }
            }catch(e:Exception){activity.runOnUiThread{
                if(mode=="RADIO"&&radioView=="POPULAR US"){
                    list?.removeAllViews();item("Directory unavailable",e.message?:"Try again later"){}
                }
            }}
        }.start()
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
                    saveStation(n,u);populate()
                }else Toast.makeText(activity,"Enter a name and a direct HTTPS stream URL",Toast.LENGTH_LONG).show()
            }.setNegativeButton("CANCEL",null).show()
    }
    private fun releaseRadio(){try{activeRadio?.release()}catch(_:Exception){};activeRadio=null}
    private fun playStream(name:String,url:String,source:String="Radio"){
        releaseRadio();stop()
        playingName=name;playingArtist=if(source=="Podcast")"Podcast episode" else "Internet radio";playingSource=source;isPlaying=false
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
        val surface=FrameLayout(activity).apply{setBackgroundColor(bg);clipChildren=true}
        if(playingSource=="Video"&&playingUri!=null){
            val v=VideoView(activity).apply{
                setVideoURI(playingUri);setOnPreparedListener{mp->
                    val videoW=mp.videoWidth;val videoH=mp.videoHeight
                    if(videoW>0&&videoH>0){
                        val width=surface.width;val height=surface.height
                        if(width>0&&height>0){
                            val fit=kotlin.math.min(width.toFloat()/videoW,height.toFloat()/videoH)
                            val layout=FrameLayout.LayoutParams((videoW*fit).toInt(),(videoH*fit).toInt(),Gravity.CENTER)
                            this.layoutParams=layout
                        }
                    }
                    mp.isLooping=false;start()
                }
                setOnCompletionListener{MediaCentre.isPlaying=false}
            }
            video=v;surface.addView(v,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        }else{
            val art=ImageView(activity).apply{scaleType=ImageView.ScaleType.FIT_CENTER}
            if(playingSource=="Music"&&playingUri!=null)try{
                val r=MediaMetadataRetriever();r.setDataSource(activity,playingUri)
                val bytes=r.embeddedPicture
                if(bytes!=null)art.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.size))
                r.release()
            }catch(_:Exception){}
            surface.addView(art,FrameLayout.LayoutParams(-1,-1))
            if(art.drawable==null)surface.addView(text(if(playingSource=="Radio")"◉ RADIO" else if(playingSource=="Podcast")"◉ PODCAST" else "♫",34f,blue,true).apply{gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,-1))
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
                (if(playingSource=="Radio"||playingSource=="Podcast")activeRadio else player)?.let{
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
