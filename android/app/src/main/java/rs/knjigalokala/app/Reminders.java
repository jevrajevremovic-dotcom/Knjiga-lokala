package rs.knjigalokala.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reminders computed by the web app ({id, at, title, text}) are kept in preferences and scheduled with
 * AlarmManager. The receiver only gets an id and looks the text up here, so a foreign broadcast cannot
 * inject notification text.
 */
final class Reminders {
    static final String ACTION = "rs.knjigalokala.app.PODSETNIK";
    private static final String PREFS = "podsetnici";
    private static final String KEY_LIST = "lista";
    private static final String CHANNEL = "podsetnici";

    private Reminders() { }

    static void schedule(Context c, String json) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        forEach(p.getString(KEY_LIST, "[]"), o -> alarms(c).cancel(intent(c, o.getInt("id"))));
        p.edit().putString(KEY_LIST, json).apply();
        setAll(c, json);
    }

    static void reschedule(Context c) {
        setAll(c, c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, "[]"));
    }

    private static void setAll(Context c, String json) {
        long now = System.currentTimeMillis();
        forEach(json, o -> {
            long at = o.getLong("at");
            if (at > now) alarms(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(c, o.getInt("id")));
        });
    }

    static void show(Context c, int id) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        String json = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, "[]");
        JSONObject found = null;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) if (a.getJSONObject(i).getInt("id") == id) { found = a.getJSONObject(i); break; }
        } catch (Exception e) { return; }
        if (found == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Podsetnici", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Rate, rokovi računa, mesečni troškovi i zadaci");
            c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder n = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(found.optString("title", "Knjiga lokala"))
                .setContentText(found.optString("text", ""))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(found.optString("text", "")))
                .setContentIntent(tap)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(c).notify(id, n.build());
        } catch (SecurityException ignored) { }
    }

    private static AlarmManager alarms(Context c) {
        return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent intent(Context c, int id) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION).putExtra("id", id);
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private interface Each { void run(JSONObject o) throws Exception; }

    private static void forEach(String json, Each f) {
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                try { f.run(a.getJSONObject(i)); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }
}
