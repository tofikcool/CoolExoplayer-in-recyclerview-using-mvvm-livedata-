/*
 * Copyright (c) 2018 Nam Nguyen, nam@ene.im
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tofik.coolexoplayer.exoplayer;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.tofik.coolexoplayer.exoplayer.cool.CoolPlayer;
import com.tofik.coolexoplayer.exoplayer.cool.annotations.RemoveIn;
import com.tofik.coolexoplayer.exoplayer.cool.media.PlaybackInfo;
import com.tofik.coolexoplayer.exoplayer.cool.media.VolumeInfo;
import com.tofik.coolexoplayer.exoplayer.cool.widget.Container;

import java.util.stream.Stream;

import static com.tofik.coolexoplayer.exoplayer.CoolExo.with;
import static com.tofik.coolexoplayer.exoplayer.cool.CoolUtil.checkNotNull;


/**
 * An implementation of {@link CoolPlayerHelper} where the actual Player is an {@link ExoPlayer}
 * implementation. This is a bridge between ExoPlayer's callback and CoolPlayerHelper behaviors.
 *
 * @author eneim (2018/01/24).
 * @since 3.4.0
 */

public  class ExoPlayerViewHelper extends CoolPlayerHelper {

    @NonNull
    private ExoPlayable playable;
    @NonNull
    private MyEventListeners listeners;
    private  boolean lazyPrepare;
    private  boolean isMute;


    // Container is no longer required for constructing new instance.
    @SuppressWarnings("unused")
    @RemoveIn(version = "3.6.0")
    @Deprecated  //
    public ExoPlayerViewHelper(Container container, @NonNull CoolPlayer player, @NonNull Uri uri, Boolean isMute) {
        this(player, uri, isMute);
    }

    public ExoPlayerViewHelper(@NonNull CoolPlayer player, @NonNull Uri uri, Boolean isMute) {
        this(player, uri, null, isMute);
    }

    private ExoPlayerViewHelper(@NonNull CoolPlayer player, @NonNull Uri uri,
                               @Nullable String fileExt, Boolean isMute) {
        this(player, uri, fileExt, with(player.getPlayerView().getContext()).getDefaultCreator(), isMute);
    }

    /**
     * Config instance should be kept as global instance.
     */
    public ExoPlayerViewHelper(@NonNull CoolPlayer player, @NonNull Uri uri, @Nullable String fileExt,
                               @NonNull Config config, Boolean isMute) {
        this(player, uri, fileExt,
                with(player.getPlayerView().getContext()).getCreator(checkNotNull(config)), isMute);
    }

    private ExoPlayerViewHelper(@NonNull CoolPlayer player, @NonNull Uri uri, @Nullable String fileExt,
                               @NonNull ExoCreator creator, Boolean isMute) {
        this(player, new ExoPlayable(creator, uri, fileExt), isMute);
    }

    private ExoPlayerViewHelper(@NonNull CoolPlayer player, @NonNull ExoPlayable playable, Boolean isMute) {
        super(player, isMute);
        //noinspection ConstantConditions
        if (player.getPlayerView() == null || !(player.getPlayerView() instanceof PlayerView)) {
            throw new IllegalArgumentException("Require non-null PlayerView");
        }

        listeners = new MyEventListeners();
        this.playable = playable;
        this.lazyPrepare = true;
        this.isMute = isMute;
    }

    @Override
    protected void initialize(@NonNull PlaybackInfo playbackInfo) {
        playable.setPlaybackInfo(playbackInfo,isMute);
        playable.addEventListener(listeners);
        playable.addErrorListener(super.getErrorListeners());
        playable.addOnVolumeChangeListener(super.getVolumeChangeListeners());
        playable.prepare(!lazyPrepare);
        playable.setPlayerView((PlayerView) player.getPlayerView());
        /*if (isMute) playable.setVolume(0f);
        else playable.setVolume(1f);*/

    }

    @Override
    public void release() {
        super.release();
        playable.setPlayerView(null);
        playable.removeOnVolumeChangeListener(super.getVolumeChangeListeners());
        playable.removeErrorListener(super.getErrorListeners());
        playable.removeEventListener(listeners);
        playable.release();
    }

    @Override
    public void play() {
        playable.play();
    }

    @Override
    public void pause() {
        playable.pause();
    }

    @Override
    public boolean isPlaying() {
        return playable.isPlaying();
    }

    @Override
    public void setVolume(float volume) {
        playable.setVolume(volume);
    }

    @Override
    public float getVolume() {
        return playable.getVolume();
    }

    @Override
    public void setVolumeInfo(@NonNull VolumeInfo volumeInfo) {
        playable.setVolumeInfo(volumeInfo);
    }

    @Override
    @NonNull
    public VolumeInfo getVolumeInfo() {
        return playable.getVolumeInfo();
    }

    @NonNull
    @Override
    public PlaybackInfo getLatestPlaybackInfo() {
        return playable.getPlaybackInfo();
    }

    @Override
    public void setPlaybackInfo(@NonNull PlaybackInfo playbackInfo) {
        this.playable.setPlaybackInfo(playbackInfo, isMute);
    }

    public void addEventListener(@NonNull Playable.EventListener listener) {
        //noinspection ConstantConditions
        if (listener != null) this.listeners.add(listener);
    }

    public void removeEventListener(Playable.EventListener listener) {
        this.listeners.remove(listener);
    }

    // A proxy, to also hook into CoolPlayerHelper's state change event.
    private class MyEventListeners extends Playable.EventListeners implements Playable.EventListener {

        MyEventListeners() {
            super();
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            ExoPlayerViewHelper.super.onPlayerStateUpdated(playWhenReady, playbackState); // important
            super.onPlayerStateChanged(playWhenReady, playbackState);

        }

        @Override
        public void onRenderedFirstFrame() {
            super.onRenderedFirstFrame();
            internalListener.onFirstFrameRendered();
            for (CoolPlayer.EventListener listener : ExoPlayerViewHelper.super.getEventListeners()) {
                listener.onFirstFrameRendered();
            }
        }

        @NonNull
        @Override
        public Stream<Playable.EventListener> stream() {
            return null;
        }

        @NonNull
        @Override
        public Stream<Playable.EventListener> parallelStream() {
            return null;
        }
    }
}