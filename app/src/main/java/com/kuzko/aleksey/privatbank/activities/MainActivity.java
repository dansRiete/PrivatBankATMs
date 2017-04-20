package com.kuzko.aleksey.privatbank.activities;

import android.Manifest;
import android.app.SearchManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.kuzko.aleksey.privatbank.R;
import com.kuzko.aleksey.privatbank.datamodel.City;
import com.kuzko.aleksey.privatbank.datamodel.DatabaseDeviceAdapter;
import com.kuzko.aleksey.privatbank.datamodel.Device;
import com.kuzko.aleksey.privatbank.datamodel.Route;
import com.kuzko.aleksey.privatbank.datamodel.RouteType;
import com.kuzko.aleksey.privatbank.utils.DatabaseHelper;
import com.kuzko.aleksey.privatbank.utils.MapUtils;
import com.kuzko.aleksey.privatbank.utils.PrivatBankService;
import com.kuzko.aleksey.privatbank.utils.RouteMapService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static com.kuzko.aleksey.privatbank.R.id.map;

public class MainActivity extends AppCompatActivity implements
        OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private final static int SET_MY_LOCATION_PERMISSION_CODE = 101;
    private final static int MIN_CHARS_NUMBER_TO_SHOW_SUGGESTIONS = 2;
    private final static int CENTER_CAMERA_PERMISSION_CODE = 102;
    private final static int ROUTE_COLOR = Color.RED;
    private final static String PRIVATBANK_BASE_API_URL = "https://api.privatbank.ua/p24api/";
    private final static String DIRECTIONS_BASE_API_URL = "https://maps.googleapis.com/maps/";
    private final static String CURSOR_CITY_KEY = "City";
    private final static String NO_SUGGESTIONS_MSG = "No suggestions";
    private static final String LAST_CITY = "current_city";
    private static final String LAST_LATITUDE = "last_latitude";
    private static final String LAST_LONGITUDE = "last_longitude";
    private static final String LOG_ERROR_TAG = "ERROR";
    private final static float DEFAULT_ZOOM = 11;
    private GoogleMap googleMap;
    private String currentCity;
    private List<DatabaseDeviceAdapter> deviceAdapters = new ArrayList<>();
    private RuntimeExceptionDao<DatabaseDeviceAdapter, Long> markersAdapterDao;
    private PrivatBankService privatBankService;
    private SearchView searchView;
    private SimpleCursorAdapter citySuggestionsAdapter;
    private GoogleApiClient mGoogleApiClient;
    private Location myLastLocation;
    private SharedPreferences mSettings;
    private ProgressBar progressBar;
    private RouteMapService routeService;
    private Polyline routeLine;
    private FloatingActionButton cancelRouteButton;
    private MapUtils mapUtils = new MapUtils();
    private class SaveDevicesToDb extends AsyncTask<Void, Void, Void>{
        private List<DatabaseDeviceAdapter> deviceAdapters;
        SaveDevicesToDb(List<DatabaseDeviceAdapter> deviceAdapters){
            this.deviceAdapters = deviceAdapters;
        }
        @Override
        protected Void doInBackground(Void... params) {
            for(DatabaseDeviceAdapter currentDevice : markersAdapterDao.queryForAll()){
                markersAdapterDao.delete(currentDevice);
            }
            for (DatabaseDeviceAdapter currentDevice : deviceAdapters) {
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

            Geocoder geocoder = new Geocoder(MainActivity.this);
            List<Address> address = null;
            String requestedAddress = null;
            try {
                requestedAddress = city + ", Украина";
                address = geocoder.getFromLocationName(requestedAddress, 5);
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
    private class MyInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

        private final View myContentsView;

        MyInfoWindowAdapter(){
            myContentsView = getLayoutInflater().inflate(R.layout.custom_marker_info_contents, null);
        }

        @Override
        public View getInfoWindow(Marker marker) {
            return null;
        }

        @Override
        public View getInfoContents(Marker marker) {

            TextView tvTitle = ((TextView)myContentsView.findViewById(R.id.title));
            tvTitle.setText(marker.getTitle());
            TextView tvSnippet = ((TextView)myContentsView.findViewById(R.id.snippet));
            tvSnippet.setText(marker.getSnippet());

            return myContentsView;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(map);
        cancelRouteButton = (FloatingActionButton) findViewById(R.id.cancelRouteButton);
        cancelRouteButton.setOnClickListener((view) -> removeRoute());
        cancelRouteButton.setVisibility(View.INVISIBLE);
        mapFragment.getMapAsync(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        progressBar = (ProgressBar) this.findViewById(R.id.pbLoading);
        mSettings = getSharedPreferences(LAST_CITY, Context.MODE_PRIVATE);
        DatabaseHelper databaseHelper = new DatabaseHelper(getApplicationContext());
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

        Retrofit retrofitPrivatBankService = new Retrofit.Builder()
                .baseUrl(PRIVATBANK_BASE_API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();

        Retrofit retrofitRouteService = new Retrofit.Builder()
                .baseUrl(DIRECTIONS_BASE_API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();

        privatBankService = retrofitPrivatBankService.create(PrivatBankService.class);
        routeService = retrofitRouteService.create(RouteMapService.class);
        this.deviceAdapters = markersAdapterDao.queryForAll();


    }

    private void hideKeyboard(){
        View view = MainActivity.this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void removeRoute(){
        if (routeLine != null) {
            routeLine.remove();
            cancelRouteButton.setVisibility(View.INVISIBLE);
        }
    }

    private void logError(Throwable throwable, boolean showToast){
        progressBar.setVisibility(ProgressBar.INVISIBLE);
        if(showToast){
            Toast.makeText(this, throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
        Log.d(LOG_ERROR_TAG, throwable.getLocalizedMessage());
    }

    private void findRoute(Marker destination) {
        if(myLastLocation != null){
            routeService.findRoute(myLastLocation.getLatitude() + "," + myLastLocation.getLongitude(),
                    destination.getPosition().latitude + "," + destination.getPosition().longitude, RouteType.DRIVING)
                    .subscribeOn(Schedulers.io())
                    .map(response -> {
                        if(response.body().getRoutes().size() > 0){
                            return response.body().getRoutes().get(0);
                        }else {
                            return null;
                        }
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::drawRouteOnMap, throwable -> logError(throwable, true));
        }

    }

    private void drawRouteOnMap(Route route){
        if(route != null){
            removeRoute();
            String encodedString = route.getOverviewPolyline().getPoints();
            routeLine = googleMap.addPolyline(
                    new PolylineOptions().addAll(mapUtils.decodePoly(encodedString))
                            .width(10).color(ROUTE_COLOR).geodesic(true)
            );
            cancelRouteButton.setVisibility(View.VISIBLE);
        }else {
            Toast.makeText(this, R.string.no_routes_msg, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
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
                    new CenterCameraOnCity(searchedCity).execute();
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                if(query.toCharArray().length >= MIN_CHARS_NUMBER_TO_SHOW_SUGGESTIONS){
                    privatBankService.fetchATMs(query)
                            .subscribeOn(Schedulers.io())
                            .map(response -> {
                                HashSet<String> citiesSet = new HashSet<>();
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
                                    throwable -> logError(throwable, false)
                            );
                }
                return true;
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
                    mc.addRow(new Object[]{i, suggestedLocations.get(i)});
                }
                citySuggestionsAdapter.changeCursor(mc);
            } else {
                mc.addRow(new Object[]{0, NO_SUGGESTIONS_MSG});
                citySuggestionsAdapter.changeCursor(mc);
            }
        }
    }

    private List<Device> mergeATMsAndTSOs(City currentCityATMs, City currentCityTSOs){
        Collection<Device> ATMsAndTSOs = currentCityATMs.getDevices();
        ATMsAndTSOs.addAll(currentCityTSOs.getDevices());
        return new ArrayList<>(ATMsAndTSOs);
    }

    public void searchDevices(String city) {

        if(city == null || city.equals("")){
            return;
        }

        progressBar.setVisibility(ProgressBar.VISIBLE);
        this.currentCity = city;
        saveLastCity(city);

        Observable.combineLatest(privatBankService.fetchATMs(city), privatBankService.fetchTSOs(city), this::mergeATMsAndTSOs)
                .subscribeOn(Schedulers.io())
                .map(this::convertToDeviceAdapters)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::redrawDevicesOnMap, throwable -> logError(throwable, true));
    }

    private List<DatabaseDeviceAdapter> convertToDeviceAdapters(Collection<Device> devices){
        List<DatabaseDeviceAdapter> deviceAdapters = new ArrayList<>();
        for (Device currentDevice : devices) {
            deviceAdapters.add(new DatabaseDeviceAdapter(currentDevice));
        }
        return deviceAdapters;
    }

    private void redrawDevicesOnMap(List<DatabaseDeviceAdapter> deviceAdapters) {

        if(deviceAdapters != null && !deviceAdapters.isEmpty()){
            this.deviceAdapters = deviceAdapters;
            new SaveDevicesToDb(deviceAdapters).execute();
            googleMap.clear();
            for (DatabaseDeviceAdapter currentDeviceAdapter : deviceAdapters) {
                LatLng currPos = new LatLng(currentDeviceAdapter.getLatitude(), currentDeviceAdapter.getLongitude());
                MarkerOptions markerOptions;
                if(currentDeviceAdapter.getDeviceType().equalsIgnoreCase("TSO")){
                    markerOptions = new MarkerOptions().position(currPos).title(currentDeviceAdapter.getMarkerTitle())
                            .snippet(currentDeviceAdapter.getMarkerSnippet()).icon(BitmapDescriptorFactory
                                    .defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                }else {
                    markerOptions = new MarkerOptions().position(currPos).title(currentDeviceAdapter.getMarkerTitle()).snippet(currentDeviceAdapter.getMarkerSnippet());
                }
                googleMap.addMarker(markerOptions);
            }
            Toast.makeText(this, deviceAdapters.size() + " devices found in \"" + currentCity + '\"', Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(this, "No devices found in \"" + currentCity + '\"', Toast.LENGTH_SHORT).show();
        }

        progressBar.setVisibility(ProgressBar.INVISIBLE);
    }

    private void redrawDevicesOnMap(){
        if (deviceAdapters != null){
            googleMap.clear();
            for (DatabaseDeviceAdapter currentDeviceAdapter : deviceAdapters) {
                LatLng currPos = new LatLng(currentDeviceAdapter.getLatitude(), currentDeviceAdapter.getLongitude());
                MarkerOptions markerOptions;
                if(currentDeviceAdapter.getDeviceType().equalsIgnoreCase("TSO")){
                    markerOptions = new MarkerOptions().position(currPos).title(currentDeviceAdapter.getMarkerTitle())
                            .snippet(currentDeviceAdapter.getMarkerSnippet()).icon(BitmapDescriptorFactory
                                    .defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                }else {
                    markerOptions = new MarkerOptions().position(currPos).title(currentDeviceAdapter.getMarkerTitle()).snippet(currentDeviceAdapter.getMarkerSnippet());
                }
                googleMap.addMarker(markerOptions);
            }
        }
    }

    private void centerCamera(LatLng givenCoords) {
        if (givenCoords != null) {
            CameraPosition cameraPosition = new CameraPosition.Builder().target(givenCoords).zoom(DEFAULT_ZOOM).build();
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
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

    private void saveLastCity(String city) {
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(LAST_CITY, city);
        editor.apply();
    }

    private String retrieveLastCity(){
        String currentCity = null;
        if(mSettings.contains(LAST_CITY)) {
            currentCity = mSettings.getString(LAST_CITY, "");
        }
        if(currentCity == null || currentCity.equals("")){
            if(myLastLocation != null){
                currentCity = mapUtils.determineCityName(myLastLocation, this);
            }
        }
        return currentCity;
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {

        this.googleMap = googleMap;
        this.googleMap.getUiSettings().setZoomControlsEnabled(true);
        this.googleMap.getUiSettings().setMapToolbarEnabled(false);
        this.googleMap.setInfoWindowAdapter(new MyInfoWindowAdapter());
        this.googleMap.setOnInfoWindowClickListener(this::findRoute);
        mGoogleApiClient.connect();
        redrawDevicesOnMap();
        this.currentCity = retrieveLastCity();
        new CenterCameraOnCity(currentCity).execute();
        if(deviceAdapters == null || !deviceAdapters.isEmpty()){
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
                googleMap.setMyLocationEnabled(true);
                myLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                if(myLastLocation != null){
                    centerCamera(new LatLng(myLastLocation.getLatitude(), myLastLocation.getLongitude()));
                }
                searchDevices(mapUtils.determineCityName(myLastLocation, this));
            } catch (SecurityException e) {
                Toast.makeText(this, R.string.no_location_permissions_msg, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == CENTER_CAMERA_PERMISSION_CODE) {
            try {
                myLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                if(myLastLocation != null){
                    centerCamera(new LatLng(myLastLocation.getLatitude(), myLastLocation.getLongitude()));
                }
                searchDevices(mapUtils.determineCityName(myLastLocation, this));
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
