package ioana.simple;

import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;
import android.os.AsyncTask;
import android.util.Log;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;

public class VerifyLocationTask extends AsyncTask {
    public static final String TAG = "VerifyLocationTask";

    private final int AP_PORT = 1832;
    private final String AP_SERVICE_URN = "urn:schemas-oxjhc-club:service:TeaParty:1";
    private WifiP2pDevice apDevice = null;

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private WiFiDirectBroadcastReceiver receiver;


    public VerifyLocationTask
            (WifiP2pManager manager, WifiP2pManager.Channel channel, WiFiDirectBroadcastReceiver receiver) {
        this.manager = manager;
        this.channel = channel;
        this.receiver = receiver;
    }

    protected Object doInBackground(Object[] params) {
        Log.d(TAG, "Executing \"doInBackground\"");

        createWiFiDirectGroup();
        discoverServiceFromAP();
        getPingFromAP();

        //removeWiFiDirectGroup();

        return null;
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
        WifiP2pManager.UpnpServiceResponseListener upnpListener = new WifiP2pManager.UpnpServiceResponseListener() {
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
        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
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

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                String status = "";

                switch (apDevice.status) {
                    case WifiP2pDevice.AVAILABLE:
                        status = "AVAILABLE";
                        break;
                    case WifiP2pDevice.CONNECTED:
                        status = "CONNECTED";
                        break;
                    case WifiP2pDevice.FAILED:
                        status = "FAILED";
                        break;
                    case WifiP2pDevice.INVITED:
                        status = "INVITED";
                        break;
                    case WifiP2pDevice.UNAVAILABLE:
                        status = "UNAVAILABLE";
                        break;
                    default:
                        status = "SOMETHING WEIRD HAPPENED";
                        break;
                }

                Log.d(TAG, "Successfully connected to AP (status: " + status + ")");

                manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
                        InetAddress ownerAddress=info.groupOwnerAddress;

                        if (ownerAddress!=null) {
                            Log.d(TAG, "Group owner address: " + ownerAddress.toString());
                        } else {
                            Log.d(TAG, "Connection failed! Try again!");
                        }

                        if (info.groupFormed) {
                            Log.d(TAG, "Group formed");
                        } else {
                            Log.d(TAG, "Failed to form group");
                        }
                    }
                });
            }

            @Override
            public void onFailure(int code) { logFailureMessage("connect to AP", code); }
        });
    }

    /**
     * Clear all registered service discovery requests from the WifiP2pManager.
     */
    public void clearServiceRequests() {
        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
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

    public void getPingFromAP() {
        try {
            DatagramSocket socket = new DatagramSocket(AP_PORT);

//            URL url = new URL("http://" + datagramSocket.getInetAddress().getHostAddress());
//            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//            InputStream fromAP = new BufferedInputStream(connection.getInputStream());
//            OutputStream toAP = new BufferedOutputStream(connection.getOutputStream());

            byte[] buf = new byte[8];
            DatagramPacket packet = new DatagramPacket(buf, 8);
            socket.receive(packet);
            int seqid = ByteBuffer.wrap(packet.getData()).getInt();

            Log.d(TAG, "Received seqid: " + seqid);

//            sendProofReq(toAP, seqid);
//
//            // DO OTHER COMMUNICATIONS
//
//            connection.disconnect();
        } catch (Exception e) {
            Log.d(TAG, e.getClass().toString());
        }
    }

    public void removeWiFiDirectGroup() {
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully removed group");
            }

            @Override
            public void onFailure(int code) {
                logFailureMessage("remove group", code);
            }
        });
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
}
