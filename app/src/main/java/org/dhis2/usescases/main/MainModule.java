package org.dhis2.usescases.main;


import org.dhis2.data.dagger.PerActivity;
import org.dhis2.data.metadata.MetadataRepository;
import org.hisp.dhis.android.core.D2;

import androidx.annotation.NonNull;
import dagger.Module;
import dagger.Provides;

@Module
public final class MainModule {

    @Provides
    @PerActivity
    MainContracts.View homeView(MainActivity activity) {
        return activity;
    }


    @Provides
    @PerActivity
    MainContracts.Presenter homePresenter(D2 d2, @NonNull MetadataRepository metadataRepository) {
        return new MainPresenter(d2, metadataRepository);
    }

}