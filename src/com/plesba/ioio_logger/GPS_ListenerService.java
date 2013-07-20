package com.plesba.ioio_logger;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class GPS_ListenerService extends Service {
	private static final String TAG = "GPS_ListenerService";
	private LocationManager locationManager;
	private LocationListener gpsLocationListener;
	private long   lastGPStime;
	private double lastLatitude;
	private double lastLongitude;
	private float  lastSpeed;

	// primitives are atomic, so we can just spit 'em out  (Right?)
	public long   getTime() { return lastGPStime; }
	public double getLat()  { return lastLatitude; }
	public double getLong() { return lastLongitude; }
	public float getSpeed() { return lastSpeed; }
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		// instantiate the inner class
		gpsLocationListener = new GPSLocationListener();
		// get the system manager
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		// register the listener
		locationManager.requestLocationUpdates(
				LocationManager.GPS_PROVIDER,
				333L, // minimum time interval between location updates, in milliseconds
				20,  //  minimum distance between location updates, in meters
				gpsLocationListener);
		Log.i(TAG, "GPS updates requested.");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		locationManager.removeUpdates(gpsLocationListener);
	}

	private class GPSLocationListener implements LocationListener {
		@Override
		public void onLocationChanged(Location location) {
			lastGPStime   = location.getTime();
			lastLatitude  = location.getLatitude();
			lastLongitude = location.getLongitude();
			lastSpeed     = location.getSpeed();
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			Log.i(TAG, "GPS provider status changed to " + status);
		}

		@Override
		public void onProviderEnabled(String provider) {
			Log.i(TAG, "GPS provider enabled.");
		}

		@Override
		public void onProviderDisabled(String provider) {
			Log.w(TAG, "GPS provider disabled?");
		}

	}

}
