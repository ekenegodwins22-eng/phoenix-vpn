package com.phoenix.vpn

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity:AppCompatActivity(){
    private lateinit var repo:ConfigRepository; private lateinit var root:LinearLayout; private var connected=false; private var started=0L; private var currentScreen=""
    private val feedUrl="https://raw.githubusercontent.com/ekenegodwins22-eng/phoenix-vpn/main/feed/phoenix-feed.json"
    private val importDoc=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{try{val text=contentResolver.openInputStream(it)?.bufferedReader()?.readText().orEmpty();showImport(text)}catch(e:Exception){toast("Import failed: ${e.message}")}}}
    private val tunnelReceiver=object:android.content.BroadcastReceiver(){override fun onReceive(c:android.content.Context,i:Intent){when(i.action){TunnelService.ACTION_TUNNEL_UP->{connected=true;statsRun=true;repo.addLog("Connected");if(currentScreen=="PhoenixTunnel")showHome()};TunnelService.ACTION_TUNNEL_DOWN->{connected=false;repo.addLog("Tunnel down");if(currentScreen=="PhoenixTunnel")showHome()}}}}
    override fun onCreate(savedInstanceState:Bundle?){installSplashScreen();super.onCreate(savedInstanceState);repo=ConfigRepository(this);window.statusBarColor=0xff120b22.toInt();registerReceiver(tunnelReceiver,android.content.IntentFilter(TunnelService.ACTION_TUNNEL_UP).apply{addAction(TunnelService.ACTION_TUNNEL_DOWN)},if(android.os.Build.VERSION.SDK_INT>=33)android.content.Context.RECEIVER_NOT_EXPORTED else 0);try{showHome();updateFeed(showResult=false)}catch(e:Throwable){crashScreen(e)}}
    override fun onDestroy(){runCatching{unregisterReceiver(tunnelReceiver)};super.onDestroy()}
    private fun crashScreen(e:Throwable){root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,40,20,20);setBackgroundColor(0xff120b22.toInt())};setContentView(root);root.addView(text("startup error",20,true));val msg=e.message?:(e.javaClass.simpleName);root.addView(text(msg,14,false));root.addView(text(android.util.Log.getStackTraceString(e),11,false));repo.addLog("CRASH $msg")}
    private fun base(title:String){currentScreen=title;root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,16,20,20);setBackgroundColor(0xff120b22.toInt())}; val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}; val menu=button("☰"){drawer()};val heading=text(title,22,true);bar.addView(menu);bar.addView(heading,LinearLayout.LayoutParams(0,-2,1f));bar.addView(text("v1",12,true));root.addView(bar);val sc=ScrollView(this);sc.addView(root);setContentView(sc)}
    private var baseRx=0L; private var baseTx=0L; private var connectAt=0L; private var statsRun=false
    private var halo:View?=null; private var statsLabel:TextView?=null; private var stateLabel:TextView?=null; private var anim:ValueAnimator?=null
    private fun showHome(){base("PhoenixTunnel")
        val status=text("STATUS :",15,false).apply{gravity=Gravity.CENTER}
        stateLabel=text(if(connected)"Connected" else "Disconnected",20,true).apply{gravity=Gravity.CENTER;setTextColor(if(connected)0xff2e9e4f.toInt() else 0xffb9a8d8.toInt())}
        root.addView(status);root.addView(stateLabel)
        val dp=resources.displayMetrics.density
        val wrap=FrameLayout(this);wrap.layoutParams=LinearLayout.LayoutParams(-1,(250*dp).toInt())
        val haloV=View(this).apply{background=android.graphics.drawable.GradientDrawable().apply{shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(if(connected)0x331e8e3e.toInt() else 0x22ffffff.toInt())};layoutParams=FrameLayout.LayoutParams((230*dp).toInt(),(230*dp).toInt(),Gravity.CENTER)}
        val circleV=View(this).apply{background=android.graphics.drawable.GradientDrawable().apply{shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(if(connected)0xff1e8e3e.toInt() else 0xff382553.toInt())};layoutParams=FrameLayout.LayoutParams((170*dp).toInt(),(170*dp).toInt(),Gravity.CENTER)}
        val glyph=text("",0,false).apply{text=if(connected)"STOP" else "GO";textSize=26f;setTextColor(-1);setTypeface(null,1);gravity=Gravity.CENTER}
        wrap.addView(haloV);wrap.addView(circleV);wrap.addView(glyph,FrameLayout.LayoutParams((170*dp).toInt(),(170*dp).toInt(),Gravity.CENTER))
        wrap.setOnClickListener{if(anim==null)startPulse(haloV);toggle()}
        root.addView(wrap);halo=haloV;startPulse(haloV)
        statsLabel=text("",14,true).apply{gravity=Gravity.CENTER}
        root.addView(statsLabel);statsRun=true;tickStats()
        root.addView(sectionLabel("SELECT SERVER"))
        val activeP=repo.selectedProfile()?.let{n->repo.listProfiles().firstOrNull{it.name==n}}
        root.addView(creebRow("🏠","BEST SERVER",activeP?.name?:"tap to pick server"){serverSheet()})
        root.addView(sectionLabel("SELECT NETWORK"))
        val activeT=repo.listTweaks().firstOrNull{it.active}
        root.addView(creebRow("🇳🇬",activeT?.name?:"tap to pick network",activeT?.protocol?:""){networkSheet()})
        root.addView(button("UPDATE TWEAKS"){updateFeed(true)})
        root.addView(button("menu  •  servers  •  configs  •  logs"){drawer()})
    }
    private fun serverSheet(){if(connected){toast("Disconnect first to change server");return};val profiles=repo.listProfiles();if(profiles.isEmpty()){toast("No servers yet — press UPDATE TWEAKS");return};val sel=repo.selectedProfile();darkDialog(this).setTitle("SELECT SERVER").setItems(profiles.map{if(it.name==sel)"✓ ${it.name}" else it.name}.toTypedArray()){_,i->val p=profiles[i];repo.selectProfile(p.name);repo.addLog("Selected server ${p.name}");toast("${p.name} selected");if(currentScreen=="PhoenixTunnel")showHome() else showServers()}.show()}
    private fun networkSheet(){if(connected){toast("Disconnect first to change network");return};val tweaks=repo.listTweaks();if(tweaks.isEmpty()){toast("No networks yet — press UPDATE TWEAKS");return};darkDialog(this).setTitle("SELECT NETWORK").setItems(tweaks.map{if(it.active)"✓ ${it.name}" else it.name}.toTypedArray()){_,i->val t=tweaks[i];repo.saveTweak(t.copy(active=true));repo.addLog("Activated network ${t.name}");toast("${t.name} selected");if(currentScreen=="PhoenixTunnel")showHome() else showNetworks()}.show()}
