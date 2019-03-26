package org.dhis2.data.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.google.firebase.perf.metrics.AddTrace;

import org.dhis2.App;
import org.dhis2.R;
import org.dhis2.utils.Constants;
import org.dhis2.utils.DateUtils;

import java.util.Calendar;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import timber.log.Timber;

/**
 * QUADRAM. Created by ppajuelo on 23/10/2018.
 */

public class SyncMetadataWorker extends Worker {

    private static final String METADATA_CHANNEL = "sync_metadata_notification";
    private static final int SYNC_METADATA_ID = 26061987;

    @Inject
    SyncPresenter presenter;

    public SyncMetadataWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public void onStopped(boolean cancelled) {
        super.onStopped(cancelled);
        Timber.d("Metadata process finished");
    }

    @NonNull
    @Override
    @AddTrace(name = "MetadataSyncTrace")
    public Result doWork() {
        if (((App) getApplicationContext()).userComponent() != null) {

            ((App) getApplicationContext()).userComponent().plus(new SyncMetadataWorkerModule()).inject(this);

            triggerNotification(SYNC_METADATA_ID,
                    getApplicationContext().getString(R.string.app_name),
                    getApplicationContext().getString(R.string.syncing_configuration));
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(new Intent("action_sync").putExtra("metaSyncInProgress", true));

            boolean isMetaOk = true;

            try {
                presenter.syncMetadata(getApplicationContext());
            } catch (Exception e) {
                Timber.e(e);
                isMetaOk = false;
            }

            String lastDataSyncDate = DateUtils.dateTimeFormat().format(Calendar.getInstance().getTime());

            SharedPreferences prefs = getApplicationContext().getSharedPreferences(Constants.SHARE_PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(Constants.LAST_META_SYNC, lastDataSyncDate).apply();
            prefs.edit().putBoolean(Constants.LAST_META_SYNC_STATUS, isMetaOk).apply();

            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(new Intent("action_sync").putExtra("metaSyncInProgress", false));

            cancelNotification();

            return Result.SUCCESS;
        } else {
            return Result.FAILURE;
        }
    }

    private void triggerNotification(int id, String title, String content) {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(METADATA_CHANNEL, "MetadataSync", NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(mChannel);
            }
        }
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(getApplicationContext(), METADATA_CHANNEL)
                        .setSmallIcon(R.drawable.ic_sync)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setAutoCancel(false)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (notificationManager != null) {
            notificationManager.notify(id, notificationBuilder.build());
        }
    }

    private void cancelNotification() {
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(getApplicationContext());
        notificationManager.cancel(SYNC_METADATA_ID);
    }
}
