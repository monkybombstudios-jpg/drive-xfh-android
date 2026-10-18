package za.co.xfh.drive

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.widget.*

/** Drive XFH's integrated contact browser and dial pad (WhatsApp opens for actual calls). */
class PhonePanel(private val host:Activity):LinearLayout(host){
    private data class Person(val name:String,val number:String,val photo:String)
    private val contacts=mutableListOf<Person>()
    private var selected:Person?=null
    private val white=Color.rgb(243,246,251)
    private val accent=Color.rgb(91,179,255)
    private val shade=Color.rgb(32,32,40)
    private val muted=Color.rgb(174,185,200)
    private lateinit var entries:LinearLayout
    private lateinit var info:LinearLayout
    private lateinit var digits:EditText
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Float=13f,color:Int=white,bold:Boolean=false)=TextView(host).apply{
        text=value;textSize=size;setTextColor(color);gravity=Gravity.CENTER_VERTICAL
        if(bold)typeface=Typeface.DEFAULT_BOLD
        setPadding(dp(5),dp(2),dp(5),dp(2))
    }
    private fun press(text:String,click:()->Unit)=text(text,16f,white,true).apply{
        gravity=Gravity.CENTER;setBackgroundColor(shade);setOnClickListener{click()}
    }
    init{orientation=HORIZONTAL;setPadding(dp(4),dp(4),dp(4),dp(4));setBackgroundColor(Color.rgb(14,14,18));layout()}
    private fun layout(){
        val left=LinearLayout(host).apply{orientation=VERTICAL;setBackgroundColor(shade);setPadding(dp(6),dp(5),dp(6),dp(5))}
        left.addView(text("CONTACTS",16f,accent,true),LayoutParams(-1,dp(32)))
        val search=EditText(host).apply{
            hint="Search contacts";textSize=13f;setSingleLine(true);setTextColor(white);setHintTextColor(muted)
        }
        left.addView(search,LayoutParams(-1,dp(38)))
        val scroll=ScrollView(host)
        entries=LinearLayout(host).apply{orientation=VERTICAL}
        scroll.addView(entries);left.addView(scroll,LayoutParams(-1,0,1f))
        addView(left,LayoutParams(0,-1,1f))
        val right=LinearLayout(host).apply{orientation=VERTICAL;setPadding(dp(9),dp(4),dp(5),dp(3))}
        info=LinearLayout(host).apply{orientation=VERTICAL;gravity=Gravity.CENTER_HORIZONTAL}
        right.addView(info,LayoutParams(-1,0,1f))
        digits=EditText(host).apply{
            textSize=17f;setTextColor(white);setSingleLine(true);gravity=Gravity.CENTER
            inputType=android.text.InputType.TYPE_CLASS_PHONE;hint="Enter a number";setHintTextColor(muted)
        }
        right.addView(digits,LayoutParams(-1,dp(42)))
        val keys=listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf("*","0","#"))
        for(keyrow in keys){
            val row=LinearLayout(host).apply{orientation=HORIZONTAL}
            for(key in keyrow){
                row.addView(press(key){digits.append(key)},LayoutParams(0,0,1f).apply{height=dp(39);setMargins(dp(2),dp(1),dp(2),dp(1))})
            }
            right.addView(row,LayoutParams(-1,dp(41)))
        }
        val actions=LinearLayout(host).apply{orientation=HORIZONTAL}
        actions.addView(press("⌫"){val t=digits.text;if(t.isNotEmpty())t.delete(t.length-1,t.length)},LayoutParams(0,dp(39),1f))
        actions.addView(press("WHATSAPP"){openWhatsApp()},LayoutParams(0,dp(39),2f).apply{leftMargin=dp(5)})
        right.addView(actions)
        addView(right,LayoutParams(0,-1,1f).apply{leftMargin=dp(7)})
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){showContacts(s?.toString()?:"")}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        showSelected();readContacts()
    }
    private fun readContacts(){
        if(host.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
            entries.addView(text("Contacts permission required",13f,muted))
            entries.addView(press("ALLOW CONTACTS"){host.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS),525)},LayoutParams(-1,dp(44)))
            return
        }
        Thread{
            val found=mutableListOf<Person>()
            try{
                val projection=arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                )
                host.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,projection,null,null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC")?.use{cursor->
                    while(cursor.moveToNext()&&found.size<3000){
                        val name=cursor.getString(0)?:continue
                        val number=cursor.getString(1)?:continue
                        val photo=cursor.getString(2)?:""
                        if(found.none{it.name==name&&it.number==number})found.add(Person(name,number,photo))
                    }
                }
            }catch(_:Exception){}
            host.runOnUiThread{contacts.clear();contacts.addAll(found);showContacts("")}
        }.start()
    }
    private fun showContacts(query:String){
        if(!::entries.isInitialized)return
        entries.removeAllViews()
        if(contacts.isEmpty()){entries.addView(text("No contacts found (check permission)",12f,muted));return}
        val matching=contacts.filter{it.name.contains(query,true)||it.number.contains(query)}.take(200)
        for(p in matching){
            entries.addView(text(p.name,13f,white,true).apply{
                setPadding(dp(7),dp(7),dp(5),dp(1))
                setOnClickListener{select(p)}
            },LayoutParams(-1,dp(28)))
            entries.addView(text(p.number,11f,muted).apply{
                setPadding(dp(7),0,dp(5),dp(7));setOnClickListener{select(p)}
            },LayoutParams(-1,dp(25)))
        }
    }
    private fun select(person:Person){
        selected=person;digits.setText(person.number);showSelected()
    }
    private fun showSelected(){
        info.removeAllViews()
        val p=selected
        val avatar=ImageView(host).apply{scaleType=ImageView.ScaleType.FIT_CENTER}
        if(p!=null&&p.photo.isNotEmpty())try{
            host.contentResolver.openInputStream(Uri.parse(p.photo))?.use{avatar.setImageBitmap(android.graphics.BitmapFactory.decodeStream(it))}
        }catch(_:Exception){}
        if(avatar.drawable==null){
            info.addView(text(p?.name?.take(1)?.uppercase()?:"☎",32f,accent,true).apply{gravity=Gravity.CENTER},
                LayoutParams(dp(72),dp(60)))
        }else info.addView(avatar,LayoutParams(dp(72),dp(60)))
        info.addView(text(p?.name?:"Select a contact",15f,white,true).apply{gravity=Gravity.CENTER;maxLines=1},
            LayoutParams(-1,dp(28)))
        info.addView(text(p?.number?:"WhatsApp calling",12f,muted).apply{gravity=Gravity.CENTER},
            LayoutParams(-1,dp(25)))
    }
    private fun openWhatsApp(){
        val number=digits.text.toString().filter{it.isDigit()}
        if(number.isEmpty()){Toast.makeText(host,"Choose a contact or enter a number",Toast.LENGTH_SHORT).show();return}
        // WhatsApp links open a conversation; voice calling remains inside WhatsApp itself.
        try{
            host.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+number)))
        }catch(_:Exception){Toast.makeText(host,"WhatsApp is unavailable",Toast.LENGTH_SHORT).show()}
    }
}
