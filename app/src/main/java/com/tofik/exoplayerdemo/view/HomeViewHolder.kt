package com.tofik.exoplayerdemo.view

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.recyclerview.widget.RecyclerView

import com.tofik.exoplayerdemo.databinding.ItemHomeBinding
import com.tofik.exoplayerdemo.utils.Data
import com.google.android.exoplayer2.ui.PlayerView
import com.tofik.coolexoplayer.exoplayer.ExoPlayerDispatcher
import com.tofik.coolexoplayer.exoplayer.ExoPlayerViewHelper
import com.tofik.coolexoplayer.exoplayer.cool.CoolPlayer
import com.tofik.coolexoplayer.exoplayer.cool.CoolUtil
import com.tofik.coolexoplayer.exoplayer.cool.media.PlaybackInfo
import com.tofik.coolexoplayer.exoplayer.cool.media.VolumeInfo
import com.tofik.coolexoplayer.exoplayer.cool.widget.Container
import com.tofik.coolexoplayer.exoplayer.cool.widget.PressablePlayerSelector

class HomeViewHolder(
    val binding: ItemHomeBinding,
    itemView: View,
    selector: PressablePlayerSelector?,
    val mcontex: Context,
    val homeAdapter: HomeAdapter
) :
    RecyclerView.ViewHolder(binding.root), CoolPlayer, CoolPlayer.OnVolumeChangeListener {

    lateinit var list: MutableList<HomeData>
    var isMute: Boolean? =null
    var helper: ExoPlayerViewHelper? = null
    lateinit var mediaUri: Uri
    var playerView: PlayerView = binding.player
    var data= Data


    override fun onVolumeChanged(volumeInfo: VolumeInfo) {
        data.isMute = volumeInfo.isMute
    }



    override fun getPlayerView(): View {
        return playerView
    }

    override fun getCurrentPlaybackInfo(): PlaybackInfo {
        return if (helper != null) helper!!.latestPlaybackInfo else PlaybackInfo()
    }

    override fun initialize(
        container: Container,
        playbackInfo: PlaybackInfo
    ) {
        if (helper == null) {
            helper = ExoPlayerViewHelper(this, mediaUri, data.isMute)

        }

        helper!!.initialize(container, playbackInfo,data.isMute)

        helper!!.addOnVolumeChangeListener(this)

    }

    override fun play() {
        if (helper != null) helper!!.play()
    }

    override fun pause() {
        if (helper != null) helper!!.pause()
    }

    override fun isPlaying(): Boolean {
        return helper != null && helper!!.isPlaying
    }

    override fun release() {
        if (helper != null) {
            helper!!.release()
            helper = null
        }
    }

    override fun wantsToPlay(): Boolean {
        return CoolUtil.visibleAreaOffset(this, itemView.parent) >= 0.85
    }

    override fun getPlayerOrder(): Int {
        return adapterPosition
    }

    override fun toString(): String {
        return "ExoPlayer{" + hashCode() + " " + adapterPosition + "}"
    }


    fun bind(data: HomeData, list: MutableList<HomeData>) {
        binding.position = adapterPosition
        binding.holder = this
        binding.data = data
        this.list = list

        mediaUri = Uri.parse(data.video)
//        playerView.
    }

    init {
        if (selector != null) playerView.setControlDispatcher(ExoPlayerDispatcher(selector, this))
    }

    fun showAllFeeds(view: View, position: Int, data: HomeData) {
        /*val intent = Intent(view.context, ActivityFeed::class.java)
        intent.putExtra("data", data.categoryName)
        view.context.startActivity(intent)*/

        val param = Pair<String, Any?>("title", data.videoName)
//        view.findNavController().navigate(R.id.action_fragmentHome_to_feedActivity, bundleOf(param))
    }



}
