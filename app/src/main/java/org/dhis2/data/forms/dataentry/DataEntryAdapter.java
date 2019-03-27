package org.dhis2.data.forms.dataentry;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.dhis2.data.forms.dataentry.fields.FieldViewModel;
import org.dhis2.data.forms.dataentry.fields.Row;
import org.dhis2.data.forms.dataentry.fields.RowAction;
import org.dhis2.data.forms.dataentry.fields.age.AgeRow;
import org.dhis2.data.forms.dataentry.fields.age.AgeViewModel;
import org.dhis2.data.forms.dataentry.fields.coordinate.CoordinateRow;
import org.dhis2.data.forms.dataentry.fields.coordinate.CoordinateViewModel;
import org.dhis2.data.forms.dataentry.fields.datetime.DateTimeRow;
import org.dhis2.data.forms.dataentry.fields.datetime.DateTimeViewModel;
import org.dhis2.data.forms.dataentry.fields.edittext.EditTextModel;
import org.dhis2.data.forms.dataentry.fields.edittext.EditTextRow;
import org.dhis2.data.forms.dataentry.fields.file.FileRow;
import org.dhis2.data.forms.dataentry.fields.file.FileViewModel;
import org.dhis2.data.forms.dataentry.fields.image.ImageRow;
import org.dhis2.data.forms.dataentry.fields.image.ImageViewModel;
import org.dhis2.data.forms.dataentry.fields.orgUnit.OrgUnitRow;
import org.dhis2.data.forms.dataentry.fields.orgUnit.OrgUnitViewModel;
import org.dhis2.data.forms.dataentry.fields.radiobutton.RadioButtonRow;
import org.dhis2.data.forms.dataentry.fields.radiobutton.RadioButtonViewModel;
import org.dhis2.data.forms.dataentry.fields.spinner.SpinnerRow;
import org.dhis2.data.forms.dataentry.fields.spinner.SpinnerViewModel;
import org.dhis2.data.forms.dataentry.fields.unsupported.UnsupportedRow;
import org.dhis2.data.forms.dataentry.fields.unsupported.UnsupportedViewModel;
import org.dhis2.data.tuples.Trio;
import org.hisp.dhis.android.core.common.ValueType;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableField;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import io.reactivex.Observable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import timber.log.Timber;

public final class DataEntryAdapter extends Adapter {
    private static final int EDITTEXT = 0;
    private static final int BUTTON = 1;
    private static final int CHECKBOX = 2;
    private static final int SPINNER = 3;
    private static final int COORDINATES = 4;
    private static final int TIME = 5;
    private static final int DATE = 6;
    private static final int DATETIME = 7;
    private static final int AGEVIEW = 8;
    private static final int YES_NO = 9;
    private static final int ORG_UNIT = 10;
    private static final int IMAGE = 11;
    private static final int UNSUPPORTED = 12;


    @NonNull
    private final List<FieldViewModel> viewModels;

    @NonNull
    private final FlowableProcessor<RowAction> processor;

    @NonNull
    private final FlowableProcessor<Integer> currentPosition;

    @NonNull
    private final ObservableField<String> imageSelector;

    @NonNull
    private final List<Row> rows;

    private final FlowableProcessor<Trio<String, String, Integer>> processorOptionSet;

    public DataEntryAdapter(@NonNull LayoutInflater layoutInflater,
                            @NonNull FragmentManager fragmentManager,
                            @NonNull DataEntryArguments dataEntryArguments,
                            @NonNull Observable<List<OrganisationUnit>> orgUnits,
                            ObservableBoolean isEditable) { //TODO: Add isEditable to all fields and test if can be changed on the fly
        setHasStableIds(true);
        rows = new ArrayList<>();
        viewModels = new ArrayList<>();
        processor = PublishProcessor.create();
        imageSelector = new ObservableField<>("");
        currentPosition = PublishProcessor.create();
        this.processorOptionSet = PublishProcessor.create();

        rows.add(EDITTEXT, new EditTextRow(layoutInflater, processor, true, dataEntryArguments.renderType(), isEditable));
        rows.add(BUTTON, new FileRow(layoutInflater, processor, true));
        rows.add(CHECKBOX, new RadioButtonRow(layoutInflater, processor, true));
        rows.add(SPINNER, new SpinnerRow(layoutInflater, processor, processorOptionSet, true, dataEntryArguments.renderType()));
        rows.add(COORDINATES, new CoordinateRow(layoutInflater, processor, true));
        rows.add(TIME, new DateTimeRow(layoutInflater, processor, currentPosition, TIME, true));
        rows.add(DATE, new DateTimeRow(layoutInflater, processor, currentPosition, DATE, true));
        rows.add(DATETIME, new DateTimeRow(layoutInflater, processor, currentPosition, DATETIME, true));
        rows.add(AGEVIEW, new AgeRow(layoutInflater, processor, true));
        rows.add(YES_NO, new RadioButtonRow(layoutInflater, processor, true));
        rows.add(ORG_UNIT, new OrgUnitRow(fragmentManager, layoutInflater, processor, true, orgUnits, dataEntryArguments.renderType()));
        rows.add(IMAGE, new ImageRow(layoutInflater, processor, dataEntryArguments.renderType()));
        rows.add(UNSUPPORTED, new UnsupportedRow(layoutInflater));

    }

