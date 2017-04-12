package com.kuzko.aleksey.privatbank.datamodel;

import com.google.android.gms.maps.model.Marker;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Created by Aleks on 25.02.2017.
 */

@DatabaseTable(tableName = "markerinfos")
public class DatabaseDeviceAdapter {

    @DatabaseField(generatedId = true)
    private long id;
    @DatabaseField
    private String markerSnippet;
    @DatabaseField
    private double latitude;
    @DatabaseField
    private double longitude;
    @DatabaseField
    private String markerTitle;

    public DatabaseDeviceAdapter(){}

    public DatabaseDeviceAdapter(Device device){
        this.markerSnippet = device.getFullAddressRu();
        this.markerTitle = device.getCityRU() + " - " + device.getPlaceRu();
        this.latitude = Double.valueOf(device.getLatitude());
        this.longitude = Double.valueOf(device.getLongitude());
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getMarkerSnippet() {
        return markerSnippet;
    }

    public void setMarkerSnippet(String markerSnippet) {
        this.markerSnippet = markerSnippet;
    }

    public String getMarkerTitle() {
        return markerTitle;
    }

    public void setMarkerTitle(String markerTitle) {
        this.markerTitle = markerTitle;
    }

    public DatabaseDeviceAdapter update(Marker marker){
        this.latitude = marker.getPosition().latitude;
        this.longitude = marker.getPosition().longitude;
        this.markerSnippet = marker.getSnippet();
        this.markerTitle = marker.getTitle();
        return this;
    }
}
