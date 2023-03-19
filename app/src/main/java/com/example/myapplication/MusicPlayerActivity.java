package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

public class MusicPlayerActivity extends AppCompatActivity {

    public static final String Broadcast_PLAY_NEW_AUDIO = "com.example.myapplication.PlayNewAudio";
    private ImageButton playButton;
    private ImageButton pauseButton;
    private TextView songTitleTextView;
    private TextView artistTextView;
    private SeekBar seekBar;

    private boolean isPlaying = false;
    private MusicService musicService;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            MusicService.LocalBinder musicBinder = (MusicService.LocalBinder) iBinder;
            musicService = musicBinder.getService();
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        playButton = findViewById(R.id.play_button);
        pauseButton = findViewById(R.id.pause_button);
        songTitleTextView = findViewById(R.id.song_title_text_view);
        artistTextView = findViewById(R.id.artist_text_view);
        seekBar = findViewById(R.id.seek_bar);

        // Bind to the MusicService
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Set the click listeners for the buttons
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (musicService != null) {
                    musicService.togglePlayPause();
                    isPlaying = true;
                    updateUI();
                }
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (musicService != null) {
                    musicService.togglePlayPause();
                    isPlaying = false;
                    updateUI();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }

    private void updateUI() {
        if (musicService != null) {
        /*    Song currentSong = musicService.getCurrentSong();
            if (currentSong != null) {
                songTitleTextView.setText(currentSong.getTitle());
                artistTextView.setText(currentSong.getArtist());
                //seekBar.setMax(currentSong.getDuration());
                seekBar.setProgress(musicService.getCurrentSongIndex());
            }*/
            //seekBar.setProgress(musicService.getCurrentSongIndex());

            if (isPlaying) {
                playButton.setVisibility(View.GONE);
                pauseButton.setVisibility(View.VISIBLE);
            } else {
                playButton.setVisibility(View.VISIBLE);
                pauseButton.setVisibility(View.GONE);
            }
        }
    }
}