package ioana.simple;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;

import com.google.protobuf.ByteString;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Random;

public class VerifyLocationActivity extends Activity {
    // Progress Dialog
    ProgressDialog progressDialog;
    private final int AP_PORT = 1832;
    private String userKeyName;
    private KeyPair keyPair;
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

        try {
            verifyLocation();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void verifyLocation() throws IOException {
        progressDialog.show();
        /*URL urlX = new URL(getResources().getString(R.string.server_url));
        HttpURLConnection urlConnection = (HttpURLConnection) urlX.openConnection();
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
        } finally {
            urlConnection.disconnect();
        }*/

        DatagramSocket datagramSocket = new DatagramSocket(AP_PORT);
        URL url = new URL("http://" + datagramSocket.getInetAddress().getHostAddress());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        InputStream fromAP = new BufferedInputStream(connection.getInputStream());
        OutputStream toAP = new BufferedOutputStream(connection.getOutputStream());

        int seqid = receiveSeqId(datagramSocket);
        sendProofReq(toAP, seqid);
        receiveProofResp(fromAP);

        // DO OTHER COMMUNICATIONS

        connection.disconnect();
    }

    /** Return sequence id, which is received from the AP */
    public int receiveSeqId(DatagramSocket socket) {
        try {
            byte[] buf = new byte[8];
            DatagramPacket packet = new DatagramPacket(buf, 8);
            socket.receive(packet);
            return ByteBuffer.wrap(packet.getData()).getInt();
        } catch (Exception e) {
            // HOPE THIS DOESN'T HAPPEN
            return -1;
        }
    }

    /** Send SignedProofReq proto to AP */
    public void sendProofReq(OutputStream toAP, int seqid) {
        byte[] encodedPublicKey = publicKey.getEncoded();
        byte[] unonce = new byte[10];
        new Random().nextBytes(unonce);

        ProofProtos.ProofReq proofReq = ProofProtos.ProofReq.getDefaultInstance();
        proofReq
                .toBuilder()
                    .setUid(ByteString.copyFrom(encodedPublicKey))
                    .setUnonce(ByteString.copyFrom(unonce))
                    .setSeqid(seqid)
                    .setVid(ByteString.copyFromUtf8(getResources().getString(R.string.vid)))
                . build();

        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(proofReq.toByteArray());
            byte[] sig = signature.sign();
            ProofProtos.SignedProofReq signedProofReq = ProofProtos.SignedProofReq.getDefaultInstance();
            signedProofReq
                    .toBuilder()
                        .setProofreq(proofReq)
                        .setSig(ByteString.copyFrom(sig))
                    .build();
            toAP.write(signedProofReq.toByteArray());
            toAP.flush();
            toAP.close();
        } catch (Exception e) {}
    }

    public void receiveProofResp(InputStream fromAP) {

    }

    public void receivePings() {
        // set up Wifi direct
    }
}
