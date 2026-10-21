package sa.urtv.screensaver;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Today's prayer times on the TV itself, no network: the same Kuwait method as the page's offline
 * fallback (src/template.html, computePrayers): Fajr at 18°, Dhuhr at noon, Asr at shadow factor 1,
 * Maghrib at sunset, Isha at 17.5°. Matches Aladhan's method 9 to a minute or two.
 */
final class PrayerTimes {
    static final String[] KEYS = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
    static final String[] NAMES = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};

    private PrayerTimes() {}

    /** Minutes after local midnight, in KEYS order, for the given local day. */
    static int[] minutes(int y, int mo, int d, double lat, double lng, TimeZone zone) {
        Calendar noonCal = Calendar.getInstance(zone);
        noonCal.clear();
        noonCal.set(y, mo - 1, d, 12, 0, 0);
        final double tz = zone.getOffset(noonCal.getTimeInMillis()) / 3600000.0;

        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(y, mo - 1, d, 0, 0, 0);
        final double jd0 = utc.getTimeInMillis() / 86400000.0 + 2440587.5 - lng / (15 * 24);

        double fajr = angleTime(jd0, lat, 18, 5, true) + tz - lng / 15;
        double dhuhr = noon(jd0, 12) + tz - lng / 15;
        double asr = asrTime(jd0, lat, 13) + tz - lng / 15;
        double maghrib = angleTime(jd0, lat, .833, 18, false) + tz - lng / 15;
        double isha = angleTime(jd0, lat, 17.5, 18, false) + tz - lng / 15;
        double[] h = {fajr, dhuhr, asr, maghrib, isha};
        int[] out = new int[5];
        for (int i = 0; i < 5; i++) out[i] = (int) (Math.round(fix(h[i], 24) * 60) % 1440);
        return out;
    }

    private static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }

    /** {declination, equation of time} for day fraction t */
    private static double[] sun(double jd0, double t) {
        double D = jd0 + t - 2451545;
        double g = fix(357.529 + .98560028 * D, 360), q = fix(280.459 + .98564736 * D, 360);
        double L = fix(q + 1.915 * Math.sin(Math.toRadians(g)) + .020 * Math.sin(Math.toRadians(2 * g)), 360);
        double e = 23.439 - .00000036 * D;
        double RA = fix(Math.toDegrees(Math.atan2(Math.cos(Math.toRadians(e)) * Math.sin(Math.toRadians(L)),
                                                  Math.cos(Math.toRadians(L)))) / 15, 24);
        double decl = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(e)) * Math.sin(Math.toRadians(L))));
        return new double[] {decl, q / 15 - RA};
    }

    private static double noon(double jd0, double t) { return fix(12 - sun(jd0, t / 24)[1], 24); }

    private static double angleTime(double jd0, double lat, double angle, double t, boolean ccw) {
        double d = sun(jd0, t / 24)[0], n = noon(jd0, t);
        double T = Math.toDegrees(Math.acos((-Math.sin(Math.toRadians(angle)) - Math.sin(Math.toRadians(d)) * Math.sin(Math.toRadians(lat)))
                                            / (Math.cos(Math.toRadians(d)) * Math.cos(Math.toRadians(lat))))) / 15;
        return n + (ccw ? -T : T);
    }

    private static double asrTime(double jd0, double lat, double t) {
        double d = sun(jd0, t / 24)[0];
        double a = -Math.toDegrees(Math.atan(1 / (1 + Math.tan(Math.toRadians(Math.abs(lat - d))))));
        return angleTime(jd0, lat, a, t, false);
    }
}
