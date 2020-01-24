package com.tofik.exoplayerdemo.view

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import com.tofik.exoplayerdemo.R
import com.tofik.exoplayerdemo.databinding.ActivityMainBinding
import com.tofik.coolexoplayer.exoplayer.cool.widget.PressablePlayerSelector
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private val viewModel by lazy { HomeViewModel() }
    private lateinit var homeBinding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        if (savedInstanceState == null) viewModel.init(applicationContext)
        homeBinding.lifecycleOwner = this
        homeBinding.model = viewModel
        val selector = PressablePlayerSelector(player_container)
        player_container!!.playerSelector = selector
        viewModel.setSelector(selector)
        viewModel.getHomeList()

    }
}
