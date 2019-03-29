package org.dhis2.data.forms;

import android.content.ContentValues;
import android.database.Cursor;

import com.google.android.gms.maps.model.LatLng;
import com.squareup.sqlbrite2.BriteDatabase;

import org.dhis2.data.forms.dataentry.fields.FieldViewModel;
import org.dhis2.data.forms.dataentry.fields.FieldViewModelFactoryImpl;
import org.dhis2.data.forms.dataentry.fields.FieldViewModelHelper;
import org.dhis2.data.tuples.Pair;
import org.dhis2.data.tuples.Trio;
import org.dhis2.utils.CodeGenerator;
import org.dhis2.utils.Constants;
import org.dhis2.utils.DateUtils;
import org.dhis2.utils.SqlConstants;
import org.hisp.dhis.android.core.category.CategoryCombo;
import org.hisp.dhis.android.core.category.CategoryOptionCombo;
import org.hisp.dhis.android.core.common.ObjectStyle;
import org.hisp.dhis.android.core.common.State;
import org.hisp.dhis.android.core.common.ValueTypeDeviceRendering;
import org.hisp.dhis.android.core.enrollment.Enrollment;
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus;
import org.hisp.dhis.android.core.event.Event;
import org.hisp.dhis.android.core.event.EventStatus;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit;
import org.hisp.dhis.android.core.period.PeriodType;
import org.hisp.dhis.android.core.program.Program;
import org.hisp.dhis.android.core.program.ProgramStage;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityType;
import org.hisp.dhis.rules.RuleEngine;
import org.hisp.dhis.rules.RuleEngineContext;
import org.hisp.dhis.rules.RuleExpressionEvaluator;
import org.hisp.dhis.rules.models.TriggerEnvironment;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.exceptions.OnErrorNotImplementedException;
import io.reactivex.functions.Consumer;
import timber.log.Timber;

import static android.text.TextUtils.isEmpty;
import static org.dhis2.utils.SqlConstants.EQUAL;
import static org.dhis2.utils.SqlConstants.FROM;
import static org.dhis2.utils.SqlConstants.LIMIT_1;
import static org.dhis2.utils.SqlConstants.QUESTION_MARK;
import static org.dhis2.utils.SqlConstants.SELECT;
import static org.dhis2.utils.SqlConstants.TABLE_FIELD_EQUALS;
import static org.dhis2.utils.SqlConstants.WHERE;

@SuppressWarnings({
        "PMD.AvoidDuplicateLiterals"
})
public class EnrollmentFormRepository implements FormRepository {
    private static final List<String> TITLE_TABLES = Arrays.asList(
            SqlConstants.ENROLLMENT_TABLE, SqlConstants.PROGRAM_TABLE);

    private static final String SELECT_TITLE = "SELECT Program.displayName\n" +
            FROM + SqlConstants.ENROLLMENT_TABLE + "\n" +
            "  JOIN Program ON Enrollment.program = Program.uid\n" +
            WHERE + SqlConstants.ENROLLMENT_TABLE + "." + SqlConstants.ENROLLMENT_UID + EQUAL + QUESTION_MARK +
            LIMIT_1;

    private static final String SELECT_ENROLLMENT_UID = "SELECT Enrollment.uid\n" +
            FROM + SqlConstants.ENROLLMENT_TABLE + "\n" +
            WHERE + SqlConstants.ENROLLMENT_TABLE + "." + SqlConstants.ENROLLMENT_UID + EQUAL + QUESTION_MARK;

    private static final String SELECT_ENROLLMENT_STATUS = "SELECT Enrollment.status\n" +
            FROM + SqlConstants.ENROLLMENT_TABLE + "\n" +
            WHERE + SqlConstants.ENROLLMENT_TABLE + "." + SqlConstants.ENROLLMENT_UID + EQUAL + QUESTION_MARK +
            LIMIT_1;

