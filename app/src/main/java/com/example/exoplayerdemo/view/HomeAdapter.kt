package com.example.exoplayerdemo.view

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.exoplayerdemo.R
import com.example.exoplayerdemo.databinding.ItemHomeBinding
import com.example.exoplayerdemo.view.HomeData
import com.example.exoplayerdemo.view.HomeViewHolder
import com.tofik.coolexoplayer.exoplayer.cool.widget.PressablePlayerSelector


class HomeAdapter(
    private val list: MutableList<HomeData>,
    private val selector: PressablePlayerSelector?,
    val context: Context
) :
    RecyclerView.Adapter<HomeViewHolder>() {

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        val binder = DataBindingUtil.inflate<ItemHomeBinding>(
            layoutInflater,
            R.layout.item_home,
            parent,
            false
        )
        val viewHolder = HomeViewHolder(binder, parent, this.selector, context,this)

        if (this.selector != null) viewHolder.itemView.setOnLongClickListener(this.selector)

        return viewHolder
    }

    override fun onBindViewHolder(holder: HomeViewHolder, position: Int) {
        holder.bind(list[position],list)
    }
}