package edu.artic.media.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.AudioAttributesCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.fuzz.rx.DisposeBag
import com.fuzz.rx.disposedBy
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.STREAM_TYPE_VOICE_CALL
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.Util
import dagger.android.AndroidInjection
import edu.artic.analytics.AnalyticsAction
import edu.artic.analytics.AnalyticsTracker
import edu.artic.analytics.EventCategoryName
import edu.artic.base.utils.asDeepLinkIntent
import edu.artic.db.models.ArticAudioFile
import edu.artic.db.models.ArticObject
import edu.artic.db.models.getAudio
import edu.artic.media.R
import edu.artic.media.audio.AudioPlayerService.PlayBackAction
import edu.artic.media.audio.AudioPlayerService.PlayBackAction.*
import edu.artic.media.audio.AudioPlayerService.PlayBackState.*
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject


/**
 * The app's global background audio player.
 *
 * The audio itself is played by [an ExoPlayer][player], and it controls a system notification
 * for changing/viewing the audio player's state via [PlayerNotificationManager].
 *
 * Audio files are streamed from URLs defined in the AppData blob. To play a track,
 * you'll need to submit a new ['Play' action][PlayBackAction.Play] to the [audioControl]
 * subject. You can pause or seek to a specific timestamp with [PlayBackAction.Pause] and
 * [PlayBackAction.Seek]. For convenience, we offer prebuilt [playPlayer] and [pausePlayer]
 * functions directly on the service.
 *
 * In general you should [bind to this service][android.app.Activity.bindService] in your
 * [BaseActivity's onResume() function][android.app.Activity.onResume] and [unbind from it]
 * [android.app.Activity.unbindService] in [onPause()][android.app.Activity.onPause].
 *
 * @author Sameer Dhakal (Fuzz)
 */
class AudioPlayerService : Service() {

    companion object {
        val FOREGROUND_CHANNEL_ID = "foreground_channel_id"
        val NOTIFICATION_ID = 200

        fun getLaunchIntent(context: Context): Intent {
            return Intent(context, AudioPlayerService::class.java)
        }
    }

    /**
     * These are the inputs callers can use to change which [PlayBackState] should be
     * active. Currently supported actions are as follows:
     *
     * * [Play] - start playing a new track
     * * [Pause] - pause current track
     * * [Resume] - resumes current track
     * * [Stop] - stop current track
     * * [Seek] - change player position to a particular timestamp
     */
    sealed class PlayBackAction {
        /**
         * Play the track associated with the given [ArticObject].
         *
         * Currently, we only support tracks defined in [ArticObject.audioCommentary].
         *
         * @see [ExoPlayer.prepare]
         * @see [Player.setPlayWhenReady]
         */
        class Play(val audioFile: ArticObject) : PlayBackAction()

        /**
         * Pause the current track.
         *
         * You can continue where you left off with [Resume].
         *
         * @see [Player.setPlayWhenReady]
         */
        class Pause : PlayBackAction()

        /**
         * Resume the current track.
         *
         * You can also resume (without resetting the stream) by sending a subsequent [Play]
         * action backed by the same [ArticObject].
         *
         * @see [Player.setPlayWhenReady]
         */
        class Resume : PlayBackAction()

        /**
         * Stop playback, seek back to the start of the file, and move this service into the background.
         *
         * @see [Player.stop]
         */
        class Stop : PlayBackAction()

        /**
         * Move playback to a specific temporal position.
         *
         * @param time number of milliseconds from the start of the track
         */
        class Seek(val time: Long) : PlayBackAction()
    }

    /**
     * Current 'state' of [the ExoPlayer][player].
     *
     * We enforce the following states:
     * * [Playing]
     * * [Paused]
     * * [Stopped]
     */
    sealed class PlayBackState(val articAudioFile: ArticAudioFile) {
        class Playing(articAudioFile: ArticAudioFile) : PlayBackState(articAudioFile)
        class Paused(articAudioFile: ArticAudioFile) : PlayBackState(articAudioFile)
        class Stopped(articAudioFile: ArticAudioFile) : PlayBackState(articAudioFile)
    }

    private val binder: Binder = AudioPlayerServiceBinder()
    private lateinit var playerNotificationManager: PlayerNotificationManager
    var articObject: ArticObject? = null

    private val audioControl: Subject<PlayBackAction> = BehaviorSubject.create()
    val audioPlayBackStatus: Subject<PlayBackState> = BehaviorSubject.create()
    val currentTrack: Subject<ArticAudioFile> = BehaviorSubject.create()

    val disposeBag = DisposeBag()

