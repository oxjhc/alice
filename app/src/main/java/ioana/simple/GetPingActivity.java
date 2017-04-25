package ioana.simple;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;

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
        progressDialog.setMessage(getResources().getString(R.string.progress_msg));
        try {
            getPing();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getPing() throws IOException {
        progressDialog.show();
        URL url = new URL(getResources().getString(R.string.server_url));
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
        } finally {
            urlConnection.disconnect();
        }
    }


}
