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

package com.tofik.coolexoplayer.exoplayer.cool;

import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.tofik.coolexoplayer.exoplayer.cool.media.PlaybackInfo;
import com.tofik.coolexoplayer.exoplayer.cool.media.VolumeInfo;
import com.tofik.coolexoplayer.exoplayer.cool.widget.Container;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.CopyOnWriteArraySet;


/**
 * Definition of a Player used in cool. Besides common playback command ({@link #play()}, {@link
 * #pause()}, etc), it provides the library necessary information about the playback and
 * components.
 *
 * @author eneim | 5/31/17.
 */

public interface CoolPlayer {

  @NonNull View getPlayerView();

  @NonNull
  PlaybackInfo getCurrentPlaybackInfo();

  /**
   * Initialize resource for the incoming playback. After this point, {@link CoolPlayer} should be
   * able to start the playback at anytime in the future (This doesn't mean that any call to {@link
   * CoolPlayer#play()} will start the playback immediately. It can start buffering enough resource
   * before any rendering).
   *
   * @param container the RecyclerView contains this Player.
   * @param playbackInfo initialize info for the preparation.
   */
  void initialize(@NonNull Container container, @NonNull PlaybackInfo playbackInfo);

  /**
   * Start playback or resume from a pausing state.
   */
  void play();

  /**
   * Pause current playback.
   */
  void pause();

  boolean isPlaying();

  /**
   * Tear down all the setup. This should release all player instances.
   */
  void release();

  boolean wantsToPlay();

  /**
   * @return prefer playback order in list. Can be customized.
   */
  int getPlayerOrder();

  /**
   * A convenient callback to help {@link CoolPlayer} to listen to different playback states.
   */
  interface EventListener {

    void onFirstFrameRendered();

    void onBuffering(); // ExoPlayer state: 2

    void onPlaying(); // ExoPlayer state: 3, play flag: true

    void onPaused();  // ExoPlayer state: 3, play flag: false

    void onCompleted(); // ExoPlayer state: 4
  }

  interface OnVolumeChangeListener {

    void onVolumeChanged(@NonNull VolumeInfo volumeInfo);
  }

  interface OnErrorListener {

    void onError(Exception error);
  }
  interface OnMuteListener {

    void onMutelistener(@NonNull VolumeInfo volumeInfo);
  }


  class EventListeners extends CopyOnWriteArraySet<EventListener> implements EventListener {

    @Override public void onFirstFrameRendered() {
      for (EventListener listener : this) {
        listener.onFirstFrameRendered();
      }
    }

    @Override public void onBuffering() {
      for (EventListener listener : this) {
        listener.onBuffering();
      }
    }

    @Override public void onPlaying() {
      for (EventListener listener : this) {
        listener.onPlaying();
      }
    }

    @Override public void onPaused() {
      for (EventListener listener : this) {
        listener.onPaused();
      }
    }

    @Override public void onCompleted() {
      for (EventListener listener : this) {
        listener.onCompleted();
      }
    }
  }

  class ErrorListeners extends CopyOnWriteArraySet<OnErrorListener>
          implements CoolPlayer.OnErrorListener {

    @Override public void onError(Exception error) {
      for (CoolPlayer.OnErrorListener listener : this) {
        listener.onError(error);
      }
    }
  }

  class VolumeChangeListeners extends CopyOnWriteArraySet<OnVolumeChangeListener>
          implements CoolPlayer.OnVolumeChangeListener {

    @Override public void onVolumeChanged(@NonNull VolumeInfo volumeInfo) {
      for (CoolPlayer.OnVolumeChangeListener listener : this) {
        listener.onVolumeChanged(volumeInfo);
      }
    }
  }


  class MuteListener extends CopyOnWriteArraySet<OnMuteListener>
          implements CoolPlayer.OnMuteListener {

    @Override
    public void onMutelistener(@NonNull VolumeInfo volumeInfo) {
      for (CoolPlayer.OnMuteListener listener : this) {
        listener.onMutelistener(volumeInfo);
      }
    }
  }

  // Adapt from ExoPlayer.
  @Retention(RetentionPolicy.SOURCE)  //
  @IntDef({ State.STATE_IDLE, State.STATE_BUFFERING, State.STATE_READY, State.STATE_END })  //
  @interface State {
    int STATE_IDLE = 1;
    int STATE_BUFFERING = 2;
    int STATE_READY = 3;
    int STATE_END = 4;
  }
}
