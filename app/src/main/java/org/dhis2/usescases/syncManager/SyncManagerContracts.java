package org.dhis2.usescases.syncManager;

import org.dhis2.data.tuples.Pair;
import org.dhis2.usescases.general.AbstractActivityContracts;
import org.hisp.dhis.android.core.imports.TrackerImportConflict;

import java.util.List;

import io.reactivex.functions.Consumer;

/**
 * QUADRAM. Created by lmartin on 21/03/2018.
 */

public class SyncManagerContracts {

    interface SyncManagerView extends AbstractActivityContracts.View {

        Consumer<Pair<Integer, Integer>> setSyncData();

        void wipeDatabase();

        void deleteLocalData();

        void showTutorial();

        void showSyncErrors(List<TrackerImportConflict> data);

        void showLocalDataDeleted(boolean error);

        void syncData();

        void syncMeta();
    }

    public interface SyncManagerPresenter {

        void init(SyncManagerView view);

        void syncData(int seconds, String scheduleTag);

        void syncMeta(int seconds, String scheduleTag);

        void syncData();

        void syncMeta();

        void disponse();

        void resetSyncParameters();

        void onWipeData();

        void wipeDb();

        void onDeleteLocalData();

        void deleteLocalData();

        void onReservedValues();

        void checkSyncErrors();

        void checkData();

        void cancelPendingWork(String meta);

        boolean dataHasErrors();

        boolean dataHasWarnings();
    }
}
