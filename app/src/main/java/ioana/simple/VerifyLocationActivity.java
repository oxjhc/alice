package ioana.simple;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.UpnpServiceResponseListener;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;
import android.os.Bundle;
import android.util.Log;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.List;
import java.util.Random;

public class VerifyLocationActivity extends Activity {
    public static final String TAG = "VerifyLocationActivity";

    private final int AP_PORT = 1832;
    private final String AP_SERVICE_URN = "urn:schemas-oxjhc-club:service:TeaParty:1";
    private WifiP2pDevice apDevice = null;

    ProgressDialog progressDialog;

    private String userKeyName;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private WiFiDirectBroadcastReceiver receiver;

    private final IntentFilter intentFilter = new IntentFilter();
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;

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

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        verifyLocation();

        // TODO: After group creation and service discovery are both finished, connect to peers

//        try {
//            verifyLocation();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public void createWiFiDirectGroup() {
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully created group");
            }

            @Override
            public void onFailure(int code) { logFailureMessage("create group", code); }
        });
    }

    private void discoverServiceFromAP() {
        // 1. Set up service response listener
        UpnpServiceResponseListener upnpListener = new UpnpServiceResponseListener() {
            @Override
            public void onUpnpServiceAvailable(List<String> uniqueServiceNames, WifiP2pDevice srcDevice) {
                // TODO: Find service of the form "urn:schemas-oxjhc-club:service:TeaParty:1"

                if (uniqueServiceNames.size() > 0) {
                    for (String name : uniqueServiceNames) {
                        Log.d(TAG, "Found service: " + name);
                        apDevice = srcDevice;
                    }
                    clearServiceRequests();
                    connectToAP();
                } else {
                    Log.d(TAG, "No service found");
                }
            }
        };

        manager.setUpnpServiceResponseListener(channel, upnpListener);

        // 2. Register service request
        WifiP2pUpnpServiceRequest serviceRequest =
                WifiP2pUpnpServiceRequest.newInstance(AP_SERVICE_URN);

        manager.addServiceRequest(channel,
                serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Successfully added request");
                    }

                    @Override
                    public void onFailure(int code) { logFailureMessage("add service request", code); }
                });

        // 3. Discover service
        manager.discoverServices(channel, new ActionListener() {
            @Override
            public void onSuccess() { Log.d(TAG, "Successfully initiated service discovery"); }

            @Override
            public void onFailure(int code) { logFailureMessage("initiate service discovery", code); }
        });
    }

    public void connectToAP() {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = apDevice.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        config.groupOwnerIntent = 15; // highest inclination to be the group owner

        manager.connect(channel, config, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully connected to AP (status: " + apDevice.status + ")");
            }

            @Override
            public void onFailure(int code) { logFailureMessage("connect to AP", code); }
        });
    }

    public void logFailureMessage(String action, int code) {
        switch (code) {
            case WifiP2pManager.P2P_UNSUPPORTED :
                Log.d(TAG, "Failed to " + action + " because P2P isn't supported on this device");
                break;
            case WifiP2pManager.ERROR :
                Log.d(TAG, "Failed to " + action + " due to internal error");
                break;
            case WifiP2pManager.BUSY :
                Log.d(TAG, "Failed to " + action + " because system is too busy");
                break;
            default:
                Log.d(TAG, "Failed to " + action + " for OTHER REASON");
                break;
        }
    }

    /**
     * Clear all registered service discovery requests from the WifiP2pManager.
     */
    public void clearServiceRequests() {
        manager.clearServiceRequests(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully cleared service requests");
            }

            @Override
            public void onFailure(int code) {
                logFailureMessage("clear service requests", code);
            }
        });
    }

    public void verifyLocation() {
        //progressDialog.show();
        createWiFiDirectGroup();
        discoverServiceFromAP();
        //connectToAP();

//        DatagramSocket datagramSocket = new DatagramSocket(AP_PORT);
//        URL url = new URL("http://" + datagramSocket.getInetAddress().getHostAddress());
//        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//        InputStream fromAP = new BufferedInputStream(connection.getInputStream());
//        OutputStream toAP = new BufferedOutputStream(connection.getOutputStream());
//
//        int seqid = receiveSeqId(datagramSocket);
//        sendProofReq(toAP, seqid);
//
//        // DO OTHER COMMUNICATIONS
//
//        connection.disconnect();
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
//        byte[] encodedPublicKey = publicKey.getEncoded();
//        byte[] unonce = new byte[10];
//        new Random().nextBytes(unonce);
//
//        ProofProtos.ProofReq proofReq = ProofProtos.ProofReq.getDefaultInstance();
//        proofReq
//                .toBuilder()
//                    .setUid(ByteString.copyFrom(encodedPublicKey))
//                    .setUnonce(ByteString.copyFrom(unonce))
//                    .setSeqid(seqid)
//                    .setVid(ByteString.copyFromUtf8(getResources().getString(R.string.vid)))
//                . build();
//
//        try {
//            Signature signature = Signature.getInstance("SHA256withECDSA");
//            signature.initSign(privateKey);
//            signature.update(proofReq.toByteArray());
//            byte[] sig = signature.sign();
//            ProofProtos.SignedProofReq signedProofReq = ProofProtos.SignedProofReq.getDefaultInstance();
//            signedProofReq
//                    .toBuilder()
//                        .setProofreq(proofReq)
//                        .setSig(ByteString.copyFrom(sig))
//                    .build();
//            toAP.write(signedProofReq.toByteArray());
//            toAP.flush();
//            toAP.close();
//        } catch (Exception e) {}
    }


    // FROM WIFIDIRECTDEMO - PROBABLY NOT NEEDED

    /**
     * Remove all peers and clear all fields. This is called on BroadcastReceiver receiving a
     * state change event.
     */
    public void resetData() {
//        DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager()
//                .findFragmentById(R.id.frag_list);
//        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager()
//                .findFragmentById(R.id.frag_detail);
//        if (fragmentList != null) {
//            fragmentList.clearPeers();
//        }
//        if (fragmentDetails != null) {
//            fragmentDetails.resetViews();
//        }
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
//        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }
}
