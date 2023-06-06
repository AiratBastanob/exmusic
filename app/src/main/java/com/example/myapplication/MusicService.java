package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import android.Manifest;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.session.MediaButtonReceiver;

import java.io.IOException;
import java.util.ArrayList;


public class MusicService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener {//MediaPlayer.OnPreparedListener,
    private final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder().setActions(
            PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    );
    // Binder для связи с активити
    private IBinder binder = new PlayerServiceBinder();
    // Ссылка на активити
    private MusicPlayerActivity activity;

    private RandomMusic randomMusic;
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
    private boolean audioFocusRequested = false, isShuffle, isRepeat;
    private int duration,currentStateCopy;
    private String StringDuration,TAG = "d",currentUri;
    private MusicRepository.Song track;
    private final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();
    private final int NOTIFICATION_ID = 116;
    private final String NOTIFICATION_DEFAULT_CHANNEL_ID = "VKaif_Channel";
    private final MusicRepository musicRepository = new MusicRepository();
    private boolean checkPause = false;

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        if (activity != null) {
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
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_DEFAULT_CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
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
        mediaSession.setSessionActivity(PendingIntent.getActivity(appContext, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE));

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, appContext, MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(appContext, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE));
        randomMusic = new RandomMusic();
        storage = new StorageSettingPlayer(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null&& intent.getAction() != null) {
            Log.d("MusicService", "Intent: " + intent.toString());
            Log.d("MusicService", "Action: " + intent.getAction());
            long action = Long.parseLong(intent.getAction());
            switch ((int) action) {
                case (int)PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS:
                    handleSkipToPrevious();
                    break;
                case (int)PlaybackStateCompat.ACTION_PLAY_PAUSE:
                    handlePlayPause();
                    break;
                case (int)PlaybackStateCompat.ACTION_SKIP_TO_NEXT:
                    handleSkipToNext();
            }
        }
       return  super.onStartCommand(intent, flags, startId);
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
        int currentState = PlaybackStateCompat.STATE_STOPPED;

