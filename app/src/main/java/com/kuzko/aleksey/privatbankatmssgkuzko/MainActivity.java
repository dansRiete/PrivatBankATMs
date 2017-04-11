package com.kuzko.aleksey.privatbankatmssgkuzko;

import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.kuzko.aleksey.privatbankatmssgkuzko.datamodel.Device;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    List<Device> devices;
    LatLng chernivtsi = new LatLng(48.29, 25.93);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private String determineCityByCoords(LatLng coords){
        Geocoder gcd = new Geocoder(this, Locale.getDefault());
        List<Address> addresses = null;
        String city = null;
        try {
            addresses = gcd.getFromLocation(chernivtsi.latitude, chernivtsi.longitude, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (addresses != null && addresses.size() > 0){
            city = addresses.get(0).getLocality();
        }else {
            city = "Черновцы";
        }
        Toast.makeText(this, city, Toast.LENGTH_LONG).show();
        return city;
    }

    /*private void showDevices(String city){
        privatBankService.fetchATM(city)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(example -> setAllDevices(example.getDevices()),
                        throwable -> Toast.makeText(this, throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show());
    }*/

    private void centerCameraOnGivenLocation(LatLng givenCoords, float zoom){
        if(givenCoords != null){
            CameraPosition cameraPosition = new CameraPosition.Builder().target(givenCoords).zoom(zoom).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }

    private void setAllDevices(List<Device> devices){
        if(devices != null)
            /*devices.forEach(device -> {
                LatLng currPos = new LatLng(Double.valueOf(device.getLatitude()), Double.valueOf(device.getLongitude()));
                mMap.addMarker(new MarkerOptions().position(currPos).title(device.getPlaceUa()));
            });*/
            for(Device device : devices){
                LatLng currPos = new LatLng(Double.valueOf(device.getLatitude()), Double.valueOf(device.getLongitude()));
                mMap.addMarker(new MarkerOptions().position(currPos).title(device.getPlaceUa()));
            }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        centerCameraOnGivenLocation(chernivtsi, 12);
        Toast.makeText(this, String.valueOf(((UserApplication) this.getApplication()).reloadDevices().size()), Toast.LENGTH_LONG).show();
        //        showDevices(determineCityByCoords(chernivtsi));
    }
}
