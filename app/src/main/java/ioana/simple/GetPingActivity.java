package ioana.simple;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GetPingActivity extends Activity {
    // Progress Dialog
    ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.get_ping);
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Looking for verifier...");
    }

    public void getPing(View view) throws IOException {
        progressDialog.show();
        URL url = new URL("localhost:8080");
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
        } finally {
            urlConnection.disconnect();
        }
    }


}
