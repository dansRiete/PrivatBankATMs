package com.kuzko.aleksey.privatbank;

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
import android.widget.ImageView;
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
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.kuzko.aleksey.privatbank.datamodel.DatabaseDeviceAdapter;
import com.kuzko.aleksey.privatbank.datamodel.Device;
import com.kuzko.aleksey.privatbank.datamodel.Route;
import com.kuzko.aleksey.privatbank.datamodel.RouteType;

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

import static com.kuzko.aleksey.privatbank.R.id.map;

public class MainActivity extends AppCompatActivity implements
        OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private final static int SET_MY_LOCATION_PERMISSION_CODE = 101;
    private final static int MIN_CHARS_NUMBER_TO_SHOW_SUGGESTIONS = 2;
    private final static int CENTER_CAMERA_PERMISSION_CODE = 102;
    private final static String PRIVATBANK_BASE_API_URL = "https://api.privatbank.ua/p24api/";
    private final static String DIRECTIONS_BASE_API_URL = "https://maps.googleapis.com/maps/";
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
    private RouteMapService routeService;
    private TextView pointDestination;
    private Polyline routeLine;
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
    private class MyInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

        private final View myContentsView;

        MyInfoWindowAdapter(){
            myContentsView = getLayoutInflater().inflate(R.layout.custom_info_contents, null);

            ImageView imageMarker = (ImageView) myContentsView.findViewById(R.id.imageMarker);
            imageMarker.setOnClickListener(view -> Toast.makeText(MainActivity.this, "CLICKED", Toast.LENGTH_SHORT ).show());
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

        Retrofit retrofit2 = new Retrofit.Builder()
                .baseUrl(DIRECTIONS_BASE_API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();

        privatBankService = retrofit.create(PrivatBankService.class);
        routeService = retrofit2.create(RouteMapService.class);
        this.devices = markersAdapterDao.queryForAll();


    }

    private void hideKeyboard(){
        View view = MainActivity.this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void drawRoute(LatLng origin, LatLng dest) {
        // type walking, bicycling, transit, driving - default
        routeService.getRoute("metric", "chernivtsi"/*origin.latitude + "," + origin.longitude*/, "kyiv"/*dest.latitude + "," + dest.longitude*/, RouteType.DRIVING)
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(
                        response -> {
                            System.out.println(response.raw().request().url());
                            if (routeLine != null) {
                                routeLine.remove();
                            }
//                            LatLngBounds.Builder latLngBuilder = new LatLngBounds.Builder();
                            System.out.println("SIZE=" + response.body().getRoutes().size());
                            for (Route currentRoute : response.body().getRoutes()) {
                                String encodedString = currentRoute.getOverviewPolyline().getPoints();
                                routeLine = mMap.addPolyline(new PolylineOptions().addAll(decodePoly(encodedString)).width(10).color(Color.RED).geodesic(true));
                            }
                        }
        );
    }

    public List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<LatLng>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng( (((double) lat / 1E5)),
                    (((double) lng / 1E5) ));
            poly.add(p);
        }
        return poly;
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
                    drawRoute(new LatLng(48, 52),new LatLng(49, 53));
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                if(query.toCharArray().length >= MIN_CHARS_NUMBER_TO_SHOW_SUGGESTIONS){
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

    private void redrawDevicesOnMap() {

        if (devices != null){
            mMap.clear();
            for (DatabaseDeviceAdapter deviceAdapter : devices) {
                LatLng currPos = new LatLng(deviceAdapter.getLatitude(), deviceAdapter.getLongitude());
                mMap.addMarker(new MarkerOptions().position(currPos).title(deviceAdapter.getMarkerTitle()).snippet(deviceAdapter.getMarkerSnippet()));
            }
        }
    }

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
        mMap.setInfoWindowAdapter(new MyInfoWindowAdapter());

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
                if(myLastLocation != null){
                    centerCamera(new LatLng(myLastLocation.getLatitude(), myLastLocation.getLongitude()));
                }
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
