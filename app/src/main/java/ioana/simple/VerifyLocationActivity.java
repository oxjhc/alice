package ioana.simple;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdTxtRecordListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.WifiP2pManager.UpnpServiceResponseListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;
import android.os.Bundle;
import android.util.Log;

import com.google.protobuf.ByteString;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class VerifyLocationActivity extends Activity {
    public static final String TAG = "VerifyLocationActivity";

    ProgressDialog progressDialog;
    private final int AP_PORT = 1832;
    private String userKeyName;
    private KeyPair keyPair;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    private NsdManager.DiscoveryListener discoveryListener;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private WiFiDirectBroadcastReceiver receiver;
    private final IntentFilter intentFilter = new IntentFilter();
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;

    final HashMap<String, String> nearbyAPs = new HashMap<String, String>();

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

        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully created group");
            }

            @Override
            public void onFailure(int code) {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                Log.d(TAG, "Failed to create group (code: " + code + ")");
            }
        });

        discoverService(); // add Upnp service response listener to manager

        WifiP2pUpnpServiceRequest serviceRequest = WifiP2pUpnpServiceRequest.newInstance("urn:schemas-oxjhc-club:service:Rabbits:1");
        manager.addServiceRequest(channel,
                serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Successfully added request");
                    }

                    @Override
                    public void onFailure(int code) {
                        // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                        Log.d(TAG, "Failed to add request");
                    }
                });

                manager.discoverServices(channel, new ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Successfully discovered services");
                    }

                    @Override
                    public void onFailure(int code) {
                        // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                        Log.d(TAG, "Unsuccessfully discovered services");

                        if (code == WifiP2pManager.P2P_UNSUPPORTED) {
                            Log.d(TAG, "P2P isn't supported on this device.");
                        } // else if (...) ...
                    }
                });


        // AFTER GROUP CREATE AND SERVICE DISCOVERY FINISH, THEN PEER CONNECT :)


//        WifiP2pDnsSdServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
//        manager.addServiceRequest(channel,
//                serviceRequest,
//                new WifiP2pManager.ActionListener() {
//                    @Override
//                    public void onSuccess() {
//                        Log.d(TAG, "Request successfully added!");
//                    }
//
//                    @Override
//                    public void onFailure(int code) {
//                        // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
//                        Log.d(TAG, "Action failed :(");
//                    }
//                });
//


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

    private void discoverService() {
//        DnsSdTxtRecordListener txtListener = new DnsSdTxtRecordListener() {
//
//            @Override
//            /* Callback includes:
//             * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
//             * record: TXT record dta as a map of key/value pairs.
//             * device: The device running the advertised service.
//             */
//            public void onDnsSdTxtRecordAvailable(
//                    String fullDomain, Map<String,String> record, WifiP2pDevice device) {
//                Log.d(TAG, "DnsSdTxtRecord available -" + record.toString());
//                nearbyAPs.put(device.deviceAddress, record.get("AP_NAME_HERE"));
//            }
//        };

//        DnsSdServiceResponseListener servListener = new DnsSdServiceResponseListener() {
//            @Override
//            public void onDnsSdServiceAvailable(String instanceName, String registrationType,
//                                                WifiP2pDevice resourceType) {
//
//                // Update the device name with the human-friendly version from
//                // the DnsTxtRecord, assuming one arrived.
//                resourceType.deviceName = nearbyAPs
//                        .containsKey(resourceType.deviceAddress) ? nearbyAPs
//                        .get(resourceType.deviceAddress) : resourceType.deviceName;
//
//                // Add to the custom adapter defined specifically for showing
//                // wifi devices.
//                /* WiFiDirectServicesList fragment = (WiFiDirectServicesList) getFragmentManager()
//                        .findFragmentById(R.id.frag_peerlist);
//                WiFiDevicesAdapter adapter = ((WiFiDevicesAdapter) fragment
//                        .getListAdapter());
//
//                adapter.add(resourceType);
//                adapter.notifyDataSetChanged();
//                Log.d(TAG, "onBonjourServiceAvailable " + instanceName); */
//            }
//        };

//        manager.setDnsSdResponseListeners(channel, servListener, txtListener);



        UpnpServiceResponseListener upnpListener = new UpnpServiceResponseListener() {
            @Override
            public void onUpnpServiceAvailable(List<String> uniqueServiceNames, WifiP2pDevice srcDevice) {
                    // check that it is of the form "urn:schemas-oxjhc-club:service:Rabbits:1"
                    if (uniqueServiceNames.size() > 0) {
                        for (String name : uniqueServiceNames) {
                            Log.d(TAG, "service name: " + name);
                        }
                        Log.d(TAG, "Found Sauyon!");
                        clearServiceRequests();
                    } else {
                        Log.d(TAG, "Couldn't find Sauyon :(");
                    }
            }
        };

        manager.setUpnpServiceResponseListener(channel, upnpListener);
    }

    public void clearServiceRequests() {
        manager.clearServiceRequests(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Cleared service requests");
            }

            @Override
            public void onFailure(int code) {
                Log.d(TAG, "Failed to clear service requests");
            }
        });
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
        /*DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_list);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }*/
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
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

        /*DatagramSocket datagramSocket = new DatagramSocket(AP_PORT);
        URL url = new URL("http://" + datagramSocket.getInetAddress().getHostAddress());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        InputStream fromAP = new BufferedInputStream(connection.getInputStream());
        OutputStream toAP = new BufferedOutputStream(connection.getOutputStream());

        int seqid = receiveSeqId(datagramSocket);
        sendProofReq(toAP, seqid);
        receiveProofResp(fromAP);*/

        // DO OTHER COMMUNICATIONS

        //connection.disconnect();
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
