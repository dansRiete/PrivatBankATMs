package com.kuzko.aleksey.privatbank;

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
    @GET("api/directions/json?key=" + "AIzaSyAtwjWUPJzZUl2tw5ejP3wsUZOfckfNJBo")
    Observable<Response<RouteResponse>> getRoute(
            @Query("units") String units,
            @Query("origin") String origin,
            @Query("destination") String destination,
            @Query("mode") RouteType mode);
}
