package ioana.simple;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
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
        URL url = new URL(getResources().getString(R.string.server_url));
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
        } finally {
            urlConnection.disconnect();
        }

        DatagramSocket socket = new DatagramSocket(AP_PORT);

        int seqid = getSeqId(socket);
        sendProofReq(socket, seqid);
    }

    // Receive sequence id from AP
    public int getSeqId(DatagramSocket socket) {
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

    public void sendProofReq(DatagramSocket socket, int seqid) {
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
            byte[] signedProofReqBytes = signedProofReq.toByteArray();
            DatagramPacket signedProofReqMessage = new DatagramPacket(signedProofReqBytes, signedProofReqBytes.length, socket.getInetAddress(), AP_PORT);
            socket.send(signedProofReqMessage);
        } catch (Exception e) {}
    }
}
