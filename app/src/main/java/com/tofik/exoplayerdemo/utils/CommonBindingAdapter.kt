package com.tofik.exoplayerdemo.utils

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.tofik.coolexoplayer.exoplayer.cool.widget.Container
import com.tofik.coolexoplayer.exoplayer.cool.widget.PressablePlayerSelector

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