    private static final String SELECT_ENROLLMENT_DATE = "SELECT Enrollment.*\n" +
            FROM + SqlConstants.ENROLLMENT_TABLE + "\n" +
            WHERE + SqlConstants.ENROLLMENT_TABLE + "." + SqlConstants.ENROLLMENT_UID + EQUAL + QUESTION_MARK +
            LIMIT_1;

    private static final String SELECT_ENROLLMENT_PROGRAM = "SELECT Program.*\n" +
            "FROM Program JOIN Enrollment ON Enrollment.program = Program.uid\n" +
            WHERE + SqlConstants.ENROLLMENT_TABLE + "." + SqlConstants.ENROLLMENT_UID + EQUAL + QUESTION_MARK +
            LIMIT_1;

    private static final String SELECT_INCIDENT_DATE = "SELECT Enrollment.* FROM Enrollment WHERE Enrollment.uid = ? LIMIT 1";

    private static final String SELECT_AUTO_GENERATE_PROGRAM_STAGE = SELECT +
            "ProgramStage.uid, " +
            "Program.uid, " +
            "Enrollment.organisationUnit, " +
            "ProgramStage.minDaysFromStart, " +
            "ProgramStage.reportDateToUse, " +
            "Enrollment.incidentDate, " +
            "Enrollment.enrollmentDate, " +
            "ProgramStage.periodType, \n" +
            "ProgramStage.generatedByEnrollmentDate \n" +
            FROM + SqlConstants.ENROLLMENT_TABLE + "\n" +
            "  JOIN Program ON Enrollment.program = Program.uid\n" +
            "  JOIN ProgramStage ON Program.uid = ProgramStage.program \n" +
            "WHERE Enrollment.uid = ? AND ProgramStage.autoGenerateEvent = 1";

    private static final String SELECT_PROGRAM = "SELECT \n" +
            "  program\n" +
            FROM + SqlConstants.ENROLLMENT_TABLE + "\n" +
            "WHERE uid = ?\n" +
            "LIMIT 1;";

    private static final String SELECT_TE_TYPE = SELECT +
            "Program.uid," +
            "Enrollment.trackedEntityInstance\n" +
            "FROM Program\n" +
            "JOIN Enrollment ON Enrollment.program = Program.uid\n" +
            WHERE + SqlConstants.ENROLLMENT_TABLE + "." + SqlConstants.ENROLLMENT_UID + " = ? LIMIT 1";

    private static final String QUERY = "SELECT \n" +
            "  Field.id,\n" +
            "  Field.label,\n" +
            "  Field.type,\n" +
            "  Field.mandatory,\n" +
            "  Field.optionSet,\n" +
            "  Value.value,\n" +
            "  Option.displayName,\n" +
            "  Field.allowFutureDate,\n" +
            "  Field.generated,\n" +
            "  Enrollment.organisationUnit,\n" +
            "  Enrollment.status,\n" +
            "  Field.displayDescription\n" +
            "FROM (Enrollment INNER JOIN Program ON Program.uid = Enrollment.program)\n" +
            "  LEFT OUTER JOIN (\n" +
            "      SELECT\n" +
            "        TrackedEntityAttribute.uid AS id,\n" +
            "        TrackedEntityAttribute.displayName AS label,\n" +
            "        TrackedEntityAttribute.valueType AS type,\n" +
            "        TrackedEntityAttribute.optionSet AS optionSet,\n" +
            "        ProgramTrackedEntityAttribute.program AS program,\n" +
            "        ProgramTrackedEntityAttribute.mandatory AS mandatory,\n" +
            "        ProgramTrackedEntityAttribute.allowFutureDate AS allowFutureDate,\n" +
            "        TrackedEntityAttribute.generated AS generated,\n" +
            "        TrackedEntityAttribute.displayDescription AS displayDescription\n" +
            "      FROM ProgramTrackedEntityAttribute INNER JOIN TrackedEntityAttribute\n" +
            "          ON TrackedEntityAttribute.uid = ProgramTrackedEntityAttribute.trackedEntityAttribute\n" +
            "    ) AS Field ON Field.program = Program.uid\n" +
            "  LEFT OUTER JOIN TrackedEntityAttributeValue AS Value ON (\n" +
            "    Value.trackedEntityAttribute = Field.id\n" +
            "        AND Value.trackedEntityInstance = Enrollment.trackedEntityInstance)\n" +
            "  LEFT OUTER JOIN Option ON (\n" +
            "    Field.optionSet = Option.optionSet AND Value.value = Option.code\n" +
            "  )\n" +
            WHERE + SqlConstants.ENROLLMENT_TABLE + "." + SqlConstants.ENROLLMENT_UID + EQUAL + QUESTION_MARK;
    private static final String CHECK_STAGE_IS_NOT_CREATED = "SELECT * FROM Event JOIN Enrollment ON Event.enrollment = Enrollment.uid WHERE Enrollment.uid = ? AND Event.programStage = ?";
    @NonNull
    private final BriteDatabase briteDatabase;

