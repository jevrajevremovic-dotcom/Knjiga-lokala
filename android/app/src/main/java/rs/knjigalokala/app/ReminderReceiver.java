package rs.knjigalokala.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Shows a due reminder, and re-arms all reminders after a reboot or an app update (alarms do not survive those). */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Reminders.ACTION.equals(action)) {
            Reminders.show(context, intent.getIntExtra("id", 0));
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Reminders.reschedule(context);
        }
    }
}
