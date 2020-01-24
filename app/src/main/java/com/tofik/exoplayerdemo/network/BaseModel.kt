package com.tofik.exoplayerdemo.network

import com.google.gson.annotations.SerializedName
import java.io.Serializable

open class BaseModel<T> : Serializable {
    @SerializedName("statusCode")
    var statusCode: Int = 0
    @SerializedName("message")
    var message: String = ""
    @SerializedName("data")
    var data: T? = null
}
