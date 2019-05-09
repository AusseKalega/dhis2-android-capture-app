package org.dhis2.data.service;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.dhis2.utils.Constants;
import org.hisp.dhis.android.core.D2;

import io.reactivex.Completable;
import io.reactivex.disposables.CompositeDisposable;

final class SyncPresenterImpl implements SyncPresenter {

    @NonNull
    private final D2 d2;

    private CompositeDisposable disposable;

    SyncPresenterImpl(@NonNull D2 d2) {
        this.d2 = d2;
        this.disposable = new CompositeDisposable();
    }

    @Override
    public void syncAndDownloadEvents(Context context) throws SyncError {
        try {
            d2.eventModule().events.upload().call();
            SharedPreferences prefs = context.getSharedPreferences(
                    Constants.SHARE_PREFS, Context.MODE_PRIVATE);
            int eventLimit = prefs.getInt(Constants.EVENT_MAX, Constants.EVENT_MAX_DEFAULT);
            boolean limityByOU = prefs.getBoolean(Constants.LIMIT_BY_ORG_UNIT, false);
            d2.eventModule().downloadSingleEvents(eventLimit, limityByOU).call();
        } catch (Exception e) {
            throw new SyncError();
        }
    }

    @Override
    public void syncAndDownloadTeis(Context context) throws Exception {
        d2.trackedEntityModule().trackedEntityInstances.upload().call();
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.SHARE_PREFS, Context.MODE_PRIVATE);
        int teiLimit = prefs.getInt(Constants.TEI_MAX, Constants.TEI_MAX_DEFAULT);
        boolean limityByOU = prefs.getBoolean(Constants.LIMIT_BY_ORG_UNIT, false);
        Completable.fromObservable(d2.trackedEntityModule().downloadTrackedEntityInstances(teiLimit, limityByOU).asObservable()).blockingAwait();
    }

    @Override
    public void syncMetadata(Context context) throws SyncError {
        try {
            d2.syncMetaData().call();
        } catch (Exception e) {
            throw new SyncError();
        }
    }

    @Override
    public void syncReservedValues() {
        d2.trackedEntityModule().reservedValueManager.syncReservedValues(null, null, 100);
    }
}
