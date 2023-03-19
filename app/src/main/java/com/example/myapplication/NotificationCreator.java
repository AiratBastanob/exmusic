package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationCreator {

    public static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID = "MusicPlayer";

    private Context context;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    public NotificationCreator(Context context) {
        this.context = context;

        // Create notification manager
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create notification channel for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Music Player",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Music Player notification channel");
            notificationManager.createNotificationChannel(channel);
        }

        // Create notification builder
        notificationBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Music Player")
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
    }

    public Notification createNotification(Song song) {
        // Set notification text
        notificationBuilder.setContentText(song.getTitle());
        // Set play/pause action
        int icon = ((MusicService) context).isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        String actionText = ((MusicService) context).isPlaying() ? "Pause" : "Play";
        PendingIntent pendingIntent = PendingIntent.getService(
                context,
                0,
                new Intent(context, MusicService.class).setAction(MusicService.ACTION_TOGGLE_PLAYBACK),
                PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.addAction(icon, actionText, pendingIntent);
        // Show notification
        return notificationBuilder.build();
    }

    public void showNotification(Notification notification) {
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    public void cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID);
    }
}
