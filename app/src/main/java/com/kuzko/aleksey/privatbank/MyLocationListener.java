package com.kuzko.aleksey.privatbank;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

/**
 * Created by Aleks on 27.02.2017.
 */

public class MyLocationListener implements LocationListener {

    public static Location imHere;
    private static LocationManager locationManager;

    public static void setUpLocationListener(Context context){
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new MyLocationListener();

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15*60*1000, 100, locationListener);
            imHere = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }catch (SecurityException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onLocationChanged(Location loc) {
        imHere = loc;
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }
}
