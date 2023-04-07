package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

public class StorageSettingPlayer
{
    private final String STORAGE = "com.example.myapplication.STORAGE";
    private SharedPreferences preferences;
    private Context context;

    public StorageSettingPlayer(Context context) {
        this.context = context;
    }

    public void storeAudio(Boolean setting, String nameSetting) {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(nameSetting, setting);
        editor.apply();
    }

    public void storeAudioIndex(int index) {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("audioIndex", index);
        editor.apply();
    }

    public Boolean loadAudio(String nameSetting) {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        return preferences.getBoolean(nameSetting, false);
    }

    public int loadAudioIndex() {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        return preferences.getInt("audioIndex", -1);//return -1 if no data found
    }

    public void clearCachedAudioPlaylist() {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
    }
}
