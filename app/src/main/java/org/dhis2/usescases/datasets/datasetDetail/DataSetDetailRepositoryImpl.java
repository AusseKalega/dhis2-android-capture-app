package org.dhis2.usescases.datasets.datasetDetail;

import android.database.Cursor;

import com.squareup.sqlbrite2.BriteDatabase;

import org.dhis2.utils.DateUtils;
import org.dhis2.utils.SqlConstants;
import org.hisp.dhis.android.core.common.State;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit;
import org.hisp.dhis.android.core.period.Period;
import org.hisp.dhis.android.core.period.PeriodType;

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;

import static org.dhis2.utils.SqlConstants.SELECT_ALL_FROM;

public class DataSetDetailRepositoryImpl implements DataSetDetailRepository {

    private static final String GET_DATA_SETS = "SELECT " +
            "DataValue.organisationUnit, " +
            "DataValue.period, " +
            "DataValue.attributeOptionCombo " +
            "FROM DataValue " +
            "JOIN DataSetDataElementLink " +
            "ON DataSetDataElementLink.dataElement = DataValue.dataElement " +
            "WHERE DataSetDataElementLink.dataSet = ? %s " +
            "GROUP BY DataValue.period,DataValue.organisationUnit,DataValue.categoryOptionCombo";

    private static final String DATA_SETS_ORG_UNIT_FILTER = "AND DataValue.organisationUnit IN (%s) ";

    private final BriteDatabase briteDatabase;

    public DataSetDetailRepositoryImpl(BriteDatabase briteDatabase) {
        this.briteDatabase = briteDatabase;
    }


    @NonNull
    @Override
    public Observable<List<OrganisationUnit>> orgUnits() {
        String selectOrgUnits = SELECT_ALL_FROM + SqlConstants.ORG_UNIT_TABLE;
        return briteDatabase.createQuery(SqlConstants.ORG_UNIT_TABLE, selectOrgUnits)
                .mapToList(OrganisationUnit::create);
    }

    @Override
    public Flowable<List<DataSetDetailModel>> dataSetGroups(String dataSetUid, List<String> orgUnits,
                                                            PeriodType selectedPeriodType, int page) {
        String sql = GET_DATA_SETS;
        String orgUnitFilter = "";
        if (orgUnits != null && !orgUnits.isEmpty()) {
            StringBuilder orgUnitUids = new StringBuilder("");
            for (int i = 0; i < orgUnits.size(); i++) {
                orgUnitUids.append(orgUnits.get(i));
                if (i != orgUnits.size() - 1)
                    orgUnitUids.append(",");
            }

            orgUnitFilter = String.format(DATA_SETS_ORG_UNIT_FILTER, orgUnitFilter);
        }

        sql = String.format(sql, orgUnitFilter);

        return briteDatabase.createQuery(SqlConstants.DATA_VALUE_TABLE, sql, dataSetUid)
                .mapToList(cursor -> {
                    String organisationUnitUid = cursor.getString(0);
                    String period = cursor.getString(1);
                    String categoryOptionCombo = cursor.getString(2);

                    String orgUnitName = "";
                    String periodName = "";
                    String catOptCombName = "";
                    State state = State.SYNCED;
                    try (Cursor orgUnitCursor = briteDatabase.query("SELECT OrganisationUnit.displayName FROM OrganisationUnit WHERE uid = ?", organisationUnitUid)) {
                        if (orgUnitCursor != null && orgUnitCursor.moveToFirst()) {
                            orgUnitName = orgUnitCursor.getString(0);
                        }
                    }

                    try (Cursor periodCursor = briteDatabase.query("SELECT Period.* FROM Period WHERE Period.periodId = ?", period)) {
                        if (periodCursor != null && periodCursor.moveToFirst()) {
                            Period periodModel = Period.create(periodCursor);
                            periodName = DateUtils.getInstance().getPeriodUIString(periodModel.periodType(),
                                    periodModel.startDate(), Locale.getDefault());
                        }
                    }

                    try (Cursor catOptCombCursor = briteDatabase.query("SELECT CategoryOptionCombo.displayName FROM CategoryOptionCombo WHERE uid = ?", categoryOptionCombo)) {
                        if (catOptCombCursor != null && catOptCombCursor.moveToFirst()) {
                            catOptCombName = catOptCombCursor.getString(0);
                        }
                    }

                    try (Cursor stateCursor = briteDatabase.query("SELECT DataValue.state FROM DataValue " +
                                    "WHERE period = ? AND organisationUnit = ? AND attributeOptionCombo = ? " +
                                    "AND state != 'SYNCED'",
                            period, organisationUnitUid, categoryOptionCombo)) {
                        if (stateCursor != null && stateCursor.moveToFirst()) {
                            State errorState = null;
                            State toPost = null;
                            State toUpdate = null;
                            for (int i = 0; i < cursor.getCount(); i++) {
                                State stateValue = State.valueOf(cursor.getString(0));
                                switch (stateValue) {
                                    case ERROR:
                                        errorState = State.ERROR;
                                        break;
                                    case TO_POST:
                                        toPost = State.TO_POST;
                                        break;
                                    case TO_UPDATE:
                                        toUpdate = State.TO_UPDATE;
                                        break;
                                    default:
                                        break;
                                }
                                cursor.moveToNext();
                            }

                            if (errorState != null)
                                state = errorState;
                            else if (toUpdate != null)
                                state = toUpdate;
                            else if (toPost != null)
                                state = toPost;
                        }
                    }

                    return DataSetDetailModel.create(organisationUnitUid, categoryOptionCombo, period, orgUnitName, catOptCombName, periodName, state);
                }).toFlowable(BackpressureStrategy.LATEST);
    }
}
