package com.kuzko.aleksey.privatbankatmssgkuzko;

import android.Manifest;
import android.app.SearchManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.kuzko.aleksey.privatbankatmssgkuzko.datamodel.Device;
import com.kuzko.aleksey.privatbankatmssgkuzko.datamodel.DeviceAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    LatLng chernivtsi = new LatLng(48.29, 25.93);
    private final static int MY_LOCATION_REQUEST_CODE = 101;
    UserApplication userApplication;
    private final static String BASE_URL = "https://api.privatbank.ua/p24api/";
    private DatabaseHelper databaseHelper;
    private List<DeviceAdapter> devices = new ArrayList<>();
    private RuntimeExceptionDao<DeviceAdapter, Long> markersAdapterDao;
    PrivatBankService privatBankService;
    private SearchView searchView;
    private MenuItem searchItem;
    private SimpleCursorAdapter citySuggestionsAdapter;
    private final static String CURSOR_CITY_KEY = "City";
    private final static String NO_SUGGESTIONS_MSG = "No suggestions";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        this.databaseHelper = new DatabaseHelper(getApplicationContext());
        this.markersAdapterDao = databaseHelper.getMarkersDao();
        citySuggestionsAdapter = new SimpleCursorAdapter(this, android.R.layout.simple_spinner_dropdown_item, null,
                new String[]{CURSOR_CITY_KEY}, new int[]{android.R.id.text1}, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();
        privatBankService = retrofit.create(PrivatBankService.class);
        this.devices = markersAdapterDao.queryForAll();
        userApplication = (UserApplication) getApplication();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);
        searchItem = menu.findItem(R.id.action_search);
        SearchManager searchManager = (SearchManager) this.getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) searchItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(this.getComponentName()));
