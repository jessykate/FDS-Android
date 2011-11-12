package com.fog.fogdiscovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Enumeration;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

public class FogDiscoveryService {
	// for debugging
	private static final String TAG = "FOG FDS Service";
	private static final boolean debug = true;
	
	// message types from FDS service
	public static final int SEND = 0;
	
	private Context fdsContext;
	private Handler activityQueue;
	private ArrayAdapter<String> remoteFogList;
	private FDSNetworkThread fdsNetworkThread;
	
	public Handler serviceQueue;

   /**                                                                                                                                                                               
     * Constructor. Prepares a new Broadcast service.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public FogDiscoveryService(Context context, Handler activityQ, ArrayAdapter<String> rfl) {
        fdsContext = context;
        activityQueue = activityQ;
        remoteFogList = rfl; 
    }   

    // start the actual thread that listens for incoming fog announcements
    public synchronized void start() {
    	if (debug) Log.d(TAG, "starting network thread");
    	
    	fdsNetworkThread = new FDSNetworkThread();
    	fdsNetworkThread.start(); // calls run()
    	serviceQueue = fdsNetworkThread.threadQueue;
    }
    
    public synchronized void stop() {
    	if (debug) Log.d(TAG, "stop thread");
    	if (fdsNetworkThread != null) {
    		fdsNetworkThread.cancel();
    		fdsNetworkThread = null;
    	}
    }
    
    // pass the outbound bytes to the network thread
    /*
    public void write(byte[] out) {
    	if(debug) Log.d(TAG, "sending bytes to network");
    	fdsNetworkThread.write(out);
    } */
    
    // the actual thread
    private class FDSNetworkThread extends Thread {
    	private static final int BROADCAST_PORT = 3141;
    	DatagramSocket sock;
    	InetAddress bcastIP;
    	InetAddress localIP;
    	
    	public Handler threadQueue = new Handler() {
    		@Override
    		public void handleMessage(Message msg) {
    			if (debug) Log.e(TAG, "-- serviceQueue threadQueue handleMessage()");
    			switch(msg.what) {
    			case SEND: // sending an outbound fog announcement
    				String writeMsg = (String) msg.obj;
    				write(writeMsg);
    				remoteFogList.add("announced fog: "+writeMsg);
    				break;					
    			}
    		}
    	};
    	
    	public FDSNetworkThread() {
    		try {
    			bcastIP = getBroadcastAddress();
    			if(debug)Log.d(TAG, "Broadcast IP : " + bcastIP); 
    			
    			localIP = getLocalAddress();
    			if(debug)Log.d(TAG, "Local IP : " + localIP); 
    			
    			sock = new DatagramSocket(BROADCAST_PORT);
    			sock.setBroadcast(true);
    			
    		} catch (IOException e) {
    			Log.e(TAG, "Could not make socket: ", e);
    		}
    	}
    	
    	public void run() {
    		try { 
    			byte[] buf = new byte[1024];

    			// where all the fun happens
    			while(true) {    				
    				try {
	    				// check for receipt of packet over broadcast channel. 
	    				// wait for 1000ms only. 
	    				DatagramPacket packet = new DatagramPacket (buf, buf.length);
	    				sock.setSoTimeout(1000);
	    				sock.receive(packet); 
	
	    				// don't count the message if we hear our own broadcast
	    				InetAddress remoteIP = packet.getAddress();
	    				if (remoteIP.equals(localIP)) continue;
	
	    				// process the received packet
	    				String s = new String(packet.getData(), 0, packet.getLength());
	    				if (debug) Log.d(TAG, "Received response " + s);
	    				
	    				// pass the received message back to the UI. 
	    				activityQueue.obtainMessage(FogDiscoveryActivity.RECEIVE, s).sendToTarget();
    				} catch (SocketTimeoutException e) {
    					Log.d(TAG, "socket receive() timed out, continuing");
    				}	
    			} 
    		} catch (IOException e) {
    	    	e.printStackTrace();
    	    }
    	}

    	public void write(String out) {
    		try {
    			Log.v(TAG, "thread write() received data: " + out);

    			DatagramPacket packet = new DatagramPacket(out.getBytes(), out.length(), bcastIP, BROADCAST_PORT);
    			sock.send(packet);

    			// notify the UI that the message was sent 
    			activityQueue.obtainMessage(FogDiscoveryActivity.RECEIVE, out).sendToTarget();    			
    			
    		} catch (Exception e) {
    			Log.e(TAG, "exception during thread write(): ", e);
    		}
    	}
    	
        private InetAddress getBroadcastAddress() throws IOException {
            WifiManager mWifi = (WifiManager) fdsContext.getSystemService(Context.WIFI_SERVICE);            
            WifiInfo info = mWifi.getConnectionInfo();
            if(debug)Log.d(TAG,"\n\nWiFi Status: " + info.toString());
          
            // DhcpInfo  is a simple object for retrieving the results of a DHCP request
            DhcpInfo dhcp = mWifi.getDhcpInfo(); 
            if (dhcp == null) { 
              Log.d(TAG, "Could not get dhcp info"); 
              return null; 
            } 
                     
            int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask; 
            byte[] quads = new byte[4]; 
            for (int k = 0; k < 4; k++) 
              quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
            
            // Returns the InetAddress corresponding to the array of bytes. 
            return InetAddress.getByAddress(quads);  // The high order byte is quads[0].
        }  

        private InetAddress getLocalAddress()throws IOException {    
            try {
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                    NetworkInterface intf = en.nextElement();
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()) {
                            //return inetAddress.getHostAddress().toString();
                            return inetAddress;
                        }
                    }
                }
            } catch (SocketException ex) {
                Log.e(TAG, ex.toString());
            }
            return null;
        }
        
        public void cancel() {
        	try {
        		sock.close();
        	} catch (Exception e) {
        		Log.e(TAG, "failed to close() network socket: ", e);
        	}
        }

    	
    } // end FDSNetworkThread class
    
} // end FogDiscoveryService class
