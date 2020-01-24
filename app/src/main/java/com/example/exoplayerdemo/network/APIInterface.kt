package com.example.exoplayerdemo.network

import com.example.exoplayerdemo.view.HomeData
import io.reactivex.Observable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface APIInterface {


    @GET("5e2adf4b32000077001c6e9e")
    fun getHomeList(): Observable<BaseModel<List<HomeData>>>

}
