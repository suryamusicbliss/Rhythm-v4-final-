package com.rhythm.music

import android.Manifest
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    private val bg = Color.rgb(8, 13, 24)
    private val panel = Color.rgb(17, 27, 43)
    private val panel2 = Color.rgb(25, 40, 61)
    private val blue = Color.rgb(169, 201, 232)
    private val white = Color.rgb(239, 244, 250)
    private val muted = Color.rgb(145, 161, 181)

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

    private var nowPlayingDialog: AlertDialog? = null
    private var miniPlayButton: TextView? = null
private var nowPlayingToggle: TextView? = null
    private var nowPlayingTitle: TextView? = null
private var nowPlayingArtist: TextView? = null
    private var isMusicPlaying = false

    private val stateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {

        if (intent?.action == PlaybackService.STATE) {

            val title = intent.getStringExtra("title")
                ?: "Nothing playing"

            val artist = intent.getStringExtra("artist")
                ?: ""

            val playing = intent.getBooleanExtra("playing", false)

            isMusicPlaying = playing

            if (::miniTitle.isInitialized) {
                miniTitle.text = title
            }

            if (::miniArtist.isInitialized) {
                miniArtist.text = artist
            }
            nowPlayingTitle?.text = title
nowPlayingArtist?.text = artist

            miniPlayButton?.text =
                if (playing) "Ⅱ" else "▶"

            nowPlayingToggle?.text =
                if (playing) "Ⅱ PAUSE" else "▶ PLAY"
        }
    }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        loadSavedData()
        registerPlaybackReceiver()
        requestNotificationPermission()

        render()
    }

    private fun loadSavedData() {
        val prefs = getSharedPreferences("rhythm_v4", MODE_PRIVATE)

        favourites.addAll(
            prefs.getStringSet("favourites", emptySet()) ?: emptySet()
        )

        val raw = prefs.getString("playlists", "{}") ?: "{}"

        try {
            val obj = JSONObject(raw)

            obj.keys().forEach { key ->
                val arr = obj.optJSONArray(key) ?: JSONArray()

                playlists[key] = mutableSetOf<String>().apply {
                    for (i in 0 until arr.length()) {
                        add(arr.optString(i))
                    }
                }
            }
        } catch (_: Exception) {
            playlists.clear()
        }
    }

    private fun registerPlaybackReceiver() {
        val filter = IntentFilter(PlaybackService.STATE)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                stateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }

        receiverRegistered = true
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                10
            )
        }
    }

    private fun d(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun shape(
        color: Int,
        radius: Int = 16,
        stroke: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = d(radius).toFloat()

            if (stroke != 0) {
                setStroke(d(1), stroke)
            }
        }
    }

    private fun label(
        textValue: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)

            if (bold) {
                setTypeface(typeface, Typeface.BOLD)
            }
        }
    }

    private fun pill(
        textValue: String,
        active: Boolean = false
    ): TextView {
        return label(
            textValue,
            12f,
            if (active) bg else blue,
            true
        ).apply {
            gravity = Gravity.CENTER
            background = shape(
                if (active) blue else panel2,
                13
            )
            setPadding(d(12), d(10), d(12), d(10))
        }
    }

    private fun saveData() {
        val obj = JSONObject()

        playlists.forEach { (name, ids) ->
            obj.put(name, JSONArray(ids.toList()))
        }

        getSharedPreferences("rhythm_v4", MODE_PRIVATE)
            .edit()
            .putStringSet("favourites", favourites.toSet())
            .putString("playlists", obj.toString())
            .apply()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(d(18), d(12), d(18), 0)
        }

        val scroll = ScrollView(this).apply {
            setFillViewport(true)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, d(20))
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams( -1, 0, 1f)
        )

        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, d(8), 0, d(16))
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        brand.addView(label("R H Y T H M", 23f, blue, true))
        brand.addView(
            label("F E E L   E V E R Y   B E A T", 8f, muted, true)
        )

        head.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        head.addView(pill("PLATINUM BLUE"))
        content.addView(head)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.rgb(21, 39, 61), 22)
            setPadding(d(19), d(19), d(19), d(18))
        }

        hero.addView(
            label("YOUR SOUND. YOUR SPACE.", 10f, blue, true)
        )

        hero.addView(
            label("Find your next\nfavourite.", 28f, white, true).apply {
                setPadding(0, d(8), 0, d(8))
            }
        )

        hero.addView(
            label(
                "Discover independent music for every mood.",
                12f,
                muted
            )
        )

        val searchRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, d(16), 0, 0)
        }

        val search = EditText(this).apply {
            hint = "Songs, artists, albums..."
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(d(13), 0, d(8), 0)
            background = shape(bg, 12, Color.rgb(56, 79, 107))
        }

        val go = pill("SEARCH", true)

        searchRow.addView(
            search,
            LinearLayout.LayoutParams(0, d(49), 1f).apply {
                marginEnd = d(8)
            }
        )

        searchRow.addView(
            go,
            LinearLayout.LayoutParams(d(88), d(49))
        )

        hero.addView(searchRow)
        content.addView(hero)

        val tabs = LinearLayout(this).apply {
            setPadding(0, d(17), 0, d(17))
        }

        listOf(
            "Discover",
            "Favourites",
            "Playlists",
            "Downloads"
        ).forEach { tab ->

            val button = pill(tab, currentTab == tab)

            tabs.addView(
                button,
                LinearLayout.LayoutParams(0, d(42), 1f).apply {
                    if (tab != "Downloads") {
                        marginEnd = d(5)
                    }
                }
            )

            button.setOnClickListener {
                currentTab = tab
                render()
            }
        }

        content.addView(tabs)

        val section = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = when (currentTab) {
            "Favourites" -> "YOUR FAVOURITES"
            "Playlists" -> "YOUR PLAYLISTS"
            "Downloads" -> "OFFLINE LIBRARY"
            else -> "DISCOVER MUSIC"
        }

        section.addView(
            label(heading, 11f, blue, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        if (currentTab == "Playlists") {
            val add = pill("+ NEW")
            section.addView(add)

            add.setOnClickListener {
                createPlaylist()
            }
        }

        content.addView(section)

        status = label(
            when (currentTab) {
                "Playlists" -> "Choose a playlist to view its tracks."
                "Favourites" -> "Your saved tracks appear here."
                "Downloads" -> "Downloads are saved to your Downloads folder."
                else -> "Search for a song or artist to get started."
            },
            12f,
            muted
        ).apply {
            setPadding(0, d(12), 0, d(10))
        }

        content.addView(status)
        listBox = content

        when (currentTab) {
            "Discover" -> showTracks(tracks)

            "Favourites" -> showTracks(
                tracks.filter { it.id in favourites }
            )

            "Playlists" -> showPlaylistList()

            "Downloads" -> showDownloadsInfo()
        }

        val mini = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = shape(panel, 17)
            setPadding(d(12), d(10), d(10), d(10))
        }

        mini.addView(
            label("♫", 22f, blue, true),
            LinearLayout.LayoutParams(d(34), -2)
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        miniTitle = label(
            currentTrack?.name ?: "Nothing playing yet",
            12f,
            white,
            true
        )

        miniArtist = label(
            currentTrack?.artist ?: "Choose a track to begin",
            10f,
            muted
        )

        info.addView(miniTitle)
        info.addView(miniArtist)

        mini.addView(
            info,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val play = pill("▶", true)
        miniPlayButton = play
miniPlayButton = play

        mini.addView(
            play,
            LinearLayout.LayoutParams(d(48), d(42))
        )

        play.setOnClickListener {
            sendAction(PlaybackService.TOGGLE)
        }

        mini.setOnClickListener {
            openNowPlaying()
        }

        root.addView(
            mini,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = d(8)
                bottomMargin = d(8)
            }
        )

        root.addView(
            label(
                "MADE WITH MUSIC  •  SHUBHAM UPADHYAY",
                9f,
                muted
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, d(5), 0, d(10))
            }
        )

        setContentView(root)

        fun doSearch() {
            val query = search.text.toString().trim()

            if (query.isNotEmpty()) {
                searchSongs(query)
            }
        }

        go.setOnClickListener {
            doSearch()
        }

        search.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else {
                false
            }
        }
    }

    private fun searchSongs(query: String) {
        status.text = "Searching Jamendo..."

        thread {
            var connection: HttpURLConnection? = null

            try {
                val encodedQuery =
                    URLEncoder.encode(query, "UTF-8")

                val apiUrl =
                    "https://api.jamendo.com/v3.0/tracks/" +
                    "?client_id=$clientId" +
                    "&format=json" +
                    "&limit=40" +
                    "&namesearch=$encodedQuery" +
                    "&audioformat=mp31" +
                    "&include=musicinfo"

                connection =
                    URL(apiUrl).openConnection() as HttpURLConnection

                connection.connectTimeout = 15000
                connection.readTimeout = 20000

                val raw = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val array =
                    JSONObject(raw).optJSONArray("results") ?: JSONArray()

                val found = mutableListOf<Track>()

                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)

                    val audio = item.optString("audio", "")

                    if (audio.isNotBlank() && audio != "null") {
                        val allowed =
                            item.optBoolean(
                                "audiodownload_allowed",
                                false
                            )

                        val download =
                            item.optString("audiodownload", "")

                        found.add(
                            Track(
                                item.optString("id"),
                                item.optString(
                                    "name",
                                    "Unknown track"
                                ),
                                item.optJSONObject("artist")
                                    ?.optString("name")
                                    ?: "Unknown artist",
                                audio,
                                item.optString("album_image", ""),
                                if (download == "null") "" else download,
                                allowed &&
                                    download.isNotBlank() &&
                                    download != "null"
                            )
                        )
                    }
                }

                runOnUiThread {
                    tracks.clear()
                    tracks.addAll(found)

                    currentTab = "Discover"
                    render()

                    status.text = if (found.isEmpty()) {
                        "No tracks found. Try another search."
                    } else {
                        "${found.size} tracks found"
                    }
                }

            } catch (_: Exception) {
                runOnUiThread {
                    status.text =
                        "Music search failed. Check internet and try again."
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showTracks(items: List<Track>) {
        if (items.isEmpty()) return

        items.forEachIndexed { index, track ->

            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = shape(panel, 15)
                setPadding(d(11), d(10), d(10), d(10))
            }

            val art = TextView(this).apply {
                text = listOf("♫", "♪", "♬", "✦", "◖")[index % 5]
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(blue)
                background = shape(panel2, 12)
            }

            row.addView(
                art,
                LinearLayout.LayoutParams(d(48), d(48))
            )

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            info.addView(
                label(track.name, 13f, white, true).apply {
                    maxLines = 1
                }
            )

            info.addView(
                label(track.artist, 10f, muted).apply {
                    maxLines = 1
                }
            )

            row.addView(
                info,
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = d(10)
                    marginEnd = d(5)
                }
            )

            val favourite = label(
                if (track.id in favourites) "♥" else "♡",
                21f,
                blue,
                true
            ).apply {
                gravity = Gravity.CENTER
                setPadding(d(5), d(5), d(5), d(5))
            }

            row.addView(favourite)

            favourite.setOnClickListener {
                if (!favourites.add(track.id)) {
                    favourites.remove(track.id)
                }

                saveData()
                render()
            }

            row.setOnClickListener {
                playTrack(track, items)
            }

            row.setOnLongClickListener {
                showTrackOptions(track)
                true
            }

            listBox.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = d(8)
                }
            )
        }
    }

    private fun showTrackOptions(track: Track) {
        val options = arrayOf(
            "Add to playlist",
            "Download if permitted"
        )

        AlertDialog.Builder(this)
            .setTitle(track.name)
            .setItems(options) { _, which ->
                if (which == 0) {
                    addTrackToPlaylist(track)
                } else {
                    downloadTrack(track)
                }
            }
            .show()
    }

    private fun playTrack(
        track: Track,
        queue: List<Track>
    ) {
        currentTrack = track

        miniTitle.text = track.name
        miniArtist.text = track.artist

        val index = queue.indexOf(track).coerceAtLeast(0)

        val intent = Intent(
            this,
            PlaybackService::class.java
        ).apply {
            action = PlaybackService.PLAY

            putStringArrayListExtra(
                "urls",
                ArrayList(queue.map { it.audioUrl })
            )

            putStringArrayListExtra(
                "names",
                ArrayList(queue.map { it.name })
            )

            putStringArrayListExtra(
                "artists",
                ArrayList(queue.map { it.artist })
            )

            putExtra("index", index)
        }

        ContextCompat.startForegroundService(this, intent)
        openNowPlaying()
    }
        private fun sendAction(
        action: String,
        position: Int = 0
    ) {
        val intent = Intent(
            this,
            PlaybackService::class.java
        ).apply {
            this.action = action
            putExtra("position", position)
        }

        ContextCompat.startForegroundService(
            this,
            intent
        )
    }

    private fun openNowPlaying() {
        val track = currentTrack ?: return

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(22), d(22), d(22), d(18))
            background = shape(panel, 20)
        }

        box.addView(
            label("NOW PLAYING", 11f, blue, true)
        )

        val titleView = label(track.name, 21f, white, true).apply {
    setPadding(0, d(16), 0, d(5))
}
nowPlayingTitle = titleView
box.addView(titleView)

