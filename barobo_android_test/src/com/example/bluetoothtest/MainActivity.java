package com.example.bluetoothtest;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.support.v4.app.NavUtils;
import android.bluetooth.*;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Context;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.ListView;
import android.widget.AdapterView.*;
import android.widget.AdapterView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.Set;
import java.util.UUID;
import java.util.Timer;
import java.util.TimerTask;
import java.io.IOException;
import java.lang.Float;
import java.lang.String;

public class MainActivity extends Activity implements SensorEventListener{
	private volatile static float mLastX, mLastY, mLastZ;
	private boolean mInitialized;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private final float NOISE = (float) 2.0;
	static int REQUEST_ENABLE_BT = 5;
	private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	Button buttonGo;
	Button buttonReset;
	ArrayAdapter mArrayAdapter;
	BluetoothAdapter mBluetoothAdapter;
	ListView mDevicesList;
	BluetoothDevice[] mBluetoothDevices = new BluetoothDevice[10];
	BluetoothSocket[] mBluetoothSockets = new BluetoothSocket[10];
	int mNumSockets = 0;
	Timer mTimer = null;
	Thread mThread;
	Boolean mThreadFlag = true;
	Boolean isConnecting;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		/* Initialize accel sensor */
		mInitialized = false;
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		
		/* Initialize bluetooth view and stuff */
		mArrayAdapter = new ArrayAdapter(getApplicationContext(),
				android.R.layout.simple_list_item_1);
		mDevicesList = (ListView) findViewById(R.id.listView1);
		mDevicesList.setAdapter(mArrayAdapter);
		mDevicesList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				connectToDevice(position);
			}
		});
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			// Device does not support bluetooth
			Toast.makeText(this, "No bluetooth adapter found",
					Toast.LENGTH_LONG).show();
			finish();
		}
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		} else {
			beginBluetoothConnection();
		}
		addListenerOnButton();
		addListenerOnResetButton();
	}

	protected void onResume() {
		super.onResume();
		mSensorManager.registerListener(this, 
				mAccelerometer, 
				SensorManager.SENSOR_DELAY_NORMAL);
	}
	
	protected void onPause() {
		super.onPause();
		mSensorManager.unregisterListener(this);
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	// can be safely ignored for this demo
	}
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		float x = event.values[0];
		float y = event.values[1];
		float z = event.values[2];
		mLastX = x;
		mLastY = y;
		mLastZ = z;
		//Log.d("blah", "New sensor values " + String.valueOf(x) + " " + String.valueOf(y));
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_ENABLE_BT) {
			// Make sure it was an affirmative result
			if (resultCode == RESULT_OK) {
				beginBluetoothConnection();
			}
		}
	}

	public void addListenerOnButton() {
		buttonGo = (Button) findViewById(R.id.button1);
		buttonGo.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				Log.d("blah","GO clicked.");
				buttonGo.setEnabled(false);
				Thread thread = new Thread() {
					public void run() {
						//connectToDevice(0);
						//connectToDevice(1);
						//beginCopycat2();
						beginAccelDrive();
					}
				};
				thread.start();
				mThreadFlag = true;
			}
		});
	}

	public void addListenerOnResetButton() {
		buttonReset = (Button) findViewById(R.id.button2);
		buttonGo = (Button) findViewById(R.id.button1);
		buttonReset.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				buttonGo.setEnabled(true);
				// Close all open sockets
				for (int i = 0; i < mNumSockets; i++) {
					try {
						mBluetoothSockets[i].close();
					} catch (IOException e) {
					}
				}
				mNumSockets = 0;
				mThreadFlag = false;
			}
		});
	}

	protected void beginBluetoothConnection() {
		Toast.makeText(this, "Showing paired devices...", Toast.LENGTH_LONG)
				.show();
		// Try to display found devices
		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
				.getBondedDevices();
		// If there are paired devices
		if (pairedDevices.size() > 0) {
			// Loop through paired devices
			int i = 0;
			for (BluetoothDevice device : pairedDevices) {
				// Add the name and address to an array adapter to show in a
				// ListView
				mArrayAdapter
						.add(device.getName() + "\n" + device.getAddress());
				// Toast.makeText(this, device.getName(),
				// Toast.LENGTH_LONG).show();
				mBluetoothDevices[i] = device;
				i++;
			}
			mArrayAdapter.notifyDataSetChanged();
		}
	}

	protected void connectToDevice(int position) {
		BluetoothDevice device = mBluetoothDevices[position];
		BluetoothSocket tmp = null;
		BluetoothSocket skt = null;
		try {
			tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
		} catch (IOException e) {
			connectErrorDialog();
			return;
		}
		skt = tmp;
		try {
			skt.connect();
		} catch (IOException e) {
			connectErrorDialog();
			return;
		}
		mBluetoothSockets[mNumSockets] = skt;
		mNumSockets++;
	}

	protected void beginCopycat() {
		// Begin a timer callback that does the copycat stuff every once in a
		// while
		mTimer = new Timer();
		mTimer.schedule(new TimerTask() {
			public void run() {
				int len = 0;
				byte[] buf = new byte[80];

				// Send motor angle query to first mobot
				buf[0] = 0x30 + 10;
				buf[1] = 3;
				buf[2] = 0x00;
				try {
					mBluetoothSockets[0].getOutputStream().write(buf, 0, 3);
				} catch (IOException e) {
				}
				try {
					// Make sure we read all the data
					int msglen = 0;
					len = mBluetoothSockets[0].getInputStream()
							.read(buf, 0, 80);
					msglen = len;
					while (msglen < 2) {
						len = mBluetoothSockets[0].getInputStream().read(buf,
								msglen, 80 - msglen);
						msglen += len;
					}
					while (msglen < buf[1]) {
						len = mBluetoothSockets[0].getInputStream().read(buf,
								msglen, 80 - msglen);
						msglen += len;
					}
				} catch (IOException e) {
				}
				if (len != 0x13) {
					return;
				}
				// Now send those motor angles to all the other connected mobots
				buf[0] = 0x30 + 9;
				buf[18] = 0x00;
				for (int i = 1; i < mNumSockets; i++) {
					try {
						mBluetoothSockets[i].getOutputStream().write(buf, 0,
								buf[1]);
					} catch (IOException e) {
					}
				}
			}
		}, 100, 50);
	}

	protected void beginCopycat2() {
		mThread = new Thread() {
			public void run() {
				int len = 0;
				byte[] buf = new byte[80];
				while (mThreadFlag) {
					len = 0;
					// Send motor angle query to first mobot
					buf[0] = 0x30 + 10;
					buf[1] = 3;
					buf[2] = 0x00;
					try {
						mBluetoothSockets[0].getOutputStream().write(buf, 0, 3);
					} catch (IOException e) {
					}
					try {
						// Make sure we read all the data
						int msglen = 0;
						len = mBluetoothSockets[0].getInputStream().read(buf,
								0, 80);
						msglen = len;
						while (msglen < 2) {
							len = mBluetoothSockets[0].getInputStream().read(
									buf, msglen, 80 - msglen);
							msglen += len;
						}
						while (msglen < buf[1]) {
							len = mBluetoothSockets[0].getInputStream().read(
									buf, msglen, 80 - msglen);
							msglen += len;
						}
					} catch (IOException e) {
					}
					
					// The first bit of each floating point number is the sign 
					// bit according to IEEE754. So, we just need to flip the 
					// first bit of the first and  fourth floating point nums...
					// [0 - command] [1 - size] [2-5 f1] [6-9 f2] [10-13 f3] [14-17 f4] [18 END]
					buf[5] = (byte)(buf[5] ^ 0x80);
					/*
					buf[2] = 0;
					buf[3] = 0;
					buf[4] = 0;
					buf[5] = 0;
					*/
					buf[17] = (byte)(buf[17] ^ 0x80);

					// Now send those motor angles to all the other connected
					// mobots
					buf[0] = 0x30 + 9;
					buf[18] = 0x00;
					for (int i = 1; i < mNumSockets; i++) {
						try {
							mBluetoothSockets[i].getOutputStream().write(buf,
									0, buf[1]);
						} catch (IOException e) {
						}
					}
				}
			}
		};
		mThread.start();
	}
	protected void beginAccelDrive() {
		mThread = new Thread() {
			public void run() {
				int len = 0;
				byte[] buf = new byte[80];
				float motorPower, motorOffset;
				short m1, m2;
				while (mThreadFlag) {
					motorPower = (float)(mLastY/9.81)*500;
					motorOffset = (float)(mLastX/9.81)*256;
					m1 = (short)(-motorPower - motorOffset);
					m2 = (short)(motorPower - motorOffset);
					//Log.d("blah", String.valueOf(mLastY) + " " + String.valueOf(mLastX));
					len = 0;
					// Calculate motor powers and send to the robot
					buf[0] = 0x68;
					buf[1] = 0x0A;
					buf[2] = 0x05;
					buf[3] = (byte)((m1>>8) & 0x00ff);
					buf[4] = (byte)(m1 & 0x00ff);
					buf[5] = 0;
					buf[6] = 0;
					buf[7] = (byte)((m2>>8) & 0x00ff);
					buf[8] = (byte)(m2 & 0x00ff);
					buf[9] = 0x00;
					buf[10] = 0x00;
					try {
						mBluetoothSockets[0].getOutputStream().write(buf, 0, 10);
					} catch (IOException e) {
					}
					try {
						// Make sure we read all the data
						int msglen = 0;
						len = mBluetoothSockets[0].getInputStream().read(buf,
								0, 80);
						msglen = len;
						while (msglen < 2) {
							len = mBluetoothSockets[0].getInputStream().read(
									buf, msglen, 80 - msglen);
							msglen += len;
						}
						while (msglen < buf[1]) {
							len = mBluetoothSockets[0].getInputStream().read(
									buf, msglen, 80 - msglen);
							msglen += len;
						}
					} catch (IOException e) {
					}
				}
			}
		};
		mThread.start();
	}


	protected void connectErrorDialog() {
		AlertDialog ad = new AlertDialog.Builder(this).create();
		ad.setCancelable(false); // This blocks the 'BACK' button
		ad.setMessage("Error Connecting");
		ad.setButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		ad.show();
	}

	protected void connectSuccessDialog() {
		AlertDialog ad = new AlertDialog.Builder(this).create();
		ad.setCancelable(false); // This blocks the 'BACK' button
		ad.setMessage("Connection Successful");
		ad.setButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		ad.show();
	}
}
