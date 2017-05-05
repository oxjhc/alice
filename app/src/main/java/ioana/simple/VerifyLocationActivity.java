package ioana.simple;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.LinkAddress;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

@TargetApi(21)
public class VerifyLocationActivity extends Activity {
    public static final String TAG = "VerifyLocationActivity";

    ProgressDialog progressDialog;

    private String userKeyName;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private WiFiDirectBroadcastReceiver receiver;

    private final IntentFilter intentFilter = new IntentFilter();
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;
    private AsyncTask task;
    private WifiManager wifiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.get_ping);

        Intent intent = getIntent();
        userKeyName = intent.getStringExtra("userKeyName");

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null); // Reload Android key store
            privateKey = (PrivateKey) keyStore.getKey(userKeyName, null);
            publicKey = keyStore.getCertificate(userKeyName).getPublicKey();
        } catch (Exception e) { /* NO EXCEPTION EXPECTED */ }

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getResources().getString(R.string.progress_msg));

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
//        wifiManager.setWifiEnabled(false);
//        wifiManager.setWifiEnabled(true);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        KeyguardManager keyguardManager = (KeyguardManager) getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
        Intent kg = keyguardManager.createConfirmDeviceCredentialIntent("Alice", "Authenticate your key.");
        startActivity(kg);

        verifyLocation();
        // TODO: After group creation and service discovery are both finished, connect to peers
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public void verifyLocation() {
        task = new VerifyLocationTask(
                manager, wifiManager, channel, publicKey,
                privateKey, getResources().openRawResource(R.raw.vid),
                this).execute();
    }



    // FROM WIFIDIRECTDEMO - PROBABLY NOT NEEDED

    /**
     * Remove all peers and clear all fields. This is called on BroadcastReceiver receiving a
     * state change event.
     */
    public void resetData() {
//        DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager()
//                .findFragmentById(R.id.frag_list);
//        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager()
//                .findFragmentById(R.id.frag_detail);
//        if (fragmentList != null) {
//            fragmentList.clearPeers();
//        }
//        if (fragmentDetails != null) {
//            fragmentDetails.resetViews();
//        }
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
//        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        task.cancel(true);
    }
}
