package com.example.myapplication;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

public class SongProvider {
    public static ArrayList<Song> getSongList(Context context) {
        Song song;
        ArrayList<Song> songList = new ArrayList<>();
 /*       song = new Song("Call Me Remastered", "Artist 1",null, "https://ru.hitmotop.com/get/music/20200823/Blondie_-_Call_Me_Remastered_Remastered_70684203.mp3", 3.26);
        songList.add(song);
        song = new Song("Mykonos", "Artist 1",null, "https://ru.hitmotop.com/get/music/20200823/Blondie_-_Call_Me_Remastered_Remastered_70684203.mp3", 3.26);
        songList.add(song);*/
        return songList;
    }
}

