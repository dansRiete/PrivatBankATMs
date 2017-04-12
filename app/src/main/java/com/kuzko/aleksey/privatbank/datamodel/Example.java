
package com.kuzko.aleksey.privatbank.datamodel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Example {

    private String city;
    private String address;
    private Collection<Device> devices = null;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Collection<Device> getDevices() {
        return devices;
    }

    public void setDevices(Collection<Device> devices) {
        this.devices = devices;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