val artistView = label(track.artist, 13f, muted)
nowPlayingArtist = artistView
box.addView(artistView)

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, d(22), 0, d(8))
        }

        val previous = pill("⏮")
        val toggle = pill(
    if (isMusicPlaying) "Ⅱ PAUSE" else "▶ PLAY",
    true
)
nowPlayingToggle = toggle
        val next = pill("⏭")

        controls.addView(
            previous,
            LinearLayout.LayoutParams(0, d(48), 1f).apply {
                marginEnd = d(5)
            }
        )

        controls.addView(
            toggle,
            LinearLayout.LayoutParams(0, d(48), 1f).apply {
                marginEnd = d(5)
            }
        )

        controls.addView(
            next,
            LinearLayout.LayoutParams(0, d(48), 1f)
        )

        box.addView(controls)

        previous.setOnClickListener {
    sendAction(PlaybackService.PREVIOUS)
}

toggle.setOnClickListener {
    sendAction(PlaybackService.TOGGLE)
}

next.setOnClickListener {
    sendAction(PlaybackService.NEXT)
}

        nowPlayingDialog = AlertDialog.Builder(this)
            .setView(box)
            .setPositiveButton("CLOSE", null)
            .create()

        nowPlayingDialog?.show()
        nowPlayingDialog?.window?.setBackgroundDrawable(
            shape(bg, 20)
        )
    }

    private fun createPlaylist() {
        val input = EditText(this).apply {
            hint = "Playlist name"
            setTextColor(white)
            setHintTextColor(muted)
        }

        AlertDialog.Builder(this)
            .setTitle("Create playlist")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CREATE") { _, _ ->
                val name = input.text.toString().trim()

                if (name.isNotEmpty()) {
                    playlists.getOrPut(name) { mutableSetOf() }
                    saveData()
                    render()
                }
            }
            .show()
    }

    private fun showPlaylistList() {
        if (playlists.isEmpty()) {
            status.text = "No playlists yet. Tap + NEW to create one."
            return
        }

        playlists.keys.forEach { name ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = shape(panel, 14)
                setPadding(d(14), d(14), d(12), d(14))
            }

            row.addView(
                label(name, 14f, white, true),
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            row.addView(pill("OPEN"))

            row.setOnClickListener {
                showPlaylist(name)
            }

            row.setOnLongClickListener {
                renamePlaylist(name)
                true
            }

            listBox.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = d(8)
                }
            )
        }
    }

    private fun showPlaylist(name: String) {
        val ids = playlists[name] ?: return
        val items = tracks.filter { it.id in ids }

        status.text = "$name • ${items.size} tracks"

        if (items.isEmpty()) {
            status.text =
                "No currently loaded tracks in this playlist. Search and add tracks again."
            return
        }

        showTracks(items)
    }

    private fun renamePlaylist(oldName: String) {
        val input = EditText(this).apply {
            setText(oldName)
            setTextColor(white)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename playlist")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val newName = input.text.toString().trim()

                if (
                    newName.isNotEmpty() &&
                    newName != oldName &&
                    !playlists.containsKey(newName)
                ) {
                    playlists[newName] =
                        playlists.remove(oldName) ?: mutableSetOf()

                    saveData()
                    render()
                }
            }
            .setNeutralButton("DELETE") { _, _ ->
                playlists.remove(oldName)
                saveData()
                render()
            }
            .show()
    }

    private fun addTrackToPlaylist(track: Track) {
        if (playlists.isEmpty()) {
            Toast.makeText(
                this,
                "Create a playlist first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val names = playlists.keys.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Add to playlist")
            .setItems(names) { _, which ->
                playlists[names[which]]?.add(track.id)
                saveData()

                Toast.makeText(
                    this,
                    "Added to ${names[which]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun downloadTrack(track: Track) {
        if (
            !track.downloadAllowed ||
            track.downloadUrl.isBlank()
        ) {
            Toast.makeText(
                this,
                "Download is not permitted for this track.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val request = DownloadManager.Request(
                Uri.parse(track.downloadUrl)
            ).apply {
                setTitle(track.name)
                setDescription("Downloading from Rhythm")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    "Rhythm_${track.id}.mp3"
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }

            val manager =
                getSystemService(Context.DOWNLOAD_SERVICE)
                    as DownloadManager

            manager.enqueue(request)

            Toast.makeText(
                this,
                "Download started",
                Toast.LENGTH_SHORT
            ).show()

        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Download could not be started.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showDownloadsInfo() {
        status.text =
            "Permitted downloads are saved in your phone's Downloads folder."

        val button = pill("OPEN DOWNLOADS")

        listBox.addView(
            button,
            LinearLayout.LayoutParams(-1, d(48)).apply {
                topMargin = d(12)
            }
        )

        button.setOnClickListener {
            try {
                val intent = Intent(
                    DownloadManager.ACTION_VIEW_DOWNLOADS
                )
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "Open the Downloads app on your phone.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
    if (receiverRegistered) {
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {
        }
        receiverRegistered = false
    }

    nowPlayingDialog?.dismiss()
    super.onDestroy()
    }
}
