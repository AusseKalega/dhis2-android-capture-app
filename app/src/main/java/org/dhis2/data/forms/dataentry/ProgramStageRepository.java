package org.dhis2.data.forms.dataentry;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.squareup.sqlbrite2.BriteDatabase;

import org.dhis2.data.forms.dataentry.fields.FieldViewModel;
import org.dhis2.data.forms.dataentry.fields.FieldViewModelFactory;
import org.dhis2.data.forms.dataentry.fields.FieldViewModelHelper;
import org.dhis2.utils.DateUtils;
import org.dhis2.utils.SqlConstants;
import org.hisp.dhis.android.core.common.ObjectStyle;
import org.hisp.dhis.android.core.common.ValueType;
import org.hisp.dhis.android.core.common.ValueTypeDeviceRendering;
import org.hisp.dhis.android.core.event.Event;
import org.hisp.dhis.android.core.event.EventStatus;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnitLevel;
import org.hisp.dhis.android.core.program.Program;
import org.hisp.dhis.android.core.program.ProgramStage;
import org.hisp.dhis.android.core.program.ProgramStageSectionRenderingType;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityDataValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.reactivex.Observable;
import timber.log.Timber;

import static android.text.TextUtils.isEmpty;


@SuppressWarnings({
        "PMD.AvoidDuplicateLiterals"
})
final class ProgramStageRepository implements DataEntryRepository {
    private static final String QUERY = "SELECT\n" +
            "  Field.id,\n" +
            "  Field.label,\n" +
            "  Field.type,\n" +
            "  Field.mandatory,\n" +
            "  Field.optionSet,\n" +
            "  Value.value,\n" +
            "  Option.displayName,\n" +
            "  Field.section,\n" +
            "  Field.allowFutureDate,\n" +
            "  Event.status,\n" +
            "  Field.formLabel,\n" +
            "  Field.displayDescription,\n" +
            "  Field.formOrder,\n" +
            "  Field.sectionOrder\n" +
            "FROM Event\n" +
            "  LEFT OUTER JOIN (\n" +
            "      SELECT\n" +
            "        DataElement.displayName AS label,\n" +
            "        DataElement.valueType AS type,\n" +
            "        DataElement.uid AS id,\n" +
            "        DataElement.optionSet AS optionSet,\n" +
            "        DataElement.displayFormName AS formLabel,\n" +
            "        ProgramStageDataElement.sortOrder AS formOrder,\n" +
            "        ProgramStageDataElement.programStage AS stage,\n" +
            "        ProgramStageDataElement.compulsory AS mandatory,\n" +
            "        ProgramStageSectionDataElementLink.programStageSection AS section,\n" +
            "        ProgramStageDataElement.allowFutureDate AS allowFutureDate,\n" +
            "        DataElement.displayDescription AS displayDescription,\n" +
            "        ProgramStageSectionDataElementLink.sortOrder AS sectionOrder\n" + //This should override dataElement formOrder
            "      FROM ProgramStageDataElement\n" +
            "        INNER JOIN DataElement ON DataElement.uid = ProgramStageDataElement.dataElement\n" +
            "        LEFT JOIN ProgramStageSection ON ProgramStageSection.programStage = ProgramStageDataElement.programStage\n" +
            "        LEFT JOIN ProgramStageSectionDataElementLink ON ProgramStageSectionDataElementLink.programStageSection = ProgramStageSection.uid AND ProgramStageSectionDataElementLink.dataElement = DataElement.uid\n" + "    ) AS Field ON (Field.stage = Event.programStage)\n" +
            "  LEFT OUTER JOIN TrackedEntityDataValue AS Value ON (\n" +
            "    Value.event = Event.uid AND Value.dataElement = Field.id\n" +
            "  )\n" +
            "  LEFT OUTER JOIN Option ON (\n" +
            "    Field.optionSet = Option.optionSet AND Value.value = Option.code\n" +
            "  )\n" +
            " %s  " +
            "ORDER BY CASE" +
            " WHEN Field.sectionOrder IS NULL THEN Field.formOrder" +
            " WHEN Field.sectionOrder IS NOT NULL THEN Field.sectionOrder" +
            " END ASC;";

    private static final String SECTION_RENDERING_TYPE = "SELECT ProgramStageSection.mobileRenderType FROM ProgramStageSection WHERE ProgramStageSection.uid = ?";
    private static final String ACCESS_QUERY = "SELECT ProgramStage.accessDataWrite FROM ProgramStage JOIN Event ON Event.programStage = ProgramStage.uid WHERE Event.uid = ?";
    private static final String PROGRAM_ACCESS_QUERY = "SELECT Program.accessDataWrite FROM Program JOIN Event ON Event.program = Program.uid WHERE Event.uid = ?";
    private static final String OPTIONS = "SELECT Option.uid, Option.displayName, Option.code FROM Option WHERE Option.optionSet = ?";

