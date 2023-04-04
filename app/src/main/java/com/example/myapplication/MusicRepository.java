package com.example.myapplication;

public class MusicRepository {

    String timestring,secondsString;

    public Song[] data = {
            new Song("Triangle", "Jason Shaw", R.drawable.image266680,                   "https://codeskulptor-demos.commondatastorage.googleapis.com/pang/paza-moduless.mp3", null),//(3 * 60 + 41) * 1000
            new Song("Rubix Cube", "Jason Shaw", R.drawable.image396168,                 "https://codeskulptor-demos.commondatastorage.googleapis.com/descent/background%20music.mp3", null),//(3 * 60 + 44) * 1000
            new Song("MC Ballad S Early Eighties", "Frank Nora", R.drawable.image533998, "https://commondatastorage.googleapis.com/codeskulptor-assets/sounddogs/soundtrack.mp3", null),//(2 * 60 + 50) * 1000
            new Song("Folk Song", "Brian Boyko", R.drawable.image544064,                 "https://commondatastorage.googleapis.com/codeskulptor-demos/riceracer_assets/music/lose.ogg", null),//(3 * 60 + 5) * 1000
            new Song("Morning Snowflake", "Kevin MacLeod", R.drawable.image208815,       "https://commondatastorage.googleapis.com/codeskulptor-demos/riceracer_assets/music/race1.ogg", null),//(2 * 60 + 0) * 1000
    };

    private final int maxIndex = data.length - 1;
    private int currentItemIndex = 0;

    Song getNext() {
        if (currentItemIndex == maxIndex)
            currentItemIndex = 0;
        else
            currentItemIndex++;
        return getCurrent();
    }

    Song getPrevious() {
        if (currentItemIndex == 0)
            currentItemIndex = maxIndex;
        else
            currentItemIndex--;
        return getCurrent();
    }

    Song getCurrent() {
        return data[currentItemIndex];
    }
    void setIdUserMusic(int IdUserMusic)
    {
        currentItemIndex = IdUserMusic;
    }
    String ConvertingTime(int milliSeconds) {
        timestring = "";
        int hours = (int) (milliSeconds / (1000 * 60 * 60));
        int minutes = (int) (milliSeconds % (1000 * 60 * 60)) / (1000 * 60);
        int seconds = (int) ((milliSeconds % (1000 * 60 * 60)) % (1000 * 60) / 1000);
        if (hours > 0) {
            timestring = hours + ":";
        }
        if (seconds < 10) {
            secondsString = "0" + seconds;
        } else {
            secondsString = "" + seconds;

        }
        timestring = timestring + minutes + ":" + secondsString;
        return timestring;
    }
    static class Song
    {
        private int bitmapResId;
        private String title;
        private String artist;
        private String album;
        private String MusicPath;
        private Long duration;

        public Song()
        {}

        public Song(String title, String artist, int bitmapResId, String MusicPath, Long duration) {
            this.title = title;
            this.artist = artist;
            this.MusicPath = MusicPath;
            this.duration=duration;
            this.bitmapResId = bitmapResId;
        }

        public Song(String title, String artist, int bitmapResId, String MusicPath, Long duration, String album) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.MusicPath = MusicPath;
            this.duration=duration;
            this.bitmapResId = bitmapResId;
        }

        public String getTitle() {
            return title;
        }

        public String getArtist() {
            return artist;
        }

        public String getAlbum() {
            return album;
        }

        public String getMusicPath() {
            return MusicPath;
        }
        public int getBitmapResId() {
            return bitmapResId;
        }
        public Long getDuration() {
            return duration;
        }
    }
}
