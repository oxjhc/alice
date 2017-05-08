package ioana.simple;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

class SendProof extends AsyncTask<URL, Integer, ProofProtos.SignedToken> {
    private ProgressDialog progressDialog;
    private ProofProtos.SignedToken response;
    private ProofProtos.SignedLocnProof locnProof;
    private AlertDialog.Builder alertDialog;

    SendProof(ProgressDialog progressDialog, ProofProtos.SignedLocnProof locnProof,
                     AlertDialog.Builder alertDialog) {
        this.progressDialog = progressDialog;
        this.locnProof = locnProof;
        this.alertDialog = alertDialog;
    }

    protected void onPreExecute() {
        progressDialog.show();
    }

    protected ProofProtos.SignedToken doInBackground(URL... urls) {
        URL url = urls[0] ;
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-type", "application/x-protobuf");
            urlConnection.setDoOutput(true);
            urlConnection.setChunkedStreamingMode(0);

            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            out.write(locnProof.toByteArray());
            Log.i("tag", "wrote");
            out.flush();
            out.close();

            BufferedInputStream in = new BufferedInputStream(urlConnection.getInputStream());
            response = ProofProtos.SignedToken.parseFrom(in);
            in.close();
            Log.i("tag", urlConnection.getResponseMessage());

        } catch (ProtocolException e) {
            e.printStackTrace();
            return ProofProtos.SignedToken.getDefaultInstance();
        } catch (IOException e) {
            e.printStackTrace();
            return ProofProtos.SignedToken.getDefaultInstance();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        return response;
    }


    protected void onPostExecute(ProofProtos.SignedToken token) {

        progressDialog.dismiss();

        if(token.equals(ProofProtos.SignedToken.getDefaultInstance())) {
            alertDialog.setTitle("Error!");
            alertDialog.setMessage("An error has occurred while sending the proof to the server");
            alertDialog.show();
        } else {
            alertDialog.setTitle("Success!");
            alertDialog.setMessage("");
            alertDialog.show();
        }

    }
}
