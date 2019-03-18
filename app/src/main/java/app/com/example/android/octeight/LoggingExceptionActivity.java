package app.com.example.android.octeight;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.Date;

import static app.com.example.android.octeight.Utils.appendToFile;
import static app.com.example.android.octeight.Utils.getAppVersionNumber;

public class LoggingExceptionActivity extends AppCompatActivity implements Thread.UncaughtExceptionHandler {

    private final static String TAG = LoggingExceptionActivity.class.getSimpleName() + "_LOG";
    private final Context context;
    private final Thread.UncaughtExceptionHandler rootHandler;


    public LoggingExceptionActivity(Context context) {
        this.attachBaseContext(context);
        this.context = context;
        // we should store the current exception handler -- to invoke it for all not handled exceptions ...
        rootHandler = Thread.getDefaultUncaughtExceptionHandler();
        // we replace the exception handler now with us -- we will properly dispatch the exceptions ...
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(final Thread thread, final Throwable ex) {
        try {
            Log.d(TAG, "called for " + ex.getClass());
            // assume we would write each error in one file ...

            // log this exception ...

            String stackTrace = "";
            for (int i = 0; i < ex.getStackTrace().length; i++) {
                stackTrace += ex.getStackTrace()[i] + "\n";
            }
            String causeTrace = "";
            if (ex.getCause() != null) {
                for (int i = 0; i < ex.getCause().getStackTrace().length; i++) {
                    stackTrace += ex.getCause().getStackTrace()[i] + "\n";
                }
            }
            String fileInfoLine = getAppVersionNumber(context) + "#1" + System.lineSeparator();

            String errorReport = "Exception in: " + context.getClass().getName()
                    + " " + ex.getClass().getName() + "\n" +
                    ex.getMessage() + "\n" +
                    stackTrace + "\n" +
                    causeTrace + "\n" +
                    System.currentTimeMillis() + "\n" +
                    Build.VERSION.RELEASE + "\n" +
                    Build.DEVICE + "\n" +
                    Build.MODEL + "\n" +
                    Build.PRODUCT;

            appendToFile((fileInfoLine + errorReport), "CRASH_REPORT" + new Date().toString() + ".txt", this);

            SharedPreferences sharedPrefs = getApplicationContext()
                    .getSharedPreferences("simraPrefs", Context.MODE_PRIVATE);

            SharedPreferences.Editor editor = sharedPrefs.edit();

            editor.putBoolean("NEW-UNSENT-ERROR", true);
            editor.apply();

            restartApp();

        } catch (Exception e) {
            Log.e(TAG, "Exception Logger failed!", e);
        }

    }

    private void restartApp() {
        Intent intent = new Intent(getApplicationContext(), StartActivity.class);
        int mPendingIntentId = 1337;
        PendingIntent mPendingIntent = PendingIntent.getActivity(getApplicationContext(), mPendingIntentId, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        System.exit(0);
    }
}