package ioana.simple;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ViewFlipper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;


public class MainActivity extends AppCompatActivity {

    private final String USER_KEY_NAME = "userKey";
    private KeyPair keyPair = null;

    private ViewFlipper mFlipper;
    private Animation in_from_left;
    private Animation in_from_right;
    private Animation out_to_left;
    private Animation out_to_right;
    private Animation fade_in;
    private Animation fade_out;
    private Menu menu;
    private ListView proofList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        checkKeyPairExists();

        Button getVerifyLocationBtn = (Button) findViewById(R.id.verify_location);
        getVerifyLocationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              verifyLocation(view);
            }
        });
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

//            if (curChild == nextChild) {
//                enableMenu(true);
//                return false;
//            } else if (curChild < nextChild) {
//                mFlipper.setInAnimation(in_from_right);
//                mFlipper.setOutAnimation(out_to_left);
//                mFlipper.setDisplayedChild(nextChild);
//            } else if (curChild > nextChild) {
//                mFlipper.setInAnimation(in_from_left);
//                mFlipper.setOutAnimation(out_to_right);
//                mFlipper.setDisplayedChild(nextChild);
//            }

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
                                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                                .setDigests(KeyProperties.DIGEST_SHA256,
                                        KeyProperties.DIGEST_SHA384,
                                        KeyProperties.DIGEST_SHA512)
                                // Only permit the private key to be used if the user authenticated
                                // within the last five minutes.
                                .setUserAuthenticationRequired(true)
                                .setUserAuthenticationValidityDurationSeconds(5 * 60 /* seconds */)
                                .build());
                keyPair = keyPairGenerator.generateKeyPair();
            }
        } catch (Exception e) { /* NO EXCEPTION EXPECTED */ }
    }

    public void verifyLocation(View view) {
        Intent intent = new Intent(this, VerifyLocationActivity.class);
        intent.putExtra("userKeyName", USER_KEY_NAME);
        intent.putExtra("keyPair", keyPair);
        startActivity(intent);
    }

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
