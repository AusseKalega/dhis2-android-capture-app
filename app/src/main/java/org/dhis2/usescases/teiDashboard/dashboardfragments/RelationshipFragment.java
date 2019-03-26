package org.dhis2.usescases.teiDashboard.dashboardfragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.wangjie.rapidfloatingactionbutton.RapidFloatingActionHelper;
import com.wangjie.rapidfloatingactionbutton.contentimpl.labellist.RFACLabelItem;
import com.wangjie.rapidfloatingactionbutton.contentimpl.labellist.RapidFloatingActionContentLabelList;
import com.wangjie.rapidfloatingactionbutton.util.RFABTextUtil;

import org.dhis2.R;
import org.dhis2.data.tuples.Pair;
import org.dhis2.data.tuples.Trio;
import org.dhis2.databinding.FragmentRelationshipsBinding;
import org.dhis2.usescases.general.FragmentGlobalAbstract;
import org.dhis2.usescases.teiDashboard.TeiDashboardContracts;
import org.dhis2.usescases.teiDashboard.adapters.RelationshipAdapter;
import org.dhis2.usescases.teiDashboard.mobile.TeiDashboardMobileActivity;
import org.dhis2.utils.ColorUtils;
import org.dhis2.utils.Constants;
import org.hisp.dhis.android.core.relationship.Relationship;
import org.hisp.dhis.android.core.relationship.RelationshipType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import io.reactivex.functions.Consumer;

import static android.app.Activity.RESULT_OK;

/**
 * QUADRAM. Created by ppajuelo on 29/11/2017.
 */

public class RelationshipFragment extends FragmentGlobalAbstract {

    private FragmentRelationshipsBinding binding;
    private TeiDashboardContracts.Presenter presenter;

    private static RelationshipFragment instance;
    private RelationshipAdapter relationshipAdapter;
    private RapidFloatingActionHelper rfaHelper;
    private RelationshipType relationshipType;

    public static RelationshipFragment getInstance() {
        if (instance == null) {
            instance = new RelationshipFragment();
        }
        return instance;
    }

    public static RelationshipFragment createInstance() {
        instance = new RelationshipFragment();
        return instance;
    }


    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        presenter = ((TeiDashboardMobileActivity) context).getPresenter();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_relationships, container, false);
        binding.setPresenter(presenter);
        relationshipAdapter = new RelationshipAdapter(presenter);
        binding.relationshipRecycler.setAdapter(relationshipAdapter);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        setData();
    }

    public void setData() {
        binding.executePendingBindings();
        presenter.subscribeToRelationships(this);
        presenter.subscribeToRelationshipTypes(this);

    }

    public Consumer<List<Pair<Relationship, RelationshipType>>> setRelationships() {
        return relationships -> {
            if (relationshipAdapter != null) {
                relationshipAdapter.addItems(relationships);
            }
        };
    }

    public Consumer<List<Trio<RelationshipType, String, Integer>>> setRelationshipTypes() {
        return this::initFab;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQ_ADD_RELATIONSHIP && resultCode == RESULT_OK && data != null) {
            String teiA = data.getStringExtra("TEI_A_UID");
            presenter.addRelationship(teiA, relationshipType.uid());
        }
    }

    private void openRelationship(RFACLabelItem item) {
        Pair<RelationshipType, String> pair = (Pair<RelationshipType, String>) item.getWrapper();
        goToRelationShip(pair.val0(), pair.val1());
    }

    private void initFab(List<Trio<RelationshipType, String, Integer>> relationshipTypes) {

        RapidFloatingActionContentLabelList rfaContent = new RapidFloatingActionContentLabelList(getAbstracContext());
        rfaContent.setOnRapidFloatingActionContentLabelListListener(
                new RapidFloatingActionContentLabelList.OnRapidFloatingActionContentLabelListListener() {
                    @Override
                    public void onRFACItemLabelClick(int position, RFACLabelItem item) {
                        openRelationship(item);
                    }

                    @Override
                    public void onRFACItemIconClick(int position, RFACLabelItem item) {
                        openRelationship(item);
                    }
                });
        List<RFACLabelItem> items = new ArrayList<>();
        for (Trio<RelationshipType, String, Integer> trio : relationshipTypes) {
            RelationshipType relationshipTypeAux = trio.val0();
            int resource = trio.val2();
            items.add(new RFACLabelItem<Pair<RelationshipType, String>>()
                    .setLabel(relationshipTypeAux.displayName())
                    .setResId(resource)
                    .setLabelTextBold(true)
                    .setLabelBackgroundDrawable(ContextCompat.getDrawable(getAbstracContext(), R.drawable.bg_chip))
                    .setIconNormalColor(ColorUtils.getPrimaryColor(getAbstracContext(), ColorUtils.ColorType.PRIMARY_DARK))
                    .setWrapper(Pair.create(relationshipTypeAux, trio.val1()))
            );
        }

        if (!items.isEmpty()) {
            rfaContent.setItems(items)
                    .setItems(items)
                    .setIconShadowRadius(RFABTextUtil.dip2px(getAbstracContext(), 5))
                    .setIconShadowColor(0xff888888)
                    .setIconShadowDy(RFABTextUtil.dip2px(getAbstracContext(), 5))
                    .setIconShadowColor(0xff888888);

            rfaHelper = new RapidFloatingActionHelper(getAbstracContext(), binding.rfabLayout, binding.rfab, rfaContent).build();
        }
    }

    private void goToRelationShip(@NonNull RelationshipType relationshipTypeModel,
                                  @NonNull String teiTypeUid) {
        rfaHelper.toggleContent();
        relationshipType = relationshipTypeModel;
        presenter.goToAddRelationship(teiTypeUid);
    }
}
