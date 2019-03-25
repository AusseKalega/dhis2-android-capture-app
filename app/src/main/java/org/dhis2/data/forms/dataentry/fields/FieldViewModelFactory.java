package org.dhis2.data.forms.dataentry.fields;

import org.hisp.dhis.android.core.common.ObjectStyle;
import org.hisp.dhis.android.core.common.ValueType;
import org.hisp.dhis.android.core.common.ValueTypeDeviceRendering;
import org.hisp.dhis.android.core.program.ProgramStageSectionRenderingType;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface FieldViewModelFactory {

    @SuppressWarnings("squid:S00107")
    @NonNull
    FieldViewModel create(@NonNull String id,
                          @NonNull String label,
                          @NonNull ValueType valueType,
                          @NonNull Boolean mandatory,
                          @Nullable String optionSet,
                          @Nullable String value,
                          @Nullable String programStageSection,
                          @Nullable Boolean allowFutureDate,
                          @NonNull Boolean editable,
                          @Nullable ProgramStageSectionRenderingType renderingType,
                          @Nullable String description,
                          @Nullable ValueTypeDeviceRendering fieldRendering,
                          @Nullable Integer optionCount,
                          @NonNull ObjectStyle objectStyle);
}
