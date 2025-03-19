package com.example.smssender;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 101;
    private TextView statusText, sentCountText;
    private TextInputEditText phoneNumberInput, messageInput, totalSmsInput, delayInput;
    private MaterialButton startButton;
    private LinearProgressIndicator sendProgressBar;

    private int sentCount = 0;

    // Broadcast receiver actions
    private static final String SENT_ACTION = "SMS_SENT";
    private static final String DELIVERED_ACTION = "SMS_DELIVERED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        initializeUI();

        // Register broadcast receivers with RECEIVER_NOT_EXPORTED flag
        registerReceiver(sentReceiver, new IntentFilter(SENT_ACTION), RECEIVER_NOT_EXPORTED);
        registerReceiver(deliveredReceiver, new IntentFilter(DELIVERED_ACTION), RECEIVER_NOT_EXPORTED);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkSMSPermission()) {
                    if (validateInputs()) {
                        resetUI();
                        sendSMSMessages();
                    }
                } else {
                    requestSMSPermission();
                }
            }
        });
    }

    private void initializeUI() {
        startButton = findViewById(R.id.startButton);
        statusText = findViewById(R.id.statusText);
        sentCountText = findViewById(R.id.sentCountText);
        phoneNumberInput = findViewById(R.id.phoneNumberInput);
        messageInput = findViewById(R.id.messageInput);
        totalSmsInput = findViewById(R.id.totalSmsInput);
        delayInput = findViewById(R.id.delayInput);
        sendProgressBar = findViewById(R.id.sendProgressBar);
        updateSentCountDisplay();
    }

    private boolean validateInputs() {
        String phone = phoneNumberInput.getText().toString().trim();
        String message = messageInput.getText().toString().trim();
        String totalSmsStr = totalSmsInput.getText().toString().trim();
        String delayStr = delayInput.getText().toString().trim();

        if (phone.isEmpty()) {
            phoneNumberInput.setError("Phone number is required");
            return false;
        }
        if (message.isEmpty()) {
            messageInput.setError("Message is required");
            return false;
        }
        if (totalSmsStr.isEmpty()) {
            totalSmsInput.setError("Total SMS is required");
            return false;
        }
        if (delayStr.isEmpty()) {
            delayInput.setError("Delay is required");
            return false;
        }

        try {
            int totalSms = Integer.parseInt(totalSmsStr);
            int delayMs = Integer.parseInt(delayStr) * 1000; // Convert seconds to milliseconds
            if (totalSms <= 0) {
                totalSmsInput.setError("Total SMS must be greater than 0");
                return false;
            }
            if (delayMs < 1000) { // Minimum 1 second delay
                delayInput.setError("Delay must be at least 1 second");
                return false;
            }
        } catch (NumberFormatException e) {
            totalSmsInput.setError("Invalid number format");
            return false;
        }

        return true;
    }

    private void resetUI() {
        sentCount = 0;
        sendProgressBar.setProgress(0);
        statusText.setText("Status: Ready");
        updateSentCountDisplay();
    }

    private void updateSentCountDisplay() {
        String totalSmsStr = totalSmsInput.getText().toString().trim();
        int totalSms = totalSmsStr.isEmpty() ? 0 : Integer.parseInt(totalSmsStr);
        sentCountText.setText("Sent: " + sentCount + "/" + totalSms);
    }

    private void updateProgressBar(int current) {
        int totalSms = Integer.parseInt(totalSmsInput.getText().toString().trim());
        int progressPercentage = (current * 100) / totalSms;
        sendProgressBar.setProgress(progressPercentage);
    }

    private boolean checkSMSPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSMSPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.SEND_SMS},
                SMS_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS Permission Granted", Toast.LENGTH_SHORT).show();
            if (validateInputs()) {
                resetUI();
                sendSMSMessages();
            }
        } else {
            Toast.makeText(this, "SMS Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendSMSMessages() {
        final SmsManager smsManager = SmsManager.getDefault();
        startButton.setEnabled(false);

        final String phoneNumber = phoneNumberInput.getText().toString().trim();
        final String message = messageInput.getText().toString().trim();
        final int totalSms = Integer.parseInt(totalSmsInput.getText().toString().trim());
        final int delayMs = Integer.parseInt(delayInput.getText().toString().trim()) * 1000;

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 1; i <= totalSms; i++) {
                    try {
                        final int current = i;

                        PendingIntent sentPI = PendingIntent.getBroadcast(MainActivity.this, i,
                                new Intent(SENT_ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        PendingIntent deliveredPI = PendingIntent.getBroadcast(MainActivity.this, i,
                                new Intent(DELIVERED_ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                        smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusText.setText("Status: Attempting " + current + " of " + totalSms + " SMS");
                                updateProgressBar(current - 1);
                            }
                        });
                        Thread.sleep(delayMs);
                    } catch (Exception e) {
                        final String errorMessage = e.getMessage();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusText.setText("Status: Error sending SMS: " + errorMessage);
                                startButton.setEnabled(true);
                            }
                        });
                        break;
                    }
                }
            }
        }).start();
    }

    private BroadcastReceiver sentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int resultCode = getResultCode();
            int totalSms = Integer.parseInt(totalSmsInput.getText().toString().trim());
            switch (resultCode) {
                case RESULT_OK:
                    sentCount++;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Status: Sent " + sentCount + " of " + totalSms + " SMS");
                            updateSentCountDisplay();
                            updateProgressBar(sentCount);

                            if (sentCount >= totalSms) {
                                startButton.setEnabled(true);
                                statusText.setText("Status: All messages sent successfully");
                                sendProgressBar.setProgress(100);
                            }
                        }
                    });
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    updateErrorStatus("Status: Stopped - No service");
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    updateErrorStatus("Status: Stopped - Radio off");
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    updateErrorStatus("Status: Stopped - Credit isn't enough or generic failure");
                    break;
                default:
                    updateErrorStatus("Status: Stopped - Unknown error (Code: " + resultCode + ")");
                    break;
            }
        }
    };

    private void updateErrorStatus(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText(message);
                startButton.setEnabled(true);
            }
        });
    }

    private BroadcastReceiver deliveredReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getResultCode() == RESULT_OK) {
                // Optional: Log delivery if needed
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(sentReceiver);
        unregisterReceiver(deliveredReceiver);
    }
}