package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.session.MediaButtonReceiver;

import java.io.IOException;
import java.util.ArrayList;


public class MusicService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener {//MediaPlayer.OnPreparedListener,

    public static final String ACTION_PLAY = "com.example.myapplication.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.example.myapplication.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.example.myapplication.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.example.myapplication.ACTION_NEXT";
    public static final String ACTION_STOP = "com.example.myapplication.ACTION_STOP";

    private ArrayList<MusicRepository.Song> songs;
    private MusicRepository.Song song;

    // Binder для связи с активити
    private IBinder binder = new PlayerServiceBinder();
    // Ссылка на активити
    private MusicPlayerActivity activity;

    private RandomMusic randomMusic;
    private int currentSongIndex=0;
    private NotificationManager notificationManager;
    private Notification notification;

    StorageSettingPlayer storage;

    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;

    //Used to pause/resume MediaPlayer
    private int resumePosition;

    //Handle incoming phone calls
    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;
    private int IdMusic;

    //AudioFocus
    private AudioManager audioManager;
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private AudioFocusRequest audioFocusRequest;
    private boolean audioFocusRequested = false,isShuffle,isRepeat;
    private int duration;
    private String StringDuration;
    private MusicRepository.Song track;
    private int currentStateCopy;
    public static final String ACTION_TOGGLE_PLAYBACK = "com.example.musicplayer.ACTION_TOGGLE_PLAYBACK";
    private final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

    private final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder().setActions(
            PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    );
    private final int NOTIFICATION_ID = 116;
    private final String NOTIFICATION_DEFAULT_CHANNEL_ID = "VKaif_Channel";
    private final MusicRepository musicRepository = new MusicRepository();
    private boolean checkPause=false;
    String TAG="d";
    private String currentUri;

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        if(activity!=null)
        {
            activity.playerSeekBar.setSecondaryProgress(percent);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        //Invoked when there has been an error during an asynchronous operation
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        //Invoked to communicate some info
        return false;
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
//Invoked indicating the completion of a seek operation.
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @SuppressLint("WrongConstant") NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_DEFAULT_CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManagerCompat.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .setAudioAttributes(audioAttributes)
                    .build();
        }

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mediaSession = new MediaSessionCompat(this, "PlayerService");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(mediaSessionCallback);

        Context appContext = getApplicationContext();

