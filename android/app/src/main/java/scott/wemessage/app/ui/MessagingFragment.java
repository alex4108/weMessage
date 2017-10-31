package scott.wemessage.app.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.TextView;

import scott.wemessage.R;
import scott.wemessage.app.connection.ConnectionService;
import scott.wemessage.commons.connection.json.action.JSONAction;
import scott.wemessage.commons.connection.json.message.JSONMessage;
import scott.wemessage.commons.types.ActionType;
import scott.wemessage.commons.types.FailReason;
import scott.wemessage.commons.types.ReturnType;

public abstract class MessagingFragment extends Fragment {

    private final int ERROR_SNACKBAR_DURATION = 5;

    protected void showMessageSendFailureSnackbar(JSONMessage jsonMessage,  ReturnType returnType){
        switch (returnType) {
            case INVALID_NUMBER:
                showErroredSnackbar(getString(R.string.message_delivery_failure_invalid_number));
                break;
            case NUMBER_NOT_IMESSAGE:
                showErroredSnackbar(getString(R.string.message_delivery_failure_imessage));
                break;
            case GROUP_CHAT_NOT_FOUND:
                showErroredSnackbar(getString(R.string.message_delivery_failure_group_chat));
                break;
            case SERVICE_NOT_AVAILABLE:
                showErroredSnackbar(getString(R.string.message_delivery_failure_service));
                break;
            case ASSISTIVE_ACCESS_DISABLED:
                showErroredSnackbar(getString(R.string.message_delivery_failure_assistive));
                break;
            case UI_ERROR:
                showErroredSnackbar(getString(R.string.message_delivery_failure_ui_error));
                break;
        }
    }