private fun sectionLabel(s:String):TextView=text(s,14,true).apply{setPadding(8,26,8,8)}
    private fun creebRow(icon:String,title:String,sub:String,action:()->Unit):View{val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(26,24,26,24);background=android.graphics.drawable.GradientDrawable().apply{setColor(0xff26183b.toInt());cornerRadius=26f};layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,10,0,10)};setOnClickListener{action()}}
        row.addView(text(icon,26,false).apply{setPadding(0,0,26,0)})
        val col=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        col.addView(text(title,17,true).apply{setPadding(0,0,0,2)});col.addView(text(sub,12,false))
        row.addView(col);return row}
    private fun startPulse(v:View){anim?.cancel();val a=ValueAnimator.ofFloat(0.85f,1.15f).apply{duration=1100;repeatCount=ValueAnimator.INFINITE;repeatMode=ValueAnimator.REVERSE;addUpdateListener{val f=it.animatedValue as Float;v.scaleX=f;v.scaleY=f}};a.start();anim=a}
    private fun tickStats(){val l=statsLabel?:return;if(currentScreen!="PhoenixTunnel"){statsRun=false;return}
        if(connected){val rx=(android.net.TrafficStats.getTotalRxBytes()-baseRx).coerceAtLeast(0);val tx=(android.net.TrafficStats.getTotalTxBytes()-baseTx).coerceAtLeast(0);val mins=(System.currentTimeMillis()-connectAt)/60000
            val ip=try{java.net.NetworkInterface.getNetworkInterfaces().asSequence().filter{it.name?.startsWith("tun")==true}.flatMap{n->n.inetAddresses.asSequence()}.firstOrNull{a->a is java.net.Inet4Address}?.hostAddress}catch(e:Exception){null}
            l.text="↓ ${kb(rx)}   ↑ ${kb(tx)}   •   session ${mins}m${if(ip!=null)"\nIP: $ip" else ""}"}
        else{l.text="not connected"}
        Handler(Looper.getMainLooper()).postDelayed({if(statsRun)tickStats()},1000)}
    private fun tm():android.telephony.TelephonyManager?=getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
    private fun kb(b:Long):String=if(b>=1048576)"%.1f MB".format(b/1048576f) else "${b/1024} kB"
    private fun drawer(){val choices=arrayOf("Home","Servers","UPDATE TWEAKS","Networks","Create Config","Import Config","V2Ray URI Converter","Tweak Lab","Logs","Settings","About");darkDialog(this).setTitle("PhoenixTunnel").setItems(choices){_,i->when(i){0->showHome();1->showServers();2->updateFeed(true);3->showNetworks();4->showCreate();5->importDoc.launch(arrayOf("*/*"));6->showConverter();7->showLab();8->showLogs();else->{base(choices[i]);root.addView(text(choices[i],18,true))}}}.show()}
    private fun showServers(){base("Servers")
        root.addView(button("UPDATE TWEAKS"){updateFeed(true)})
        val sel=repo.selectedProfile()
        repo.listProfiles().forEach{p->val isSel=p.name==sel
            val row=creebRow(if(p.protocol==TunnelProtocol.WIREGUARD)"🛡" else "⚡",p.name.uppercase(),"${p.protocol.label}${if(p.content.contains("PLACEHOLDER"))" • keys need replacing" else ""}"){repo.selectProfile(p.name);repo.addLog("Selected server ${p.name}");toast("${p.name} selected");showServers()}
            if(isSel)row.background=android.graphics.drawable.GradientDrawable().apply{setColor(0xff2e9e4f.toInt());cornerRadius=26f}
            row.setOnLongClickListener{darkDialog(this).setTitle("Delete ${p.name}?").setPositiveButton("Delete"){_,_->repo.deleteProfile(p.name);if(sel==p.name)selFileDel();repo.addLog("Deleted profile ${p.name}");showServers()}.setNegativeButton("Cancel",null).show();true}
            root.addView(row)}
        if(repo.listProfiles().isEmpty())root.addView(text("no servers yet — press UPDATE TWEAKS",15,false))
        root.addView(button("＋ create / import profile"){drawer()})}
    private fun selFileDel(){val f=java.io.File(java.io.File(filesDir,"phoenix"),"selected.txt");if(f.exists())f.delete()}