    public DataEntryAdapter(@NonNull LayoutInflater layoutInflater,
                            @NonNull FragmentManager fragmentManager,
                            @NonNull DataEntryArguments dataEntryArguments,
                            @NonNull Observable<List<OrganisationUnit>> orgUnits,
                            ObservableBoolean isEditable,
                            @NonNull FlowableProcessor<RowAction> processor,
                            @NonNull FlowableProcessor<Trio<String, String, Integer>> processorOptSet) { //TODO: Add isEditable to all fields and test if can be changed on the fly
        setHasStableIds(true);
        rows = new ArrayList<>();
        viewModels = new ArrayList<>();
        this.processor = processor;
        imageSelector = new ObservableField<>("");
        currentPosition = PublishProcessor.create();
        this.processorOptionSet = processorOptSet;

        rows.add(EDITTEXT, new EditTextRow(layoutInflater, processor, true, dataEntryArguments.renderType(), isEditable));
        rows.add(BUTTON, new FileRow(layoutInflater, processor, true));
        rows.add(CHECKBOX, new RadioButtonRow(layoutInflater, processor, true));
        rows.add(SPINNER, new SpinnerRow(layoutInflater, processor, processorOptionSet, true, dataEntryArguments.renderType()));
        rows.add(COORDINATES, new CoordinateRow(layoutInflater, processor, true));
        rows.add(TIME, new DateTimeRow(layoutInflater, processor, currentPosition, TIME, true));
        rows.add(DATE, new DateTimeRow(layoutInflater, processor, currentPosition, DATE, true));
        rows.add(DATETIME, new DateTimeRow(layoutInflater, processor, currentPosition, DATETIME, true));
        rows.add(AGEVIEW, new AgeRow(layoutInflater, processor, true));
        rows.add(YES_NO, new RadioButtonRow(layoutInflater, processor, true));
        rows.add(ORG_UNIT, new OrgUnitRow(fragmentManager, layoutInflater, processor, true, orgUnits, dataEntryArguments.renderType()));
        rows.add(IMAGE, new ImageRow(layoutInflater, processor, dataEntryArguments.renderType()));
        rows.add(UNSUPPORTED, new UnsupportedRow(layoutInflater));

    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == IMAGE)
            return ((ImageRow) rows.get(IMAGE)).onCreate(parent, getItemCount(), imageSelector);
        else
            return rows.get(viewType).onCreate(parent);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        rows.get(holder.getItemViewType()).onBind(holder,
                viewModels.get(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return viewModels.size();
    }

    private int getDateTimeItemViewType(DateTimeViewModel viewModel) {
        if (viewModel.valueType() == ValueType.DATE)
            return DATE;
        if (viewModel.valueType() == ValueType.TIME)
            return TIME;
        else
            return DATETIME;
    }

    @Override
    public int getItemViewType(int position) {

        FieldViewModel viewModel = viewModels.get(position);
        if (viewModel instanceof EditTextModel) {
            return EDITTEXT;
        } else if (viewModel instanceof RadioButtonViewModel) {
            return CHECKBOX;
        } else if (viewModel instanceof SpinnerViewModel) {
            return SPINNER;
        } else if (viewModel instanceof CoordinateViewModel) {
            return COORDINATES;
        } else if (viewModel instanceof DateTimeViewModel) {
            return getDateTimeItemViewType((DateTimeViewModel) viewModel);
        } else if (viewModel instanceof AgeViewModel) {
            return AGEVIEW;
        } else if (viewModel instanceof FileViewModel) {
            return BUTTON;
        } else if (viewModel instanceof OrgUnitViewModel) {
            return ORG_UNIT;
        } else if (viewModel instanceof ImageViewModel) {
            return IMAGE;
        } else if (viewModel instanceof UnsupportedViewModel) {
            return UNSUPPORTED;
        } else {
            throw new IllegalStateException("Unsupported view model type: "
                    + viewModel.getClass());
        }
    }

    @Override
    public long getItemId(int position) {
        return viewModels.get(position).uid().hashCode();
    }

    @NonNull
    public FlowableProcessor<RowAction> asFlowable() {
        return processor;
    }

    public FlowableProcessor<Trio<String, String, Integer>> asFlowableOption() {
        return processorOptionSet;
    }

    public void swap(@NonNull List<FieldViewModel> updates) {
        long currentTime = System.currentTimeMillis();
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new DataEntryDiffCallback(viewModels, updates));

        viewModels.clear();
        viewModels.addAll(updates);

        diffResult.dispatchUpdatesTo(this);
        Timber.d("ADAPTER SWAP TOOK %s ms", System.currentTimeMillis() - currentTime);
    }

    public boolean mandatoryOk() {
        boolean isOk = true;
        for (FieldViewModel fieldViewModel : viewModels) {
            if (fieldViewModel.mandatory() && (fieldViewModel.value() == null || fieldViewModel.value().isEmpty()))
                isOk = false;
        }

        return isOk;
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        rows.get(holder.getItemViewType()).deAttach(holder);
    }

    public boolean hasError() {
        boolean hasError = false;
        for (FieldViewModel fieldViewModel : viewModels) {
            if (fieldViewModel.error() != null)
                hasError = true;
        }

        return hasError;
    }
}
