package sa.urtv.screensaver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * The adhan at each prayer time: one exact alarm for the next prayer, re-armed after it fires, after a reboot,
 * when the clock or time zone changes, and whenever settings are shown. Times come from PrayerTimes (no network).
 */
public class AdhanReceiver extends BroadcastReceiver {
    static final String ACTION_ADHAN = "sa.urtv.screensaver.ADHAN";
    private static final String EXTRA_AT = "at", EXTRA_NAME = "name";
    /** an alarm delivered later than this (TV was off, then woke) is skipped, not played late */
    private static final long LATE_MS = 3 * 60 * 1000;

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION_ADHAN.equals(i.getAction())) {
            long at = i.getLongExtra(EXTRA_AT, 0);
            if (new Prefs(c).adhan() && Math.abs(System.currentTimeMillis() - at) < LATE_MS)
                AdhanService.play(c, i.getStringExtra(EXTRA_NAME));
        }
        schedule(c);
    }

    /** Arms the alarm for the next prayer, or cancels it when the adhan is off. */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi;
        Prefs prefs = new Prefs(c);
        long[] next = next(prefs, System.currentTimeMillis());
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACTION_ADHAN)
            .putExtra(EXTRA_AT, next[0]).putExtra(EXTRA_NAME, PrayerTimes.NAMES[(int) next[1]]);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        pi = PendingIntent.getBroadcast(c, 0, i, flags);
        if (!prefs.adhan()) { am.cancel(pi); return; }
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (Build.VERSION.SDK_INT >= 23) {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next[0], pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next[0], pi);
        } else if (Build.VERSION.SDK_INT >= 19) {
            am.setExact(AlarmManager.RTC_WAKEUP, next[0], pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, next[0], pi);
        }
    }

    /** {epoch millis, index in PrayerTimes.KEYS} of the first prayer after now */
    static long[] next(Prefs prefs, long now) {
        String[] city = prefs.city();
        double lat = Double.parseDouble(city[1]), lon = Double.parseDouble(city[2]);
        TimeZone zone = TimeZone.getTimeZone(Prefs.TZ);
        Calendar day = Calendar.getInstance(zone);
        day.setTimeInMillis(now);
        for (int k = 0; k < 2; k++) {
            int y = day.get(Calendar.YEAR), mo = day.get(Calendar.MONTH) + 1, d = day.get(Calendar.DAY_OF_MONTH);
            int[] m = PrayerTimes.minutes(y, mo, d, lat, lon, zone);
            for (int p = 0; p < m.length; p++) {
                Calendar at = Calendar.getInstance(zone);
                at.clear();
                at.set(y, mo - 1, d, m[p] / 60, m[p] % 60, 0);
                if (at.getTimeInMillis() > now + 1000) return new long[] {at.getTimeInMillis(), p};
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return new long[] {now + 3600000L, 0};   // never reached: tomorrow always has a Fajr
    }
}
