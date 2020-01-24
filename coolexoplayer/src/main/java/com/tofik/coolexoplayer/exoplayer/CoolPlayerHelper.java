/*
 * Copyright (c) 2017 Nam Nguyen, nam@ene.im
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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.CallSuper;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.tofik.coolexoplayer.exoplayer.cool.CoolPlayer;
import com.tofik.coolexoplayer.exoplayer.cool.annotations.RemoveIn;
import com.tofik.coolexoplayer.exoplayer.cool.media.PlaybackInfo;
import com.tofik.coolexoplayer.exoplayer.cool.media.VolumeInfo;
import com.tofik.coolexoplayer.exoplayer.cool.widget.Container;

import org.jetbrains.annotations.NotNull;

import static com.tofik.coolexoplayer.exoplayer.cool.CoolUtil.checkNotNull;


/**
 * General definition of a helper class for a specific {@link CoolPlayer}. This class helps
 * forwarding the playback state to the {@link CoolPlayer} if there is any {@link CoolPlayer.EventListener}
 * registered. It also requests the initialization for the Player.
 * <p>
 * From 3.4.0, this class can be reused as much as possible.
 *
 * @author eneim | 6/11/17.
 */
public abstract class CoolPlayerHelper {

    private boolean isMute;


    private final Handler handler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(@NotNull Message msg) {
            boolean playWhenReady = (boolean) msg.obj;
            switch (msg.what) {
                case CoolPlayer.State.STATE_IDLE:

                    break;
                case CoolPlayer.State.STATE_BUFFERING /* Player.STATE_BUFFERING */:
                    internalListener.onBuffering();
                   /* for (CoolPlayer.EventListener listener : getEventListeners()) {
                        listener.onBuffering();

                    }
                    setVolumeInfo(new VolumeInfo(isMute, (isMute) ? 0f : 1f));*/


                    break;
                case CoolPlayer.State.STATE_READY /*  Player.STATE_READY */:
                    if (playWhenReady) {
                        setVolumeInfo(new VolumeInfo(isMute, (isMute) ? 0f : 1f));

                        internalListener.onPlaying();

                    } else {
//                        setVolumeInfo(new VolumeInfo(isMute, (isMute) ? 0f : 1f));

                        internalListener.onPaused();

                    }

                   /* for (CoolPlayer.EventListener listener : getEventListeners()) {
                        if (playWhenReady) {
                            listener.onPlaying();

                        } else {
                            listener.onPaused();

                        }
                    }*/
                    break;
                case CoolPlayer.State.STATE_END /* Player.STATE_ENDED */:
                    internalListener.onCompleted();
                   /* for (CoolPlayer.EventListener listener : getEventListeners()) {
                        listener.onCompleted();
                    }*/

                    break;
                default:
                    break;
            }
            return true;
        }
    });

    @NonNull
    protected final CoolPlayer player;

    // This instance should be setup from #initialize and cleared from #release
    protected Container container;

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) //
    private CoolPlayer.EventListeners eventListeners;

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) //
    private CoolPlayer.VolumeChangeListeners volumeChangeListeners;

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) //
    private CoolPlayer.ErrorListeners errorListeners;

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) //
    private CoolPlayer.MuteListener muteListener;


    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) //
    protected final CoolPlayer.EventListener internalListener = new CoolPlayer.EventListener() {
        @Override
        public void onFirstFrameRendered() {

        }

        @Override
        public void onBuffering() {
        }

        @Override
        public void onPlaying() {
            player.getPlayerView().setKeepScreenOn(true);
        }

        @Override
        public void onPaused() {
            player.getPlayerView().setKeepScreenOn(false);
            if (container != null) {
                container.savePlaybackInfo( //
                        player.getPlayerOrder(), checkNotNull(player.getCurrentPlaybackInfo()));

            }
        }

        @Override
        public void onCompleted() {
            if (container != null) {
                // Save PlaybackInfo.SCRAP to mark this player to be re-init.
                container.savePlaybackInfo(player.getPlayerOrder(), PlaybackInfo.SCRAP);
            }
        }
    };

    CoolPlayerHelper(@NonNull CoolPlayer player, Boolean isMute) {
        this.player = player;
        this.isMute = isMute;
    }

    public final void addPlayerEventListener(@NonNull CoolPlayer.EventListener listener) {
        getEventListeners().add(checkNotNull(listener));
    }

    public final void removePlayerEventListener(CoolPlayer.EventListener listener) {
        if (eventListeners != null) eventListeners.remove(listener);
    }

    /**
     * Initialize the necessary resource for the incoming playback. For example, prepare the
     * ExoPlayer instance for SimpleExoPlayerView. The initialization is feed by an initial playback
     * info, telling if the playback should start from a specific position or from beginning.
     * <p>
     * Normally this info can be obtained from cache if there is cache manager, or {@link PlaybackInfo#SCRAP}
     * if there is no such cached information.
     *
     * @param playbackInfo the initial playback info.
     */
    protected abstract void initialize(@NonNull PlaybackInfo playbackInfo);

    public final void initialize(@NonNull Container container, @NonNull PlaybackInfo playbackInfo, Boolean isMute) {
        this.container = container;
        this.initialize(playbackInfo);
        this.isMute = isMute;
    }

    public abstract void play();

    public abstract void pause();

    public abstract boolean isPlaying();

    /**
     * @deprecated use {@link #setVolumeInfo(VolumeInfo)} instead.
     */
    @RemoveIn(version = "3.6.0")
    @Deprecated  //
    public abstract void setVolume(@FloatRange(from = 0.0, to = 1.0) float volume);

    /**
     * @deprecated use {@link #getVolumeInfo()} instead.
     */
    @RemoveIn(version = "3.6.0")
    @Deprecated  //
    public abstract @FloatRange(from = 0.0, to = 1.0)
    float getVolume();

    public abstract void setVolumeInfo(@NonNull VolumeInfo volumeInfo);

    @NonNull
    public abstract VolumeInfo getVolumeInfo();

    /**
     * Get latest playback info. Either on-going playback info if current player is playing, or latest
     * playback info available if player is paused.
     *
     * @return latest {@link PlaybackInfo} of current Player.
     */
    @NonNull
    public abstract PlaybackInfo getLatestPlaybackInfo();

    public abstract void setPlaybackInfo(@NonNull PlaybackInfo playbackInfo);

    @CallSuper
    public void addOnVolumeChangeListener(@NonNull CoolPlayer.OnVolumeChangeListener listener) {
        getVolumeChangeListeners().add(checkNotNull(listener));
    }


    @CallSuper
    public void removeOnVolumeChangeListener(CoolPlayer.OnVolumeChangeListener listener) {
        if (volumeChangeListeners != null) volumeChangeListeners.remove(listener);
    }

    @CallSuper
    public void addErrorListener(@NonNull CoolPlayer.OnErrorListener listener) {
        getErrorListeners().add(checkNotNull(listener));
    }

    @CallSuper
    public void removeErrorListener(CoolPlayer.OnErrorListener listener) {
        if (errorListeners != null) errorListeners.remove(listener);
    }

    @NonNull
    protected final CoolPlayer.EventListeners getEventListeners() {
        if (eventListeners == null) eventListeners = new CoolPlayer.EventListeners();
        return eventListeners;
    }

    @NonNull
    protected final CoolPlayer.VolumeChangeListeners getVolumeChangeListeners() {
        if (volumeChangeListeners == null) {
            volumeChangeListeners = new CoolPlayer.VolumeChangeListeners();
        }
        return volumeChangeListeners;
    }

    @NonNull
    protected final CoolPlayer.MuteListener getMuteListener() {
        if (muteListener == null) {
            muteListener = new CoolPlayer.MuteListener();
        }
        return muteListener;
    }

    @NonNull
    protected final CoolPlayer.ErrorListeners getErrorListeners() {
        if (errorListeners == null) errorListeners = new CoolPlayer.ErrorListeners();
        return errorListeners;
    }

    // Mimic ExoPlayer
    @CallSuper
    protected final void onPlayerStateUpdated(boolean playWhenReady,
                                              @CoolPlayer.State int playbackState) {
        handler.obtainMessage(playbackState, playWhenReady).sendToTarget();
    }

    @CallSuper
    public void release() {
        handler.removeCallbacksAndMessages(null);
        this.container = null;
    }

    @NonNull
    @Override
    public String toString() {
        return "ToroLib:Helper{" + "player=" + player + ", container=" + container + '}';
    }
}
