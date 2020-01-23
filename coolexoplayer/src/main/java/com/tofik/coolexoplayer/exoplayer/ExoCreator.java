
package com.tofik.coolexoplayer.exoplayer;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;


public interface ExoCreator {

  String TAG = "CoolExo:Creator";


  @Nullable
  Context getContext();


  @NonNull
  SimpleExoPlayer createPlayer();

  @NonNull
  MediaSource createMediaSource(@NonNull Uri uri, @Nullable String fileExt);

  @NonNull
  PlayableImpl createPlayable(@NonNull Uri uri, @Nullable String fileExt);
}
