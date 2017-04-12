package com.kuzko.aleksey.privatbank;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.kuzko.aleksey.privatbank.datamodel.DatabaseDeviceAdapter;

/**
 * Created by Aleks on 11.04.2017.
 */

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

    private RuntimeExceptionDao<DatabaseDeviceAdapter, Long> markersDao;
    private static final String DATABASE_NAME = "markers.db3";
    private static final int DATABASE_VERSION = 1;
    private ConnectionSource connectionSource;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        this.connectionSource = connectionSource;
        try {
            TableUtils.createTable(connectionSource, DatabaseDeviceAdapter.class);
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            TableUtils.dropTable(connectionSource, DatabaseDeviceAdapter.class, false);
            onCreate(database, connectionSource);
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
    }

    public RuntimeExceptionDao<DatabaseDeviceAdapter, Long> getMarkersDao(){
        if(markersDao == null){
            markersDao = getRuntimeExceptionDao(DatabaseDeviceAdapter.class);
        }
        return markersDao;
    }
}
