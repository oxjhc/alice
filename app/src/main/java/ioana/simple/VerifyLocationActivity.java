package ioana.simple;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.concurrent.ExecutionException;

@TargetApi(21)
public class VerifyLocationActivity extends Activity {
    // Progress Dialog
    ProgressDialog progressDialog;
    private String userKeyName;
    private PublicKey publicKey;
    private PrivateKey privateKey;

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
        progressDialog.show();

        SendProof sendProof = null;
        try {
            sendProof = verifyLocation();
        } catch (IOException e) {
            e.printStackTrace();
        }

//        Long res = null;
//        try {
//            res = sendProof.get();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        }
//        assert res == 1L;
    }

    private class SendProof extends AsyncTask<URL, Integer, Long> {
        protected Long doInBackground(URL... urls) {
            URL url = urls[0];
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            HttpURLConnection urlConnection = null;
            try {
                urlConnection = (HttpURLConnection) url.openConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-type", "application/x-protobuf");
                urlConnection.setDoOutput(true);
                urlConnection.setChunkedStreamingMode(0);

                String proof = "0A200A033132331204313233341A01782204353637382A0179314000000000000000120130";
                byte[] byteProof = hexStringToByteArray(proof);

                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                out.write(byteProof);
                Log.i("tag", "wrote");
                out.flush();
                out.close();

                BufferedInputStream in = new BufferedInputStream(urlConnection.getInputStream());
                String response = in.toString();
                in.close();
                Log.i("tag", urlConnection.getResponseMessage());

            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                urlConnection.disconnect();
            }

            progressDialog.dismiss();
            finishAndRemoveTask();
            return 1L;
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public SendProof verifyLocation() throws IOException {
        URL url = null;
        try {
            url = new URL(getResources().getString(R.string.server_url));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        SendProof sendProof = new SendProof();
        sendProof.execute(url);
        return sendProof;
    }
}
