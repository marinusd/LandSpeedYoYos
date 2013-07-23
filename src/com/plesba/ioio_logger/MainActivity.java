package com.plesba.ioio_logger;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.annotation.SuppressLint;
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
	private TextView clockView;
	private TextView leftHeightView;
	private TextView rightHeightView;
	private TextView speedView;
	private Button maxCalButton;
	private Button normalCalButton;
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
		// what burns more power, the GPS or a dimmed screen? If the screen,
		// perhaps that
		// should be PARTIAL_WAKE_LOCK... but be aware that PARTIAL ignores
		// everything
		// including the power button. :)
		wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
				"android-ioio");
		initializeGui(); // this method actually acquires the wakelock
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
	private String normalHeightLeft = "";
	private String normalHeightRight = "";
	private String maxHeightLeft = "";
	private String maxHeightRight = "";
	private String lastLeft = "";
	private String lastRight = "";

	class Looper extends BaseIOIOLooper {
		private AnalogInput leftInput;
		private AnalogInput rightInput;
		private String gpsTime = "";
		private String lastGPStime = "";
		private String latitude = "";
		private String lastLat = "";
		private String longitude = "";
		private String lastLong = "";
		private String speed = "";
		private String lastSpeed = "";
		private String leftReading = "";
		private String rightReading = "";
		private String updateTime = "12:00:00";
		@SuppressLint("SimpleDateFormat")
		private SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm:ss");

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
			 * 0.234529684f => 23 (substring(2,4) AND! the ride height sensor
			 * readings go DOWN as the accordian units are extended (and the
			 * readings go UP as the units are compressed. So we subtract the
			 * readings from 1.0 to reverse the relationship.
			 */
			updateTime = clockFormat.format(new Date());
			// leftSide sensor
			String tStr = Float.toString(1.0f - leftInput.read());
			if (tStr.length() < 4) {
				leftReading = tStr;
			} // don't ask for indexOutOfBounds...
			else {
				leftReading = tStr.substring(2, 4);
			}
			// now right side sensor
			tStr = Float.toString(1.0f - rightInput.read());
			if (tStr.length() < 4) {
				rightReading = tStr;
			} else {
				rightReading = tStr.substring(2, 4);
			}
			// the GPS service needs to be bound before these will work...
			if (isGPSserviceBound) {
				gpsTime = gpsService.getTime();
				if (!gpsTime.equals(lastGPStime)) {
					latitude = gpsService.getLat();
					longitude = gpsService.getLong();
					speed = gpsService.getSpeed();
				}
			}
			// see if anything's changed
			if (!lastLeft.equals(leftReading)
					|| !lastRight.equals(rightReading)
					|| !lastLat.equals(latitude) || !lastLong.equals(longitude)
					|| !lastSpeed.equals(speed) || !lastGPStime.equals(gpsTime)) {
				// log the data
				write.data(updateTime + "," + leftReading + "," + rightReading
						+ "," + gpsTime + "," + latitude + "," + longitude
						+ "," + speed);
				// refresh the display
				setDisplayText(clockView, updateTime);
				setDisplayText(speedView, speed);
				setDisplayText(leftHeightView, leftReading);
				setDisplayText(rightHeightView, rightReading);
				// and set the values for next time
				lastLeft = leftReading;
				lastRight = rightReading;
				lastLat = latitude;
				lastLong = longitude;
				lastSpeed = speed;
				lastGPStime = gpsTime;
			}
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
		normalHeightLeft = settings.getString("LH_NORMAL", "0");
		maxHeightLeft = settings.getString("LH_MAX", "99");
		normalHeightRight = settings.getString("RH_NORMAL", "0");
		maxHeightRight = settings.getString("RH_MAX", "99");
		write.syslog("read settings from preferences");
		write.syslog("LH NORM: " + normalHeightLeft + " LH MAX: "
				+ maxHeightLeft + " RH NORM: " + normalHeightRight
				+ " RH MAX: " + maxHeightRight);
	}

	private void initializeGui() {
		setContentView(R.layout.activity_main);
		clockView = (TextView) findViewById(R.id.clockView);
		leftHeightView = (TextView) findViewById(R.id.leftHeighDisplay);
		rightHeightView = (TextView) findViewById(R.id.rightHeightDisplay);
		speedView = (TextView) findViewById(R.id.SpeedDisplay);
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

	private void setDisplayText(final TextView view, final String str) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				view.setText(str);
			}
		});
	}

	public void calibrateNormal(View v) {
		// get the latest service output
		normalHeightLeft = lastLeft;
		normalHeightRight = lastRight;
		settings.edit().putString("LH_NORMAL", lastLeft);
		settings.edit().putString("RH_NORMAL", lastRight);
		settings.edit().commit();
		write.syslog("calibrated normal: LH_NORM " + lastLeft + " RH_NORM "
				+ lastRight);
	}

	public void calibrateMax(View v) {
		// get the latest service output
		maxHeightLeft = lastLeft;
		maxHeightRight = lastRight;
		settings.edit().putString("LH_MAX", lastLeft);
		settings.edit().putString("RH_MAX", lastRight);
		settings.edit().commit();
		write.syslog("calibrated max: LH_MAX " + lastLeft + " RH_MAX "
				+ lastRight);
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
		// release wake lock
		wakeLock.release();
		// close log files... but write this first.
		write.syslog("MainActivity stopped");
		write.finalize();

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
			// case R.id.rollLogsItem:
			// start new files somehow
			// write.rollLogs();
			// return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

}
