package com.rhythm.music

import android.Manifest
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(8,13,24)
    private val panel = Color.rgb(17,27,43)
    private val panel2 = Color.rgb(25,40,61)
    private val blue = Color.rgb(169,201,232)
    private val white = Color.rgb(239,244,250)
    private val muted = Color.rgb(145,161,181)
    private val clientId = "1c57991d"
    private val tracks = mutableListOf<Track>()
    private val favourites = linkedSetOf<String>()
    private val playlists = linkedMapOf<String, MutableSet<String>>()
    private lateinit var listBox: LinearLayout
    private lateinit var status: TextView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private var currentTab = "Discover"
    private var currentTrack: Track? = null
    private var receiverRegistered = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PlaybackService.STATE) {
                miniTitle.text = intent.getStringExtra("title") ?: "Nothing playing"
                miniArtist.text = intent.getStringExtra("artist") ?: ""
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val prefs = getSharedPreferences("rhythm_v4", MODE_PRIVATE)
        favourites.addAll(prefs.getStringSet("favourites", emptySet()) ?: emptySet())
        val raw = prefs.getString("playlists", "{}") ?: "{}"
        try {
            val obj = JSONObject(raw)
            obj.keys().forEach { key ->
                val arr = obj.optJSONArray(key) ?: JSONArray()
                playlists[key] = mutableSetOf<String>().apply {
                    for (i in 0 until arr.length()) add(arr.optString(i))
                }
            }
        } catch (_: Exception) { }
        val filter = IntentFilter(PlaybackService.STATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(stateReceiver, filter)
        receiverRegistered = true
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        render()
    }

    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 16, stroke: Int = 0) =
        GradientDrawable().apply { setColor(color); cornerRadius = d(radius).toFloat(); if (stroke != 0) setStroke(d(1), stroke) }

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun pill(s: String, active: Boolean = false) = label(s, 12f, if (active) bg else blue, true).apply {
        gravity = Gravity.CENTER; background = shape(if (active) blue else panel2, 13)
        setPadding(d(14), d(12), d(14), d(12))
    }

    private fun saveData() {
        val obj = JSONObject()
        playlists.forEach { (name, ids) -> obj.put(name, JSONArray(ids.toList())) }
        getSharedPreferences("rhythm_v4", MODE_PRIVATE).edit()
            .putStringSet("favourites", favourites.toSet())
            .putString("playlists", obj.toString()).apply()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg)
            setPadding(d(18), d(12), d(18), 0)
        }
        val scroll = ScrollView(this).apply { fillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,0,0,d(20)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))

        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0,d(8),0,d(16)) }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("R H Y T H M", 23f, blue, true))
        brand.addView(label("F E E L   E V E R Y   B E A T", 8f, muted, true))
        head.addView(brand, LinearLayout.LayoutParams(0,-2,1f))
        head.addView(pill("PLATINUM BLUE"))
        content.addView(head)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = shape(Color.rgb(21,39,61),22)
            setPadding(d(19),d(19),d(19),d(18))
        }
        hero.addView(label("YOUR SOUND. YOUR SPACE.",10f,blue,true))
        hero.addView(label("Find your next\nfavourite.",28f,white,true).apply { setPadding(0,d(8),0,d(8)) })
        hero.addView(label("Discover independent music for every mood.",12f,muted))
        val searchRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0,d(16),0,0) }
        val search = EditText(this).apply {
            hint = "Songs, artists, albums..."; setHintTextColor(muted); setTextColor(white)
            textSize = 14f; singleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(d(13),0,d(8),0); background = shape(bg,12,Color.rgb(56,79,107))
        }
        val go = pill("SEARCH",true)
        searchRow.addView(search,LinearLayout.LayoutParams(0,d(49),1f).apply { marginEnd=d(8) })
        searchRow.addView(go,LinearLayout.LayoutParams(d(88),d(49)))
        hero.addView(searchRow); content.addView(hero)

        val tabs = LinearLayout(this).apply { setPadding(0,d(17),0,d(17)) }
        listOf("Discover","Favourites","Playlists","Downloads").forEach { tab ->
            val b = pill(tab,currentTab==tab)
            tabs.addView(b,LinearLayout.LayoutParams(0,d(42),1f).apply { if (tab!="Downloads") marginEnd=d(5) })
            b.setOnClickListener { currentTab=tab; render() }
        }
        content.addView(tabs)

        val section = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        val heading = when(currentTab) {
            "Favourites" -> "YOUR FAVOURITES"
            "Playlists" -> "YOUR PLAYLISTS"
            "Downloads" -> "OFFLINE LIBRARY"
            else -> "DISCOVER MUSIC"
        }
        section.addView(label(heading,11f,blue,true),LinearLayout.LayoutParams(0,-2,1f))
        if(currentTab=="Playlists") {
            val add = pill("+ NEW")
            section.addView(add); add.setOnClickListener { createPlaylist() }
        }
        content.addView(section)
        status = label(when(currentTab) {
            "Playlists" -> if(playlists.isEmpty()) "Create a playlist to organise your music." else "Choose a playlist to view its tracks."
            "Favourites" -> "Your saved tracks appear here."
            "Downloads" -> "Downloads are saved to your device's Downloads folder."
            else -> "Search for a song or artist to get started."
        },12f,muted).apply { setPadding(0,d(12),0,d(10)) }
        content.addView(status)
        listBox = content
        when(currentTab) {
            "Discover" -> showTracks(tracks)
            "Favourites" -> showTracks(tracks.filter { it.id in favourites })
            "Playlists" -> showPlaylistList()
            "Downloads" -> showDownloadsInfo()
        }

        val mini = LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL; background=shape(panel,17); setPadding(d(12),d(10),d(10),d(10))
        }
        mini.addView(label("♫",22f,blue,true),LinearLayout.LayoutParams(d(34),-2))
        val info = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        miniTitle = label(currentTrack?.name ?: "Nothing playing yet",12f,white,true)
        miniArtist = label(currentTrack?.artist ?: "Choose a track to begin",10f,muted)
        info.addView(miniTitle); info.addView(miniArtist)
        mini.addView(info,LinearLayout.LayoutParams(0,-2,1f))
        val play = pill("▶",true); mini.addView(play,LinearLayout.LayoutParams(d(48),d(42)))
        play.setOnClickListener { sendAction(PlaybackService.TOGGLE) }
        mini.setOnClickListener { openNowPlaying() }
        root.addView(mini,LinearLayout.LayoutParams(-1,-2).apply { topMargin=d(8); bottomMargin=d(8) })
        root.addView(label("MADE WITH MUSIC  •  SHUBHAM UPADHYAY",9f,muted).apply {
            gravity=Gravity.CENTER; setPadding(0,d(5),0,d(10))
        })
        setContentView(root)

        fun doSearch() {
            val q=search.text.toString().trim()
            if(q.isNotEmpty()) searchSongs(q)
        }
        go.setOnClickListener { doSearch() }
        search.setOnEditorActionListener { _, action, _ ->
            if(action==EditorInfo.IME_ACTION_SEARCH) { doSearch(); true } else false
        }
    }

    private fun searchSongs(query: String) {
        status.text="Searching Jamendo..."
        thread {
            var c: HttpURLConnection?=null
            try {
                val q=URLEncoder.encode(query,"UTF-8")
                val url="https://api.jamendo.com/v3.0/tracks/?client_id=$clientId&format=json&limit=40&namesearch=$q&audioformat=mp31&include=musicinfo"
                c=URL(url).openConnection() as HttpURLConnection
                c.connectTimeout=15000; c.readTimeout=20000
                val raw=c.inputStream.bufferedReader().use { it.readText() }
                val arr=JSONObject(raw).optJSONArray("results") ?: JSONArray()
                val found=mutableListOf<Track>()
                for(i in 0 until arr.length()) {
                    val x=arr.getJSONObject(i)
                    val audio=x.optString("audio","")
                    if(audio.isNotBlank() && audio!="null") {
                        val allowed=x.optBoolean("audiodownload_allowed",false)
                        val download=x.optString("audiodownload","")
                        found.add(Track(
                            x.optString("id"),x.optString("name","Unknown track"),
                            x.optJSONObject("artist")?.optString("name") ?: "Unknown artist",
                            audio,x.optString("album_image",""),
                            if(download=="null") "" else download, allowed && download.isNotBlank() && download!="null"
                        ))
                    }
                }
                runOnUiThread {
                    tracks.clear(); tracks.addAll(found); currentTab="Discover"; render()
                    status.text=if(found.isEmpty()) "No tracks found. Try another search." else "${found.size} tracks found"
                }
            } catch(e:Exception) {
                runOnUiThread { status.text="Music search failed. Check internet and try again." }
            } finally { c?.disconnect() }
        }
    }

    private fun showTracks(items: List<Track>) {
        if(items.isEmpty()) return
        items.forEachIndexed { index,t ->
            val row=LinearLayout(this).apply {
                gravity=Gravity.CENTER_VERTICAL; background=shape(panel,15)
                setPadding(d(11),d(10),d(10),d(10))
            }
            val art=TextView(this).apply {
                text=listOf("♫","♪","♬","✦","◖")[index%5]; textSize=22f
                gravity=Gravity.CENTER; setTextColor(blue); background=shape(panel2,12)
            }
            row.addView(art,LinearLayout.LayoutParams(d(48),d(48)))
            val info=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            info.addView(label(t.name,13f,white,true).apply { maxLines=1 })
            info.addView(label(t.artist,10f,muted).apply { maxLines=1 })
            row.addView(info,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=d(10); marginEnd=d(5) })
            val fav=label(if(t.id in favourites) "♥" else "♡",21f,blue,true).apply { gravity=Gravity.CENTER; setPadding(d(5),d(5),d(5),d(5)) }
            row.addView(fav)
            fav.setOnClickListener {
                if(!favourites.add(t.id)) favourites.remove(t.id)
                saveData(); render()
            }
            row.setOnClickListener { playTrack(t,items) }
            row.setOnLongClickListener {
                showTrackOptions(t); true
            }
            listBox.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=d(8) })
        }
    }

    private fun showTrackOptions(track: Track) {
        val options=mutableListOf("Add to playlist","Download if permitted")
        android.app.AlertDialog.Builder(this).setTitle(track.name).setItems(options.toTypedArray()) { _, which ->
            if(which==0) addTrackToPlaylist(track)
            else downloadTrack(track)
        }.show()
    }

    private fun playTrack(track: Track, queue: List<Track>) {
        currentTrack=track; miniTitle.text=track.name; miniArtist.text=track.artist
        val idx=queue.indexOf(track).coerceAtLeast(0)
        val i=Intent(this,PlaybackService::class.java).apply {
            action=PlaybackService.PLAY
            putStringArrayListExtra("urls",ArrayList(queue.map{it.audioUrl}))
            putStringArrayListExtra("names",ArrayList(queue.map{it.name}))
            putStringArrayListExtra("artists",ArrayList(queue.map{it.artist}))
            putExtra("index",idx)
        }
        ContextCompat.startForegroundService(this,i)
        openNowPlaying()
    }

    private fun sendAction(action:String, position:Int=0) {
        val i=Intent(this,PlaybackService::class.java).setAction(action).putExtra("position",position)
        ContextCompat.startForegroundService(this,i)
    }

    private fun openNowPlaying() {
        val track=currentTrack ?: run {
            Toast.makeText(this,"Play a track first",Toast.LENGTH_SHORT).show(); return
        }
        val box=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL
            setPadding(d(22),d(18),d(22),d(12)); setBackgroundColor(bg)
        }
        box.addView(label("NOW PLAYING",10f,blue,true))
        box.addView(label("♫",76f,blue,true).apply { setPadding(0,d(25),0,d(25)) })
        box.addView(label(track.name,20f,white,true))
        box.addView(label(track.artist,13f,muted).apply { setPadding(0,d(5),0,d(20)) })
        val seek=SeekBar(this).apply { max=1000; progressTintList=android.content.res.ColorStateList.valueOf(blue); thumbTintList=android.content.res.ColorStateList.valueOf(blue) }
        box.addView(seek,LinearLayout.LayoutParams(-1,-2))
        val controls=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(0,d(18),0,d(8)) }
        listOf("⏮","Ⅱ / ▶","⏭").forEachIndexed { ix,s ->
            val b=label(s,24f,blue,true).apply { gravity=Gravity.CENTER; setPadding(d(16),d(10),d(16),d(10)) }
            controls.addView(b); b.setOnClickListener {
                sendAction(when(ix){0->PlaybackService.PREVIOUS;1->PlaybackService.TOGGLE;else->PlaybackService.NEXT})
            }
        }
        box.addView(controls)
        android.app.AlertDialog.Builder(this).setView(box).setPositiveButton("Close",null).show()
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean) { if(u) sendAction(PlaybackService.SEEK,p) }
            override fun onStartTrackingTouch(s:SeekBar?) {}
            override fun onStopTrackingTouch(s:SeekBar?) {}
        })
    }

    private fun showPlaylistList() {
        if(playlists.isEmpty()) return
        playlists.keys.toList().forEach { name ->
            val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; background=shape(panel,14); setPadding(d(14),d(14),d(10),d(14)) }
            row.addView(label("♫   $name",14f,white,true),LinearLayout.LayoutParams(0,-2,1f))
            val open=pill("OPEN"); row.addView(open)
            open.setOnClickListener { showPlaylist(name) }
            row.setOnClickListener { showPlaylist(name) }
            row.setOnLongClickListener {
                android.app.AlertDialog.Builder(this).setTitle(name)
                    .setItems(arrayOf("Rename","Delete")) { _, which ->
                        if(which==0) renamePlaylist(name) else {
                            playlists.remove(name); saveData(); render()
                        }
                    }.show(); true
            }
            listBox.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=d(8) })
        }
    }

    private fun createPlaylist() {
        val input=EditText(this).apply { hint="Playlist name"; setTextColor(white); setHintTextColor(muted) }
        android.app.AlertDialog.Builder(this).setTitle("Create playlist").setView(input)
            .setPositiveButton("Create") { _,_ ->
                val name=input.text.toString().trim()
                if(name.isNotEmpty() && !playlists.containsKey(name)) {
                    playlists[name]=linkedSetOf(); saveData(); render()
                }
            }.setNegativeButton("Cancel",null).show()
    }

    private fun renamePlaylist(old:String) {
        val input=EditText(this).apply { setText(old); setTextColor(white) }
        android.app.AlertDialog.Builder(this).setTitle("Rename playlist").setView(input)
            .setPositiveButton("Save") { _,_ ->
                val n=input.text.toString().trim()
                if(n.isNotEmpty() && n !in playlists) {
                    playlists[n]=playlists.remove(old) ?: linkedSetOf(); saveData(); render()
                }
            }.setNegativeButton("Cancel",null).show()
    }

    private fun showPlaylist(name:String) {
        val ids=playlists[name] ?: return
        val items=tracks.filter { it.id in ids }
        android.app.AlertDialog.Builder(this)
            .setTitle("$name • ${ids.size} saved tracks")
            .setMessage(if(items.isEmpty()) "Search for music, long-press a track and choose Add to playlist." else items.joinToString("\n"){it.name+" — "+it.artist})
            .setPositiveButton("Done",null).setNeutralButton("Add current search tracks") { _,_ ->
                tracks.forEach { ids.add(it.id) }; saveData(); render()
            }.show()
    }

    private fun addTrackToPlaylist(track:Track) {
        if(playlists.isEmpty()) { createPlaylist(); Toast.makeText(this,"Create a playlist, then add this track.",Toast.LENGTH_LONG).show(); return }
        val names=playlists.keys.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("Add to playlist").setItems(names) { _,which ->
            playlists[names[which]]?.add(track.id); saveData()
            Toast.makeText(this,"Added to ${names[which]}",Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun downloadTrack(track:Track) {
        if(!track.downloadAllowed || track.downloadUrl.isBlank()) {
            Toast.makeText(this,"This track is not marked as downloadable by the provider.",Toast.LENGTH_LONG).show()
            return
        }
        try {
            val request=DownloadManager.Request(Uri.parse(track.downloadUrl))
                .setTitle(track.name)
                .setDescription("Rhythm • ${track.artist}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,"Rhythm - ${track.name}.mp3")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            getSystemService(DownloadManager::class.java).enqueue(request)
            Toast.makeText(this,"Download started. Check Downloads.",Toast.LENGTH_LONG).show()
        } catch(e:Exception) {
            Toast.makeText(this,"Download could not start: ${e.message}",Toast.LENGTH_LONG).show()
        }
    }

    private fun showDownloadsInfo() {
        listBox.addView(label("Downloads are handled by Android Download Manager and saved in your device's Downloads folder.",12f,muted))
        listBox.addView(label("Only tracks explicitly marked downloadable by the Jamendo API are offered for download. Check each track's licence before sharing or redistributing it.",11f,muted).apply { setPadding(0,d(12),0,0) })
    }

    override fun onDestroy() {
        if(receiverRegistered) try { unregisterReceiver(stateReceiver) } catch (_:Exception) {}
        super.onDestroy()
    }
}
