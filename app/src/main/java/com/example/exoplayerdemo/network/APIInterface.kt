package com.example.exoplayerdemo.network

import com.example.exoplayerdemo.view.HomeData
import io.reactivex.Observable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface APIInterface {


    @GET("5e21b8592f0000840077d8ad")
    fun getHomeList(): Observable<BaseModel<List<HomeData>>>

}
