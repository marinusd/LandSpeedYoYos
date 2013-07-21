package com.plesba.ioio_logger;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.plesba.ioio_logger.GPS_ListenerService.GPSBinder;

public class MainActivity extends IOIOActivity {
	private SharedPreferences settings;
	private TextView leftHeightValue;
	private TextView rightHeightValue;
	private TextView speedValue;
	private Button maxCalButton;
	private Button normalCalButton;
	private float normalHeightLeft;
	private float normalHeightRight;
	private float maxHeightLeft;
	private float maxHeightRight;
	private FileWriter write;
	private PowerManager.WakeLock wakeLock;
    private ServiceConnection gpsSvcConn;
	private GPS_ListenerService gpsService;
	private boolean isGPSserviceBound;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		write = FileWriter.getInstance();
		settings = getPreferences(MODE_PRIVATE);
		initializeSettings();
		startGPSService();
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		// what burns more power, the GPS or a dimmed screen?  If the screen, perhaps that 
		//  should be PARTIAL_WAKE_LOCK... but be aware that PARTIAL ignores everything
		//   including the power button. :)
		wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "android-ioio");
		initializeGui();  // this method actually acquires the wakelock
		// start out the Data file
		write.data("SYSTIME,LH,RH,GPSTIME,LAT,LONG,SPEED");
	}

	// start of stuff to bind to GPS service so we can get values
	private void startGPSService() {
		startService(new Intent(this, GPS_ListenerService.class));
		gpsSvcConn = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder binder) {
				GPSBinder gpsBinder = (GPSBinder) binder;
				gpsService = gpsBinder.getService();
				isGPSserviceBound = true;
				write.syslog("GPS service bound");
			}
			@Override
			public void onServiceDisconnected(ComponentName name) {
				isGPSserviceBound = false;
				write.syslog("GPS service came unbound?");
			}
		};
		Intent intent = new Intent(this, GPS_ListenerService.class);
		bindService(intent, gpsSvcConn, Context.BIND_AUTO_CREATE);
		write.syslog("Started to bind to GPS service");
	}
	// end of stuff to bind to GPS service 	
	
	class Looper extends BaseIOIOLooper {
		private AnalogInput leftInput;
		private AnalogInput rightInput;

		@Override
		public void setup() throws ConnectionLostException {
			leftInput = ioio_.openAnalogInput(44);
			rightInput = ioio_.openAnalogInput(42);
			enableUi(true);
			write.syslog("Looper setup complete");
		}

		@Override
		public void loop() throws ConnectionLostException, InterruptedException {
			/*
			 * turn the ride height sensor readings into strings. The IOIO
			 * read() on an analog input returns a floats in the 0.0-1.0 range
			 * The measurement values have lots of trailing digits But the
			 * sensors we have will never show 1.0, so 0.99xxx is the max and we
			 * only need two digits of resolution So trim off the leading '0.'
			 * of the string, and throw away digits past two example:
			 * 0.234529684f => 23 (substring(2,4)
			 */
			final String leftReading = Float.toString(leftInput.read())
					.substring(2, 4);
			final String rightReading = Float.toString(rightInput.read())
					.substring(2, 4);
			// the GPS service needs to be bound before these will work...
			if (isGPSserviceBound) {
				final String gpsTime = Long.toString(gpsService.getTime());
				final String latitude =  gpsService.getLat();
				final String longitude = gpsService.getLong();
				final String speed =     gpsService.getSpeed();
				write.data(System.currentTimeMillis() + "," + leftReading + ","
						+ rightReading + "," + gpsTime + "," + latitude + ","
						+ longitude + "," + speed);
				setDisplayText(speed, speedValue);
				// don't have GPS data, still write something
			} else {
				write.data(System.currentTimeMillis() + "," + leftReading + ","
						+ rightReading + ",n/a,n/a,n/a,n/a");
			}
			setDisplayText(leftReading, leftHeightValue);
			setDisplayText(rightReading, rightHeightValue);
			Thread.sleep(300);
		}

		@Override
		public void disconnected() {
			enableUi(false);
		}
	}

	
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}

	private void initializeSettings() {
		normalHeightLeft = settings.getFloat("LH_NORMAL", 0f);
		maxHeightLeft = settings.getFloat("LH_MAX", 0f);
		normalHeightRight = settings.getFloat("RH_NORMAL", 99f);
		maxHeightRight = settings.getFloat("RH_MAX", 99f);
		write.syslog("read settings from preferences");
		write.syslog("LH NORM: " + normalHeightLeft + " LH MAX: " + maxHeightLeft +
					 "RH NORM: " + normalHeightRight +" RH MAX: " + maxHeightRight);	
	}

	private void initializeGui() {
		setContentView(R.layout.activity_main);
		leftHeightValue = (TextView) findViewById(R.id.leftHeighDisplay);
		rightHeightValue = (TextView) findViewById(R.id.rightHeightDisplay);
		speedValue = (TextView) findViewById(R.id.SpeedDisplay);
		normalCalButton = (Button) findViewById(R.id.CalibrateNormalButton);
		maxCalButton = (Button) findViewById(R.id.CalibrateMaxButton);
		wakeLock.acquire();
		enableUi(true);
		write.syslog("gui initialized");
	}

	private void enableUi(final boolean enable) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				normalCalButton.setEnabled(enable);
				maxCalButton.setEnabled(enable);
			}
		});
	}

	private void setDisplayText(final String str, final TextView view) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				view.setText(str);
			}
		});
	}

	public void calibrateNormal(View v) {
		// get the latest service output
		normalHeightLeft = 32.0f;
		normalHeightRight = 33.0f;
		settings.edit().putFloat("LH_NORMAL", normalHeightLeft);
		settings.edit().putFloat("RH_NORMAL", normalHeightRight);
		settings.edit().commit();
		write.syslog("calibrated normal");
	}

	public void calibrateMax(View v) {
		// get the latest service output
		maxHeightLeft = 98.0f;
		maxHeightRight = 99.0f;
		settings.edit().putFloat("LH_MAX", maxHeightLeft);
		settings.edit().putFloat("RH_MAX", maxHeightRight);
		settings.edit().commit();
		write.syslog("calibrated max");
	}

	@Override
	protected void onPause() {
		super.onPause();
		write.syslog("MainActivity paused");
	}

	@Override
	protected void onResume() {
		super.onResume();
		write.syslog("MainActivity resumed");
	}

	@Override
	protected void onStop() {
		// The activity is no longer visible (it is now "stopped")
		super.onStop();
		// stop GPS service
		unbindService(gpsSvcConn);
		stopService(new Intent(this, GPS_ListenerService.class));
		// close log files... but write this first.
		write.syslog("MainActivity stopped");
		write.finalize();
		// release wake lock
		wakeLock.release();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.CalNormItem:
                calibrateNormal(null);
                return true;
            case R.id.CalMaxItem:
                calibrateMax(null);
                return true;
         //   case R.id.rollLogsItem:
                // start new files somehow
                    // write.rollLogs();
         //       return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }




}
