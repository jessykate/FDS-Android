package com.fog.fogdiscovery;

import com.fog.fds.R;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.os.Handler;
import android.os.Message;                                                                                                                                                            
import android.util.Log;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class FogDiscoveryActivity extends Activity {

	// for debugging
	private static final String TAG = "FOG FDS Activity";
	private static final boolean debug = true;
	
	public static final int RECEIVE = 0;
	
	// array adapters and layouts (I don't really understand these, 
	// just doing What I'm Told)
	private ArrayAdapter<String> remoteFogList;
	private ListView remoteFogListView;
	private EditText announceLocalFogField;
	private Button announceButton;
	private StringBuffer outStringBuffer;
	// variable for the actual service object. 
	private FogDiscoveryService fdsService = null;
		
	Handler serviceQueue; // pass messages to the service thread

	// receive messages from the service thread
	public final Handler activityQueue = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (debug) Log.e(TAG, "-- activityQueue handleMessage()");
			switch(msg.what) {
			// receiving an inbound fog announcement or confirmation from the
			// network thread
			case RECEIVE: 
				String readBuf = (String) msg.obj;
				// XXX TODO want peer info here as well
				remoteFogList.add("observed fog: " + readBuf);
				break;
			}
		}
	};
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	if(debug) Log.e(TAG, "onCreate()");    	
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
    
    public void onStart() {                                                                                                                                                           
        super.onStart();
        if(debug) Log.e(TAG, "onStart()");
        setup();
    }  
    
    @Override
    public synchronized void onResume() {
        super.onResume();
        if(debug) { 
        	Log.e(TAG, "-- onResume()--");
        	Log.e(TAG, "-- starting fdsService network thread--");
        }        
        fdsService.start();                                                                                                                                                         
		// this has to go after start() or serviceQueue won't be initialized
		// yet. 
        serviceQueue = fdsService.serviceQueue;

    }
    
    private void setup() {
    	Log.d(TAG, "in setup()");
    	
    	// initialize array adapters    	
    	remoteFogList = new ArrayAdapter<String>(this, R.layout.message);
    	remoteFogListView = (ListView) findViewById(R.id.in);
    	remoteFogListView.setAdapter(remoteFogList);
    	
		/* initialize some temporary fields to let users manually enter in fog
		 * names to be broadcast (will later be replaced by fog apps) */
    	 announceLocalFogField = (EditText) findViewById(R.id.edit_text_out);
    	 //announceLocalFogField.setOnEditorActionListener(mWriteListener); 
    	
         // initialize sendButton with listeners 
         announceButton = (Button) findViewById(R.id.button_send);
         announceButton.setOnClickListener(new OnClickListener() {
			
			public void onClick(View v) {
				if (debug) Log.e(TAG, "[announceButton clicked]");
				// send a message using the content of the editable text field. 
				TextView view = (TextView) findViewById(R.id.edit_text_out);
				String message = view.getText().toString();
				sendMessage(message);
			}
		});
        
        // initialize the actual FDS service 
        fdsService = new FogDiscoveryService(this, activityQueue, remoteFogList);
        
        // initialize buffers
        outStringBuffer = new StringBuffer("");
    }

    public synchronized void onPause() {                                                                                                                                              
        super.onPause();
        if(debug) Log.e(TAG, "--onPause() --");
    }

    public void onStop() {
        super.onStop();
        if (fdsService != null) fdsService.stop();
        if(debug) Log.e(TAG, "-- onStop() --");
    }

    public void onDestroy() {
        super.onDestroy();
        // Stop the Broadcast chat services
        if (fdsService != null) fdsService.stop();
        if(debug) Log.e(TAG, "--- onDestroy() ---");
    }

    
    private void sendMessage(String message) {
      if(debug) Log.e(TAG, "-- input received; calling sendingMessage() --");    
        if (message.length() > 0 ) {
            // Get the message bytes and tell the fdsService to write
        	// byte[] send = message.getBytes();

			/* obtainMessage(...) populates a message from the global message
			 * pool with the arguments passed in. the message handler is
			 * automatically set to "this", which in this case is the
			 * serviceQueue. sendToTarget() sends the message to the handler. the
			 * handler.handleMessage() method will be called upon receipt of the
			 * message. */
        	serviceQueue.obtainMessage(FogDiscoveryService.SEND, message)
        	.sendToTarget();
        	
            // Reset out string buffer to zero and clear the edit text field
            outStringBuffer.setLength(0);
            announceLocalFogField.setText(outStringBuffer);
        }
    }
}