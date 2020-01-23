package com.example.exoplayerdemo.view
import com.google.gson.annotations.SerializedName


 class HomeData
 {

    @SerializedName("id")
    var id: String?=null
    @SerializedName("is_favourite")
    var isFavourite: Boolean?=null
    @SerializedName("is_offline")
    var isOffline: Boolean?=null
    @SerializedName("name")
    var name: String?=null
    @SerializedName("video")
    var video: String?=null
    @SerializedName("video_id")
    var videoId: String?=null
    @SerializedName("video_name")
    var videoName: String?=null
}