    @NonNull
    private final CodeGenerator codeGenerator;

    @NonNull
    private final Flowable<RuleEngine> cachedRuleEngineFlowable;

    @NonNull
    private final String enrollmentUid;

    private String programUid;

    public EnrollmentFormRepository(@NonNull BriteDatabase briteDatabase,
                                    @NonNull RuleExpressionEvaluator expressionEvaluator,
                                    @NonNull RulesRepository rulesRepository,
                                    @NonNull CodeGenerator codeGenerator,
                                    @NonNull String enrollmentUid) {
        this.briteDatabase = briteDatabase;
        this.codeGenerator = codeGenerator;
        this.enrollmentUid = enrollmentUid;

        // We don't want to rebuild RuleEngine on each request, since metadata of
        // the event is not changing throughout lifecycle of FormComponent.
        this.cachedRuleEngineFlowable = enrollmentProgram()
                .switchMap(program -> Flowable.zip(
                        rulesRepository.rulesNew(program),
                        rulesRepository.ruleVariables(program),
                        rulesRepository.enrollmentEvents(enrollmentUid),
                        rulesRepository.queryConstants(),
                        (rules, variables, events, constants) -> {
                            RuleEngine.Builder builder = RuleEngineContext.builder(expressionEvaluator)
                                    .rules(rules)
                                    .ruleVariables(variables)
                                    .calculatedValueMap(new HashMap<>())
                                    .supplementaryData(new HashMap<>())
                                    .constantsValue(constants)
                                    .build().toEngineBuilder();
                            builder.triggerEnvironment(TriggerEnvironment.ANDROIDCLIENT);
                            builder.events(events);
                            return builder.build();
                        }))
                .cacheWithInitialCapacity(1);
    }

    @NonNull
    @Override
    public Flowable<RuleEngine> ruleEngine() {
        return cachedRuleEngineFlowable;
    }

