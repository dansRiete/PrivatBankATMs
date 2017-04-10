
package com.kuzko.aleksey.privatbankatmssgkuzko.datamodel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Example {

    private String city;
    private String address;
    private List<Device> devices = null;
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

    public List<Device> getDevices() {
        return devices;
    }

    public void setDevices(List<Device> devices) {
        this.devices = devices;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
