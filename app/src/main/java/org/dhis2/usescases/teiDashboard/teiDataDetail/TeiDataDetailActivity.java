package org.dhis2.usescases.teiDashboard.teiDataDetail;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuInflater;
import android.view.View;

import androidx.appcompat.widget.PopupMenu;
import androidx.databinding.DataBindingUtil;

import org.dhis2.App;
import org.dhis2.Bindings.Bindings;
import org.dhis2.R;
import org.dhis2.data.forms.FormFragment;
import org.dhis2.data.forms.FormViewArguments;
import org.dhis2.databinding.ActivityTeidataDetailBinding;
import org.dhis2.usescases.general.ActivityGlobalAbstract;
import org.dhis2.usescases.map.MapSelectorActivity;
import org.dhis2.usescases.teiDashboard.DashboardProgramModel;
import org.dhis2.utils.Constants;
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus;

import java.util.Locale;

import javax.inject.Inject;

import io.reactivex.functions.Consumer;

@SuppressWarnings("squid:MaximumInheritanceDepth")
public class TeiDataDetailActivity extends ActivityGlobalAbstract implements TeiDataDetailContracts.TeiDataDetailView {
    ActivityTeidataDetailBinding binding;

    @Inject
    TeiDataDetailContracts.TeiDataDetailPresenter presenter;

    private DashboardProgramModel dashboardProgramModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((App) getApplicationContext()).userComponent().plus(new TeiDataDetailModule(getIntent().getStringExtra("ENROLLMENT_UID"))).inject(this);

        supportPostponeEnterTransition();
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_teidata_detail);
        binding.setPresenter(presenter);

        init(getIntent().getStringExtra("TEI_UID"),
                getIntent().getStringExtra("PROGRAM_UID"),
                getIntent().getStringExtra("ENROLLMENT_UID"));

        binding.programLockLayout.setOnClickListener(this::showScheduleContentOptions);
    }

    private void showScheduleContentOptions(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.deactivate:
                    presenter.onDeactivate(dashboardProgramModel);
                    break;
                case R.id.complete:
                    presenter.onComplete(dashboardProgramModel);
                    break;
                case R.id.activate:
                    presenter.onActivate(dashboardProgramModel);
                    break;
                case R.id.reOpen:
                    presenter.onReOpen(dashboardProgramModel);
                    break;
                default:
                    break;
            }
            return false;
        });// to implement on click event on items of menu
        MenuInflater inflater = popup.getMenuInflater();
        int menuId = 0;
        if (dashboardProgramModel.getCurrentEnrollment().status() == EnrollmentStatus.ACTIVE) {
            menuId = R.menu.tei_detail_options_active;
        } else if (dashboardProgramModel.getCurrentEnrollment().status() == EnrollmentStatus.CANCELLED) {
            menuId = R.menu.tei_detail_options_cancelled;
        } else if (dashboardProgramModel.getCurrentEnrollment().status() == EnrollmentStatus.COMPLETED) {
            menuId = R.menu.tei_detail_options_completed;
        }
        if (menuId != 0) {
            inflater.inflate(menuId, popup.getMenu());
            popup.show();
        }
    }


    @Override
    public void init(String teiUid, String programUid, String enrollmentUid) {
        presenter.init(this, teiUid, programUid, enrollmentUid);
    }

    @Override
    public void setData(DashboardProgramModel program) {
        this.dashboardProgramModel = program;
        binding.setDashboardModel(program);
        binding.setProgram(program.getCurrentProgram());
        binding.setEnrollmentStatus(program.getCurrentEnrollment().status());
        binding.executePendingBindings();

        if (program.getCurrentProgram().captureCoordinates()) {
            binding.coordinatesLayout.setVisibility(View.VISIBLE);
            binding.location1.setOnClickListener(v -> presenter.onLocationClick());
            binding.location2.setOnClickListener(v -> presenter.onLocation2Click());
        }

        supportStartPostponedEnterTransition();


        initForm();


    }

    private void initForm() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.dataFragment, FormFragment.newInstance(
                        FormViewArguments.createForEnrollment(dashboardProgramModel.getCurrentEnrollment().uid()), true,
                        false))
                .commit();
    }


    @Override
    public Consumer<EnrollmentStatus> handleStatus() {
        return enrollmentStatus -> {
            Bindings.setEnrolmentIcon(binding.programLock, enrollmentStatus);
            Bindings.setEnrolmentText(binding.programLockText, enrollmentStatus);
            binding.setEnrollmentStatus(enrollmentStatus);
            binding.executePendingBindings();
            initForm();
        };
    }

    @Override
    public void setLocation(double latitude, double longitude) {
        binding.lat.setText(String.format(Locale.US, "%.5f", latitude));
        binding.lon.setText(String.format(Locale.US, "%.5f", longitude));
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_OK);
        if (getSupportFragmentManager().getFragments().get(0) instanceof FormFragment)
            ((FormFragment) getSupportFragmentManager().getFragments().get(0)).onBackPressed(false);
        else
            finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.RQ_MAP_LOCATION && resultCode == RESULT_OK) {
            String savedLat = data.getStringExtra(MapSelectorActivity.LATITUDE);
            String savedLon = data.getStringExtra(MapSelectorActivity.LONGITUDE);
            setLocation(Double.valueOf(savedLat), Double.valueOf(savedLon));
            presenter.saveLocation(Double.valueOf(savedLat), Double.valueOf(savedLon));
        }
    }


}