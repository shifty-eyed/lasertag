package net.lasertag;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Handler;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

public class AdminSettingsDialog {

    private final MainActivity activity;
    private static final int ADMIN_SETTINGS_INVOCATION_TAP_COUNT = 8;
    private int adminMenuTapsLeft = ADMIN_SETTINGS_INVOCATION_TAP_COUNT;
    private final Handler resetHandler = new Handler();
    private final Runnable resetRunnable;


    public AdminSettingsDialog(MainActivity activity) {
        this.activity = activity;
        resetRunnable = () -> {
            adminMenuTapsLeft = ADMIN_SETTINGS_INVOCATION_TAP_COUNT;
        };

    }

    private void restartService() {
        activity.stopService(new Intent(activity, GameService.class));
        activity.startService(new Intent(activity, GameService.class));
    }

    public void onAdminSettingsInvocationTap() {
        resetHandler.removeCallbacks(resetRunnable);
        adminMenuTapsLeft--;
        if (adminMenuTapsLeft <= 0) {
            showAdminMenuDialog();
            adminMenuTapsLeft = ADMIN_SETTINGS_INVOCATION_TAP_COUNT;
        } else {
            if (adminMenuTapsLeft < 6) {
                Toast.makeText(activity, "Taps left: " + adminMenuTapsLeft, Toast.LENGTH_SHORT).show();
            }
            resetHandler.postDelayed(resetRunnable, 10000);
        }

    }

    private void showAdminMenuDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Set Player ID");
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("PlayerID");
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String inputText = input.getText().toString();
            if (!inputText.isEmpty()) {
                try {
                    int newId = Integer.parseInt(inputText);
                    activity.getConfig().setPlayerId(newId);
                    activity.getThisPlayer().setId(newId);
                    restartService();
                    Toast.makeText(activity, "Player ID set to: " + newId, Toast.LENGTH_SHORT).show();

                } catch (NumberFormatException e) {
                    Toast.makeText(activity, "Invalid input. Please enter a number.", Toast.LENGTH_SHORT).show();
                }
            }
            dialog.dismiss();
            activity.goFullScreen();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            activity.goFullScreen();
        });

        builder.show();
    }
}