//        searchView.setIconified(false);
        searchView.setSuggestionsAdapter(citySuggestionsAdapter);
        searchView.setOnCloseListener(() -> {
//            progressBar.setVisibility(ProgressBar.INVISIBLE);
            /*if(weatherRequest != null){
                weatherRequest.cancel();
            }*/
            return false;
        });
        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionClick(int position) {
                Cursor cursor = searchView.getSuggestionsAdapter().getCursor();
                cursor.moveToPosition(position);
                //Takes the city name from a whole string like: "Chernivtsi, Chernivetska obl, Ukraine" --> "Chernivtsi"
                String suggestedCity = cursor.getString(cursor.getColumnIndex(CURSOR_CITY_KEY)).split(",")[0];
                searchView.setQuery(suggestedCity, false);
//                updateWeather(suggestedCity);
                return true;
            }

            @Override
            public boolean onSuggestionSelect(int position) {
                return true;
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String searchedCity) {
//                progressBar.setVisibility(ProgressBar.VISIBLE);
                if (searchedCity != null && !searchedCity.equals("")) {
                    reloadDevices(searchedCity);
                    centerCameraOnGivenLocation(getLocationFromAddress(searchedCity), 15);
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {

                if (query.equals("")) {
//                    progressBar.setVisibility(ProgressBar.INVISIBLE);
                    /*if(weatherRequest != null){
                        weatherRequest.cancel();
                    }*/
                    showSuggestions(null);
                } else {
                    /*weatherService.searchLocations(query).enqueue(new Callback<List<Location>>() {
                        @Override
                        public void onResponse(Call<List<Location>> call, Response<List<Location>> response) {
                            if (response.isSuccessful()) {
                                showSuggestions(response.body());
                            }
                        }
                        @Override
                        public void onFailure(Call<List<Location>> call, Throwable t) {
                            showSuggestions(null);
                        }
                    });*/
                }
                return false;
            }
        });
        return true;
    }

    private void showSuggestions(List<Location> suggestedLocations){
        if(suggestedLocations == null){
            citySuggestionsAdapter.changeCursor(new MatrixCursor(new String[] {BaseColumns._ID, CURSOR_CITY_KEY}));
        }else {
            MatrixCursor mc = new MatrixCursor(new String[]{BaseColumns._ID, CURSOR_CITY_KEY});
            if(!suggestedLocations.isEmpty()){
                for (int i = 0; i < suggestedLocations.size(); i++) {
                    mc.addRow(new Object[]{i, suggestedLocations.get(i)/*.getName()*/});
                }
                citySuggestionsAdapter.changeCursor(mc);
            }else {
                mc.addRow(new Object[]{0, NO_SUGGESTIONS_MSG});
                citySuggestionsAdapter.changeCursor(mc);
            }
        }
    }

    private String determineCityByCoords(LatLng coords) {
        Geocoder gcd = new Geocoder(this, Locale.getDefault());
        List<Address> addresses = null;
        String city = null;
        try {
            addresses = gcd.getFromLocation(chernivtsi.latitude, chernivtsi.longitude, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (addresses != null && addresses.size() > 0) {
            city = addresses.get(0).getLocality();
        } else {
            city = "Черновцы";
        }
        Toast.makeText(this, city, Toast.LENGTH_LONG).show();
        return city;
    }

    public void reloadDevices(String city) {

        privatBankService.fetchATM(city)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        example -> {
                            mMap.clear();
                            clear();
                            devices.clear();
                            for (Device currentDevice : example.getDevices()) {
                                devices.add(new DeviceAdapter(currentDevice));
                            }
                            persistAll();
                            setAllDevicesOnMap();
                            Toast.makeText(this, "Devices loaded: " +
                                                 String.valueOf(example.getDevices().size()), Toast.LENGTH_LONG).show();
                        },
                        throwable -> Toast.makeText(this, throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show()
                );

    }

    public LatLng getLocationFromAddress(String strAddress) {

        Geocoder coder = new Geocoder(this);
        List<Address> address;
        LatLng p1 = null;

        try {
            // May throw an IOException
            address = coder.getFromLocationName(strAddress, 5);
            if (address == null) {
                return null;
            }
            Address location = address.get(0);
            location.getLatitude();
            location.getLongitude();

            p1 = new LatLng(location.getLatitude(), location.getLongitude() );

        } catch (IOException ex) {

            ex.printStackTrace();
        }

        return p1;
    }

    public void clear() {
        for (DeviceAdapter currentDevice : devices) {
            markersAdapterDao.delete(currentDevice);
        }
        devices.clear();
    }

    public void persistAll() {
        for (DeviceAdapter currentDevice : devices) {
            markersAdapterDao.create(currentDevice);
        }
    }

    /*private void showDevices(String city){
        privatBankService.fetchATM(city)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(example -> setAllDevicesOnMap(example.getDevices()),
                        throwable -> Toast.makeText(this, throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show());
    }*/

    private void centerCameraOnGivenLocation(LatLng givenCoords, float zoom) {
        if (givenCoords != null) {
            CameraPosition cameraPosition = new CameraPosition.Builder().target(givenCoords).zoom(zoom).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }

    private void setAllDevicesOnMap() {
        if (devices != null)
            for (DeviceAdapter deviceAdapter : devices) {
                LatLng currPos = new LatLng(deviceAdapter.getLatitude(), deviceAdapter.getLongitude());
                mMap.addMarker(new MarkerOptions().position(currPos).title(deviceAdapter.getMarkerTitle()).snippet(deviceAdapter.getMarkerSnippet()));
            }
    }

    private void centerCameraOnGivenLocation(Location givenLocation){
        if(givenLocation != null){
            LatLng givenCoords = new LatLng(givenLocation.getLatitude(), givenLocation.getLongitude());
            CameraPosition cameraPosition = new CameraPosition.Builder().target(givenCoords).zoom(12).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }

    private Location retrieveLastKnownLocation(){
        MyLocationListener.setUpLocationListener(this);
        return MyLocationListener.imHere;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        reloadDevices(determineCityByCoords(new LatLng(retrieveLastKnownLocation().getLatitude(), retrieveLastKnownLocation().getLongitude())));
        setAllDevicesOnMap();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
            centerCameraOnGivenLocation(retrieveLastKnownLocation());
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, MY_LOCATION_REQUEST_CODE);
            }
        }
//        Toast.makeText(this, String.valueOf(((UserApplication) this.getApplication()).reloadDevices().size()), Toast.LENGTH_LONG).show();
        //        showDevices(determineCityByCoords(chernivtsi));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MY_LOCATION_REQUEST_CODE) {
            try {
                mMap.setMyLocationEnabled(true);
                centerCameraOnGivenLocation(retrieveLastKnownLocation());
            } catch (SecurityException e) {
                Toast.makeText(this, "NO LOCATION PERMISSIONS", Toast.LENGTH_LONG).show();
            }
        }
    }
}
