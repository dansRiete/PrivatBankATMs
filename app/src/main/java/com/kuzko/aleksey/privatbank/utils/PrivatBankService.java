package com.kuzko.aleksey.privatbank.utils;

import com.kuzko.aleksey.privatbank.datamodel.City;

import retrofit2.http.GET;
import retrofit2.http.Query;
import rx.Observable;

/**
 * Created by Aleks on 10.04.2017.
 */

public interface PrivatBankService {

    @GET("infrastructure?json&atm")
    Observable<City> fetchATMs(@Query("city") String city);

    @GET("infrastructure?json&tso")
    Observable<City> fetchTSOs(@Query("city") String city);
}
