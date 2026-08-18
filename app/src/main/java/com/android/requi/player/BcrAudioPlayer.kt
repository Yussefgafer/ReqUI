package com.android.requi.player

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BcrAudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private var activeUri: Uri? = null
    private var activeTitle = ""
    private var activeArtist = ""

    val currentTitle: String get() = activeTitle
    val currentArtist: String get() = activeArtist

    var mediaSession: MediaSession? = null

    companion object {
        var instance: BcrAudioPlayer? = null
    }

    init {
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = MediaSession(context, "BcrAudioPlayer").apply {
                isActive = true
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        resume()
                    }
                    override fun onPause() {
                        pause()
                    }
                    override fun onSeekTo(pos: Long) {
                        this@BcrAudioPlayer.seekTo(pos)
                    }
                    override fun onSkipToNext() {
                        val newPos = (currentPosition.value + 10000).coerceAtMost(duration.value)
                        this@BcrAudioPlayer.seekTo(newPos)
                    }
                    override fun onSkipToPrevious() {
                        val newPos = (currentPosition.value - 10000).coerceAtLeast(0)
                        this@BcrAudioPlayer.seekTo(newPos)
                    }
                })
            }
        }
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    _currentPosition.value = mp.currentPosition.toLong()
                    updatePlaybackState()
                    handler.postDelayed(this, 100)
                }
            }
        }
    }

    private fun updatePlaybackState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val state = if (_isPlaying.value) {
                PlaybackState.STATE_PLAYING
            } else {
                PlaybackState.STATE_PAUSED
            }
            val playbackState = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, _currentPosition.value, _playbackSpeed.value)
                .build()
            mediaSession?.setPlaybackState(playbackState)
        }
    }

    private fun updateMetadata(title: String, artist: String, durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val builder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)

            // Dynamically render the premium app launcher icon as album art
            try {
                val drawable = context.packageManager.getApplicationIcon(context.packageName)
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                builder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            mediaSession?.setMetadata(builder.build())
        }
    }

    private fun startService() {
        val intent = Intent(context, BcrPlaybackService::class.java).apply {
            action = BcrPlaybackService.ACTION_START
            putExtra(BcrPlaybackService.EXTRA_TITLE, activeTitle)
            putExtra(BcrPlaybackService.EXTRA_ARTIST, activeArtist)
            mediaSession?.let {
                putExtra(BcrPlaybackService.EXTRA_TOKEN, it.sessionToken)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun updateServiceState() {
        val intent = Intent(context, BcrPlaybackService::class.java).apply {
            action = BcrPlaybackService.ACTION_UPDATE_STATE
            putExtra(BcrPlaybackService.EXTRA_IS_PLAYING, _isPlaying.value)
        }
        context.startService(intent)
    }

    private fun stopService() {
        val intent = Intent(context, BcrPlaybackService::class.java).apply {
            action = BcrPlaybackService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun play(uri: Uri, title: String, artist: String) {
        if (activeUri == uri && mediaPlayer != null) {
            resume()
            return
        }

        stop()
        activeUri = uri
        activeTitle = title
        activeArtist = artist

        try {
            val mp = MediaPlayer().apply {
                setDataSource(context, uri)

                setOnPreparedListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            playbackParams = PlaybackParams().apply { speed = _playbackSpeed.value }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    start()

                    _isPlaying.value = true
                    _duration.value = duration.toLong()
                    _currentPosition.value = 0L

                    updateMetadata(title, artist, duration.toLong())
                    updatePlaybackState()
                    startService()

                    handler.post(updateProgressAction)
                }

                setOnErrorListener { _, _, _ ->
                    _isPlaying.value = false
                    true
                }

                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = _duration.value
                    try {
                        this.seekTo(0)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    _currentPosition.value = 0L
                    updatePlaybackState()
                    updateServiceState()
                    handler.removeCallbacks(updateProgressAction)
                }

                prepareAsync()
            }

            mediaPlayer = mp
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
        }
    }

    fun pause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
                updatePlaybackState()
                updateServiceState()
                handler.removeCallbacks(updateProgressAction)
            }
        }
    }

    fun resume() {
        mediaPlayer?.let { mp ->
            if (!mp.isPlaying) {
                mp.start()
                _isPlaying.value = true
                updatePlaybackState()
                updateServiceState()
                handler.post(updateProgressAction)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            mp.seekTo(positionMs.toInt())
            _currentPosition.value = positionMs
            updatePlaybackState()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        mediaPlayer?.let { mp ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val wasPlaying = mp.isPlaying
                    mp.playbackParams = mp.playbackParams.apply { this.speed = speed }
                    if (!wasPlaying) {
                        mp.pause()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        updatePlaybackState()
    }

    fun stop() {
        handler.removeCallbacks(updateProgressAction)
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        activeUri = null
        stopService()
    }

    fun release() {
        stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession?.release()
        }
    }
}