    @NonNull
    private final BriteDatabase briteDatabase;

    @NonNull
    private final FieldViewModelFactory fieldFactory;

    @NonNull
    private final String eventUid;

    @Nullable
    private final String sectionUid;

    private ProgramStageSectionRenderingType renderingType;
    private boolean accessDataWrite;

    ProgramStageRepository(@NonNull BriteDatabase briteDatabase,
                           @NonNull FieldViewModelFactory fieldFactory,
                           @NonNull String eventUid, @Nullable String sectionUid) {
        this.briteDatabase = briteDatabase;
        this.fieldFactory = fieldFactory;
        this.eventUid = eventUid;
        this.sectionUid = sectionUid;
        this.renderingType = ProgramStageSectionRenderingType.LISTING;
    }

    @NonNull
    @Override
    public Observable<List<FieldViewModel>> list() {

        try (Cursor cursor = briteDatabase.query(SECTION_RENDERING_TYPE, sectionUid == null ? "" : sectionUid)) {
            if (cursor != null && cursor.moveToFirst()) {
                renderingType = cursor.getString(0) != null ?
                        ProgramStageSectionRenderingType.valueOf(cursor.getString(0)) :
                        ProgramStageSectionRenderingType.LISTING;
            }
        }

        try (Cursor accessCursor = briteDatabase.query(ACCESS_QUERY, eventUid)) {
            if (accessCursor != null && accessCursor.moveToFirst()) {
                accessDataWrite = accessCursor.getInt(0) == 1;
            }
        }

        try (Cursor programAccessCursor = briteDatabase.query(PROGRAM_ACCESS_QUERY, eventUid)) {
            if (programAccessCursor != null && programAccessCursor.moveToFirst()) {
                accessDataWrite = accessDataWrite && programAccessCursor.getInt(0) == 1;
            }
        }

        return briteDatabase
                .createQuery(SqlConstants.TEI_DATA_VALUE_TABLE, prepareStatement())
                .mapToList(this::transform)
                .map(this::checkRenderType);
    }

    private ArrayList<FieldViewModel> parseCursor(Cursor cursor, FieldViewModel fieldViewModel) {
        ArrayList<FieldViewModel> renderList = new ArrayList<>();
        int optionCount = cursor.getCount();
        for (int i = 0; i < optionCount; i++) {
            String uid = cursor.getString(0);
            String displayName = cursor.getString(1);
            String optionCode = cursor.getString(2);

            ObjectStyle objectStyle = ObjectStyle.builder().build();
            try (Cursor objStyleCursor = briteDatabase.query("SELECT * FROM ObjectStyle WHERE uid = ?", fieldViewModel.uid())) {
                if (objStyleCursor.moveToFirst())
                    objectStyle = ObjectStyle.create(objStyleCursor);
            }

            renderList.add(fieldFactory.create(
                    fieldViewModel.uid() + "." + uid, //fist
                    displayName + "-" + optionCode, ValueType.TEXT, false,
                    fieldViewModel.optionSet(), fieldViewModel.value(), fieldViewModel.programStageSection(),
                    fieldViewModel.allowFutureDate(),
                    fieldViewModel.editable() != null && fieldViewModel.editable(),
                    renderingType, fieldViewModel.description(), null, optionCount, objectStyle));

            cursor.moveToNext();
        }
        return renderList;
    }

    private ArrayList<FieldViewModel> parseFieldViewModelOptionSet(FieldViewModel fieldViewModel) {
        ArrayList<FieldViewModel> renderList = new ArrayList<>();
        if (!isEmpty(fieldViewModel.optionSet())) {
            try (Cursor cursor = briteDatabase.query(OPTIONS, fieldViewModel.optionSet() == null ? "" : fieldViewModel.optionSet())) {
                if (cursor != null && cursor.moveToFirst()) {
                    renderList.addAll(parseCursor(cursor, fieldViewModel));
                }
            }
        } else {
            renderList.add(fieldViewModel);
        }
        return renderList;
    }

    @Override
    public List<FieldViewModel> fieldList() {
        return new ArrayList<>();
    }

    private List<FieldViewModel> checkRenderType(List<FieldViewModel> fieldViewModels) {
        ArrayList<FieldViewModel> renderList = new ArrayList<>();
        if (renderingType != ProgramStageSectionRenderingType.LISTING) {
            for (FieldViewModel fieldViewModel : fieldViewModels) {
                renderList.addAll(parseFieldViewModelOptionSet(fieldViewModel));
            }
        } else
            renderList.addAll(fieldViewModels);

        return renderList;
    }

