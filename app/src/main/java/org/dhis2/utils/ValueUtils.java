package org.dhis2.utils;

import android.database.Cursor;

import com.squareup.sqlbrite2.BriteDatabase;

import org.hisp.dhis.android.core.common.ValueType;
import org.hisp.dhis.android.core.option.Option;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityAttributeValue;

/**
 * QUADRAM. Created by ppajuelo on 25/09/2018.
 */

public class ValueUtils {

    private ValueUtils() {
        // hide public constructor
    }

    private static TrackedEntityAttributeValue createOrgUnitTeiAttrValue(BriteDatabase briteDatabase,
                                                                         Cursor cursor,
                                                                         TrackedEntityAttributeValue teAttrValue) {
        String orgUnitUid = cursor.getString(cursor.getColumnIndex("value"));
        try (Cursor orgUnitCursor = briteDatabase.query("SELECT OrganisationUnit.displayName FROM OrganisationUnit WHERE OrganisationUnit.uid = ?", orgUnitUid)) {
            if (orgUnitCursor != null && orgUnitCursor.moveToFirst()) {
                String orgUnitName = orgUnitCursor.getString(0);
                teAttrValue = TrackedEntityAttributeValue.builder()
                        .trackedEntityInstance(teAttrValue.trackedEntityInstance())
                        .lastUpdated(teAttrValue.lastUpdated())
                        .created(teAttrValue.created())
                        .trackedEntityAttribute(teAttrValue.trackedEntityAttribute())
                        .value(orgUnitName)
                        .build();
            }
        }
        return teAttrValue;
    }

    private static TrackedEntityAttributeValue createTeiAttrValue(BriteDatabase briteDatabase, Cursor cursor,
                                                                  TrackedEntityAttributeValue teAttrValue, int optionSetIndex) {
        String optionSet = cursor.getString(optionSetIndex);
        String optionCode = cursor.getString(cursor.getColumnIndex("value"));
        try (Cursor optionsCursor = briteDatabase.query("SELECT * FROM Option WHERE optionSet = ?", optionSet)) {
            if (optionsCursor != null && optionsCursor.moveToFirst()) {
                for (int i = 0; i < optionsCursor.getCount(); i++) {
                    Option optionModel = Option.create(optionsCursor);
                    if (optionModel.code().equals(optionCode) || optionModel.name().equals(optionCode)) {
                        teAttrValue = TrackedEntityAttributeValue.builder()
                                .trackedEntityInstance(teAttrValue.trackedEntityInstance())
                                .lastUpdated(teAttrValue.lastUpdated())
                                .created(teAttrValue.created())
                                .trackedEntityAttribute(teAttrValue.trackedEntityAttribute())
                                .value(optionModel.displayName())
                                .build();
                    }
                    optionsCursor.moveToNext();
                }
            }
        }
        return teAttrValue;
    }

    /**
     * @param briteDatabase access to database
     * @param cursor        cursor of the original TEAV
     * @return Returns a trackedEntityAttributeValueModel which value has been parse for valueType orgunit uid or optionSet code/name
     */
    public static TrackedEntityAttributeValue transform(BriteDatabase briteDatabase, Cursor cursor) {
        TrackedEntityAttributeValue teAttrValue = TrackedEntityAttributeValue.create(cursor);
        int valueTypeIndex = cursor.getColumnIndex("valueType");
        int optionSetIndex = cursor.getColumnIndex("optionSet");
        if (cursor.getString(valueTypeIndex).equals(ValueType.ORGANISATION_UNIT.name())) {
            teAttrValue = createOrgUnitTeiAttrValue(briteDatabase, cursor, teAttrValue);
        } else if (cursor.getString(optionSetIndex) != null) {
            teAttrValue = createTeiAttrValue(briteDatabase, cursor, teAttrValue, optionSetIndex);
        }
        return teAttrValue;
    }

    public static String optionSetCodeToDisplayName(BriteDatabase briteDatabase, String optionSet, String optionSetCode) {
        String displayName = optionSetCode;
        try (Cursor optionsCursor = briteDatabase.query("SELECT * FROM Option WHERE optionSet = ? AND code = ? LIMIT 1", optionSet, optionSetCode)) {
            if (optionsCursor != null && optionsCursor.moveToFirst()) {
                Option optionModel = Option.create(optionsCursor);
                displayName = optionModel.displayName();
            }
        }
        return displayName;
    }

    public static String orgUnitUidToDisplayName(BriteDatabase briteDatabase, String value) {
        String displayName = value;
        try (Cursor orgUnitCursor = briteDatabase.query("SELECT OrganisationUnit.displayName FROM OrganisationUnit WHERE OrganisationUnit.uid = ?", value)) {
            if (orgUnitCursor != null && orgUnitCursor.moveToFirst()) {
                displayName = orgUnitCursor.getString(0);
            }
        }
        return displayName;
    }
}
