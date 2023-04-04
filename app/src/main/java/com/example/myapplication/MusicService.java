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
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_PLAY = "com.example.myapplication.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.example.myapplication.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.example.myapplication.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.example.myapplication.ACTION_NEXT";
    public static final String ACTION_STOP = "com.example.myapplication.ACTION_STOP";

    private MediaPlayer player;
    private ArrayList<MusicRepository.Song> songs;
    private MusicRepository.Song song;
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

    private long duration;
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
    boolean checkPause=false;
    String TAG="d";

    @Override
    public void onAudioFocusChange(int focusChange) {

    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {

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

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaSession.release();
        if (mediaPlayer != null || mediaPlayer.isPlaying()) {
            mediaPlayer.release();
        }
    }

    private MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {

        private String currentUri;
        int currentState = PlaybackStateCompat.STATE_STOPPED;

        @Override
        public void onPlay()
        {
            startService(new Intent(getApplicationContext(), MusicService.class));
            track = musicRepository.getCurrent(); //MusicRepository.Song
            if(checkPause)
            {
                resumeMedia();
            }
            else
            {
                prepareToPlay(track.getMusicPath());
            }

            if (!audioFocusRequested) {
                audioFocusRequested = true;

                int audioFocusResult;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusResult = audioManager.requestAudioFocus(audioFocusRequest);
                } else {
                    audioFocusResult = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                }
                if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                    return;
            }

            mediaSession.setActive(true); // Сразу после получения фокуса

            registerReceiver(becomingNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
            currentState = PlaybackStateCompat.STATE_PLAYING;

            refreshNotificationAndForegroundStatus(currentState);
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
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());//mediaPlayer.getCurrentPosition()
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
            updateMetadataFromTrack(track);

            stopMedia();
            mediaPlayer.reset();

            refreshNotificationAndForegroundStatus(currentState);

            prepareToPlay(track.getMusicPath());
        }

        @Override
        public void onSkipToPrevious() {
            MusicRepository.Song track = musicRepository.getPrevious();
            updateMetadataFromTrack(track);

            stopMedia();
            mediaPlayer.reset();

            refreshNotificationAndForegroundStatus(currentState);

            prepareToPlay(track.getMusicPath());
        }
        public void prepareToPlay(String uri)
        {
            if (!uri.equals(currentUri))
            {
                if (mediaPlayer == null){
                    mediaPlayer = new MediaPlayer();
                }

                currentUri = uri;

                //Set up MediaPlayer event listeners
                mediaPlayer.setOnCompletionListener(MusicService.this);
                mediaPlayer.setOnPreparedListener(MusicService.this);

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
            }
        }
        private void updateMetadataFromTrack(MusicRepository.Song track) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(getResources(), track.getBitmapResId()));
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getTitle());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getArtist());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist());
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION,duration);
            mediaSession.setMetadata(metadataBuilder.build());
        }
    };
    protected Boolean GetisRepeat() {
        if (mediaPlayer.isLooping()) {
            return true;
        }
        return false;
    }
    private void resumeMedia() {
        if (!mediaPlayer.isPlaying()) {
            MusicPlayerActivity musicPlayerActivity=new MusicPlayerActivity();
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
            musicPlayerActivity.updateSeekBar();
        }
    }

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    // resume playback
                    mediaSessionCallback.onPlay(); // Не очень красиво
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    mediaSessionCallback.onPause();
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
        return new MusicService.PlayerServiceBinder();
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer)
    {
        //isShuffle=storage.loadAudio("Shuffle");
        isShuffle=new  StorageSettingPlayer(getApplicationContext()).loadAudio("Shuffle");
        isRepeat=new  StorageSettingPlayer(getApplicationContext()).loadAudio("Repeat");
        checkPause=false;
        Log.d(TAG, String.valueOf(mediaPlayer.isPlaying()));
        //Invoked when playback of a media source has completed.
        if(isShuffle)
        {
            currentStateCopy = PlaybackStateCompat.STATE_STOPPED;
            refreshNotificationAndForegroundStatus(currentStateCopy);
            IdMusic=randomMusic.SetRandomId(IdMusic);
            stopMedia();
            musicRepository.setIdUserMusic(IdMusic);
            MusicPlayerActivity musicPlayerActivity=new MusicPlayerActivity();
            musicPlayerActivity.isPlaying=false;
            musicPlayerActivity.updateUI();
            mediaSessionCallback.onPlay();
        }
        else if(isRepeat)
        {
            mediaPlayer.setLooping(true);
        }
        else
        {
            stopMedia();
            currentStateCopy = PlaybackStateCompat.STATE_PAUSED;
            refreshNotificationAndForegroundStatus(currentStateCopy);
            MusicPlayerActivity musicPlayerActivity=new MusicPlayerActivity();
            musicPlayerActivity.playerSeekBar.setProgress(0);
            musicPlayerActivity.isPlaying=false;
            musicPlayerActivity.updateUI();

        }

        /*stopMedia();
        mediaPlayer.reset();*/
        //mediaSessionCallback.onSkipToNext();
        //mediaSessionCallback.onPlay();
    }
    @Override
    public void onPrepared(MediaPlayer mp) {
        MusicPlayerActivity musicPlayerActivity=new MusicPlayerActivity();
        if(isShuffle)
        {
            musicPlayerActivity.isPlaying=true;
            musicPlayerActivity.updateUI();
        }
        musicPlayerActivity.isPlaying=true;
        musicPlayerActivity.updateTextTimeandSeek(StringDuration,duration);
        Log.d(TAG, StringDuration);
        //Log.d(TAG, "onPrepared");
        playMedia();
        musicPlayerActivity.updateSeekBar();
        Log.d(TAG, "onPrepared");
    }

    private void playMedia()
    {
        mediaPlayer.start();
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

        protected void SetShuffle(Boolean checkShuffle) {
            if(checkShuffle)
            {
                isShuffle=true;
                new  StorageSettingPlayer(getApplicationContext()).storeAudio(isShuffle,"Shuffle");
            }
            else
            {
                isShuffle=false;
                new  StorageSettingPlayer(getApplicationContext()).storeAudio(isShuffle,"Shuffle");
            }
        }
        protected void SetRepeat(Boolean checkRepeat) {
            if(checkRepeat)
            {
                mediaPlayer.setLooping(true);
                isRepeat=true;
                storage.storeAudio(isRepeat,"Repeat");
            }
            else
            {
                mediaPlayer.setLooping(false);
                isRepeat=false;
                storage.storeAudio(isRepeat,"Repeat");
            }
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
