package com.tofik.coolexoplayer.exoplayer.cool.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public interface DrmMedia {

    // DRM Scheme
    @NonNull
    String getType();

    @Nullable
    String getLicenseUrl();

    @Nullable
    String[] getKeyRequestPropertiesArray();

    boolean multiSession();
}
