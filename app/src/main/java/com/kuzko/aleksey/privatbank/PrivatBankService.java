package com.kuzko.aleksey.privatbank;

import com.kuzko.aleksey.privatbank.datamodel.Example;

import retrofit2.http.GET;
import retrofit2.http.Query;
import rx.Observable;

/**
 * Created by Aleks on 10.04.2017.
 */

public interface PrivatBankService {

    @GET("infrastructure?json&atm")
    Observable<Example> fetchATMs(@Query("city") String city);
}