        Intent activityIntent = new Intent(appContext, MusicService.class);
        mediaSession.setSessionActivity(PendingIntent.getActivity(appContext, 0, activityIntent, 0));

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, appContext, MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(appContext, 0, mediaButtonIntent, 0));
        randomMusic=new RandomMusic();
        storage = new StorageSettingPlayer(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        //storage = new StorageSettingPlayer(getApplicationContext());
        IdMusic = intent.getIntExtra("idmusic", 0);
        musicRepository.setIdUserMusic(IdMusic);
        return super.onStartCommand(intent, flags, startId);
    }
    // Метод для установки ссылки на активити
    public void setActivity(MusicPlayerActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaSession.release();
        if (mediaPlayer != null || mediaPlayer.isPlaying()) {
            mediaPlayer.release();
        }
    }

    private MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {

        //private String currentUri;
        private int currentState = PlaybackStateCompat.STATE_STOPPED;

        @Override
        public void onPlay()
        {
            startService(new Intent(getApplicationContext(), MusicService.class));
            //track = musicRepository.getCurrent(); //MusicRepository.Song
            if (!audioFocusRequested) {
                audioFocusRequested = true;
                int audioFocusResult;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusResult = audioManager.requestAudioFocus(audioFocusRequest);
                }
                else {
                    audioFocusResult = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                }
                if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                    return;
            }
            mediaSession.setActive(true); // Сразу после получения фокуса
            //register after getting audio focus
            registerReceiver(becomingNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
            currentState = PlaybackStateCompat.STATE_PLAYING;

            refreshNotificationAndForegroundStatus(currentState);
            if(checkPause)
            {
                resumeMedia();
            }
            else
            {
                Log.d(TAG,"ONPLAY");
                playMedia();
            }
            checkPause=false;
        }
        @Override
        public void onPause() {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                resumePosition = mediaPlayer.getCurrentPosition();
                checkPause=true;
                unregisterReceiver(becomingNoisyReceiver);
            }
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
            currentState = PlaybackStateCompat.STATE_PAUSED;
            refreshNotificationAndForegroundStatus(currentState);
        }

        @Override
        public void onStop()
        {
            if (mediaPlayer == null) return;
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                unregisterReceiver(becomingNoisyReceiver);
            }
            if (audioFocusRequested) {
                audioFocusRequested = false;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest);
                } else {
                    audioManager.abandonAudioFocus(audioFocusChangeListener);
                }
            }
            mediaSession.setActive(false);

            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
            currentState = PlaybackStateCompat.STATE_STOPPED;

            refreshNotificationAndForegroundStatus(currentState);

            stopSelf();
        }

        @Override
        public void onSkipToNext() {
            MusicRepository.Song track = musicRepository.getNext();
            storage.storeAudioIndex(track.getBitmapResId());
            updateMetadataFromTrack(track);

            stopMedia();
            mediaPlayer.reset();

            refreshNotificationAndForegroundStatus(currentState);

            prepareToPlay(track.getMusicPath());
        }

        @Override
        public void onSkipToPrevious() {
            MusicRepository.Song track = musicRepository.getPrevious();
            storage.storeAudioIndex(track.getBitmapResId());
            updateMetadataFromTrack(track);

            stopMedia();
            mediaPlayer.reset();

            refreshNotificationAndForegroundStatus(currentState);

            prepareToPlay(track.getMusicPath());
        }
    };
    public void prepareToPlay(String uri)
    {
        if (mediaPlayer == null){
            mediaPlayer = new MediaPlayer();
        }
        currentUri = uri;
        //Set up MediaPlayer event listeners
        mediaPlayer.setOnCompletionListener(MusicService.this);
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer.reset();
        try
        {
            mediaPlayer.setDataSource(currentUri);
            mediaPlayer.prepare();
            duration=mediaPlayer.getDuration();
            StringDuration=musicRepository.ConvertingTime(duration);
            updateMetadataFromTrack(track);
            Log.d(TAG, "setDataSource");
        }
        catch (IOException | IllegalArgumentException | IllegalStateException e) {}
        if(activity!=null)
            activity.updateTextView(StringDuration,duration);
    }
    private void updateMetadataFromTrack(MusicRepository.Song track) {
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(getResources(), track.getBitmapResId()));
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getTitle());
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getArtist());
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist());
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION,duration);
        mediaSession.setMetadata(metadataBuilder.build());
    }
    protected Boolean GetisRepeat() {
        if (mediaPlayer.isLooping()) {
            return true;
        }
        return false;
    }
    private void resumeMedia() {
        if (!mediaPlayer.isPlaying() && activity!=null) {
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
            activity.updateSeekBar();
        }
    }

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    //mediaSessionCallback.onPlay(); // Не очень красиво
                    // возобновить воспроизведение
                    if (mediaPlayer == null && activity!=null){
                        activity.isPlaying=false;
                        activity.playerSeekBar.setProgress(0);
                        activity.textTotalDuration.setText(R.string.zero);
                        activity.handler.removeCallbacks(activity.updater);//надо подумать
                        activity.textCurrentTime.setText(R.string.zero);
                        activity.updateUI();
                        IdMusic=storage.loadAudioIndex();
                        musicRepository.setIdUserMusic(IdMusic);
                        track = musicRepository.getCurrent();
                        prepareToPlay(track.getMusicPath());
                    }
                    else if (!mediaPlayer.isPlaying() && activity!=null)
                    {
                        duration=mediaPlayer.getDuration();
                        StringDuration=musicRepository.ConvertingTime(duration);
                        activity.updateTextView(StringDuration,duration);
                        playMedia();
                    }
                    mediaPlayer.setVolume(1.0f, 1.0f);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    // Потеря фокуса на неограниченное время: остановить воспроизведение и отпустите медиаплеер
                    if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                    mediaPlayer.release();
                    mediaPlayer = null;
                    mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
                    currentStateCopy = PlaybackStateCompat.STATE_STOPPED;
                    refreshNotificationAndForegroundStatus(currentStateCopy);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    // Потеря фокуса на короткое время, но мы должны остановить воспроизведение
                    // Мы не выпускаем медиаплеер, потому что воспроизведение, скорее всего, возобновится
                    if (mediaPlayer.isPlaying()) mediaSessionCallback.onPause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // Потерял фокус на короткое время, но можно продолжать играть
                    // на ослабленном уровне
                    if (mediaPlayer.isPlaying()) mediaPlayer.setVolume(0.1f, 0.1f);
                    break;
                default:
                    mediaSessionCallback.onPause();
                    break;
            }
        }
    };

    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Disconnecting headphones - stop playback
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                mediaSessionCallback.onPause();
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        //return new MusicService.PlayerServiceBinder();
        return binder;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer)
    {
        //isShuffle=storage.loadAudio("Shuffle");
        isShuffle=new  StorageSettingPlayer(getApplicationContext()).loadAudio("Shuffle");
        isRepeat=new  StorageSettingPlayer(getApplicationContext()).loadAudio("Repeat");
        checkPause=false;
        //Invoked when playback of a media source has completed.
        if (activity != null) {
            if(isShuffle)
            {
                stopMedia();
                currentStateCopy = PlaybackStateCompat.STATE_STOPPED;
                refreshNotificationAndForegroundStatus(currentStateCopy);
                activity.playerSeekBar.setProgress(0);
                IdMusic=randomMusic.SetRandomId(storage.loadAudioIndex());
                storage.storeAudioIndex(IdMusic);
                musicRepository.setIdUserMusic(IdMusic);
                activity.isPlaying=false;
                activity.updateUI();
                track = musicRepository.getCurrent();
                prepareToPlay(track.getMusicPath());
                mediaSessionCallback.onPlay();
                Log.d(TAG,"SHUFFLE");
            }
            else if(isRepeat)
            {
                mediaPlayer.setLooping(true);
                Log.d(TAG,"REPEAT");
            }
            else
            {
                mediaPlayer.reset();
                unregisterReceiver(becomingNoisyReceiver);
                mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
                currentStateCopy = PlaybackStateCompat.STATE_STOPPED;
                refreshNotificationAndForegroundStatus(currentStateCopy);
                activity.playerSeekBar.setProgress(0);
                activity.textTotalDuration.setText(R.string.zero);
                activity.handler.removeCallbacks(activity.updater);//надо подумать
                activity.textCurrentTime.setText(R.string.zero);
                activity.isPlaying=false;
                activity.updateUI();
                musicRepository.setIdUserMusic(storage.loadAudioIndex());
                track = musicRepository.getCurrent();
                prepareToPlay(track.getMusicPath());
                Log.d(TAG,"COMPLETION");
            }
        }
    }

    private void playMedia()
    {
        if (activity != null) {
            activity.isPlaying=true;
            activity.updateUI();
            mediaPlayer.start();
            activity.updateSeekBar();
            Log.d(TAG, "onPrepared");
        }
    }

    private void stopMedia() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    /**
     * Service Binder
     */
    public class PlayerServiceBinder extends Binder {
        public MediaSessionCompat.Token getMediaSessionToken() {
            return mediaSession.getSessionToken();
        }
        public MusicService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MusicService.this;
        }
        protected int GetDurationPlayer() {
            return mediaPlayer.getDuration() / 100;
        }
        protected Integer GetCurrentPosition()
        {
            if(mediaPlayer!=null)
                return mediaPlayer.getCurrentPosition();
            return null;
        }
        protected Boolean GetisPlayerPlayer() {
            return mediaPlayer.isPlaying();
        }
        protected void SetPositionPlayer(int Position) {
            if(mediaPlayer.isPlaying()){
                mediaPlayer.pause();
            }
            mediaPlayer.seekTo(Position);
            mediaPlayer.start();
        }
        protected void SetPrepareMusic(int _IdMusic)
        {
            IdMusic = _IdMusic;
            storage.storeAudioIndex(IdMusic);
            musicRepository.setIdUserMusic(IdMusic);
            track = musicRepository.getCurrent();
            Log.d(TAG, String.valueOf(IdMusic));
            prepareToPlay(track.getMusicPath());
        }

        protected void SetShuffle(Boolean checkShuffle) {
            if(checkShuffle)
            {
                isShuffle=true;
                //new  StorageSettingPlayer(getApplicationContext()).storeAudio(isShuffle,"Shuffle");
            }
            else
            {
                isShuffle=false;
                //new  StorageSettingPlayer(getApplicationContext()).storeAudio(isShuffle,"Shuffle");
            }
            storage.storeAudio(isShuffle,"Shuffle");
            mediaPlayer.setLooping(false);
        }
        protected void SetRepeat(Boolean checkRepeat) {
            if(checkRepeat)
            {
                mediaPlayer.setLooping(true);
                isRepeat=true;
                //storage.storeAudio(isRepeat,"Repeat");
            }
            else
            {
                mediaPlayer.setLooping(false);
                isRepeat=false;
                //storage.storeAudio(isRepeat,"Repeat");
            }
            storage.storeAudio(isRepeat,"Repeat");
        }
    }

    private void refreshNotificationAndForegroundStatus(int playbackState) {
        switch (playbackState) {
            case PlaybackStateCompat.STATE_PLAYING: {
                startForeground(NOTIFICATION_ID, getNotification(playbackState));
                break;
            }
            case PlaybackStateCompat.STATE_PAUSED: {
                NotificationManagerCompat.from(MusicService.this).notify(NOTIFICATION_ID, getNotification(playbackState));
                stopForeground(false);
                break;
            }
            default: {
                stopForeground(true);
                break;
            }
        }
    }

    private Notification getNotification(int playbackState) {
        NotificationCompat.Builder builder = MediaNotificationManager.from(this, mediaSession);
        builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_previous, getString(R.string.previous), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));

        if (playbackState == PlaybackStateCompat.STATE_PLAYING)
            builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, getString(R.string.pause), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));
        else
            builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_play, getString(R.string.play), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));

        builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, getString(R.string.next), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)));
        builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(1)
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
                .setMediaSession(mediaSession.getSessionToken())); // setMediaSession требуется для Android Wear
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark)); // The whole background (in MediaStyle), not just icon background
        builder.setShowWhen(false);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setOnlyAlertOnce(true);
        builder.setChannelId(NOTIFICATION_DEFAULT_CHANNEL_ID);

        return builder.build();
    }
}
