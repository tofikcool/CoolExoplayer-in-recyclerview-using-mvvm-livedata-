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

import android.content.Context;
import android.net.Uri;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;

import static com.tofik.coolexoplayer.exoplayer.CoolExo.with;
import static com.tofik.coolexoplayer.exoplayer.cool.CoolUtil.checkNotNull;

/**
 * Usage: use this as-it or inheritance.
 *
 * @author eneim (2018/02/04).
 * @since 3.4.0
 */

@SuppressWarnings({ "unused", "WeakerAccess" }) //
public class DefaultExoCreator implements ExoCreator, MediaSourceEventListener {

   CoolExo cool;  // per application
   Config config;
  private  TrackSelector trackSelector;  // 'maybe' stateless
  private  LoadControl loadControl;  // stateless
  private MediaSourceBuilder mediaSourceBuilder;  // stateless
  private  RenderersFactory renderersFactory;  // stateless
  private  DataSource.Factory mediaDataSourceFactory;  // stateless
  private  DataSource.Factory manifestDataSourceFactory; // stateless

  @SuppressWarnings("unchecked")  //
  public DefaultExoCreator(@NonNull CoolExo cool, @NonNull Config config) {
    this.cool = checkNotNull(cool);
    this.config = checkNotNull(config);
    trackSelector = new DefaultTrackSelector();
    loadControl = config.loadControl;
    mediaSourceBuilder = config.mediaSourceBuilder;
    renderersFactory = new DefaultRenderersFactory(this.cool.context, config.extensionMode);
    DataSource.Factory baseFactory = config.dataSourceFactory;
    if (baseFactory == null) {
      baseFactory = new DefaultHttpDataSourceFactory(cool.appName, config.meter);
    }
    DataSource.Factory factory = new DefaultDataSourceFactory(this.cool.context,  //
        config.meter, baseFactory);
    if (config.cache != null) factory = new CacheDataSourceFactory(config.cache, factory);
    mediaDataSourceFactory = factory;
    manifestDataSourceFactory = new DefaultDataSourceFactory(this.cool.context, this.cool.appName);
  }

  public DefaultExoCreator(Context context, Config config) {
    this(with(context), config);
  }

  @SuppressWarnings("SimplifiableIfStatement") @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DefaultExoCreator that = (DefaultExoCreator) o;

    if (!cool.equals(that.cool)) return false;
    if (!trackSelector.equals(that.trackSelector)) return false;
    if (!loadControl.equals(that.loadControl)) return false;
    if (!mediaSourceBuilder.equals(that.mediaSourceBuilder)) return false;
    if (!renderersFactory.equals(that.renderersFactory)) return false;
    if (!mediaDataSourceFactory.equals(that.mediaDataSourceFactory)) return false;
    return manifestDataSourceFactory.equals(that.manifestDataSourceFactory);
  }

  @Override
  public int hashCode() {
    int result = cool.hashCode();
    result = 31 * result + trackSelector.hashCode();
    result = 31 * result + loadControl.hashCode();
    result = 31 * result + mediaSourceBuilder.hashCode();
    result = 31 * result + renderersFactory.hashCode();
    result = 31 * result + mediaDataSourceFactory.hashCode();
    result = 31 * result + manifestDataSourceFactory.hashCode();
    return result;
  }

  final TrackSelector getTrackSelector() {
    return trackSelector;
  }

  @Nullable @Override
  public Context getContext() {
    return cool.context;
  }

  @NonNull @Override
  public SimpleExoPlayer createPlayer() {
    return new CoolExoPlayer(cool.context, renderersFactory, trackSelector, loadControl,
        new DefaultBandwidthMeter(), config.drmSessionManager, Util.getLooper());
  }

  @NonNull @Override
  public MediaSource createMediaSource(@NonNull Uri uri, String fileExt) {
    return mediaSourceBuilder.buildMediaSource(this.cool.context, uri, fileExt, new Handler(),
        manifestDataSourceFactory, mediaDataSourceFactory, this);
  }

  @NonNull @Override //
  public PlayableImpl createPlayable(@NonNull Uri uri, String fileExt) {
    return new PlayableImpl(this, uri, fileExt);
  }

  /// MediaSourceEventListener

  @Override
  public void onLoadStarted(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                            LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // no-ops
  }

  @Override
  public void onLoadCompleted(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                              LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // no-ops
  }

  @Override
  public void onLoadCanceled(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                             LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // no-ops
  }

  @Override
  public void onLoadError(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                          LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error,
                          boolean wasCanceled) {
    // no-ops
  }

  @Override
  public void onReadingStarted(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {
    // no-ops
  }

  @Override
  public void onUpstreamDiscarded(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId,
      MediaLoadData mediaLoadData) {
    // no-ops
  }

  @Override
  public void onDownstreamFormatChanged(int windowIndex,
                                        @Nullable MediaSource.MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
    // no-ops
  }

  @Override
  public void onMediaPeriodCreated(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {
    // no-ops
  }

  @Override
  public void onMediaPeriodReleased(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {
    // no-ops
  }
}
