package com.example.exoplayerdemo.utils

import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

import com.tofik.coolexoplayer.exoplayer.cool.widget.Container
import com.tofik.coolexoplayer.exoplayer.cool.widget.PressablePlayerSelector

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

@BindingAdapter("setLinearContainer")
fun bindcontainer(
    recyclerView: Container,
    adapter: RecyclerView.Adapter<*>?
) {
    recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
    val selector = PressablePlayerSelector(recyclerView)
    recyclerView.playerSelector = selector
    recyclerView.adapter = adapter
}



