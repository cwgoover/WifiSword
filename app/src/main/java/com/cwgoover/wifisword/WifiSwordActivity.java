package com.cwgoover.wifisword;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;

public class WifiSwordActivity extends Activity implements OnClickListener {
    private static final String TAG = "WifiSword";
    private static final int DELAY_TIME = 8*60*1000;
    //private static final int DELAY_TIME = 60*1000;
    private static final int WIFI_SIGNAL_STRENGTH = 5;
    private boolean FLAG = false;

    private WifiManager mWifiManager;
    private NotificationManager mNotificationManager;
    private Handler mHandler;
    private Handler mUIHandler;
    //private Looper mLooper;
    private Notification mNotification;
    private PendingIntent pi;

    boolean mWifiEnabled, mWifiConnected;
    boolean mWaiting;
    String mWifiSsid;
    int mWifiRssi, mWifiLevel;

    private Button mStartButton;
    private Button mStopButton;

    private final BroadcastReceiver mReceivedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                // Indicating that Wi-Fi has been enabled, disabled, enabling, disabling, or unknown.
                mWifiEnabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                mWaiting = (state == WifiManager.WIFI_STATE_DISABLING)
                        || (state == WifiManager.WIFI_STATE_ENABLING);
                Log.d(TAG, "WIFI_STATE_CHANGED_ACTION: mWifiEnabled=" + mWifiEnabled
                        + ", mWifiConnected=" + mWifiConnected);
                updateWifiState();

            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                // Indicating that the state of Wi-Fi connectivity has changed.
                final NetworkInfo networkInfo = (NetworkInfo)
                        intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                boolean wasConnected = mWifiConnected;
                mWifiConnected = networkInfo != null && networkInfo.isConnected();
                // If Connected grab the signal strength and ssid
                if (mWifiConnected) {
                    // try getting it out of the intent first
                    WifiInfo info = (WifiInfo) intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                    if (info == null) {
                        info = mWifiManager.getConnectionInfo();
                    }
                    if (info != null) {
                        mWifiSsid = huntForSsid(info);
                    } else {
                        mWifiSsid = null;
                    }
                } else if (!mWifiConnected) {
                    mWifiSsid = null;
                }
                Log.d(TAG, "NETWORK_STATE_CHANGED_ACTION: mWifiEnabled=" + mWifiEnabled
                        + ", mWifiConnected=" + mWifiConnected);
                if (wasConnected && !mWifiConnected) {
                    notifyByRing();
                }
                updateWifiState();

            } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
                // The RSSI (signal strength) has changed.
                mWifiRssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -200);
                mWifiLevel = WifiManager.calculateSignalLevel(
                        mWifiRssi, WIFI_SIGNAL_STRENGTH);
//                Log.d(TAG, "RSSI_CHANGED_ACTION: mWifiLevel=" + mWifiLevel);
            }
        }
    };

    Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "mRunnable");
            handleStartMotion();
            // This schedule a runnable to run every 5 minutes
            mHandler.postDelayed(this, DELAY_TIME);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_sword);

        mStartButton = (Button) findViewById(R.id.startBtn);
        mStartButton.setOnClickListener(this);
        mStopButton = (Button) findViewById(R.id.stopBtn);
        mStopButton.setOnClickListener(this);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mReceivedReceiver, filter);

        mUIHandler = new Handler();
        final HandlerThread ht = new HandlerThread(WifiSwordActivity.class.getSimpleName());
        ht.start();
        mHandler = new Handler(ht.getLooper());
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceivedReceiver);
        mHandler.removeCallbacks(mRunnable);
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (v == mStartButton) {
            Toast.makeText(this, getString(R.string.toast_open), Toast.LENGTH_SHORT).show();
            FLAG = true;
            mHandler.removeCallbacks(mRunnable);
            mHandler.post(mRunnable);
        } else if (v == mStopButton) {
            Toast.makeText(this, getString(R.string.toast_close), Toast.LENGTH_SHORT).show();
            FLAG = false;
            mHandler.removeCallbacks(mRunnable);
            handleStopMotion();
        }
    }

    private void handleStartMotion() {
        Log.d(TAG, "handleStartMotion: wifi enable: mWifiConnected=" + mWifiConnected);
        if (!mWifiConnected) {
            mWifiManager.setWifiEnabled(true);
        } else {
            Log.d(TAG, "handleStartMotion:refresh state");
            mWifiManager.setWifiEnabled(false);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
            mWifiManager.setWifiEnabled(true);
        }
    }

    private void handleStopMotion() {
        Log.d(TAG, "handleStopMotion: wifi disable");
        mWifiManager.setWifiEnabled(false);
    }

    private String huntForSsid(WifiInfo info) {
        String ssid = info.getSSID();
        if (ssid != null) return ssid;

        // OK, it's not in the connectionInfo; we have to go hunting for it.
        List<WifiConfiguration> networks = mWifiManager.getConfiguredNetworks();
        for (WifiConfiguration net : networks) {
            if (net.networkId == info.getNetworkId()) {
                return net.SSID;
            }
        }
        return null;
    }

    private void notifyByRing() {
        Log.d(TAG, "notify disabled state in the notification");
        if (mNotification == null) {
            mNotification = new Notification();
            mNotification.when = 0;
        }
        CharSequence message = "WifiSword's message";
        // sound
        mNotification.defaults |= Notification.DEFAULT_SOUND;
        // dismissable
        mNotification.flags = Notification.FLAG_AUTO_CANCEL;
        // ticker
        mNotification.tickerText = getString(R.string.ticker);
        // The PendingIntent to launch our activity if the user selects this
        // notification.  Note the use of FLAG_CANCEL_CURRENT so that, if there
        // is already an active matching pending intent, cancel it and replace
        // it with the new array of Intents.
        if (pi == null) {
            Intent intent = new Intent();
            pi = PendingIntent.getBroadcast(this, 0, intent, 0);
        }
//        mNotification.setLatestEventInfo(this, message, message, pi);
        // The reason I was getting the error was because an img MUST be added, if not, it will not show!
        mNotificationManager.notify(R.string.ticker, mNotification);
    }

    private void updateWifiState() {
        Log.d(TAG, "updateWifiState: FLAG=" + FLAG + ", mWaiting=" + mWaiting);
        if (mWaiting || !FLAG) return;
        if (!mWifiEnabled) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... arg0) {
                    Log.d(TAG, "updateWifiState: set true");
                    mWifiManager.setWifiEnabled(true);
                    return null;
                }
            }.execute();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
}