    @NonNull
    @Override
    public Flowable<String> title() {
        return briteDatabase
                .createQuery(TITLE_TABLES, SELECT_TITLE, enrollmentUid)
                .mapToOne(cursor -> cursor.getString(0)).toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @NonNull
    @Override
    public Flowable<Pair<Program, String>> reportDate() {
        return briteDatabase.createQuery(SqlConstants.PROGRAM_TABLE, SELECT_ENROLLMENT_PROGRAM, enrollmentUid)
                .mapToOne(Program::create)
                .flatMap(programModel -> briteDatabase.createQuery(SqlConstants.ENROLLMENT_TABLE, SELECT_ENROLLMENT_DATE, enrollmentUid)
                        .mapToOne(Enrollment::create)
                        .map(enrollmentModel -> Pair.create(programModel, enrollmentModel.enrollmentDate() != null ?
                                DateUtils.uiDateFormat().format(enrollmentModel.enrollmentDate()) : "")))
                .toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @NonNull
    @Override
    public Flowable<Pair<Program, String>> incidentDate() {
        return briteDatabase.createQuery(SqlConstants.PROGRAM_TABLE, SELECT_ENROLLMENT_PROGRAM, enrollmentUid)
                .mapToOne(Program::create)
                .flatMap(programModel -> briteDatabase.createQuery(SqlConstants.ENROLLMENT_TABLE, SELECT_INCIDENT_DATE, enrollmentUid)
                        .mapToOne(Enrollment::create)
                        .map(enrollmentModel -> Pair.create(programModel, enrollmentModel.incidentDate() != null ?
                                DateUtils.uiDateFormat().format(enrollmentModel.incidentDate()) : "")))
                .toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @Override
    public Flowable<Program> getAllowDatesInFuture() {
        return briteDatabase.createQuery(SqlConstants.PROGRAM_TABLE, SELECT_ENROLLMENT_PROGRAM, enrollmentUid)
                .mapToOne(Program::create)
                .toFlowable(BackpressureStrategy.LATEST);
    }

    @NonNull
    @Override
    public Flowable<ReportStatus> reportStatus() {
        return briteDatabase
                .createQuery(SqlConstants.ENROLLMENT_TABLE, SELECT_ENROLLMENT_STATUS, enrollmentUid)
                .mapToOne(cursor ->
                        ReportStatus.fromEnrollmentStatus(EnrollmentStatus.valueOf(cursor.getString(0)))).toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @NonNull
    @Override
    public Flowable<List<FormSectionViewModel>> sections() {
        return briteDatabase
                .createQuery(SqlConstants.ENROLLMENT_TABLE, SELECT_ENROLLMENT_UID, enrollmentUid)
                .mapToList(cursor -> FormSectionViewModel
                        .createForEnrollment(cursor.getString(0))).toFlowable(BackpressureStrategy.LATEST);
    }

    @NonNull
    @Override
    public Consumer<String> storeReportDate() {
        return reportDate -> {
            Calendar cal = DateUtils.getCalendarFromDate(reportDate);

            ContentValues enrollment = new ContentValues();
            enrollment.put(SqlConstants.ENROLLMENT_DATE, DateUtils.databaseDateFormat().format(cal.getTime()));
            enrollment.put(SqlConstants.ENROLLMENT_STATE, State.TO_UPDATE.name()); // TODO: Check if state is TO_POST
            // TODO: and if so, keep the TO_POST state

            briteDatabase.update(SqlConstants.ENROLLMENT_TABLE, enrollment,
                    SqlConstants.ENROLLMENT_UID + " = ?", enrollmentUid);
        };
    }

    @NonNull
    @Override
    public Consumer<LatLng> storeCoordinates() {
        return latLng -> {
            ContentValues enrollment = new ContentValues();
            enrollment.put(SqlConstants.ENROLLMENT_LATITUDE, latLng.latitude);
            enrollment.put(SqlConstants.ENROLLMENT_LONGITUDE, latLng.longitude); // TODO: Check if state is TO_POST
            // TODO: and if so, keep the TO_POST state

            briteDatabase.update(SqlConstants.ENROLLMENT_TABLE, enrollment,
                    SqlConstants.ENROLLMENT_UID + " = ?", enrollmentUid);
        };
    }

    @NonNull
    @Override
    public Consumer<String> storeIncidentDate() {
        return incidentDate -> {
            Calendar cal = Calendar.getInstance();
            Date date = DateUtils.databaseDateFormat().parse(incidentDate);
            cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            ContentValues enrollment = new ContentValues();
            enrollment.put(SqlConstants.ENROLLMENT_INCIDENT_DATE, DateUtils.databaseDateFormat().format(cal.getTime()));
            enrollment.put(SqlConstants.ENROLLMENT_STATE, State.TO_UPDATE.name()); // TODO: Check if state is TO_POST
            // TODO: and if so, keep the TO_POST state

            briteDatabase.update(SqlConstants.ENROLLMENT_TABLE, enrollment,
                    SqlConstants.ENROLLMENT_UID + " = ?", enrollmentUid);
        };
    }

    @NonNull
    @Override
    public Consumer<ReportStatus> storeReportStatus() {
        return reportStatus -> {
            ContentValues enrollment = new ContentValues();
            enrollment.put(SqlConstants.ENROLLMENT_STATUS,
                    ReportStatus.toEnrollmentStatus(reportStatus).name());
            enrollment.put(SqlConstants.ENROLLMENT_STATE, State.TO_UPDATE.name()); // TODO: Check if state is TO_POST
            // TODO: and if so, keep the TO_POST state

            briteDatabase.update(SqlConstants.ENROLLMENT_TABLE, enrollment,
                    SqlConstants.ENROLLMENT_UID + " = ?", enrollmentUid);
        };
    }

    @NonNull
    @Override
    public Observable<String> autoGenerateEvents(String enrollmentUid) {
        Calendar calNow = Calendar.getInstance();
        calNow.set(Calendar.HOUR_OF_DAY, 0);
        calNow.set(Calendar.MINUTE, 0);
        calNow.set(Calendar.SECOND, 0);
        calNow.set(Calendar.MILLISECOND, 0);
        Date now = calNow.getTime();

        try (Cursor cursor = briteDatabase.query(SELECT_AUTO_GENERATE_PROGRAM_STAGE, enrollmentUid == null ? "" : enrollmentUid)) {
            if (cursor != null) {
                cursor.moveToFirst();
                for (int i = 0; i < cursor.getCount(); i++) {
                    createNextEvent(cursor, now);
                    cursor.moveToNext();
                }
            }
        }

        return Observable.just(enrollmentUid);
    }


    private Date getEventDate(String reportDateToUse, Date incidentDate, Date enrollmentDate,
                              boolean generatedByEnrollmentDate, PeriodType periodType, int minDaysFromStart) {
        Date eventDate;
        Calendar cal = DateUtils.getInstance().getCalendar();
        switch (reportDateToUse) {
            case Constants.ENROLLMENT_DATE:
                cal.setTime(enrollmentDate != null ? enrollmentDate : Calendar.getInstance().getTime());
                break;
            case Constants.INCIDENT_DATE:
                cal.setTime(incidentDate != null ? incidentDate : Calendar.getInstance().getTime());
                break;
            default:
                cal.setTime(Calendar.getInstance().getTime());
                break;
        }

        if (!generatedByEnrollmentDate && incidentDate != null)
            cal.setTime(incidentDate);

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DATE, minDaysFromStart);
        eventDate = cal.getTime();

        if (periodType != null)
            eventDate = DateUtils.getInstance().getNextPeriod(periodType, eventDate, 0); //Sets eventDate to current Period date

        return eventDate;
    }

    private void createNextEvent(Cursor cursor, Date now) {
        String programStage = cursor.getString(0);
        String program = cursor.getString(1);
        String orgUnit = cursor.getString(2);
        int minDaysFromStart = cursor.getInt(3);
        String reportDateToUse = cursor.getString(4) != null ? cursor.getString(4) : "";
        String incidentDateString = cursor.getString(5);
        String reportDateString = cursor.getString(6);
        Date incidentDate = null;
        Date enrollmentDate = null;
        PeriodType periodType = cursor.getString(7) != null ? PeriodType.valueOf(cursor.getString(7)) : null;
        boolean generatedByEnrollmentDate = cursor.getInt(8) == 1;

        if (incidentDateString != null)
            try {
                incidentDate = DateUtils.databaseDateFormat().parse(incidentDateString);
            } catch (Exception e) {
                Timber.e(e);
            }

        if (reportDateString != null)
            try {
                enrollmentDate = DateUtils.databaseDateFormat().parse(reportDateString);
            } catch (Exception e) {
                Timber.e(e);
            }

        Date eventDate = getEventDate(reportDateToUse, incidentDate, enrollmentDate,
                generatedByEnrollmentDate, periodType, minDaysFromStart);

        insertEvent(programStage, program, eventDate, orgUnit, now);
    }

    private void insertEvent(String programStage, String program, Date eventDate, String orgUnit, Date now) {
        try (Cursor eventCursor = briteDatabase.query(CHECK_STAGE_IS_NOT_CREATED, enrollmentUid, programStage)) {
            if (!eventCursor.moveToFirst()) {
                Event.Builder eventBuilder = Event.builder()
                        .uid(codeGenerator.generate())
                        .created(Calendar.getInstance().getTime())
                        .lastUpdated(Calendar.getInstance().getTime())
//                            .eventDate(eventDate)
//                            .dueDate(eventDate)
                        .enrollment(enrollmentUid)
                        .program(program)
                        .programStage(programStage)
                        .organisationUnit(orgUnit)
                        .status(eventDate.after(now) ? EventStatus.SCHEDULE : EventStatus.ACTIVE)
                        .state(State.TO_POST);
                if (eventDate.after(now)) //scheduling
                    eventBuilder.dueDate(eventDate);
                else
                    eventBuilder.eventDate(eventDate);

                Event event = eventBuilder.build();

                if (briteDatabase.insert(SqlConstants.EVENT_TABLE, event.toContentValues()) < 0) {
                    throw new OnErrorNotImplementedException(new Throwable("Unable to store event:" + event));
                }
            }
        }
    }

    @NonNull
    @Override
    public Observable<List<FieldViewModel>> fieldValues() {
        return briteDatabase
                .createQuery(SqlConstants.TE_ATTR_VALUE_TABLE, QUERY, enrollmentUid)
                .mapToList(this::transform);
    }


    @Override
    public void deleteTrackedEntityAttributeValues(@NonNull String trackedEntityInstanceId) {
        String deleteWhereRelationship = String.format(
                TABLE_FIELD_EQUALS,
                SqlConstants.TE_ATTR_VALUE_TABLE, SqlConstants.TE_ATTR_VALUE_TEI);
        briteDatabase.delete(SqlConstants.TE_ATTR_VALUE_TABLE, deleteWhereRelationship + "'" + trackedEntityInstanceId + "'");
    }

    @Override
    public void deleteEnrollment(@NonNull String trackedEntityInstanceId) {
        String deleteWhereRelationship = String.format(
                TABLE_FIELD_EQUALS,
                SqlConstants.ENROLLMENT_TABLE, SqlConstants.ENROLLMENT_TEI);
        briteDatabase.delete(SqlConstants.ENROLLMENT_TABLE, deleteWhereRelationship + "'" + trackedEntityInstanceId + "'");
    }

    @Override
    public void deleteEvent() {
        // not necessary
    }

    @Override
    public void deleteTrackedEntityInstance(@NonNull String trackedEntityInstanceId) {
        String deleteWhereRelationship = String.format(
                TABLE_FIELD_EQUALS,
                SqlConstants.TEI_TABLE, SqlConstants.TEI_UID);
        briteDatabase.delete(SqlConstants.TEI_TABLE, deleteWhereRelationship + "'" + trackedEntityInstanceId + "'");
    }

    @NonNull
    @Override
    public Observable<String> getTrackedEntityInstanceUid() {
        String selectTe = SELECT + SqlConstants.ENROLLMENT_TABLE + "." + SqlConstants.ENROLLMENT_TEI +
                " FROM " + SqlConstants.ENROLLMENT_TABLE +
                " WHERE " + SqlConstants.ENROLLMENT_UID + " = ?" +
                " LIMIT 1";

        return briteDatabase.createQuery(SqlConstants.ENROLLMENT_TABLE, selectTe, enrollmentUid).mapToOne(cursor -> cursor.getString(0));
    }

    @Override
    public Observable<Trio<Boolean, CategoryCombo, List<CategoryOptionCombo>>> getProgramCategoryCombo() {
        return null;
    }

    @Override
    public void saveCategoryOption(CategoryOptionCombo selectedOption) {
        // unused
    }

    @Override
    public Observable<Boolean> captureCoodinates() {
        return briteDatabase.createQuery("Program", "SELECT Program.captureCoordinates FROM Program " +
                "JOIN Enrollment ON Enrollment.program = Program.uid WHERE Enrollment.uid = ?", enrollmentUid)
                .mapToOne(cursor -> cursor.getInt(0) == 1);
    }

    @Override
    public Observable<OrganisationUnit> getOrgUnitDates() {
        return briteDatabase.createQuery("SELECT * FROM OrganisationUnit " +
                "JOIN Enrollment ON Enrollment.organisationUnit = OrganisationUnit.uid " +
                WHERE + SqlConstants.ENROLLMENT_TABLE + "." + SqlConstants.ENROLLMENT_UID + " = ?", enrollmentUid)
                .mapToOne(OrganisationUnit::create);
    }

    @NonNull
    private FieldViewModel transform(@NonNull Cursor cursor) {
        FieldViewModelHelper fieldViewModelHelper = FieldViewModelHelper.createFromCursor(cursor);
        EnrollmentStatus status = EnrollmentStatus.valueOf(cursor.getString(10));
        String description = cursor.getString(11);
        if (!isEmpty(fieldViewModelHelper.getOptionCodeName())) {
            fieldViewModelHelper.setDataValue(fieldViewModelHelper.getOptionCodeName());
        }

        int optionCount = 0;
        if (fieldViewModelHelper.getOptionSetUid() != null)
            try (Cursor countCursor = briteDatabase.query("SELECT COUNT (uid) FROM Option WHERE optionSet = ?",
                    fieldViewModelHelper.getOptionSetUid())) {
                if (countCursor != null && countCursor.moveToFirst())
                    optionCount = countCursor.getInt(0);
            } catch (Exception e) {
                Timber.e(e);
            }

        ValueTypeDeviceRendering fieldRendering = null;
        try (Cursor rendering = briteDatabase.query("SELECT ValueTypeDeviceRendering.* FROM ValueTypeDeviceRendering " +
                "JOIN ProgramTrackedEntityAttribute ON ProgramTrackedEntityAttribute.uid = ValueTypeDeviceRendering.uid WHERE ProgramTrackedEntityAttribute.trackedEntityAttribute = ?",
                fieldViewModelHelper.getUid())) {
            if (rendering != null && rendering.moveToFirst()) {
                fieldRendering = ValueTypeDeviceRendering.create(rendering);
            }
        }

        FieldViewModelFactoryImpl fieldFactory = new FieldViewModelFactoryImpl(
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "");

        ObjectStyle objectStyle = ObjectStyle.builder().build();
        try (Cursor objStyleCursor = briteDatabase.query("SELECT * FROM ObjectStyle WHERE uid = ?", fieldViewModelHelper.getUid())) {
            if (objStyleCursor != null && objStyleCursor.moveToFirst())
                objectStyle = ObjectStyle.create(objStyleCursor);
        }

        return fieldFactory.create(fieldViewModelHelper.getUid(), fieldViewModelHelper.getLabel(), fieldViewModelHelper.getValueType(),
                fieldViewModelHelper.isMandatory(), fieldViewModelHelper.getOptionSetUid(), fieldViewModelHelper.getDataValue(),
                fieldViewModelHelper.getSection(), fieldViewModelHelper.getAllowFutureDates(),
                status == EnrollmentStatus.ACTIVE, null, description, fieldRendering, optionCount, objectStyle);
    }

    @NonNull
    @Override
    public Observable<Trio<String, String, String>> useFirstStageDuringRegistration() { //enrollment uid, trackedEntityType, event uid

        return briteDatabase.createQuery(SqlConstants.PROGRAM_TABLE, "SELECT * FROM Program WHERE uid = ?", programUid)
                .mapToOne(Program::create)
                .flatMap(programModel ->
                        briteDatabase.createQuery(SqlConstants.PROGRAM_STAGE_TABLE, "SELECT * FROM ProgramStage WHERE program = ? ORDER BY ProgramStage.sortOrder", programModel.uid())
                                .mapToList(ProgramStage::create).map(programstages -> Trio.create(programModel.useFirstStageDuringRegistration(), programstages, programModel.trackedEntityType())))
                .map(data -> {
                    ProgramStage stageToOpen = getStageToOpen(data);

                    if (stageToOpen != null) { //we should check if event exist (if not create) and open
                        try (Cursor eventCursor = briteDatabase.query("SELECT Event.uid FROM Event WHERE Event.programStage = ? AND Event.enrollment = ?", stageToOpen.uid(), enrollmentUid)) {
                            if (eventCursor != null && eventCursor.moveToFirst()) {
                                String eventUid = eventCursor.getString(0);
                                return Trio.create(getTeiUid(), programUid, eventUid);
                            } else {
                                return insertEvent(stageToOpen);
                            }
                        }
                    } else { //open Dashboard
                        return openDashboard();
                    }
                });
    }

    private ProgramStage getStageToOpen(Trio<Boolean, List<ProgramStage>, TrackedEntityType> data) {
        ProgramStage stageToOpen = null;
        if (data.val0() && !data.val1().isEmpty()) {
            stageToOpen = data.val1().get(0);
        } else if (!data.val1().isEmpty()) {
            for (ProgramStage programStage : data.val1()) {
                if (programStage.openAfterEnrollment() && stageToOpen == null)
                    stageToOpen = programStage;
            }
        }
        return stageToOpen;
    }

    private Trio<String, String, String> insertEvent(@NonNull ProgramStage stageToOpen) {
        try (Cursor enrollmentOrgUnitCursor = briteDatabase.query("SELECT Enrollment.organisationUnit FROM Enrollment WHERE Enrollment.uid = ?",
                enrollmentUid)) {
            if (enrollmentOrgUnitCursor != null && enrollmentOrgUnitCursor.moveToFirst()) {
                Date createdDate = DateUtils.getInstance().getCalendar().getTime();
                Event eventToCreate = Event.builder()
                        .uid(codeGenerator.generate())
                        .created(createdDate)
                        .lastUpdated(createdDate)
                        .eventDate(createdDate)
                        .enrollment(enrollmentUid)
                        .program(stageToOpen.program().uid())
                        .programStage(stageToOpen.uid())
                        .organisationUnit(enrollmentOrgUnitCursor.getString(0))
                        .status(EventStatus.ACTIVE)
                        .state(State.TO_POST)
                        .build();

                if (briteDatabase.insert(SqlConstants.EVENT_TABLE, eventToCreate.toContentValues()) < 0) {
                    throw new OnErrorNotImplementedException(new Throwable("Unable to store event:" + eventToCreate));
                }

                return Trio.create(getTeiUid(), programUid, eventToCreate.uid());//teiUid, programUio, eventUid
            } else
                throw new IllegalArgumentException("Can't create event in enrollment with null organisation unit");
        }
    }

    private Trio<String, String, String> openDashboard() {
        try (Cursor tetCursor = briteDatabase.query(SELECT_TE_TYPE, enrollmentUid)) {
            String programUidAux = "";
            String teiUid = "";
            if (tetCursor != null && tetCursor.moveToFirst()) {
                programUidAux = tetCursor.getString(0);
                teiUid = tetCursor.getString(1);
            }
            return Trio.create(teiUid, programUidAux, "");
        }
    }

    private String getTeiUid() {
        String teiUid = "";
        try (Cursor teiUidCursor = briteDatabase.query("SELECT DISTINCT TrackedEntityInstance.uid " +
                "FROM TrackedEntityInstance JOIN Enrollment ON Enrollment.trackedEntityInstance = TrackedEntityInstance.uid " +
                "WHERE Enrollment.uid = ? LIMIT 1", enrollmentUid)) {
            if (teiUidCursor != null && teiUidCursor.moveToFirst()) {
                teiUid = teiUidCursor.getString(0);
            }
        }
        return teiUid;
    }

    @NonNull
    private Flowable<String> enrollmentProgram() {
        return briteDatabase
                .createQuery(SqlConstants.ENROLLMENT_TABLE, SELECT_PROGRAM, enrollmentUid)
                .mapToOne(cursor -> {
                    programUid = cursor.getString(0);
                    return programUid;
                })
                .toFlowable(BackpressureStrategy.LATEST);
    }
}