package com.kuzko.aleksey.privatbankatmssgkuzko;

import android.app.Application;

import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.kuzko.aleksey.privatbankatmssgkuzko.datamodel.DeviceAdapter;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by Aleks on 25.01.2017.
 */

public class UserApplication extends Application {

    private final static String BASE_URL = "https://api.privatbank.ua/p24api/";
    private DatabaseHelper databaseHelper;
    private List<DeviceAdapter> devices = new ArrayList<>();
    private RuntimeExceptionDao<DeviceAdapter, Long> markersAdapterDao;
    PrivatBankService privatBankService;

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseHelper = new DatabaseHelper(getApplicationContext());
        this.markersAdapterDao = databaseHelper.getMarkersDao();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();
        privatBankService = retrofit.create(PrivatBankService.class);
        this.devices = markersAdapterDao.queryForAll();
    }



    public List<DeviceAdapter> getDevices() {
        return devices;
    }

    public DatabaseHelper getDatabaseHelper() {
        return databaseHelper;
    }
}
