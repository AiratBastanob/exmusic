package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

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

    private final IBinder binder = new LocalBinder();
    private MediaPlayer player;
    private ArrayList<Song> songs;
    private Song song;
    private int currentSongIndex=0;
    private NotificationManager notificationManager;
    private NotificationCreator notificationCreator;
    private Notification notification;

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

    //AudioFocus
    private AudioManager audioManager;
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private AudioFocusRequest audioFocusRequest;
    private boolean audioFocusRequested = false;

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


    // Service lifecycle methods
    @Override
    public void onCreate() {
        super.onCreate();
        // Perform one-time setup procedures

        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener();
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();
        //Listen for new Audio to play -- BroadcastReceiver
        register_playNewAudio();
    }

    //The system calls this method when an activity, requests the service be started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {

            //Load data from SharedPreferences
            StorageUtil storage = new StorageUtil(getApplicationContext());
            currentSongIndex = storage.loadAudioIndex();

            if (currentSongIndex != -1 && !(songs.size() <=-1)) {
                //index is in a valid range
                song = songs.get(currentSongIndex);
            } else {
                stopSelf();
            }
        } catch (NullPointerException e) {
            stopSelf();
        }

        //Request audio focus
        if (requestAudioFocus() == false) {
            //Could not gain focus
            stopSelf();
        }

        if (mediaSessionManager == null) {
            try {
                song = Song.getCurrent();
                initMediaSession();
                initMediaPlayer();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
            //buildNotification(PlaybackStatus.PLAYING);
        }

        //Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * MediaSession and Notification actions
     */
    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return; //mediaSessionManager exists

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        // Create a new MediaSession
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        //Get MediaSessions transport controls
        transportControls = mediaSession.getController().getTransportControls();
        //set MediaSession -> ready to receive media commands
        mediaSession.setActive(true);
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        //Set mediaSession's MetaData
        updateMetaData();

        // Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            // Implement callbacks
            @Override
            public void onPlay() {
                super.onPlay();

                resumeMedia();
                //buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();

                pauseMedia();
                //buildNotification(PlaybackStatus.PAUSED);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();

                skipToNext();
                updateMetaData();
                //buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();

                skipToPrevious();
                updateMetaData();
                //buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onStop() {
                super.onStop();
                //removeNotification();
                //Stop the service
                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
            }
        });
    }

    /*public void playNextSong() {
        int nextSongIndex = (currentSongIndex + 1) % songs.size();
        playSong(nextSongIndex);
    }

    public void playPreviousSong() {
        int previousSongIndex = (currentSongIndex - 1 + songs.size()) % songs.size();
        playSong(previousSongIndex);
    }*/

    /**
     * MediaPlayer actions
     */
    private void initMediaPlayer() {
        if (mediaPlayer == null)
            mediaPlayer = new MediaPlayer();//new MediaPlayer instance

        //Set up MediaPlayer event listeners
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer.reset();


        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            // Set the data source to the mediaFile location
            mediaPlayer.setDataSource(song.getMusicPath());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public void togglePlayPause() {
        if (player.isPlaying()) {
            player.pause();
            resumePosition = mediaPlayer.getCurrentPosition();
        } else {
            player.start();
        }
        updateNotification();
    }
    private void resumeMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
        }
    }
    private void stopMedia() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }
    private void pauseMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();
        }
    }
    private void skipToNext() {

        if (currentSongIndex == songs.size() - 1) {
            //if last in playlist
            currentSongIndex = 0;
            song = songs.get(currentSongIndex);
        } else {
            //get next in playlist
            song = songs.get(++currentSongIndex);
        }

        //Update stored index
        new StorageUtil(getApplicationContext()).storeAudioIndex(currentSongIndex);

        stopMedia();
        //reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }

    private void skipToPrevious() {

        if (currentSongIndex == 0) {
            //if first in playlist
            //set index to the last of audioList
            currentSongIndex = songs.size() - 1;
            song = songs.get(currentSongIndex);
        } else {
            //get previous in playlist
            song = songs.get(--currentSongIndex);
        }

        //Update stored index
        new StorageUtil(getApplicationContext()).storeAudioIndex(currentSongIndex);

        stopMedia();
        //reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }
    }

    /**
     * MediaPlayer callback methods
     */
    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        //Invoked when playback of a media source has completed.
        stopMedia();

        //removeNotification();
        //stop the service
        stopSelf();
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
    public void onPrepared(MediaPlayer mp) {
        //Invoked when the media source is ready for playback.
        togglePlayPause();
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        //Invoked indicating the completion of a seek operation.
    }

    /**
     * AudioFocus
     */
    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            //Focus gained
            return true;
        }
        //Could not gain focus
        return false;
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                audioManager.abandonAudioFocus(this);
    }

    // Notification methods
    private void updateNotification() {
        Notification notification = notificationCreator.createNotification(songs.get(currentSongIndex));
        if (notification == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            notificationCreator.showNotification(notification);
        } else {
            startForeground(NotificationCreator.NOTIFICATION_ID, notification);
        }
    }

    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            togglePlayPause();
            //buildNotification(PlaybackStatus.PAUSED);
        }
    };

    private void registerBecomingNoisyReceiver() {
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    /**
     * Handle PhoneState changes
     */
    private void callStateListener() {
        // Get the telephony manager
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //Starting listening for PhoneState changes
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null) {
                            togglePlayPause();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Phone idle. Start playing.
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }
//тут будет возврат, когда убирется фокус
    private final BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Disconnecting headphones - stop playback
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                currentSongIndex = new StorageUtil(getApplicationContext()).loadAudioIndex();
                if (currentSongIndex  != -1 && !(songs.size() <=-1)) {
                    //index is in a valid range
                    song = songs.get(currentSongIndex);
                }
                else
                {
                    stopSelf();
                }

                //A PLAY_NEW_AUDIO action received
                //reset mediaPlayer to play the new Audio
                stopMedia();
                mediaPlayer.reset();
                initMediaPlayer();
                updateMetaData();
                //buildNotification(PlaybackStatus.PLAYING);
            }
        }
    };

    private void updateMetaData() {
        /*Bitmap albumArt = BitmapFactory.decodeResource(getResources(),
                R.drawable.image5); //replace with medias albumArt
        // Update the current metadata
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio.getAlbum())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getTitle())
                .build());*/
    }

    private void register_playNewAudio() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(playNewAudio, filter);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mediaSession.release();
        //removeNotification();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();
        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        //removeNotification();

        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);

        //clear cached playlist
        new StorageUtil(getApplicationContext()).clearCachedAudioPlaylist();
    }

    @Override
    public void onAudioFocusChange(int focusChange) {

    }

    /**
     * Service Binder
     */
    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }
}