    @Inject
    lateinit var analyticsTracker: AnalyticsTracker

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
        setUpNotificationManager()
        player.addListener(object : Player.DefaultEventListener() {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                val articAudioFile = articObject?.getAudio()
                articAudioFile?.let { audioFile ->
                    when {
                        playWhenReady && playbackState == Player.STATE_READY -> audioPlayBackStatus.onNext(PlayBackState.Playing(audioFile))
                        playbackState == Player.STATE_ENDED -> {
                            /*Play back completed*/
                            analyticsTracker.reportEvent(EventCategoryName.PlayBack, AnalyticsAction.playbackCompleted, articAudioFile.title.orEmpty())
                            audioPlayBackStatus.onNext(PlayBackState.Stopped(audioFile))
                        }
                        playbackState == Player.STATE_IDLE -> audioPlayBackStatus.onNext(PlayBackState.Stopped(audioFile))
                        else -> audioPlayBackStatus.onNext(PlayBackState.Paused(audioFile))
                    }
                }
            }
        })

        audioControl.subscribe { playBackAction ->
            when (playBackAction) {
                is PlayBackAction.Play -> {
                    setArticObject(playBackAction.audioFile)
                    player.playWhenReady = true
                }

                is PlayBackAction.Resume -> {
                    player.playWhenReady = true
                }

                is PlayBackAction.Pause -> {
                    player.playWhenReady = false
                }

                is PlayBackAction.Stop -> {
                    articObject?.getAudio()?.let { audioFile ->
                        audioPlayBackStatus.onNext(PlayBackState.Stopped(audioFile))
                    }
                    player.stop()
                }

                is PlayBackAction.Seek -> {
                    player.seekTo(playBackAction.time)
                }
            }
        }.disposedBy(disposeBag)
    }

    private fun setUpNotificationManager() {
        playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
                this,
                FOREGROUND_CHANNEL_ID,
                R.string.channel_name,
                NOTIFICATION_ID,
                object : PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun createCurrentContentIntent(player: Player?): PendingIntent? {
                        //TODO make it dynamic so that activity that started the audio stream will be the destination of Intent
                        val notificationIntent = "edu.artic.audio".asDeepLinkIntent()
                        return PendingIntent.getActivity(this@AudioPlayerService, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    }

                    override fun getCurrentContentText(player: Player?): String? {
                        return null
                    }

                    override fun getCurrentContentTitle(player: Player?): String {
                        return articObject?.title.orEmpty()
                    }


                    override fun getCurrentLargeIcon(player: Player?, callback: PlayerNotificationManager.BitmapCallback): Bitmap? {
                        articObject?.largeImageFullPath?.let {

                            Glide.with(this@AudioPlayerService)
                                    .asBitmap()
                                    .load(it)
                                    .into(BitmapCallbackTarget(callback))
                        }
                        return null
                    }
                })

        playerNotificationManager.setNotificationListener(object : PlayerNotificationManager.NotificationListener {
            override fun onNotificationCancelled(notificationId: Int) {
            }

            override fun onNotificationStarted(notificationId: Int, notification: Notification?) {
                startForeground(notificationId, notification)
            }
        })
        playerNotificationManager.apply {
            setUseNavigationActions(false)
            setStopAction(null)
            setFastForwardIncrementMs(10 * 1000)
            setRewindIncrementMs(10 * 1000)
            setOngoing(false)
            setPlayer(player)
            setSmallIcon(R.drawable.icn_notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    inner class AudioPlayerServiceBinder : Binder() {
        fun getService(): AudioPlayerService {
            return this@AudioPlayerService
        }
    }

    fun setArticObject(_articObject: ArticObject, resetPosition: Boolean = false) {

        if (articObject != _articObject || player.playbackState == Player.STATE_IDLE) {

            /** Check if the current audio is being interrupted by other audio object.**/
            articObject?.let { articObject ->
                if (player.playbackState != Player.STATE_IDLE) {
                    analyticsTracker.reportEvent(EventCategoryName.PlayBack, AnalyticsAction.playbackInterrupted, articObject.getAudio()?.title.orEmpty())
                }
            }
            articObject = _articObject
            val audioFile = articObject?.getAudio()
            audioFile?.let {
                val fileUrl = audioFile.fileUrl
                fileUrl?.let { url ->
                    val uri = Uri.parse(url)
                    val mediaSource = buildMediaSource(uri)
                    player.prepare(mediaSource, resetPosition, false)
                    player.seekTo(0)
                }
                currentTrack.onNext(audioFile)
            }
        }
    }

    /**
     * AIC wants to play the music through the ear piece.
     * @see SimpleExoPlayer.setAudioStreamType
     */
    private val audioAttributes = AudioAttributesCompat.Builder()
            .setContentType(Util.getAudioContentTypeForStreamType(STREAM_TYPE_VOICE_CALL))
            .setUsage(Util.getAudioUsageForStreamType(STREAM_TYPE_VOICE_CALL))
            .build()

    val player: ExoPlayer by lazy {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = false
        AudioFocusExoPlayerDecorator(audioAttributes,
                audioManager,
                ExoPlayerFactory.newSimpleInstance(DefaultRenderersFactory(this),
                        DefaultTrackSelector(),
                        DefaultLoadControl()).apply {
                    audioAttributes = AudioAttributes.Builder()
                            .setUsage(Util.getAudioUsageForStreamType(STREAM_TYPE_VOICE_CALL))
                            .setContentType(Util.getAudioContentTypeForStreamType(STREAM_TYPE_VOICE_CALL))
                            .build()
                }
        )
    }

    private fun buildMediaSource(uri: Uri): MediaSource {
        return ExtractorMediaSource.Factory(
                DefaultHttpDataSourceFactory("exoplayer-aic")).createMediaSource(uri)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerNotificationManager.setPlayer(null)
        player.release()
        disposeBag.dispose()
    }

    fun pausePlayer() {
        audioControl.onNext(PlayBackAction.Pause())
    }

    fun playPlayer(audioFile: ArticObject?) {
        audioFile?.let {
            audioControl.onNext(PlayBackAction.Play(it))
        }
    }

    fun resumePlayer() {
        audioControl.onNext(AudioPlayerService.PlayBackAction.Resume())
    }

    fun stopPlayer() {
        analyticsTracker.reportEvent(EventCategoryName.PlayBack, AnalyticsAction.playbackInterrupted, articObject?.getAudio()?.title.orEmpty())
        audioControl.onNext(AudioPlayerService.PlayBackAction.Stop())
    }
}

/**
 * Kotlin(version:1.2.51) was unable to resolve this class when it was defined anonymously,
 * so had to create this class.
 */
class BitmapCallbackTarget(private val callback: PlayerNotificationManager.BitmapCallback?) : SimpleTarget<Bitmap>() {
    override fun onResourceReady(resource: Bitmap?, transition: Transition<in Bitmap>?) {
        callback?.onBitmap(resource)
    }
}