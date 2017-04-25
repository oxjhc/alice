package ioana.simple;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Bundle;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyGenParameterSpec;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;


public class MainActivity extends AppCompatActivity {

    private final String USER_KEY_NAME = "userKey";

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
                KeyPair keyPair = keyPairGenerator.generateKeyPair();
                Signature signature = Signature.getInstance("SHA256withECDSA");
                signature.initSign(keyPair.getPrivate());
            }
        } catch (Exception e) { /* NO EXCEPTION EXPECTED */ }
    }

    public void verifyLocation(View view) {
        Intent intent = new Intent(this, VerifyLocationActivity.class);
        intent.putExtra("userKeyName", USER_KEY_NAME);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