        @Override
        public void onPlay() {
            startService(new Intent(getApplicationContext(), MusicService.class));
            //track = musicRepository.getCurrent(); //MusicRepository.Song
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
            //register after getting audio focus
            registerReceiver(becomingNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
            currentState = PlaybackStateCompat.STATE_PLAYING;
            Log.d("dPLAYSERVICE",String.valueOf(checkPause));
            refreshNotificationAndForegroundStatus(currentState);
            if (checkPause)
            {
                resumeMedia();
            }
            else
            {
                Log.d("dPLAYSERVICE", "ONPLAY");
                playMedia();
            }
            checkPause = activity.isPlaying;
        }

        @Override
        public void onPause() {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                resumePosition = mediaPlayer.getCurrentPosition();
                checkPause = true;
                if(activity.isPlaying){
                    activity.isPlaying=false;
                    activity.updateUI();
                }
                //unregisterReceiver(becomingNoisyReceiver);
            }
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.ACTION_PAUSE, 1).build());
            currentState = PlaybackStateCompat.STATE_PAUSED;
            refreshNotificationAndForegroundStatus(currentState);
        }

        @Override
        public void onStop() {
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
            stopMedia();
            checkPause = false;
            activity.playerSeekBar.setProgress(0);
            activity.isPlaying=false;
            activity.updateUI();
            track = musicRepository.getNext();
            storage.storeAudioIndex(track.getBitmapResId());
            prepareToPlay(track.getMusicPath());
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
            currentState = PlaybackStateCompat.STATE_PAUSED;
            refreshNotificationAndForegroundStatus(currentState);
        }

        @Override
        public void onSkipToPrevious() {
            stopMedia();
            checkPause = false;
            activity.playerSeekBar.setProgress(0);
            activity.isPlaying=false;
            activity.updateUI();
            track = musicRepository.getPrevious();
            storage.storeAudioIndex(track.getBitmapResId());
            prepareToPlay(track.getMusicPath());
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
            currentState = PlaybackStateCompat.STATE_PAUSED;
            refreshNotificationAndForegroundStatus(currentState);
        }
    };

    public void prepareToPlay(String uri) {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        currentUri = uri;
        //Set up MediaPlayer event listeners
        mediaPlayer.setOnCompletionListener(MusicService.this);
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(currentUri);
            mediaPlayer.prepare();
            duration = mediaPlayer.getDuration();
            StringDuration = musicRepository.ConvertingTime(duration);
            updateMetadataFromTrack(track);
            activity.artistTextView.setText(track.getArtist());
            activity.songTitleTextView.setText(track.getTitle());
            if (activity != null)
                activity.updateTextView(StringDuration, duration);
            Log.d(TAG, "setDataSource");
        }
        catch (IOException | IllegalArgumentException | IllegalStateException e) {
        }

    }

    private void updateMetadataFromTrack(MusicRepository.Song track) {
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(getResources(), track.getBitmapResId()));
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getTitle());
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getArtist());
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist());
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration());
        mediaSession.setMetadata(metadataBuilder.build());
    }

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d("AudioFocusChange","AudioFocusChange");
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    //mediaSessionCallback.onPlay(); // Не очень красиво
                    // возобновить воспроизведение
                    if (mediaPlayer == null && activity != null) {
                        activity.isPlaying = false;
                        activity.playerSeekBar.setProgress(0);
                        activity.textTotalDuration.setText(R.string.zero);
                        activity.handler.removeCallbacks(activity.updater);//надо подумать
                        activity.textCurrentTime.setText(R.string.zero);
                        activity.updateUI();
                        IdMusic = storage.loadAudioIndex();
                        musicRepository.setIdUserMusic(IdMusic);
                        track = musicRepository.getCurrent();
                        prepareToPlay(track.getMusicPath());
                    }
                    else if(mediaPlayer != null&&activity.isPlaying)
                    {
                        if (!mediaPlayer.isPlaying() && activity != null) {
                            duration = mediaPlayer.getDuration();
                            StringDuration = musicRepository.ConvertingTime(duration);
                            activity.updateTextView(StringDuration, duration);
                            playMedia();
                        }
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
            //registerReceiver(broadcastReceiver, new IntentFilter("Music_MS"));
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        //return new MusicService.PlayerServiceBinder();
        return binder;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {       ;
        isShuffle = storage.loadAudio("Shuffle");
        isRepeat = storage.loadAudio("Repeat");
        checkPause = false;
        //Invoked when playback of a media source has completed.
        if (activity != null) {
            if (isShuffle) {
                stopMedia();
                currentStateCopy = PlaybackStateCompat.STATE_STOPPED;
                refreshNotificationAndForegroundStatus(currentStateCopy);
                activity.playerSeekBar.setProgress(0);
                IdMusic = randomMusic.SetRandomId(storage.loadAudioIndex());
                storage.storeAudioIndex(IdMusic);
                musicRepository.setIdUserMusic(IdMusic);
                Log.d(TAG, String.valueOf(activity.isPlaying));
                track = musicRepository.getCurrent();
                prepareToPlay(track.getMusicPath());
                mediaSessionCallback.onPlay();
                Log.d(TAG, "SHUFFLE");
            } else if (isRepeat) {
                mediaPlayer.setLooping(true);
                activity.isPlaying =true;
                Log.d(TAG, "REPEAT");
            } else {
                mediaPlayer.reset();
                unregisterReceiver(becomingNoisyReceiver);
                mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
                currentStateCopy = PlaybackStateCompat.STATE_STOPPED;
                refreshNotificationAndForegroundStatus(currentStateCopy);
                activity.playerSeekBar.setProgress(0);
                activity.textTotalDuration.setText(R.string.zero);
                activity.handler.removeCallbacks(activity.updater);//надо подумать
                activity.textCurrentTime.setText(R.string.zero);
                activity.isPlaying = false;
                activity.updateUI();
                musicRepository.setIdUserMusic(storage.loadAudioIndex());
                track = musicRepository.getCurrent();
                prepareToPlay(track.getMusicPath());
                Log.d(TAG, "COMPLETION");
            }
        }
    }
    private void resumeMedia() {
        if (!mediaPlayer.isPlaying() && activity != null) {
            activity.isPlaying = true;
            activity.updateUI();
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
            activity.updateSeekBar();
        }
    }
    private void playMedia() {
        if (activity != null)
        {
            activity.isPlaying = true;
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
            mediaPlayer.reset();
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

        protected Integer GetCurrentPosition() {
            if (mediaPlayer != null)
                return mediaPlayer.getCurrentPosition();
            return null;
        }

        protected void SetPositionPlayer(int Position) {
            if(activity!=null){
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    mediaPlayer.seekTo(Position);
                    mediaPlayer.start();
                }
                else{
                    resumePosition=Position;
                    mediaPlayer.seekTo(Position);
                    mediaSessionCallback.onPlay();
                }
            }
        }

        protected void SetPrepareMusic(int _IdMusic) {
            IdMusic = _IdMusic;
            storage.storeAudioIndex(IdMusic);
            musicRepository.setIdUserMusic(IdMusic);
            track = musicRepository.getCurrent();
            Log.d(TAG, String.valueOf(IdMusic));
            prepareToPlay(track.getMusicPath());
        }

        protected void SetShuffle(Boolean checkShuffle) {
            if (checkShuffle) {
                isShuffle = true;
            } else {
                isShuffle = false;            ;
            }
            storage.storeAudio(isShuffle, "Shuffle");
            mediaPlayer.setLooping(false);
        }

        protected void SetRepeat(Boolean checkRepeat) {
            if (checkRepeat) {
                mediaPlayer.setLooping(true);
                isRepeat = true;
            } else {
                mediaPlayer.setLooping(false);
                isRepeat = false;
            }
            storage.storeAudio(isRepeat, "Repeat");
        }
    }

    @SuppressLint("MissingPermission")
    private void refreshNotificationAndForegroundStatus(int playbackState) {
        Notification notification = getNotification(playbackState);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                switch (playbackState) {
                    case PlaybackStateCompat.STATE_PLAYING: {
                        startForeground(NOTIFICATION_ID, notification);
                        break;
                    }
                    case PlaybackStateCompat.STATE_PAUSED: {
                        NotificationManagerCompat.from(MusicService.this).notify(NOTIFICATION_ID, notification);
                        stopForeground(false);
                        break;
                    }
                    default: {
                        stopForeground(true);
                        break;
                    }
                }
            }
            else {
                switch (playbackState) {
                    case PlaybackStateCompat.STATE_PLAYING: {
                        startForeground(NOTIFICATION_ID, notification);
                        break;
                    }
                    case PlaybackStateCompat.STATE_PAUSED: {
                        NotificationManagerCompat.from(MusicService.this).notify(NOTIFICATION_ID, notification);
                        stopForeground(false);
                        break;
                    }
                    default: {
                        stopForeground(true);
                        break;
                    }
                }
            }
    }
    // Метод обработчика кнопки "Previous"
    private void handleSkipToPrevious() {
        // Реализация логики для перехода к предыдущему треку
        mediaSessionCallback.onSkipToPrevious();
    }

    // Метод обработчика кнопки "Play/Pause"
    private void handlePlayPause() {
        Log.d("DASDAS","NORIFPLAY");
        if (mediaPlayer.isPlaying()) {
            // Реализация логики для паузы воспроизведения
            mediaSessionCallback.onPause();
        } else {
            // Реализация логики для возобновления воспроизведения
            mediaSessionCallback.onPlay();
        }
    }

    // Метод обработчика кнопки "Next"
    private void handleSkipToNext() {
        // Реализация логики для перехода к следующему треку
        mediaSessionCallback.onSkipToNext();
    }

    private Notification getNotification(int playbackState) {
        NotificationCompat.Builder builder = MediaNotificationManager.from(this, mediaSession);
        // Добавление обработчиков кнопок только для Android 10 и ниже
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
           Intent previousIntent = new Intent(this, MusicService.class);
            previousIntent.setAction(String.valueOf(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));
            PendingIntent previousPendingIntent = PendingIntent.getService(this, 0, previousIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(android.R.drawable.ic_media_previous, getString(R.string.previous), previousPendingIntent);
            Intent playPauseIntent = new Intent(this, MusicService.class);
            playPauseIntent.setAction(String.valueOf(PlaybackStateCompat.ACTION_PLAY_PAUSE));
            PendingIntent playPausePendingIntent = PendingIntent.getService(this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
                builder.addAction(android.R.drawable.ic_media_pause, getString(R.string.pause), playPausePendingIntent);
            } else {
                builder.addAction(android.R.drawable.ic_media_play, getString(R.string.play), playPausePendingIntent);
            }
            Intent nextIntent = new Intent(this, MusicService.class);
            nextIntent.setAction(String.valueOf(PlaybackStateCompat.ACTION_SKIP_TO_NEXT));
            PendingIntent nextPendingIntent = PendingIntent.getService(this, 0, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(android.R.drawable.ic_media_next, getString(R.string.next), nextPendingIntent);
        }
        else {
            builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_previous, getString(R.string.previous), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));
            if (playbackState == PlaybackStateCompat.STATE_PLAYING)
                builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, getString(R.string.pause), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));
            else
                builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_play, getString(R.string.play), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));
            builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, getString(R.string.next), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)));
        }
        builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(1)
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
                .setMediaSession(mediaSession.getSessionToken())); // setMediaSession требуется для Android Wear
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark)); // The whole background (in MediaStyle), not just icon background
        builder.setShowWhen(false);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setChannelId(NOTIFICATION_DEFAULT_CHANNEL_ID);
        builder.setSilent(true);
        Log.d("PAUSEFORPOSITION",String.valueOf(resumePosition));
        builder.setProgress(mediaPlayer.getDuration(), resumePosition, false);
        return builder.build();
    }
}
