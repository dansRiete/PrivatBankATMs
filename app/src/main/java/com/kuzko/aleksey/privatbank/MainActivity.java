package com.kuzko.aleksey.privatbank;

import android.Manifest;
import android.app.SearchManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.kuzko.aleksey.privatbank.datamodel.DatabaseDeviceAdapter;
import com.kuzko.aleksey.privatbank.datamodel.Device;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements
        OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private final static int SET_MY_LOCATION_PERMISSION_CODE = 101;
    private final static int CENTER_CAMERA_PERMISSION_CODE = 102;
    private final static String PRIVATBANK_BASE_API_URL = "https://api.privatbank.ua/p24api/";
    private final static String CURSOR_CITY_KEY = "City";
    private final static String NO_SUGGESTIONS_MSG = "No suggestions";
    private static final String LAST_CITY = "current_city";
    private static final String LAST_LATITUDE = "last_latitude";
    private static final String LAST_LONGITUDE = "last_lonitude";
    private final static float DEFAULT_ZOOM = 11;
    private GoogleMap mMap;
    private String currentCity;
    private DatabaseHelper databaseHelper;
    private List<DatabaseDeviceAdapter> devices = new ArrayList<>();
    private RuntimeExceptionDao<DatabaseDeviceAdapter, Long> markersAdapterDao;
    private PrivatBankService privatBankService;
    private SearchView searchView;
    private MenuItem searchItem;
    private SimpleCursorAdapter citySuggestionsAdapter;
    private GoogleApiClient mGoogleApiClient;
    private Location myLastLocation;
    private SharedPreferences mSettings;
    private ProgressBar progressBar;
    private class SaveDevicesToDb extends AsyncTask<Void, Void, Void>{
        @Override
        protected Void doInBackground(Void... params) {
            for(DatabaseDeviceAdapter currentDevice : markersAdapterDao.queryForAll()){
                markersAdapterDao.delete(currentDevice);
            }
            for (DatabaseDeviceAdapter currentDevice : devices) {
                markersAdapterDao.create(currentDevice);
            }
            return null;
        }
    }
    private class CenterCameraOnCity extends AsyncTask<Void, Void, Void>{
        LatLng coords;
        String city;
        CenterCameraOnCity(String city){
            this.city = city;
        }
        @Override
        protected Void doInBackground(Void... params) {
            if(city == null || city.equals("")){
                return null;
            }
            //// TODO: 12.04.2017 TRY TO CHECK IS THERE CONNECTION TO THE INTERNET FIRSTLY

            Geocoder geocoder = new Geocoder(MainActivity.this);
            List<Address> address = null;
            try {
                address = geocoder.getFromLocationName(city + ", Украина", 5);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            if(address != null && address.size() > 0){
                Address location = address.get(0);
                coords = new LatLng(location.getLatitude(), location.getLongitude());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if(coords != null){
                centerCamera(coords);
            }else {
                centerCamera(retrieveLastLocation());
            }

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        progressBar = (ProgressBar) this.findViewById(R.id.pbLoading);
        mSettings = getSharedPreferences(LAST_CITY, Context.MODE_PRIVATE);
        this.databaseHelper = new DatabaseHelper(getApplicationContext());
        this.markersAdapterDao = databaseHelper.getMarkersDao();
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        citySuggestionsAdapter = new SimpleCursorAdapter(this, android.R.layout.simple_spinner_dropdown_item, null,
                new String[]{CURSOR_CITY_KEY}, new int[]{android.R.id.text1}, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(PRIVATBANK_BASE_API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();
        privatBankService = retrofit.create(PrivatBankService.class);
        this.devices = markersAdapterDao.queryForAll();

    }

    private void hideKeyboard(){
        View view = MainActivity.this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);
        searchItem = menu.findItem(R.id.action_search);
        SearchManager searchManager = (SearchManager) this.getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) searchItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(this.getComponentName()));
        searchView.setSuggestionsAdapter(citySuggestionsAdapter);
        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionClick(int position) {
                Cursor cursor = searchView.getSuggestionsAdapter().getCursor();
                cursor.moveToPosition(position);
                String searchedCity = cursor.getString(cursor.getColumnIndex(CURSOR_CITY_KEY));
                searchView.setQuery(searchedCity, false);
                hideKeyboard();
                if (searchedCity != null && !searchedCity.equals("")) {
                    searchDevices(searchedCity);
//                    centerCamera(obtainCoordinates(searchedCity), DEFAULT_ZOOM);
                    new CenterCameraOnCity(searchedCity).execute();
                }
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
                progressBar.setVisibility(ProgressBar.VISIBLE);
                if (searchedCity != null && !searchedCity.equals("")) {
                    searchDevices(searchedCity);
//                    centerCamera(obtainCoordinates(searchedCity), DEFAULT_ZOOM);
                    new CenterCameraOnCity(searchedCity).execute();
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                if(query.toCharArray().length >= 3){
                    privatBankService.fetchDevices(query)
                            .subscribeOn(Schedulers.io())
                            .map(response -> {
                                LinkedHashSet<String> citiesSet = new LinkedHashSet<>();
                                for(Device currentDevice : response.getDevices()){
                                    if(currentDevice.getCityRU().toLowerCase().startsWith(query.toLowerCase())){
                                        citiesSet.add(currentDevice.getCityRU());
                                    }
                                }
                                List<String> sortedCities = new ArrayList<>(citiesSet);
                                Collections.sort(sortedCities);
                                return sortedCities;
                            })
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    sortedCities -> showSuggestions(sortedCities),
                                    throwable -> System.out.println(throwable.getLocalizedMessage())
                            );
                }


                /*if (query.equals("")) {
//                    progressBar.setVisibility(ProgressBar.INVISIBLE);
                    *//*if(weatherRequest != null){
                        weatherRequest.cancel();
                    }*//*
                    showSuggestions(Arrays.asList(query, "Two", query));
                } else {
                    *//*weatherService.searchLocations(query).enqueue(new Callback<List<Location>>() {
                        @Override
                        public void onResponse(Call<List<Location>> call, City<List<Location>> response) {
                            if (response.isSuccessful()) {
                                showSuggestions(response.body());
                            }
                        }
                        @Override
                        public void onFailure(Call<List<Location>> call, Throwable t) {
                            showSuggestions(null);
                        }
                    });*//*
                }*/
                return false;
            }
        });
        return true;
    }

    private void showSuggestions(List<String> suggestedLocations) {
        if (suggestedLocations == null) {
            citySuggestionsAdapter.changeCursor(new MatrixCursor(new String[]{BaseColumns._ID, CURSOR_CITY_KEY}));
        } else {
            MatrixCursor mc = new MatrixCursor(new String[]{BaseColumns._ID, CURSOR_CITY_KEY});
            if (!suggestedLocations.isEmpty()) {
                for (int i = 0; i < suggestedLocations.size(); i++) {
                    mc.addRow(new Object[]{i, suggestedLocations.get(i)/*.getName()*/});
                }
                citySuggestionsAdapter.changeCursor(mc);
            } else {
                mc.addRow(new Object[]{0, NO_SUGGESTIONS_MSG});
                citySuggestionsAdapter.changeCursor(mc);
            }
        }
    }

    private String determineCityName(Location coords) {
        String city = null;
        if(coords != null){
            Geocoder gcd = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = null;

            try {
                addresses = gcd.getFromLocation(coords.getLatitude(), coords.getLongitude(), 1);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (addresses != null && addresses.size() > 0) {
                city = addresses.get(0).getLocality();
            }
        }
        return city;
    }

    public void searchDevices(String city) {

        if(city == null || city.equals("")){
            return;
        }
        progressBar.setVisibility(ProgressBar.VISIBLE);
        privatBankService.fetchDevices(city)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        example -> {
                            if(!example.getDevices().isEmpty()){
                                this.devices = toDeviceAdaptersList(example.getDevices());
                                redrawDevicesOnMap();
                                new SaveDevicesToDb().execute();
                                this.currentCity = city;
                                saveLastCity(city);
                            }
                            progressBar.setVisibility(ProgressBar.INVISIBLE);
                            Toast.makeText(this, example.getDevices().size() + " devices found in \"" + city + '\"', Toast.LENGTH_LONG).show();
                        },
                        throwable -> {
                            progressBar.setVisibility(ProgressBar.INVISIBLE);
                            Toast.makeText(this, throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
                );
    }

    private List<DatabaseDeviceAdapter> toDeviceAdaptersList(Collection<Device> devices){
        List<DatabaseDeviceAdapter> deviceAdapters = new ArrayList<>();
        for (Device currentDevice : devices) {
            deviceAdapters.add(new DatabaseDeviceAdapter(currentDevice));
        }
        return deviceAdapters;
    }

    //// TODO: 12.04.2017 Make asyncronous
    /*public LatLng obtainCoordinates(String cityName) {

        if(cityName == null || cityName.equals("")){
            return null;
        }

        Geocoder geocoder = new Geocoder(this);
        List<Address> address = null;
        LatLng obtainedCoordinates = null;
        try {
            address = geocoder.getFromLocationName(cityName + ", Украина", 5);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if(address != null && address.size() > 0){
            Address location = address.get(0);
            obtainedCoordinates = new LatLng(location.getLatitude(), location.getLongitude());
        }

        return obtainedCoordinates;
    }*/

    /*private void centerCamera(LatLng givenCoords, float zoom) {
        if (givenCoords != null) {
            CameraPosition cameraPosition = new CameraPosition.Builder().target(givenCoords).zoom(zoom).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }*/

    private void redrawDevicesOnMap() {

        if (devices != null){
            mMap.clear();
            for (DatabaseDeviceAdapter deviceAdapter : devices) {
                LatLng currPos = new LatLng(deviceAdapter.getLatitude(), deviceAdapter.getLongitude());
                mMap.addMarker(new MarkerOptions().position(currPos).title(deviceAdapter.getMarkerTitle()).snippet(deviceAdapter.getMarkerSnippet()));
            }
        }
    }

    /*private void centerCamera(Location givenLocation) {
        if (givenLocation != null) {
            LatLng givenCoords = new LatLng(givenLocation.getLatitude(), givenLocation.getLongitude());
            CameraPosition cameraPosition = new CameraPosition.Builder().target(givenCoords).zoom(DEFAULT_ZOOM).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }*/

    private void centerCamera(LatLng givenCoords) {
        if (givenCoords != null) {
            CameraPosition cameraPosition = new CameraPosition.Builder().target(givenCoords).zoom(DEFAULT_ZOOM).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            saveLastLocation(givenCoords);
        }
    }

    private void saveLastLocation(LatLng location){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putFloat(LAST_LATITUDE, (float) location.latitude);
        editor.putFloat(LAST_LONGITUDE, (float) location.longitude);
        editor.apply();
    }

    private LatLng retrieveLastLocation(){
        LatLng latLng = null;
        if(mSettings.contains(LAST_LATITUDE) && mSettings.contains(LAST_LONGITUDE)) {
            latLng = new LatLng(mSettings.getFloat(LAST_LATITUDE, 0), mSettings.getFloat(LAST_LONGITUDE, 0));
        }
        return latLng;
    }

    private String retrieveLastCity(){
        String currentCity = null;
        if(mSettings.contains(LAST_CITY)) {
            currentCity = mSettings.getString(LAST_CITY, "");
        }
        if(currentCity == null || currentCity.equals("")){
            if(myLastLocation != null){
                currentCity = determineCityName(myLastLocation);
            }
        }
        return currentCity;
    }

    private void saveLastCity(String city) {
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(LAST_CITY, city);
        editor.apply();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mGoogleApiClient.connect();
        redrawDevicesOnMap();
        this.currentCity = retrieveLastCity();
//        centerCamera(obtainCoordinates(currentCity));
        new CenterCameraOnCity(currentCity).execute();
        if(devices == null || devices.size() == 0){
            searchDevices(currentCity);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, SET_MY_LOCATION_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == SET_MY_LOCATION_PERMISSION_CODE) {
            try {
                mMap.setMyLocationEnabled(true);
            } catch (SecurityException e) {
                Toast.makeText(this, "NO LOCATION PERMISSIONS", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == CENTER_CAMERA_PERMISSION_CODE) {
            try {
                myLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                centerCamera(new LatLng(myLastLocation.getLatitude(), myLastLocation.getLongitude()));
                searchDevices(determineCityName(myLastLocation));
            } catch (SecurityException e) {
                Toast.makeText(this, R.string.msg_no_location_permissons, Toast.LENGTH_LONG).show();
            }

        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            myLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            this.currentCity = retrieveLastCity();
//            centerCamera(obtainCoordinates(currentCity));
            new CenterCameraOnCity(currentCity).execute();
            searchDevices(currentCity);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, CENTER_CAMERA_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }
}
