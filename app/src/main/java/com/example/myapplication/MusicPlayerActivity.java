package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

public class MusicPlayerActivity extends AppCompatActivity {

    public static final String Broadcast_PLAY_NEW_AUDIO = "com.example.myapplication.PlayNewAudio";
    private ImageButton playButton,pauseButton,skipToNextButton,skipToPreviousButton,stopButton,Repeat,shuffle,next_10,replay_10;
    protected TextView songTitleTextView,textCurrentTime,textTotalDuration,artistTextView;
    protected SeekBar playerSeekBar;
    private boolean isBound = false;
    private int TotalDur,seek;
    private MusicService.PlayerServiceBinder playerServiceBinder;
    private MusicService musicService;
    private MediaControllerCompat mediaController;
    private MediaControllerCompat.Callback callback;
    private ServiceConnection serviceConnection;
    private Integer idMusic=1,RandomIdMusic;
    private final MusicRepository musicRepository = new MusicRepository();
    protected Handler handler = new Handler();

    boolean isPlaying = false,isRepeat=false,isShuffle=false,check=true,isStop=false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        playButton = findViewById(R.id.play_button);
        pauseButton = findViewById(R.id.pause_button);
        stopButton = findViewById(R.id.stop);
        skipToNextButton = findViewById(R.id.skip_to_next);
        skipToNextButton = findViewById(R.id.skip_to_next);
        skipToPreviousButton = findViewById(R.id.skip_to_previous);
        artistTextView = findViewById(R.id.artist_text_view);
        Repeat = findViewById(R.id.repeat);
        textCurrentTime = findViewById(R.id.textCurrentTime);
        textTotalDuration = findViewById(R.id.textTotalDuration);
        shuffle = findViewById(R.id.shuffle);
        playerSeekBar = findViewById(R.id.seek_bar);
        replay_10 = findViewById(R.id.replay10);
        next_10 = findViewById(R.id.forward_10);

        //playerSeekBar.setMax(100);

        musicService=new MusicService();

