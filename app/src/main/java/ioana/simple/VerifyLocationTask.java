package ioana.simple;

import android.app.Activity;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class VerifyLocationTask extends AsyncTask<Void, Void, Void> {
    public static final String TAG = "VerifyLocationTask";

    private final int AP_PORT = 1832;
    private final String AP_SERVICE_URN = "urn:schemas-oxjhc-club:service:TeaParty:1";
    private WifiP2pDevice apDevice = null;

    private WifiP2pManager manager;
    private WifiManager wifiManager;
    private WifiP2pManager.Channel channel;
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private String vid;
    private Activity activity;

    private final Lock lock = new ReentrantLock();
    private final Condition pingCond = lock.newCondition();
    private boolean failed = false;

    public VerifyLocationTask(
            WifiP2pManager manager,
            WifiManager wifiManager,
            WifiP2pManager.Channel channel,
            PublicKey publicKey,
            PrivateKey privateKey,
            String vid,
            Activity activity) {
        this.manager = manager;
        this.wifiManager = wifiManager;
        this.channel = channel;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.vid = vid;
        this.activity = activity;
    }

    protected Void doInBackground(Void[] params) {
        Log.d(TAG, "Executing \"doInBackground\"");

        setUpWifiDirect();

        getPingFromAP();

        Log.d(TAG, "Finished executing.");

        return null;
    }

    public void setUpWifiDirect() {
        Log.d(TAG, "Starting connection procedure.");
        setUpUpnpListener();
    }

    public void setUpUpnpListener() {
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

    public void createWiFiDirectGroup() {
        // 2. Create WiFi direct group
        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                Log.d(TAG, "Group info is available.");
                if (group != null) {
                    Log.d(TAG, "Group which previously existed: " + group.getNetworkName());
                    manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Successfully removed group.");
                            manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
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
                Log.d(TAG, "Cleared service requests.");
                manager.addServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
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

                Log.d(TAG, "Successfully connected to AP (status: " + status + ").");

                manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
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

    public void getPingFromAP() {
        lock.lock();
        try {
            pingCond.await();
        } catch (InterruptedException e) {
            if (isCancelled()) {
                Log.d(TAG, "Ping exiting, task cancelled");
                return;
            }
            Log.d(TAG, "IT'S ALL GONE WRONG!");
            return;
        } finally {
            lock.unlock();
        }

        if (failed) {return;}

        Log.d(TAG, "Getting ping from AP.");

        DatagramSocket socket;
        try {
            socket = new DatagramSocket(AP_PORT);
        } catch (SocketException e) {
            Log.d(TAG, "Socket error.");
            e.printStackTrace();
            return;
        }

        byte[] buf = new byte[8];
        long seqid = 0;
        DatagramPacket packet = new DatagramPacket(buf, 8);

        try {
            Log.d(TAG, "Waiting for seqids.");
            for (int i = 0; i < 1; i++) {
                socket.receive(packet);
                seqid = ByteBuffer.wrap(packet.getData()).getLong();
                Log.d(TAG, "Received seqid: " + seqid);
            }
        } catch (IOException e) {
            Log.d(TAG, "Error receiving seqids.");
            e.printStackTrace();
            return;
        } finally {
            socket.close();
        }

        String baseUrl = "http://" + packet.getAddress().getHostAddress() + ":80";
        HttpURLConnection connection;
        try {
            URL url = new URL(baseUrl + "/proof_req");
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setChunkedStreamingMode(0);
        } catch (IOException e) {
            Log.d(TAG, "URL error.");
            e.printStackTrace();
            socket.close();
            return;
        }

        try {
            OutputStream toAP = new BufferedOutputStream(connection.getOutputStream());

            Log.d(TAG, "Creating proof req.");
            sendProofReq(toAP, seqid);

//            Log.d(TAG, "Code: " + connection.getResponseCode());

            InputStream fromAP = new BufferedInputStream(connection.getInputStream());

            ProofProtos.SignedProofResp resp = ProofProtos.SignedProofResp.parseFrom(fromAP);
            Log.d(TAG, "AP response was: " + resp.toString());
            Log.d(TAG, "AP response message was: " + connection.getResponseMessage());

        } catch (Exception e) {
            Log.d(TAG, "Error in getting ping: " + e);
            e.printStackTrace();
        } finally {
            connection.disconnect();
        }

        ProofProtos.SignedLocnProof locnProof;
        try {
            URL url = new URL(baseUrl + "/proof");

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
        } finally {
            connection.disconnect();
        }

    }

    public void tearDownWifiDirect() {
        Log.d(TAG, "Tearing down WiFi direct");
        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
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
    }

    /** Send SignedProofReq proto to AP */
    public void sendProofReq(OutputStream toAP, long seqid) {
        Log.d(TAG, "Building proof req.");
        byte[] encodedPublicKey = publicKey.getEncoded();
        byte[] unonce = new byte[10];
        new Random().nextBytes(unonce);

        ProofProtos.ProofReq proofReq =
            ProofProtos.ProofReq.newBuilder()
                .setUid(ByteString.copyFrom(encodedPublicKey))
                .setUnonce(ByteString.copyFrom(unonce))
                .setSeqid(seqid)
                .setVid(ByteString.copyFromUtf8(vid))
            .build();

        Log.d(TAG, "Proof req is: " + proofReq.toString());

        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(proofReq.toByteArray());
            byte[] sig = signature.sign();
            ProofProtos.SignedProofReq signedProofReq =
                    ProofProtos.SignedProofReq.newBuilder()
                        .setProofreq(proofReq)
                        .setSig(ByteString.copyFrom(sig))
                    .build();
            Log.d(TAG, "Sending proof req.");
            toAP.write(signedProofReq.toByteArray());
            toAP.flush();
            Log.d(TAG, "Sent proof req.");
        } catch (Exception e) {
            Log.d(TAG, "Error while signing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void logFailureMessage(String action, int reason) {
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
        lock.lock();
        try {
            pingCond.signal();
        } finally {
            lock.unlock();
        }
        Log.d(TAG, "Cancelled.");
    }

    @Override
    protected void onPostExecute(Void o) {
        super.onPostExecute(o);
        activity.finish();
    }


    // COPIED

    public static void setIpAssignment(String assign , WifiConfiguration wifiConf)
            throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException, InvocationTargetException {
        Object ipConfiguration = getIpConfiguration(wifiConf);
        setEnumField(ipConfiguration, assign, "ipAssignment");
    }

    public static void setIpAddress(InetAddress addr, int prefixLength, WifiConfiguration wifiConf)
            throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException, InvocationTargetException {
        Object ipConfiguration = getIpConfiguration(wifiConf);
        Object staticIpConfiguration = getDeclaredField(ipConfiguration, "staticIpConfiguration");
        Class laClass = Class.forName("android.net.LinkAddress");
        Constructor laConstructor = laClass.getConstructor(new Class[]{InetAddress.class, int.class});
        Object linkAddress = laConstructor.newInstance(addr, prefixLength);

        Field ipAddress = staticIpConfiguration.getClass().getField("ipAddress");
        ipAddress.set(staticIpConfiguration, linkAddress);
    }

    public static void setGateway(InetAddress addr, WifiConfiguration wifiConf)
            throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException, InvocationTargetException {
        Object ipConfiguration = getIpConfiguration(wifiConf);
        Object staticIpConfiguration = getDeclaredField(ipConfiguration, "staticIpConfiguration");

        Field gateway = staticIpConfiguration.getClass().getField("gateway");
        gateway.set(staticIpConfiguration, addr);
    }

    public static Object getIpConfiguration(WifiConfiguration wifiConfiguration)
            throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException, InvocationTargetException{
        return getDeclaredField(wifiConfiguration, "mIpConfiguration");
    }


    public static Object getDeclaredField(Object obj, String name)
            throws SecurityException, NoSuchFieldException,
            IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object out = f.get(obj);
        return out;
    }

    public static void setEnumField(Object obj, String value, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException{
        Field f = obj.getClass().getField(name);
        f.set(obj, Enum.valueOf((Class<Enum>) f.getType(), value));
    }
}
