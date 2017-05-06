package ioana.simple;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.spec.ECGenParameterSpec;


public class MainActivity extends AppCompatActivity {

    private ViewFlipper mFlipper;
    private Animation in_from_left;
    private Animation in_from_right;
    private Animation out_to_left;
    private Animation out_to_right;
    private Animation fade_in;
    private Animation fade_out;
    private Menu menu;
    private ListView proofList;

    private final String USER_KEY_NAME = "userKey";
    private KeyPair keyPair = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        navigation.setOnNavigationItemReselectedListener(mOnNavigationItemReselectedListener);
        menu = navigation.getMenu();

        mFlipper = (ViewFlipper) findViewById(R.id.viewFlipper);
        mFlipper.setAutoStart(false);
        mFlipper.setDisplayedChild(0);

        in_from_left = AnimationUtils.loadAnimation(this, R.anim.in_from_left);
        in_from_right = AnimationUtils.loadAnimation(this, R.anim.in_from_right);
        out_to_left = AnimationUtils.loadAnimation(this, R.anim.out_to_left);
        out_to_right  = AnimationUtils.loadAnimation(this, R.anim.out_to_right);
        fade_in = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        fade_out = AnimationUtils.loadAnimation(this, R.anim.fade_out);

        final FloatingActionButton proveLocation = (FloatingActionButton) findViewById(R.id.proveLocation);
        final ActivityOptions options =
                ActivityOptions.makeCustomAnimation(this, R.anim.in_from_right, R.anim.out_to_left);
        final Intent intent = new Intent(this, ProveLocationActivity.class)
                .putExtra("userKeyName", USER_KEY_NAME);
        proveLocation.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    startActivity(intent, options.toBundle());
                }
                return true;
            }
        });

        proofList = (ListView) findViewById(R.id.proofList);

        proofList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Toast.makeText(getApplicationContext(),
                        "Click ListItem Number " + position, Toast.LENGTH_LONG)
                        .show();
            }
        });

        String[] values = new String[] {
                "Proof 1", "Proof 2", "Proof 3",
                "Proof 1", "Proof 2", "Proof 3",
                "Proof 1", "Proof 2", "Proof 3",
                "Proof 1", "Proof 2", "Proof 3",
                "Proof 1", "Proof 2", "Proof 3"
        };
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.activity_main_proof_list_item, R.id.firstLine, values);

        proofList.setAdapter(adapter);

        Log.d("MainActivity", "Checking keypair.");
        checkKeyPairExists();
    }

    @TargetApi(23)
    public void checkKeyPairExists() {
        try {
            // Check if public/private key pair has already been created
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            if (keyStore.getKey(USER_KEY_NAME, null) == null) {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
                keyPairGenerator.initialize(
                        new KeyGenParameterSpec.Builder(
                                USER_KEY_NAME,
                                KeyProperties.PURPOSE_SIGN)
                                .setAlgorithmParameterSpec(new ECGenParameterSpec("prime256v1"))
                                .setDigests(KeyProperties.DIGEST_SHA256)
                                // Only permit the private key to be used if the user authenticated
                                // within the last five minutes.
                                .setUserAuthenticationRequired(true)
                                .setUserAuthenticationValidityDurationSeconds(5 * 60 /* seconds */)
                                .build());
                keyPair = keyPairGenerator.generateKeyPair();
            }
        } catch (Exception e) { /* NO EXCEPTION EXPECTED */ }
    }

    private void enableMenu(Boolean enable) {
        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setEnabled(enable);
        }
    }
    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            enableMenu(false);

            int curChild = mFlipper.getDisplayedChild();
            int nextId;
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    nextId = R.id.homeView;
                    break;
                case R.id.navigation_dashboard:
                    nextId = R.id.dashboardView;
                    break;
                case R.id.navigation_proofs:
                    nextId = R.id.proofsView;
                    break;
                default:
                    nextId = -1;
            }
            int nextChild = mFlipper.indexOfChild(findViewById(nextId));
            mFlipper.setInAnimation(fade_in);
            mFlipper.setOutAnimation(fade_out);
            mFlipper.setDisplayedChild(nextChild);

            enableMenu(true);
            return true;
        }

    };

    private BottomNavigationView.OnNavigationItemReselectedListener mOnNavigationItemReselectedListener
            = new BottomNavigationView.OnNavigationItemReselectedListener() {
        @Override
        public void onNavigationItemReselected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_proofs:
                    proofList.smoothScrollToPosition(0);
            }
        }
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings, menu);
        return true;
    }

    public static class AboutDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("About")
                    .setMessage("Made by OxJHC.");
            return builder.create();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings_settings:;
                return true;
            case R.id.settings_about:
                DialogFragment newFragment = new AboutDialogFragment();
                newFragment.show(getFragmentManager(), "about");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
