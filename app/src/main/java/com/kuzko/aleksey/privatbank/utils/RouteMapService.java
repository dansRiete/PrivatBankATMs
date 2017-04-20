package com.kuzko.aleksey.privatbank.utils;

import com.kuzko.aleksey.privatbank.datamodel.RouteResponse;
import com.kuzko.aleksey.privatbank.datamodel.RouteType;

import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Query;
import rx.Observable;

/**
 * Created by Aleks on 14.04.2017.
 */

public interface RouteMapService {

    String API_KEY = "AIzaSyBYYOC3DZzCgu0uwxkq0Hl0rF4NB0KkwOg";
    String UNIT_SYSTEM = "metric";

    @GET("api/directions/json?units=" + UNIT_SYSTEM + "&key=" + API_KEY)
    Observable<Response<RouteResponse>> findRoute(
            @Query("origin") String origin,
            @Query("destination") String destination,
            @Query("mode") RouteType mode
    );
}
