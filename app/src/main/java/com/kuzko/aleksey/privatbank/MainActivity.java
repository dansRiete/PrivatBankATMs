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
import com.kuzko.aleksey.privatbank.datamodel.Device;
import com.kuzko.aleksey.privatbank.datamodel.DeviceAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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

    private GoogleMap mMap;
    private final static LatLng DEFAULT_COORDS = new LatLng(48.29, 25.93);
    private final static String DEFAULT_CITY_NAME = "Черновцы";
    private final static int SET_MY_LOCATION_PERMISSION_CODE = 101;
    private final static int CENTER_CAMERA_PERMISSION_CODE = 102;
    private final static String PRIVATBANK_BASE_API_URL = "https://api.privatbank.ua/p24api/";
    private final static String CURSOR_CITY_KEY = "City";
    private final static String NO_SUGGESTIONS_MSG = "No suggestions";
    private final static float DEFAULT_ZOOM = 11;
    private String currentCity;
    private DatabaseHelper databaseHelper;
    private List<DeviceAdapter> deviceAdapters = new ArrayList<>();
    private RuntimeExceptionDao<DeviceAdapter, Long> markersAdapterDao;
    private PrivatBankService privatBankService;
    private SearchView searchView;
    private MenuItem searchItem;
    private SimpleCursorAdapter citySuggestionsAdapter;
    private GoogleApiClient mGoogleApiClient;
    private Location myLastLocation;
    SharedPreferences mSettings;
    public static final String APP_PREFERENCES = "current_city";


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Toast.makeText(this, "RESTORED", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        this.databaseHelper = new DatabaseHelper(getApplicationContext());
        this.markersAdapterDao = databaseHelper.getMarkersDao();
        // Create an instance of GoogleAPIClient.
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
        this.deviceAdapters = markersAdapterDao.queryForAll();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);
        searchItem = menu.findItem(R.id.action_search);
        SearchManager searchManager = (SearchManager) this.getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) searchItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(this.getComponentName()));
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
                    searchDevices(searchedCity);
                    centerCamera(obtainCoordinates(searchedCity), DEFAULT_ZOOM);
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

    private void showSuggestions(List<Location> suggestedLocations) {
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

    private String determineCityByCoords(Location coords) {
        Geocoder gcd = new Geocoder(this, Locale.getDefault());
        List<Address> addresses = null;
        String city = null;
        try {
            addresses = gcd.getFromLocation(coords.getLatitude(), coords.getLongitude(), 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (addresses != null && addresses.size() > 0) {
            city = addresses.get(0).getLocality();
        } else {
            city = DEFAULT_CITY_NAME;
        }
        return city;
    }

    public void searchDevices(String city) {

        privatBankService.fetchATMs(city)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        example -> {
                            clearDatabase();
                            this.deviceAdapters = toDevicesAdapterList(example.getDevices());
                            persistInDatabase(deviceAdapters);
                            redrawDevicesOnMap();
                            this.currentCity = city;
                            SharedPreferences.Editor editor = mSettings.edit();
                            editor.putString(APP_PREFERENCES, city);
                            editor.apply();
                            Toast.makeText(this, example.getDevices().size() + " devices found in \"" + city + '\"', Toast.LENGTH_LONG).show();
                        },
                        throwable -> Toast.makeText(this, throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private List<DeviceAdapter> toDevicesAdapterList(Collection<Device> devices){
        List<DeviceAdapter> deviceAdapters = new ArrayList<>();
        for (Device currentDevice : devices) {
            deviceAdapters.add(new DeviceAdapter(currentDevice));
        }
        return deviceAdapters;
    }

    public LatLng obtainCoordinates(String strAddress) {

        Geocoder coder = new Geocoder(this);
        List<Address> address;
        LatLng obtainedCoordinates = null;

        try {
            address = coder.getFromLocationName(strAddress, 5);
            if (address == null) {
                return DEFAULT_COORDS;
            }
            Address location = address.get(0);
            obtainedCoordinates = new LatLng(location.getLatitude(), location.getLongitude());
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return obtainedCoordinates;
    }

    public void clearDatabase() {
        for (DeviceAdapter currentDevice : deviceAdapters) {
            markersAdapterDao.delete(currentDevice);
        }
        deviceAdapters.clear();
    }

    public void persistInDatabase(Collection<DeviceAdapter> deviceAdapters) {
        for (DeviceAdapter currentDevice : deviceAdapters) {
            markersAdapterDao.create(currentDevice);
        }
    }

    private void centerCamera(LatLng givenCoords, float zoom) {
        if (givenCoords != null) {
            CameraPosition cameraPosition = new CameraPosition.Builder().target(givenCoords).zoom(zoom).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }

    private void redrawDevicesOnMap() {
        mMap.clear();
        if (deviceAdapters != null)
            for (DeviceAdapter deviceAdapter : deviceAdapters) {
                LatLng currPos = new LatLng(deviceAdapter.getLatitude(), deviceAdapter.getLongitude());
                mMap.addMarker(new MarkerOptions().position(currPos).title(deviceAdapter.getMarkerTitle()).snippet(deviceAdapter.getMarkerSnippet()));
            }
    }

    private void centerCamera(Location givenLocation) {
        if (givenLocation != null) {
            LatLng givenCoords = new LatLng(givenLocation.getLatitude(), givenLocation.getLongitude());
            CameraPosition cameraPosition = new CameraPosition.Builder().target(givenCoords).zoom(DEFAULT_ZOOM).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }

    private void centerCamera(LatLng givenCoords) {
        if (givenCoords != null) {
            CameraPosition cameraPosition = new CameraPosition.Builder().target(givenCoords).zoom(DEFAULT_ZOOM).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;
        mGoogleApiClient.connect();
        redrawDevicesOnMap();
//        String currentCity = null;
        if(mSettings.contains(APP_PREFERENCES)) {
            currentCity = mSettings.getString(APP_PREFERENCES, "");
        }
        if(currentCity == null || currentCity.equalsIgnoreCase(""))  {
            if(myLastLocation != null){
                centerCamera(myLastLocation);
            }else {
                centerCamera(DEFAULT_COORDS);
            }
        }else {
            centerCamera(obtainCoordinates(currentCity));
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
                centerCamera(myLastLocation);
            } catch (SecurityException e) {
                Toast.makeText(this, "NO LOCATION PERMISSIONS", Toast.LENGTH_LONG).show();
            }

        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Toast.makeText(this, "onConnected(@Nullable Bundle bundle)", Toast.LENGTH_SHORT).show();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            myLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
//            this.currentCity = determineCityByCoords(myLastLocation);
            if(deviceAdapters == null || deviceAdapters.size() == 0)
                searchDevices(currentCity);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, CENTER_CAMERA_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        centerCamera(DEFAULT_COORDS, DEFAULT_ZOOM);
        searchDevices(DEFAULT_CITY_NAME);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        centerCamera(DEFAULT_COORDS, DEFAULT_ZOOM);
        searchDevices(DEFAULT_CITY_NAME);
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
