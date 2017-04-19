package com.kuzko.aleksey.privatbank.datamodel;

/**
 *
 * Created by Igor Havrylyuk on 29.01.2017.
 */

public enum RouteType {
    // type walking, bicycling, transit, driving - default
    WALKING(1,"walking"),
    BICYCLING(2,"bicycling"),
    TRANSIT(3,"transit"),
    DRIVING(4,"driving");

    private int typeId;
    private String typeName;

    RouteType(int id) {
        this.typeId = id;
    }

    RouteType(int id, String roleName) {
        this.typeId = id;
        this.typeName = roleName;
    }

    public String getTypeName() {
        return typeName;
    }

    public long getTypeId() {
        return typeId;
    }

    public static RouteType getById(Long id) {
        for(RouteType r : values()) {
            if(r.typeId == id) return r;
        }
        return null;
    }

}
