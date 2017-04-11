package com.kuzko.aleksey.privatbankatmssgkuzko;

import com.kuzko.aleksey.privatbankatmssgkuzko.datamodel.Example;

import retrofit2.http.GET;
import retrofit2.http.Query;
import rx.Observable;

/**
 * Created by Aleks on 10.04.2017.
 */

public interface PrivatBankService {

    @GET("infrastructure?json&atm")
    Observable<Example> fetchATM (@Query("city") String city);
}
