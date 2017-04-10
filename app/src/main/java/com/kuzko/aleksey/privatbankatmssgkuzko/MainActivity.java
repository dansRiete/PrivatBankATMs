package com.kuzko.aleksey.privatbankatmssgkuzko;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.kuzko.aleksey.privatbankatmssgkuzko.datamodel.Device;

import java.util.List;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private final static String BASE_URL = "https://api.privatbank.ua/p24api/";
    PrivatService privatService;
    List<Device> devices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();
        privatService = retrofit.create(PrivatService.class);
        privatService.fetchATM("")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(example -> setAllDevices(example.getDevices()),
                        throwable -> Toast.makeText(this, throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show());

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

        // Add a marker in Sydney and move the camera

        LatLng chernivtsi = new LatLng(48.29, 25.93);
        /*devices.forEach(device -> {
            LatLng currPos = new LatLng(Double.valueOf(device.getLatitude()), Double.valueOf(device.getLongitude()));
            mMap.addMarker(new MarkerOptions().position(currPos).title(device.getPlaceUa()));
        });
        for(Device device : devices){
            LatLng currPos = new LatLng(Double.valueOf(device.getLatitude()), Double.valueOf(device.getLongitude()));
            mMap.addMarker(new MarkerOptions().position(currPos).title(device.getPlaceUa()));
        }*/

        mMap.moveCamera(CameraUpdateFactory.newLatLng(chernivtsi));
    }
}