private fun hasNetwork():Boolean{val manager=getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager;val network=manager.activeNetwork?:return false;val caps=manager.getNetworkCapabilities(network)?:return false;return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)}
    private fun updateFeed(showResult:Boolean){
        if(!hasNetwork()){if(showResult)toast("Offline. Connect to Wi-Fi or mobile data and try again");return}
        Thread({var connection:java.net.HttpURLConnection?=null;try{
            connection=(java.net.URL(feedUrl).openConnection() as java.net.HttpURLConnection).apply{connectTimeout=10000;readTimeout=15000;requestMethod="GET";setRequestProperty("Accept","application/json")}
            if(connection.responseCode !in 200..299)throw java.io.IOException("Feed request failed (${connection.responseCode})")
            val json=JSONObject(connection.inputStream.bufferedReader().use{it.readText()});val servers=json.getJSONArray("servers");val tweaks=json.getJSONArray("tweaks");var serverCount=0;var tweakCount=0
            for(i in 0 until servers.length()){val server=servers.getJSONObject(i);repo.saveFeedServer(server.getString("name"),server.getString("host"),server.getInt("port"),server.optString("type","node"),server.optJSONObject("wg"));serverCount++}
            for(i in 0 until tweaks.length()){val tweak=tweaks.getJSONObject(i);repo.saveTweak(TweakProfileJsonParser.parse(tweak.toString()));tweakCount++}
            runOnUiThread{if(showResult)toast("Loaded $serverCount servers, $tweakCount tweaks");if(currentScreen=="Servers")showServers()}
        }catch(e:Exception){if(showResult)runOnUiThread{toast("Update failed: ${e.message?:"check your connection"}")}}finally{connection?.disconnect()}},"Phoenix-feed-update").apply{isDaemon=true;start()}
    }
    private fun showNetworks(){base("Networks");repo.listTweaks().forEach{p->root.addView(button("${if(p.active)"✓ " else ""}${p.name}  ·  ${p.protocol}"){repo.saveTweak(p.copy(active=true));repo.addLog("Activated tweak ${p.name}");showNetworks()})};root.addView(button("Add tweak profile"){tweakEditor()})}
    private fun tweakEditor(){val name=edit("Profile name");val protocol=edit("Protocol");val payload=edit("Payload with [host], [crlf], [ua]");val box=LinearLayout(this).apply{orientation=1;addView(name);addView(protocol);addView(payload)};darkDialog(this).setTitle("Tweak profile").setView(box).setPositiveButton("Save"){_,_->try{val p=TweakProfileJsonParser.parse(JSONObject().put("name",name.text.toString()).put("protocol",protocol.text.toString()).put("payload",payload.text.toString()).toString());repo.saveTweak(p);showNetworks()}catch(e:Exception){toast(e.message?:"Invalid tweak")}}.setNegativeButton("Cancel",null).show()}
    private fun showCreate(){
        base("Create Config")
        val protocol=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,TunnelProtocol.entries.map{it.label})}
        val name=edit("Profile name")
        val host=edit("SSH host")
        val port=EditText(this).apply{hint="SSH port (22)";inputType=android.text.InputType.TYPE_CLASS_NUMBER;setText("22")}
        val username=edit("SSH username")
        val password=EditText(this).apply{hint="SSH password";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD}
        val sni=edit("Optional TLS SNI / SniffedHost")
        val payload=edit("Optional payload ([host], [ua], [crlf], [split])",true)
        val wg=edit("[Interface]\\nPrivateKey = ...\\nAddress = ...\\n[Peer]\\nPublicKey = ...\\nEndpoint = host:port\\nAllowedIPs = 0.0.0.0/0",true)
        root.addView(protocol);root.addView(name);root.addView(host);root.addView(port);root.addView(username);root.addView(password);root.addView(sni);root.addView(payload);root.addView(wg)
        protocol.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent:AdapterView<*>?){ }
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){
                val isWg=TunnelProtocol.fromLabel(protocol.selectedItem.toString())==TunnelProtocol.WIREGUARD
                listOf(host,port,username,password,sni,payload).forEach{it.visibility=if(isWg)View.GONE else View.VISIBLE}
                wg.visibility=if(isWg)View.VISIBLE else View.GONE
            }
        }
        root.addView(button("Validate and save",true){
            try{
                val selected=TunnelProtocol.fromLabel(protocol.selectedItem.toString())
                val content=if(selected==TunnelProtocol.WIREGUARD)wg.text.toString() else JSONObject().put("host",host.text.toString().trim()).put("port",port.text.toString().toIntOrNull()?:22).put("username",username.text.toString()).put("password",password.text.toString()).put("sniffedHost",sni.text.toString()).put("payload",payload.text.toString()).toString()
                val engine=TunnelEngines.forProtocol(selected,this@MainActivity)
                val result=engine.validate(content)
                if(selected==TunnelProtocol.WIREGUARD)reachability(content)
                repo.saveProfile(name.text.toString(),content,selected)
                repo.addLog("Saved ${name.text} after validation: $result")
                toast("Validated and saved")
                showServers()
            }catch(e:Exception){repo.addLog("Config rejected: ${e.message}");toast("Rejected: ${e.message}")}
        })
    }
    private fun reachability(s:String){val endpoint=WireGuardConfigParser.parse(s).endpoint;val host=endpoint.substringBeforeLast(':').removePrefix("[").removeSuffix("]");val port=endpoint.substringAfterLast(':').toIntOrNull()?:throw IllegalArgumentException("Endpoint port invalid");val ok=java.net.Socket().use{it.connect(java.net.InetSocketAddress(host,port),2500);true};if(!ok)throw IllegalArgumentException("Reachability check failed")}
    private fun showImport(content:String,name:String="Imported"){base("Import Config");val input=EditText(this).apply{setText(content);minLines=10;gravity=Gravity.TOP};val n=EditText(this).apply{hint="Profile name";setText(name)};root.addView(text("Paste a WireGuard .conf profile",14,false));root.addView(n);root.addView(input);root.addView(button("Validate and import",true){try{repo.importProfile(n.text.toString(),input.text.toString());repo.addLog("Imported profile ${n.text}");toast("Imported");showServers()}catch(e:Exception){toast("Import rejected: ${e.message}")}})}
    private fun showConverter(){base("V2Ray URI Converter");val input=edit("vmess:// or vless://");root.addView(input);root.addView(button("Convert / inspect",true){try{val c=V2RayUriParser.parse(input.text.toString());root.addView(card("${c.protocol.uppercase()}  ${c.server}:${c.port}\nID: ${c.id}\nNetwork: ${c.network}  TLS: ${c.tls}"))}catch(e:Exception){toast("Invalid URI: ${e.message}")}})}
    private fun showLogs(){base("Logs");val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};bar.addView(text("HOME     •     LOGS",14,true),LinearLayout.LayoutParams(0,-2,1f));bar.addView(button("🗑"){repo.clearLogs();toast("Logs cleared");showLogs()});root.addView(bar);root.addView(text(repo.logs(),14,false));root.addView(button("Home"){showHome()})}
    private fun toggle(){if(connected){stopService(Intent(this,TunnelService::class.java));connected=false;repo.addLog("Disconnected");showHome();return};val selName=repo.selectedProfile();val profile=repo.listProfiles().firstOrNull{it.name==selName}?:repo.listProfiles().firstOrNull();if(profile==null){toast("Import or create a profile first");return};tm()?.let{val op=it.networkOperatorName;if(!op.isNullOrBlank())repo.addLog("Network provider: $op")};repo.addLog("Connecting");baseRx=android.net.TrafficStats.getTotalRxBytes();baseTx=android.net.TrafficStats.getTotalTxBytes();connectAt=System.currentTimeMillis();val consent=VpnService.prepare(this);if(consent!=null)startActivityForResult(consent,9) else{repo.addLog("Connecting via ${profile.name}");startTunnel(profile)}}
    private fun showLab(){base("Tweak Lab");root.addView(text("Connection diagnostics: one manual check per press. Single checks only, no automation.",13,false));val host=edit("Host e.g. 1.1.1.1 or example.com");val port=EditText(this).apply{hint="Port";inputType=android.text.InputType.TYPE_CLASS_NUMBER};val method=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("TCP","GET","POST","CONNECT"))};val template=edit("Optional raw request template ([host] [ua] [crlf] [split])",true);val out=TextView(this).apply{textSize=13f;setTextColor(0xffcbb9dd.toInt());setPadding(8,16,8,16)};root.addView(host);root.addView(port);root.addView(method);root.addView(template);root.addView(button("TEST",true){val h=host.text.toString().trim();val pt=port.text.toString().toIntOrNull();if(h.isEmpty()||pt==null||pt !in 1..65535){toast("Enter a valid host and port");return@button};out.text="testing $h:$pt ...";Thread{val t0=System.currentTimeMillis();try{java.net.Socket().use{sock->sock.connect(java.net.InetSocketAddress(h,pt),4000);val ms=System.currentTimeMillis()-t0;var resp="";val tmpl=template.text.toString();if(tmpl.isNotBlank()&&method.selectedItem.toString()!="TCP"){val req=tmpl.replace("[host]",h).replace("[ua]","Mozilla/5.0 (Linux; Android 16) PhoenixTunnel/1.0").replace("[crlf]","\r\n").replace("[split]","\r\n\r\n");sock.getOutputStream().write(req.toByteArray());sock.soTimeout=4000;resp=sock.getInputStream().bufferedReader().readLine()?:""};runOnUiThread{out.text="PASS  connect ${ms}ms\n$resp\n\nsaved? use SAVE AS PROFILE below"}}}catch(e:Exception){val ms=System.currentTimeMillis()-t0;runOnUiThread{out.text="FAIL after ${ms}ms: ${e.message}"}}}.start()});root.addView(button("SAVE AS PROFILE"){val h=host.text.toString().trim();val pt=port.text.toString();if(h.isEmpty()||pt.toIntOrNull()==null){toast("Fill host and port first");return@button};val name="lab-$h-$pt";val protocol=if(method.selectedItem.toString()=="TCP")TunnelProtocol.WIREGUARD else TunnelProtocol.SSH_DIRECT;val content=if(protocol==TunnelProtocol.WIREGUARD)"[Interface]\nAddress = 10.13.13.2/32\nDNS = 1.1.1.1\n[Peer]\nEndpoint = $h:$pt\nAllowedIPs = 0.0.0.0/0\n# lab combo: ${method.selectedItem} ${template.text.toString().take(120)}" else JSONObject().put("host",h).put("port",pt).put("username","TODO").put("password","TODO").put("payload",template.text.toString()).toString();repo.saveProfile(name,content,protocol);repo.addLog("Tweak Lab saved $name");toast("Saved $name");showServers()});root.addView(out)}

    private fun edit(hint:String,multiline:Boolean=false):EditText=EditText(this).apply{this.hint=hint; setHintTextColor(0xffb9a8d8.toInt()); setTextColor(-1); setBackgroundColor(0xff26183b.toInt()); setPadding(20,16,20,16); minLines=if(multiline)3 else 1; gravity=if(multiline)Gravity.TOP else Gravity.CENTER_VERTICAL}
    private fun dialog():AlertDialog.Builder=AlertDialog.Builder(this,androidx.appcompat.R.style.Theme_AppCompat_DayNight_Dialog).apply{}

    private fun darkDialog(ctx:android.content.Context)=AlertDialog.Builder(ctx).apply{}
    private fun text(s:String,size:Number,bold:Boolean)=TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(if(bold)0xfff3e8ff.toInt() else 0xffcbb9dd.toInt());if(bold)setTypeface(null,1);setPadding(8,12,8,12)}
    private fun button(s:String,large:Boolean=false,action:()->Unit)=Button(this).apply{text=s;textSize=if(large)20f else 14f;setTextColor(-1);backgroundTintList=android.content.res.ColorStateList.valueOf(if(large)0xff8a2be2.toInt() else 0xff382553.toInt());setOnClickListener{action()};minimumHeight=if(large)150 else 48}
    private fun card(s:String)=TextView(this).apply{text=s;textSize=18f;setTextColor(-1);setPadding(22,24,22,24);background=android.graphics.drawable.GradientDrawable().apply{setColor(0xff26183b.toInt());cornerRadius=28f};layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,18,0,18)}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