        callback = new MediaControllerCompat.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackStateCompat state) {
                if (state == null)
                    return;
                boolean playing = state.getState() == PlaybackStateCompat.STATE_PLAYING;
                playButton.setEnabled(!playing);
                pauseButton.setEnabled(playing);
                stopButton.setEnabled(playing);
            }
        };

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                playerServiceBinder = (MusicService.PlayerServiceBinder) service;
                musicService = playerServiceBinder.getService();
                isBound=true;
                mediaController = new MediaControllerCompat(MusicPlayerActivity.this, playerServiceBinder.getMediaSessionToken());
                mediaController.registerCallback(callback);
                callback.onPlaybackStateChanged(mediaController.getPlaybackState());
                // Вызов методов MusicService, которые нужны в активити
                musicService.setActivity(MusicPlayerActivity.this);
                //new Intent(MusicPlayerActivity.this, MusicService.class).putExtra("idmusic", 1);
                //startService( new Intent(MusicPlayerActivity.this, MusicService.class).putExtra("idmusic", idMusic));
                playerServiceBinder.SetPrepareMusic(idMusic);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                playerServiceBinder = null;
                isBound = false;
                if (mediaController != null) {
                    mediaController.unregisterCallback(callback);
                    mediaController = null;
                }
            }
        };
        bindService(new Intent(this, MusicService.class), serviceConnection, BIND_AUTO_CREATE);

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("d","play2222222222222222222222222");
                if (mediaController != null)
                {
                    mediaController.getTransportControls().play();
                    isPlaying = true;
                    updateUI();
                    updateSeekBar();
                }
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaController != null)
                {
                    mediaController.getTransportControls().pause();
                    isPlaying = false;
                    //updateUI(isPlaying);
                    handler.removeCallbacks(updater);
                    updateUI();
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaController != null)
                {
                    mediaController.getTransportControls().stop();
                    isPlaying = false;
                    //updateUI(isPlaying);
                    updateUI();
                }
            }
        });

        skipToNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaController != null)
                    mediaController.getTransportControls().skipToNext();
            }
        });

        skipToPreviousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaController != null)
                    mediaController.getTransportControls().skipToPrevious();
            }
        });

        next_10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                if(playerServiceBinder != null && mediaController != null){
                    Runnable next10=new Runnable() {
                        @Override
                        public void run() {
                            seek = playerServiceBinder.GetCurrentPosition() + (10 * 1000);
                            playerServiceBinder.SetPositionPlayer(seek);
                        }
                    };
                    Thread thread=new Thread(next10);
                    thread.start();
                }
            }
        });
        replay_10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                if(playerServiceBinder != null && mediaController != null){
                    Runnable replay10=new Runnable() {
                        @Override
                        public void run() {
                            seek = playerServiceBinder.GetCurrentPosition() - (10 * 1000);
                            playerServiceBinder.SetPositionPlayer(seek);
                        }
                    };
                    Thread thread=new Thread(replay10);
                    thread.start();
                }
            }
        });

        playerSeekBar.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent)
            {
                Runnable playerseekbar=new Runnable() {
                    @Override
                    public void run() {
                        SeekBar seekBar=(SeekBar) view;
                        int PlayerPosition=playerServiceBinder.GetDurationPlayer();
                        int playPosition = PlayerPosition * seekBar.getProgress();
                        playerServiceBinder.SetPositionPlayer(playPosition);
                    }
                };
                Thread thread=new Thread(playerseekbar);
                thread.start();
                return false;
            }
        });

        shuffle.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Toast message1=Toast.makeText(MusicPlayerActivity.this,"зашел",Toast.LENGTH_SHORT);
                message1.show();
                if(isRepeat)
                {
                    Toast message2=Toast.makeText(MusicPlayerActivity.this,"Cannot_select_random_music_repeat_on",Toast.LENGTH_SHORT);
                    message2.show();
                }
                else
                {
                    check=true;
                    if (isShuffle)
                    {
                        isShuffle=false;
                        shuffle.setImageResource(R.drawable.ic_shuffle);
                        //musicService.SetShuffle(isShuffle);
                        playerServiceBinder.SetShuffle(isShuffle);
                    }
                    else
                    {
                        isShuffle=true;
                        shuffle.setImageResource(R.drawable.ic_shuffle_selected);
                        //musicService.SetShuffle(isShuffle);
                        playerServiceBinder.SetShuffle(isShuffle);
                        Toast message3=Toast.makeText(MusicPlayerActivity.this,"зашел2",Toast.LENGTH_SHORT);
                        message3.show();
                    }
                }
          /*      Runnable Shuffle=new Runnable() {
                    @Override
                    public void run() {
                        if(isRepeat)
                        {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast message=Toast.makeText(MusicPlayerActivity.this,"Cannot_select_random_music_repeat_on",Toast.LENGTH_SHORT);
                                    message.show();
                                }
                            });
                        }
                        else
                        {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast message=Toast.makeText(MusicPlayerActivity.this,"зашел2",Toast.LENGTH_SHORT);
                                    message.show();
                                }
                            });
                            check=true;
                            Random r = new Random();
                            if (isShuffle)
                            {
                                isShuffle=false;
                                shuffle.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        shuffle.setBackgroundResource(R.drawable.ic_shuffle);
                                    }
                                });
                                musicService.SetShuffle(isShuffle);
                            }
                            else
                            {
                                isShuffle=true;
                             *//*   while (check)
                                {
                                    RandomIdMusic = r.nextInt(26 - 1) + 1;
                                    if(RandomIdMusic.equals(idMusic) || RandomIdMusic>=26)
                                    {

                                    }
                                    else
                                    {
                                        check=false;
                                        idMusic=RandomIdMusic;
                                        shuffle.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                shuffle.setBackgroundResource(R.drawable.ic_shuffle_selected);
                                            }
                                        });
                                    }
                                }*//*
                                shuffle.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        shuffle.setBackgroundResource(R.drawable.ic_shuffle_selected);
                                    }
                                });
                                musicService.SetShuffle(isShuffle);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast message=Toast.makeText(MusicPlayerActivity.this,"зашел2",Toast.LENGTH_SHORT);
                                        message.show();
                                    }
                                });
                            }
                        }
                    }
                };
                Thread thread=new Thread(Shuffle);
                thread.start();*/
            }
        });

        Repeat.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Runnable repeat=new Runnable() {
                    @Override
                    public void run() {
                        if(isShuffle)
                        {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast message=Toast.makeText(MusicPlayerActivity.this,"You_cant_turn_repeat_shuffle_music_on",Toast.LENGTH_SHORT);
                                    message.show();
                                }
                            });
                        }
                        else
                        {
                            if(isRepeat)//mediaPlayer.isLooping()musicService.GetisRepeat()
                            {
                                isRepeat=false;
                                //musicService.SetRepeat(isRepeat);
                                playerServiceBinder.SetRepeat(isRepeat);
                                //mediaPlayer.setLooping(false);
                                Repeat.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Repeat.setImageResource(R.drawable.ic_repeat);
                                    }
                                });
                            }
                            else
                            {
                                isRepeat=true;
                                playerServiceBinder.SetRepeat(isRepeat);
                                //musicService.SetRepeat(isRepeat);
                                //mediaPlayer.setLooping(true);
                                Repeat.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Repeat.setImageResource(R.drawable.ic_repeat_selected);
                                    }
                                });
                            }
                        }
                    }
                };
                Thread thread=new Thread(repeat);
                thread.start();
            }
        });
    }

    protected void updateUI() {
        if (mediaController != null && playerServiceBinder != null) {
            if (isPlaying) {
                playButton.setVisibility(View.GONE);
                pauseButton.setVisibility(View.VISIBLE);
            } else {
                //Log.d("d","updateui2222222222222222222222222");
                playButton.setVisibility(View.VISIBLE);
                pauseButton.setVisibility(View.GONE);
            }
        }
    }

    public Runnable updater = new Runnable()
    {
        @Override
        public void run() {
            updateSeekBar();
            int currentDuration = playerServiceBinder.GetCurrentPosition();
            textCurrentTime.setText(musicRepository.ConvertingTime(currentDuration));
        }
    };

    public void updateSeekBar() {
        if (isPlaying && playerServiceBinder != null) {
            Runnable updateseekbar = new Runnable() {
                @Override
                public void run() {
                    int currentPosition = playerServiceBinder.GetCurrentPosition();
                    if (currentPosition > 0) { // Проверяем, что позиция корректна
                        int progress = (int) (((float) currentPosition / TotalDur) * 100);
                        playerSeekBar.setProgress(progress);
                    }
                    handler.postDelayed(updater, 1000);
                }
            };
            Thread thread = new Thread(updateseekbar);
            thread.start();
        }
    }
    public void updateTextView(String TotalDuration,int lTotalDuration)
    {
        if (mediaController != null) {
            // Обновление текстовых полей
            textTotalDuration.setText(TotalDuration);
            Toast.makeText(this,"dsadsadsa",Toast.LENGTH_SHORT).show();
        }
        TotalDur=lTotalDuration;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        playerServiceBinder = null;
        if (mediaController != null) {
            mediaController.unregisterCallback(callback);
            mediaController = null;
        }
        if(isBound){
            unbindService(serviceConnection);
            isBound = false;
        }

    }
}