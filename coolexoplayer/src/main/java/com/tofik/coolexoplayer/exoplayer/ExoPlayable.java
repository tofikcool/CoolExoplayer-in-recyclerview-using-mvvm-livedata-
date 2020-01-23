
package com.tofik.coolexoplayer.exoplayer;

import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.tofik.coolexoplayer.R;

import static com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS;
import static com.tofik.coolexoplayer.exoplayer.CoolExo.cool;

/**
 * Making {@link Playable} extensible. This can be used with custom {@link ExoCreator}. Extending
 * this class must make sure the re-usability of the implementation.
 *
 * @author tofik 23/1/2020.
 * @since 3.4.0
 */

public class ExoPlayable extends PlayableImpl {

  @SuppressWarnings("unused") private static final String TAG = "CoolExo:Playable";

  private EventListener listener;

  // Adapt from ExoPlayer demo.
  @SuppressWarnings("WeakerAccess") protected boolean inErrorState = false;
  @SuppressWarnings("WeakerAccess") protected TrackGroupArray lastSeenTrackGroupArray;

  public ExoPlayable(ExoCreator creator, Uri uri, String fileExt) {
    super(creator, uri, fileExt);
  }

  @Override
  public void prepare(boolean prepareSource) {
    if (listener == null) {
      listener = new Listener();
      super.addEventListener(listener);
    }
    super.prepare(prepareSource);
    this.lastSeenTrackGroupArray = null;
    this.inErrorState = false;
  }

  @Override
  public void setPlayerView(@Nullable PlayerView playerView) {
    // This will also clear these flags
    // TODO [20180301] double check this setup.
    if (playerView != this.playerView) {
      this.lastSeenTrackGroupArray = null;
      this.inErrorState = false;
    }
    super.setPlayerView(playerView);
  }

  @Override
  public void reset() {
    super.reset();
    this.lastSeenTrackGroupArray = null;
    this.inErrorState = false;
  }

  @Override
  public void release() {
    if (listener != null) {
      super.removeEventListener(listener);
      listener = null;
    }
    super.release();
    this.lastSeenTrackGroupArray = null;
    this.inErrorState = false;
  }

  @SuppressWarnings({ "WeakerAccess", "unused" }) //
  protected void onErrorMessage(@NonNull String message) {
    // Sub class can have custom reaction about the error here, including not to show this toast
    // (by not calling super.onErrorMessage(message)).
    if (this.errorListeners.size() > 0) {
      this.errorListeners.onError(new RuntimeException(message));
    } else if (playerView != null) {
      Toast.makeText(playerView.getContext(), message, Toast.LENGTH_SHORT).show();
    }
  }

  private class Listener extends DefaultEventListener {

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
      super.onTracksChanged(trackGroups, trackSelections);
      if (trackGroups == lastSeenTrackGroupArray) return;
      lastSeenTrackGroupArray = trackGroups;
      if (!(creator instanceof DefaultExoCreator)) return;
      TrackSelector selector = ((DefaultExoCreator) creator).getTrackSelector();
      if (selector instanceof DefaultTrackSelector) {
        MappedTrackInfo trackInfo = ((DefaultTrackSelector) selector).getCurrentMappedTrackInfo();
        if (trackInfo != null) {
          if (trackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO) == RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
            onErrorMessage(cool.getString(R.string.error_unsupported_video));
          }

          if (trackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO) == RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
            onErrorMessage(cool.getString(R.string.error_unsupported_audio));
          }
        }
      }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      /// Adapt from ExoPlayer Demo
      String errorString = null;
      if (error.type == ExoPlaybackException.TYPE_RENDERER) {
        Exception cause = error.getRendererException();
        if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
          // Special case for decoder initialization failures.
          MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
              (MediaCodecRenderer.DecoderInitializationException) cause;
          if (decoderInitializationException.decoderName == null) {
            if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
              errorString = cool.getString(R.string.error_querying_decoders);
            } else if (decoderInitializationException.secureDecoderRequired) {
              errorString = cool.getString(R.string.error_no_secure_decoder,
                  decoderInitializationException.mimeType);
            } else {
              errorString = cool.getString(R.string.error_no_decoder,
                  decoderInitializationException.mimeType);
            }
          } else {
            errorString = cool.getString(R.string.error_instantiating_decoder,
                decoderInitializationException.decoderName);
          }
        }
      }

      if (errorString != null) onErrorMessage(errorString);

      inErrorState = true;
      if (isBehindLiveWindow(error)) {
        ExoPlayable.super.reset();
      } else {
        ExoPlayable.super.updatePlaybackInfo();
      }

      super.onPlayerError(error);
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
      if (inErrorState) {
        // Adapt from ExoPlayer demo.
        // "This will only occur if the user has performed a seek whilst in the error state. Update
        // the resume position so that if the user then retries, playback will resume from the
        // position to which they seek." - ExoPlayer
       ExoPlayable.super.updatePlaybackInfo();
      }

      super.onPositionDiscontinuity(reason);
    }
  }

  @SuppressWarnings("WeakerAccess") static boolean isBehindLiveWindow(ExoPlaybackException error) {
    if (error.type != ExoPlaybackException.TYPE_SOURCE) return false;
    Throwable cause = error.getSourceException();
    while (cause != null) {
      if (cause instanceof BehindLiveWindowException) return true;
      cause = cause.getCause();
    }
    return false;
  }
}