    @Override
    public Observable<List<OrganisationUnit>> getOrgUnits() {
        return briteDatabase.createQuery(SqlConstants.ORG_UNIT_TABLE, "SELECT * FROM " + SqlConstants.ORG_UNIT_TABLE)
                .mapToList(OrganisationUnit::create);
    }

    @Override
    public void assign(String field, String content) {
        try (Cursor dataValueCursor = briteDatabase.query("SELECT * FROM TrackedEntityDataValue WHERE dataElement = ?", field == null ? "" : field)) {
            if (dataValueCursor != null && dataValueCursor.moveToFirst()) {
                TrackedEntityDataValue dataValue = TrackedEntityDataValue.create(dataValueCursor);
                ContentValues contentValues = dataValue.toContentValues();
                contentValues.put(SqlConstants.TEI_DATA_VALUE_VALUE, content);
                int row = briteDatabase.update(SqlConstants.TEI_DATA_VALUE_TABLE, contentValues, "dataElement = ?", field == null ? "" : field);
                if (row == -1)
                    Timber.d("Error updating field %s", field == null ? "" : field);
            }
        }

    }

    @NonNull
    private FieldViewModel transform(@NonNull Cursor cursor) {
        FieldViewModelHelper fieldViewModelHelper = FieldViewModelHelper.createFromCursor(cursor);
        EventStatus eventStatus = EventStatus.valueOf(cursor.getString(9));

        int optionCount = FieldViewModelHelper.getOptionCount(briteDatabase, fieldViewModelHelper.getOptionSetUid());

        ValueTypeDeviceRendering fieldRendering = null;
        try (Cursor rendering = briteDatabase.query("SELECT ValueTypeDeviceRendering.* FROM ValueTypeDeviceRendering" +
                " JOIN ProgramStageDataElement ON ProgramStageDataElement.uid = ValueTypeDeviceRendering.uid" +
                " WHERE ProgramStageDataElement.uid = ?", fieldViewModelHelper.getUid())) {
            if (rendering != null && rendering.moveToFirst()) {
                fieldRendering = ValueTypeDeviceRendering.create(rendering);
            }
        }

        Event eventModel;
        ProgramStage programStageModel;
        Program programModel;
        try (Cursor eventCursor = briteDatabase.query("SELECT * FROM Event WHERE uid = ?", eventUid)) {
            eventCursor.moveToFirst();
            eventModel = Event.create(eventCursor);
        }

        try (Cursor programStageCursor = briteDatabase.query("SELECT * FROM ProgramStage WHERE uid = ?", eventModel.programStage())) {
            programStageCursor.moveToFirst();
            programStageModel = ProgramStage.create(programStageCursor);
        }

        try (Cursor programCursor = briteDatabase.query("SELECT * FROM Program WHERE uid = ?", eventModel.program())) {
            programCursor.moveToFirst();
            programModel = Program.create(programCursor);
        }

        boolean hasExpired = DateUtils.getInstance().hasExpired(eventModel, programModel.expiryDays(), programModel.completeEventsExpiryDays(), programStageModel.periodType() != null ? programStageModel.periodType() : programModel.expiryPeriodType());

        ObjectStyle objectStyle = ObjectStyle.builder().build();
        try (Cursor objStyleCursor = briteDatabase.query("SELECT * FROM ObjectStyle WHERE uid = ?", fieldViewModelHelper.getUid())) {
            if (objStyleCursor != null && objStyleCursor.moveToFirst())
                objectStyle = ObjectStyle.create(objStyleCursor);
        }

        return fieldFactory.create(fieldViewModelHelper.getUid(), isEmpty(fieldViewModelHelper.getFormLabel()) ?
                        fieldViewModelHelper.getLabel() : fieldViewModelHelper.getFormLabel(),
                fieldViewModelHelper.getValueType(),
                fieldViewModelHelper.isMandatory(), fieldViewModelHelper.getOptionSetUid(), fieldViewModelHelper.getDataValue(),
                fieldViewModelHelper.getSection(), fieldViewModelHelper.getAllowFutureDates(),
                accessDataWrite && eventStatus == EventStatus.ACTIVE && !hasExpired, renderingType, fieldViewModelHelper.getDescription(),
                fieldRendering, optionCount, objectStyle);
    }

    @NonNull
    @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
    private String prepareStatement() {
        String where;
        if (isEmpty(sectionUid)) {
            where = String.format(Locale.US, "WHERE Event.uid = '%s'", eventUid);
        } else {
            where = String.format(Locale.US, "WHERE Event.uid = '%s' AND " +
                    "Field.section = '%s'", eventUid, sectionUid);
        }

        return String.format(Locale.US, QUERY, where);
    }

    @Override
    public Observable<List<OrganisationUnitLevel>> getOrgUnitLevels() {
        return null;
    }
}
