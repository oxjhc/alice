package ioana.simple;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.Log;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.EditText;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;

@TargetApi(21)
public class ProveLocationActivity extends AppCompatActivity {
    public static final String TAG = "ProveLocationActivity";

    private AlertDialog.Builder alertDialog;
    public EditText nickname;
    public AlertDialog.Builder sendDialog;
    public ProgressDialog sendProgress;

    private String userKeyName;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;

    private final IntentFilter intentFilter = new IntentFilter();
    private AsyncTask task;
    private WifiManager wifiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prove_location);

        nickname = new EditText(this);
        nickname.setHint("Proof name");

        LinearLayoutCompat.LayoutParams lp = new LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.MATCH_PARENT);
        lp.setMarginEnd(24);
        lp.setMarginStart(24);
        nickname.setLayoutParams(lp);

        alertDialog = new AlertDialog.Builder(this).setView(nickname);
        alertDialog.setTitle("Proof created");
        alertDialog.setMessage("Do you want to send the proof now or save it for later?");

        sendProgress = new ProgressDialog(this);
        sendDialog = new AlertDialog.Builder(this);

        Intent intent = getIntent();

        userKeyName = intent.getStringExtra("userKeyName");

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null); // Reload Android key store
            privateKey = (PrivateKey) keyStore.getKey(userKeyName, null);
            publicKey = keyStore.getCertificate(userKeyName).getPublicKey();
        } catch (Exception e) {
            Log.d(TAG, "Key error.");
        }


        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        KeyguardManager keyguardManager = (KeyguardManager) getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
        Intent kg = keyguardManager.createConfirmDeviceCredentialIntent("Alice", "Authenticate your key.");
        startActivity(kg);

        verifyLocation();
    }

    public void verifyLocation() {
        task = new ProveLocationTask(
                manager, wifiManager, channel, publicKey,
                privateKey, getResources().openRawResource(R.raw.vid),
                alertDialog, this).execute();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                supportFinishAfterTransition();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        task.cancel(true);
    }
}
