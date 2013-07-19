package com.plesba.ioio_logger;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.content.SharedPreferences;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import ioio.lib.api.AnalogInput;
import ioio.lib.api.exception.ConnectionLostException;

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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		settings = getPreferences(MODE_PRIVATE);
		write = new FileWriter(this);
		initializeSettings();
		initializeGui();
	}

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
			write.data(System.currentTimeMillis() + "," + leftReading + ","
					+ rightReading);
			setDisplayText(leftReading, leftHeightValue);
			setDisplayText(rightReading, rightHeightValue);
			setDisplayText("200", speedValue);
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
	}

	private void initializeGui() {
		setContentView(R.layout.activity_main);
		leftHeightValue = (TextView) findViewById(R.id.leftHeighDisplay);
		rightHeightValue = (TextView) findViewById(R.id.rightHeightDisplay);
		speedValue = (TextView) findViewById(R.id.SpeedDisplay);
		normalCalButton = (Button) findViewById(R.id.CalibrateNormalButton);
		maxCalButton = (Button) findViewById(R.id.CalibrateMaxButton);
		enableUi(true);
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
		super.onStop();
		// The activity is no longer visible (it is now "stopped")
		// close log file
		write.syslog("MainActivity stopped");
		write.finalize();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
