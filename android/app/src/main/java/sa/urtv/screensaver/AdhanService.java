package sa.urtv.screensaver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;

/**
 * Plays the adhan once (res/raw/adhan), as media so the TV's own volume applies, and asks other apps to pause
 * while it plays. A foreground service so it isn't killed halfway; it stops itself when the recording ends.
 */
public class AdhanService extends Service {
    static final String ACTION_STOP = "sa.urtv.screensaver.ADHAN_STOP";
    private static final String CHANNEL = "adhan";
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;

    static void play(Context c, String name) {
        Intent i = new Intent(c, AdhanService.class).putExtra("name", name);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    static void stop(Context c) {
        c.startService(new Intent(c, AdhanService.class).setAction(ACTION_STOP));
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        if (i != null && ACTION_STOP.equals(i.getAction())) { finish(); return START_NOT_STICKY; }
        foreground(i != null && i.getStringExtra("name") != null ? i.getStringExtra("name") : "");
        if (player != null) return START_NOT_STICKY;   // already playing
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audio != null) {
                if (Build.VERSION.SDK_INT >= 26) {
                    focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build();
                    audio.requestAudioFocus(focus);
                } else {
                    audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                }
            }
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(this, android.net.Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.adhan));
            player.setOnCompletionListener(mp -> finish());
            player.setOnErrorListener((mp, what, extra) -> { finish(); return true; });
            player.prepare();
            player.start();
        } catch (Exception e) {
            finish();
        }
        return START_NOT_STICKY;
    }

    private void foreground(String name) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "الأذان", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Notification n = b.setSmallIcon(R.drawable.ic_screensaver)
            .setContentTitle(name.isEmpty() ? "الأذان" : "أذان " + name).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(7, n);
    }

    private void finish() {
        if (player != null) { try { player.release(); } catch (Exception ignored) { } player = null; }
        if (audio != null) {
            if (Build.VERSION.SDK_INT >= 26 && focus != null) audio.abandonAudioFocusRequest(focus);
            else audio.abandonAudioFocus(null);
        }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (player != null) { try { player.release(); } catch (Exception ignored) { } player = null; }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
