package org.dhis2.usescases.sms;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import org.dhis2.App;
import org.dhis2.R;
import org.dhis2.usescases.general.ActivityGlobalAbstract;
import org.hisp.dhis.android.core.D2;
import org.hisp.dhis.android.core.sms.domain.interactor.SmsSubmitCase;
import org.hisp.dhis.android.core.sms.domain.repository.SmsRepository;
import org.hisp.dhis.android.core.sms.domain.repository.WebApiRepository;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;

public class SmsSubmitActivity extends ActivityGlobalAbstract {
    public static String ARG_TEI = "tei";
    public static String ARG_EVENT = "event";
    private static final int SMS_PERMISSIONS_REQ_ID = 102;
    private CompositeDisposable disposables;
    private String eventId;
    private String teiId;
    private String gatewayNumber = null;
    private SmsSubmitCase smsSender;
    private LinearLayout layout;
    @Inject
    D2 d2;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms);
        layout = findViewById(R.id.smsLogLayout);
        eventId = getIntent().getStringExtra(ARG_EVENT);
        teiId = getIntent().getStringExtra(ARG_TEI);
        ((App) getApplicationContext()).userComponent().inject(this);
        smsSender = d2.smsModule().smsSubmitCase();
    }

    @Override
    protected void onStart() {
        super.onStart();
        disposables = new CompositeDisposable();
        sendSMS(false);
    }

    @Override
    protected void onStop() {
        disposables.dispose();
        super.onStop();
    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != SMS_PERMISSIONS_REQ_ID) return;
        // Try to send anyway. It will show a right message in case of important permission missing.
        sendSMS(true);
    }

    private WebApiRepository.GetMetadataIdsConfig getMetadataConfig() {
        WebApiRepository.GetMetadataIdsConfig config = new WebApiRepository.GetMetadataIdsConfig();
        config.categoryOptionCombos = true;
        config.dataElements = true;
        config.organisationUnits = true;
        config.programs = true;
        config.trackedEntityAttributes = true;
        config.trackedEntityTypes = true;
        config.users = true;
        return config;
    }

    public void sendSMS(boolean skipPermissionCheck) {
        addText("Started sending");
        // check number
        String number = gatewayNumber;
        if (number == null || number.length() < 2) {
            addText("Asking for number");
            askForNumber();
            return;
        }
        // check permissions
        String[] smsPermissions = new String[]{Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS};
        if (!skipPermissionCheck && !hasPermissions(smsPermissions)) {
            addText("Asking for permissions");
            ActivityCompat.requestPermissions(this, smsPermissions, SMS_PERMISSIONS_REQ_ID);
            return;
        }

        disposables.add(d2.smsModule(
        ).initCase().initSMSModule(number, getMetadataConfig()
        ).andThen(Single.fromCallable(() ->
                d2.trackedEntityModule().trackedEntityDataValues.byEvent().eq(eventId).get())
        ).map(values ->
                d2.eventModule().events.byUid().eq(eventId).one().get().toBuilder()
                        .trackedEntityInstance(teiId)
                        .trackedEntityDataValues(values)
                        .build()
        ).flatMapObservable(
                smsSender::submit
        ).subscribeOn(Schedulers.newThread()
        ).observeOn(AndroidSchedulers.mainThread()
        ).subscribeWith(new DisposableObserver<SmsRepository.SmsSendingState>() {
            @Override
            public void onNext(SmsRepository.SmsSendingState state) {
                switch (state.getState()) {
                    case SENDING:
                        addText(getString(R.string.sms_sending, state.getSent(), state.getTotal()));
                        break;
                    case WAITING_SMS_COUNT_ACCEPT:
                        addText(R.string.sms_waiting_amount_confirm);
                        askForMessagesAmount(state.getTotal());
                        break;
                    case ALL_SENT:
                        addText(R.string.sms_all_sent);
                        break;
                }
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();

                if (e instanceof SmsSubmitCase.PreconditionFailed) {
                    switch (((SmsSubmitCase.PreconditionFailed) e).getType()) {
                        case NO_NETWORK:
                            addText(R.string.sms_error_no_network);
                            break;
                        case NO_CHECK_NETWORK_PERMISSION:
                            addText(R.string.sms_error_no_check_network_permission);
                            break;
                        case NO_RECEIVE_SMS_PERMISSION:
                            addText(R.string.sms_error_no_receive_sms_permission);
                            break;
                        case NO_SEND_SMS_PERMISSION:
                            addText(R.string.sms_error_no_send_sms_permission);
                            break;
                        case NO_GATEWAY_NUMBER_SET:
                            addText(R.string.sms_error_no_gateway_set);
                            break;
                        case NO_USER_LOGGED_IN:
                            addText(R.string.sms_error_no_user_login);
                            break;
                    }
                } else {
                    addText(R.string.sms_error);
                }
            }

            @Override
            public void onComplete() {
                TextView text = new TextView(SmsSubmitActivity.this);
                text.setText(R.string.sms_completed);
                layout.addView(text, 0);
            }
        }));
    }

    private void addText(int text) {
        addText(getString(text));
    }

    private void addText(String text) {
        TextView textView = new TextView(SmsSubmitActivity.this);
        textView.setText(text);
        layout.addView(textView, 0);
    }

    private void askForMessagesAmount(int amount) {
        Bundle args = new Bundle();
        args.putInt(MessagesAmountDialog.ARG_AMOUNT, amount);
        MessagesAmountDialog dialog = new MessagesAmountDialog();
        dialog.setArguments(args);
        dialog.show(getSupportFragmentManager(), null);
    }

    private void askForNumber() {
        ReceiverDialog dialog = new ReceiverDialog();
        dialog.show(getSupportFragmentManager(), null);
    }

    private void numberSet(String number) {
        if (number.length() > 1) {
            gatewayNumber = number;
            sendSMS(false);
        }
    }

    public static class MessagesAmountDialog extends DialogFragment {
        static final String ARG_AMOUNT = "amount";

        public MessagesAmountDialog() {
        }

        @NotNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int amount = getArguments().getInt(ARG_AMOUNT);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(getString(R.string.sms_amount_question, amount));

            builder.setPositiveButton(android.R.string.yes, (dialog, which) ->
                    ((SmsSubmitActivity) getActivity()).smsSender.acceptSMSCount(true)
            );
            builder.setNegativeButton(android.R.string.no, (dialog, which) ->
                    ((SmsSubmitActivity) getActivity()).smsSender.acceptSMSCount(false)
            );
            return builder.create();
        }

        @Override
        public void onPause() {
            dismiss();
            super.onPause();
        }
    }

    public static class ReceiverDialog extends DialogFragment {
        public ReceiverDialog() {
        }

        @NotNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.sms_receiver_number);
            final EditText input = new EditText(getActivity());
            input.setInputType(InputType.TYPE_CLASS_PHONE);
            input.setText("+");
            builder.setView(input);

            builder.setPositiveButton(android.R.string.ok, (dialog, which) ->
                    ((SmsSubmitActivity) getActivity()).numberSet(input.getText().toString())
            );
            builder.setNegativeButton(android.R.string.cancel, (dialog, which) ->
                    dialog.cancel()
            );
            return builder.create();
        }
    }
}