    protected void showActionFailureSnackbar(JSONAction jsonAction, ReturnType returnType){
        ActionType actionType = ActionType.fromCode(jsonAction.getActionType());

        if (actionType == null){
            showErroredSnackbar(getString(R.string.action_failure_generic));
            return;
        }

        switch (actionType){
            case CREATE_GROUP:
                switch (returnType){
                    case UNKNOWN_ERROR:
                        showErroredSnackbar(getString(R.string.action_failure_create_group_unknown));
                        break;
                    case INVALID_NUMBER:
                        showErroredSnackbar(getString(R.string.action_failure_create_group_invalid_number));
                        break;
                    case NUMBER_NOT_IMESSAGE:
                        showErroredSnackbar(getString(R.string.action_failure_create_group_number_not_imessage));
                        break;
                    case ASSISTIVE_ACCESS_DISABLED:
                        showErroredSnackbar(getString(R.string.action_failure_create_group_assistive_access));
                        break;
                    case UI_ERROR:
                        showErroredSnackbar(getString(R.string.action_failure_create_group_ui_error));
                        break;
                    default:
                        showErroredSnackbar(getString(R.string.action_failure_create_group_unknown));
                        break;
                }
                break;
            case RENAME_GROUP:
                switch (returnType){
                    case UNKNOWN_ERROR:
                        showErroredSnackbar(getString(R.string.action_failure_rename_group_unknown));
                        break;
                    case GROUP_CHAT_NOT_FOUND:
                        showErroredSnackbar(getString(R.string.action_failure_rename_group_not_found));
                        break;
                    case ASSISTIVE_ACCESS_DISABLED:
                        showErroredSnackbar(getString(R.string.action_failure_rename_group_assistive_access));
                        break;
                    case UI_ERROR:
                        showErroredSnackbar(getString(R.string.action_failure_rename_group_ui_error));
                        break;
                    default:
                        showErroredSnackbar(getString(R.string.action_failure_rename_group_unknown));
                        break;
                }
                break;
            case ADD_PARTICIPANT:
                switch (returnType){
                    case UNKNOWN_ERROR:
                        showErroredSnackbar(getString(R.string.action_failure_add_participant_unknown));
                        break;
                    case INVALID_NUMBER:
                        showErroredSnackbar(getString(R.string.action_failure_add_participant_invalid_number));
                        break;
                    case NUMBER_NOT_IMESSAGE:
                        showErroredSnackbar(getString(R.string.action_failure_add_participant_number_not_imessage));
                        break;
                    case GROUP_CHAT_NOT_FOUND:
                        showErroredSnackbar(getString(R.string.action_failure_add_participant_group_not_found));
                        break;
                    case ASSISTIVE_ACCESS_DISABLED:
                        showErroredSnackbar(getString(R.string.action_failure_add_participant_assistive_access));
                        break;
                    case UI_ERROR:
                        showErroredSnackbar(getString(R.string.action_failure_add_participant_ui_error));
                        break;
                    default:
                        showErroredSnackbar(getString(R.string.action_failure_add_participant_unknown));
                        break;
                }
                break;
            case REMOVE_PARTICIPANT:
                switch (returnType){
                    case UNKNOWN_ERROR:
                        showErroredSnackbar(getString(R.string.action_failure_remove_participant_unknown));
                        break;
                    case INVALID_NUMBER:
                        showErroredSnackbar(getString(R.string.action_failure_remove_participant_invalid_number));
                        break;
                    case GROUP_CHAT_NOT_FOUND:
                        showErroredSnackbar(getString(R.string.action_failure_remove_participant_group_not_found));
                        break;
                    case ASSISTIVE_ACCESS_DISABLED:
                        showErroredSnackbar(getString(R.string.action_failure_remove_participant_assistive_access));
                        break;
                    case UI_ERROR:
                        showErroredSnackbar(getString(R.string.action_failure_remove_participant_ui_error));
                        break;
                    default:
                        showErroredSnackbar(getString(R.string.action_failure_remove_participant_unknown));
                        break;
                }
                break;
            case LEAVE_GROUP:
                switch (returnType){
                    case UNKNOWN_ERROR:
                        showErroredSnackbar(getString(R.string.action_failure_leave_chat_unknown));
                        break;
                    case GROUP_CHAT_NOT_FOUND:
                        showErroredSnackbar(getString(R.string.action_failure_leave_chat_group_not_found));
                        break;
                    case ASSISTIVE_ACCESS_DISABLED:
                        showErroredSnackbar(getString(R.string.action_failure_leave_chat_assistive_access));
                        break;
                    case UI_ERROR:
                        showErroredSnackbar(getString(R.string.action_failure_leave_chat_ui_error));
                        break;
                    default:
                        showErroredSnackbar(getString(R.string.action_failure_leave_chat_unknown));
                        break;
                }
                break;
            default:
                showErroredSnackbar(getString(R.string.action_failure_generic));
                break;
        }
    }

    protected void showAttachmentSendFailureSnackbar(FailReason reason){
        switch (reason){
            case MEMORY:
                showErroredSnackbar(getString(R.string.attachment_send_failure_size));
                break;
            default:
                showErroredSnackbar(getString(R.string.attachment_send_failure_generic));
                break;
        }
    }

    protected void showAttachmentReceiveFailureSnackbar(FailReason reason){
        switch (reason){
            case MEMORY:
                showErroredSnackbar(getString(R.string.attachment_receive_failure_size));
                break;
            default:
                showErroredSnackbar(getString(R.string.attachment_receive_failure_generic));
                break;
        }
    }

    protected boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    protected boolean isConnectionServiceRunning(){
        return isServiceRunning(ConnectionService.class);
    }


    private void showErroredSnackbar(String message){
        if (getView() != null) {
            final Snackbar snackbar = Snackbar.make(getView(), message, ERROR_SNACKBAR_DURATION * 1000);

            snackbar.setAction(getString(R.string.dismiss_button), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    snackbar.dismiss();
                }
            });
            snackbar.setActionTextColor(getResources().getColor(R.color.brightRedText));

            View snackbarView = snackbar.getView();
            TextView textView = snackbarView.findViewById(android.support.design.R.id.snackbar_text);
            textView.setMaxLines(5);

            snackbar.show();
        }
    }
}