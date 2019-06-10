package org.dhis2.data.forms.dataentry.fields.picture;

import androidx.appcompat.content.res.AppCompatResources;

import org.dhis2.R;
import org.dhis2.data.forms.dataentry.fields.FormViewHolder;
import org.dhis2.data.forms.dataentry.fields.RowAction;
import org.dhis2.databinding.CustomFormPictureBinding;
import org.dhis2.utils.custom_views.PictureView;

import io.reactivex.processors.FlowableProcessor;

import static android.text.TextUtils.isEmpty;

public class PictureHolder extends FormViewHolder {

    private final FlowableProcessor<RowAction> processor;
    private CustomFormPictureBinding binding;
    private PictureViewModel model;

    public PictureHolder(
            PictureView.OnIntentSelected onIntentSelected,
            CustomFormPictureBinding binding,
            FlowableProcessor<RowAction> processor, boolean isSearchMode) {
        super(binding);
        this.processor = processor;
        this.binding = binding;
        this.binding.formPictures.setOnIntentSelected(onIntentSelected);
    }

    void update(PictureViewModel pictureViewModel) {
        this.model = pictureViewModel;
        binding.formPictures.setProcessor(pictureViewModel.uid(), processor);
        descriptionText = pictureViewModel.description();
        label = new StringBuilder(pictureViewModel.label());
        if (pictureViewModel.mandatory())
            label.append("*");
        binding.formPictures.setLabel(label.toString());
        binding.formPictures.setDescription(descriptionText);
        if (!isEmpty(pictureViewModel.value()))
            binding.formPictures.setInitialValue(pictureViewModel.value());

        if (pictureViewModel.warning() != null)
            binding.formPictures.setWarning(pictureViewModel.warning());
        else if (pictureViewModel.error() != null)
            binding.formPictures.setError(pictureViewModel.error());
        else
            binding.formPictures.setError(null);

    }

    @Override
    public void dispose() {
    }

    @Override
    public void performAction() {
        itemView.setBackground(AppCompatResources.getDrawable(itemView.getContext(), R.drawable.item_selected_bg));
       // binding.formCoordinates.performOnFocusAction();
    }

}