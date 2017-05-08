package ioana.simple;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;
import android.os.AsyncTask;
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
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class ProveLocationTask extends AsyncTask<Void, Void, ProofProtos.SignedLocnProof> {
    private static final String TAG = "ProveLocationTask";

    private final int AP_PORT = 1832;
    private final int DELAY = 200;
    private final String AP_SERVICE_URN = "urn:schemas-oxjhc-club:service:TeaParty:1";
    private WifiP2pDevice apDevice = null;

    private WifiP2pManager manager;
    private WifiManager wifiManager;
    private WifiP2pManager.Channel channel;
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private ByteString vid;
    private AlertDialog.Builder builder;
    private Activity activity;

    private final Lock lock = new ReentrantLock();
    private final Condition pingCond = lock.newCondition();
    private boolean failed = false;
    private boolean ready = false;

    ProveLocationTask(
            WifiP2pManager manager,
            WifiManager wifiManager,
            WifiP2pManager.Channel channel,
            PublicKey publicKey,
            PrivateKey privateKey,
            InputStream vid,
            AlertDialog.Builder builder,
            Activity activity) {
        this.manager = manager;
        this.wifiManager = wifiManager;
        this.channel = channel;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        try {
            this.vid = ByteString.readFrom(vid);
        } catch (IOException e) {
            this.vid = null;
        }
        this.builder = builder;
        this.activity = activity;
    }

    protected ProofProtos.SignedLocnProof doInBackground(Void[] params) {
        Log.d(TAG, "Executing \"doInBackground\"");

        setUpWifiDirect();

        ProofProtos.SignedLocnProof locnProof = getPingFromAP();

        Log.d(TAG, "Finished executing.");

        return locnProof;
    }

    private void setUpWifiDirect() {
        Log.d(TAG, "Starting connection procedure.");
        setUpUpnpListener();
    }

    private void setUpUpnpListener() {
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
                    connectToAP();
                } else {
                    Log.d(TAG, "No service found");
                }
            }
        };

        manager.setUpnpServiceResponseListener(channel, upnpListener);
        createWiFiDirectGroup();
    }

    private void createWiFiDirectGroup() {
        // 2. Create WiFi direct group
        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                try {
                    Thread.sleep(DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "Group info is available.");
                if (group != null) {
                    Log.d(TAG, "Group which previously existed: " + group.getNetworkName());
                    manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            try {
                                Thread.sleep(DELAY);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            Log.d(TAG, "Successfully removed group.");
                            manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    try {
                                        Thread.sleep(DELAY);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    Log.d(TAG, "Successfully created group.");
                                    discoverServiceFromAP();
                                }

                                @Override
                                public void onFailure(int reason) {
                                    logFailureMessage("create group", reason);
                                    tearDownWifiDirect();
                                }
                            });
                        }

                        @Override
                        public void onFailure(int reason) {
                            logFailureMessage("remove group", reason);
                            tearDownWifiDirect();
                        }
                    });
                } else {
                    Log.d(TAG, "No previous group existed.");
                    manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            try {
                                Thread.sleep(DELAY);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            Log.d(TAG, "Successfully created group.");
                            discoverServiceFromAP();
                        }

                        @Override
                        public void onFailure(int reason) {
                            logFailureMessage("create group", reason);
                            tearDownWifiDirect();
                        }
                    });
                }
            }
        });
    }

    private void discoverServiceFromAP() {
        // 3. Register service request and run it
        final WifiP2pUpnpServiceRequest serviceRequest =
                WifiP2pUpnpServiceRequest.newInstance(AP_SERVICE_URN);

        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                try {
                    Thread.sleep(DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "Cleared service requests.");
                manager.addServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        try {
                            Thread.sleep(DELAY);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.d(TAG, "Added service request");
                        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                // Will route to UPNP listener
                                Log.d(TAG, "Discovering services.");
                            }

                            @Override
                            public void onFailure(int reason) {
                                logFailureMessage("discover servives", reason);
                                tearDownWifiDirect();
                            }
                        });
                    }

                    @Override
                    public void onFailure(int reason) {
                        logFailureMessage("add service request", reason);
                        tearDownWifiDirect();
                    }
                });
            }

            @Override
            public void onFailure(int reason) {
                logFailureMessage("clear service requests", reason);
                tearDownWifiDirect();
            }
        });
    }

    private void connectToAP() {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = apDevice.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        config.groupOwnerIntent = 15; // highest inclination to be the group owner

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                try {
                    Thread.sleep(DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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

                Log.d(TAG, "Successfully connected to AP (status: " + status + ").");

                manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
                        try {
                            Thread.sleep(DELAY);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        InetAddress ownerAddress=info.groupOwnerAddress;

                        if (ownerAddress!=null) {
                            Log.d(TAG, "Group owner address: " + ownerAddress.toString());
                        } else {
                            Log.d(TAG, "Connection failed! Try again!");
                            tearDownWifiDirect();
                            return;
                        }

                        if (info.groupFormed) {
                            Log.d(TAG, "Group formed.");
                        } else {
                            Log.d(TAG, "Failed to form group.");
                            tearDownWifiDirect();
                            return;
                        }

                        lock.lock();
                        try {
                            ready = true;
                            pingCond.signal();
                        } finally {
                            lock.unlock();
                        }
                    }
                });
            }

            @Override
            public void onFailure(int reason) {
                logFailureMessage("connect to AP", reason);
                tearDownWifiDirect();
            }
        });
    }

    private ProofProtos.SignedLocnProof getPingFromAP() {
        Boolean notElapsed = true;
        long timeout = TimeUnit.SECONDS.toNanos(10000);
        lock.lock();
        try {
            while (timeout >= 0 && !ready) {
                timeout = pingCond.awaitNanos(timeout);
            }
        } catch (InterruptedException e) {
            if (isCancelled()) {
                Log.d(TAG, "Ping exiting, task cancelled.");
                // No tear down, done in onCancellable
                return null;
            } else if (notElapsed) {
                Log.d(TAG, "Ping did not time out, something odd happened.");
                cancel(true);
                return null;
            } else {
                Log.d(TAG, "SOMETHING WEIRD HAPPENED.");
                cancel(true);
                return null;
            }
        } finally {
            lock.unlock();
        }

        if (failed) {
            Log.d(TAG, "Failed.");
            return null;
        } else if (timeout < 0) {
            Log.d(TAG, "Ping timed out.");
            cancel(true);
            return null;
        }

        Log.d(TAG, "Getting ping from AP.");

        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(AP_PORT);
            socket.setSoTimeout(30000);
        } catch (SocketException e) {
            if (socket != null) {
                socket.disconnect();
                socket.close();
            }
            Log.d(TAG, "Socket error.");
            e.printStackTrace();
            tearDownWifiDirect();
            return null;
        }

        byte[] buf = new byte[8];
        long seqid = 0;
        DatagramPacket packet = new DatagramPacket(buf, 8);

        try {
            Log.d(TAG, "Waiting for seqids.");
            socket.receive(packet);
            seqid = ByteBuffer.wrap(packet.getData()).getLong();
            Log.d(TAG, "Received seqid: " + seqid);
        } catch (IOException e) {
            Log.d(TAG, "Error receiving seqids.");
            e.printStackTrace();
            socket.disconnect();
            socket.close();
            cancel(true);
            return null;
        }

        String baseUrl = "http://" + packet.getAddress().getHostAddress() + ":80";
        URL url;
        HttpURLConnection connection = null;

        try {
            try {
                url = new URL(baseUrl + "/proof_req");
            } catch (IOException e) {
                Log.d(TAG, "URL error.");
                e.printStackTrace();
                socket.disconnect();
                socket.close();
                cancel(true);
                return null;
            }

            do {
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setRequestMethod("POST");
                connection.setChunkedStreamingMode(0);

                OutputStream toAP = new BufferedOutputStream(connection.getOutputStream());

                try {
                    Log.d(TAG, "Waiting for seqid.");
                    socket.receive(packet);
                    seqid = ByteBuffer.wrap(packet.getData()).getLong();
                    Log.d(TAG, "Received seqid: " + seqid);
                } catch (IOException e) {
                    Log.d(TAG, "Error receiving seqids.");
                    e.printStackTrace();
                    socket.disconnect();
                    socket.close();
                    cancel(true);
                    return null;
                }
                Log.d(TAG, "Creating proof req.");
                sendProofReq(toAP, seqid);
                // Keep sending request until 200 OK received
                Log.d(TAG, "Code: " + connection.getResponseCode());
            } while (connection.getResponseCode() != 200);

            InputStream fromAP = new BufferedInputStream(connection.getInputStream());

            ProofProtos.SignedProofResp resp = ProofProtos.SignedProofResp.parseFrom(fromAP);
            Log.d(TAG, "AP response was: " + resp.toString());
            Log.d(TAG, "AP response message was: " + connection.getResponseMessage());

        } catch (Exception e) {
            Log.d(TAG, "Error in getting ping: " + e);
            e.printStackTrace();
            socket.disconnect();
            socket.close();
            cancel(true);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        ProofProtos.SignedLocnProof locnProof;
        try {
            url = new URL(baseUrl + "/proof");

            while (true) {
                Log.d(TAG, "Sending to /proof.");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                InputStream in = new BufferedInputStream(connection.getInputStream());
                if (connection.getResponseCode() == 200) {
                    locnProof = ProofProtos.SignedLocnProof.parseFrom(in);
                    in.close();
                    break;
                }
                in.close();
            }

            Log.d(TAG, "Got location proof:" + locnProof.toString());

        } catch (IOException e) {
            Log.d(TAG, "Error in getting proof: " + e);
            e.printStackTrace();
            socket.disconnect();
            socket.close();
            cancel(true);
            return null;
        } finally {
            connection.disconnect();
            socket.disconnect();
            socket.close();
        }

        tearDownWifiDirect();
        return locnProof;
    }

    private void tearDownWifiDirect() {
        try {
            Thread.sleep(DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Disabling WiFi.");
        wifiManager.setWifiEnabled(false);

        Log.d(TAG, "Tearing down WiFi direct");
        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                try {
                    Thread.sleep(DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "Successfully cleared service requests.");
                manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Successfully removed group.");
                    }

                    @Override
                    public void onFailure(int reason) {
                        logFailureMessage("remove group", reason);
                    }
                });
            }

            @Override
            public void onFailure(int reason) {
                try {
                    Thread.sleep(DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                logFailureMessage("clear service request", reason);
                manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Successfully removed group.");
                    }

                    @Override
                    public void onFailure(int reason) {
                        logFailureMessage("remove group", reason);
                    }
                });
            }
        });

        Log.d(TAG, "Enabling Wifi.");
        wifiManager.setWifiEnabled(true);

        lock.lock();
        try {
            ready = true;
            failed = true;
            pingCond.signal();
        } finally {
            lock.unlock();
        }
    }

    /** Send SignedProofReq proto to AP */
    private void sendProofReq(OutputStream toAP, long seqid) throws IOException {
        Log.d(TAG, "Building proof req.");
        byte[] encodedPublicKey = publicKey.getEncoded();
        byte[] unonce = new byte[10];
        new Random().nextBytes(unonce);

        ProofProtos.ProofReq proofReq =
            ProofProtos.ProofReq.newBuilder()
                .setUid(ByteString.copyFrom(encodedPublicKey))
                .setUnonce(ByteString.copyFrom(unonce))
                .setSeqid(seqid)
                .setVid(vid)
            .build();

        Log.d(TAG, "Proof req is: " + proofReq.toString());

        byte[] sig = new byte[0];
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(proofReq.toByteArray());
            sig = signature.sign();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        }
        ProofProtos.SignedProofReq signedProofReq =
                ProofProtos.SignedProofReq.newBuilder()
                    .setProofreq(proofReq)
                    .setSig(ByteString.copyFrom(sig))
                .build();
        Log.d(TAG, "Sending proof req.");
        toAP.write(signedProofReq.toByteArray());
        toAP.flush();
        Log.d(TAG, "Sent proof req.");
    }

    private void logFailureMessage(String action, int reason) {
        switch (reason) {
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

    @Override
    protected void onCancelled() {
        super.onCancelled();
        Log.d(TAG, "Cancelled.");
        tearDownWifiDirect();
        activity.finish();
    }

    @Override
    protected void onPostExecute(final ProofProtos.SignedLocnProof o) {
        if (o != null) {
            builder
                .setMessage("Do you want to send the proof now or save it for later?")
                .setPositiveButton("Send", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SendProof sendProof = new SendProof(new ProgressDialog(activity),
                                o, new AlertDialog.Builder(activity));
                        try {
                            sendProof.execute(new URL(
                                    activity.getResources().getString(R.string.server_url)));
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        } finally {
                            activity.finish();
                        }
                    }
                })
                .setNeutralButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Singleton.getInstance().addToList(o);
                        activity.finish();
                    }
                }).show();
        }
    }
}
