package org.dhis2.usescases.eventsWithoutRegistration.eventSummary;

import android.content.Context;
import androidx.annotation.NonNull;

import org.dhis2.data.dagger.PerActivity;
import org.dhis2.data.forms.EventRepository;
import org.dhis2.data.forms.FormRepository;
import org.dhis2.data.forms.RulesRepository;
import org.dhis2.data.metadata.MetadataRepository;
import org.dhis2.data.schedulers.SchedulerProvider;
import com.squareup.sqlbrite2.BriteDatabase;

import org.hisp.dhis.android.core.D2;
import org.hisp.dhis.rules.RuleExpressionEvaluator;

import dagger.Module;
import dagger.Provides;

/**
 * Created by Cristian on 13/02/2018.
 *
 */
@PerActivity
@Module
public class EventSummaryModule {

    @NonNull
    private Context context;

    @NonNull
    private String eventUid;

    EventSummaryModule(@NonNull Context context, @NonNull String eventUid) {
        this.context = context;
        this.eventUid = eventUid;
    }

    @Provides
    @PerActivity
    EventSummaryContract.EventSummaryView provideView(EventSummaryContract.EventSummaryView activity) {
        return activity;
    }

    @Provides
    @PerActivity
    EventSummaryContract.EventSummaryPresenter providesPresenter(EventSummaryContract.EventSummaryInteractor interactor) {
        return new EventSummaryPresenterImpl(interactor);
    }

    @Provides
    @PerActivity
    EventSummaryContract.EventSummaryInteractor provideInteractor(@NonNull EventSummaryRepository eventSummaryRepository,
                                                                  @NonNull MetadataRepository metadataRepository,
                                                                  @NonNull SchedulerProvider schedulerProvider) {
        return new EventSummaryInteractorImpl(eventSummaryRepository, metadataRepository, schedulerProvider);
    }

    @Provides
    @PerActivity
    EventSummaryRepository eventSummaryRepository(@NonNull Context context,
                                                  @NonNull BriteDatabase briteDatabase,
                                                  @NonNull FormRepository formRepository,D2 d2) {
        return new EventSummaryRepositoryImpl(context, briteDatabase, formRepository, eventUid,d2);
    }

    @Provides
    FormRepository formRepository(@NonNull BriteDatabase briteDatabase,
                                  @NonNull RuleExpressionEvaluator evaluator,
                                  @NonNull RulesRepository rulesRepository,
                                  @NonNull D2 d2) {
        return new EventRepository(briteDatabase, evaluator, rulesRepository, eventUid,d2);
    }

    @Provides
    RulesRepository rulesRepository(@NonNull BriteDatabase briteDatabase) {
        return new RulesRepository(briteDatabase);
    }
}
