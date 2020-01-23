
package com.tofik.coolexoplayer.exoplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.ContentType;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;

import static android.text.TextUtils.isEmpty;
import static com.google.android.exoplayer2.util.Util.inferContentType;

/**
 * @author tofik 23/1/2020.
 * @since 3.4.0
 */

public interface MediaSourceBuilder {

    @NonNull
    MediaSource buildMediaSource(@NonNull Context context, @NonNull Uri uri,
                                 @Nullable String fileExt, @Nullable Handler handler,
                                 @NonNull DataSource.Factory manifestDataSourceFactory,
                                 @NonNull DataSource.Factory mediaDataSourceFactory,
                                 @Nullable MediaSourceEventListener listener);

    MediaSourceBuilder DEFAULT = new MediaSourceBuilder() {
        @NonNull
        @Override
        public MediaSource buildMediaSource(@NonNull Context context, @NonNull Uri uri,
                                            @Nullable String ext, @Nullable Handler handler,
                                            @NonNull DataSource.Factory manifestDataSourceFactory,
                                            @NonNull DataSource.Factory mediaDataSourceFactory, MediaSourceEventListener listener) {
            @ContentType int type = isEmpty(ext) ? inferContentType(uri) : inferContentType("." + ext);
            MediaSource result;
            switch (type) {
                case C.TYPE_SS:
                    result = new SsMediaSource.Factory( //
                            new DefaultSsChunkSource.Factory(mediaDataSourceFactory), manifestDataSourceFactory)//
                            .createMediaSource(uri);
                    break;
                case C.TYPE_DASH:
                    result = new DashMediaSource.Factory(
                            new DefaultDashChunkSource.Factory(mediaDataSourceFactory), manifestDataSourceFactory)
                            .createMediaSource(uri);
                    break;
                case C.TYPE_HLS:
                    result = new HlsMediaSource.Factory(mediaDataSourceFactory) //
                            .createMediaSource(uri);
                    break;
                case C.TYPE_OTHER:
                    result = new ExtractorMediaSource.Factory(mediaDataSourceFactory) //
                            .createMediaSource(uri);
                    break;
                default:
                    throw new IllegalStateException("Unsupported type: " + type);
            }

            result.addEventListener(handler, listener);
            return result;
        }
    };

    MediaSourceBuilder LOOPING = new MediaSourceBuilder() {

        @NonNull
        @Override
        public MediaSource buildMediaSource(@NonNull Context context, @NonNull Uri uri,
                                            @Nullable String fileExt, @Nullable Handler handler,
                                            @NonNull DataSource.Factory manifestDataSourceFactory,
                                            @NonNull DataSource.Factory mediaDataSourceFactory,
                                            @Nullable MediaSourceEventListener listener) {
            return new LoopingMediaSource(
                    DEFAULT.buildMediaSource(context, uri, fileExt, handler, manifestDataSourceFactory,
                            mediaDataSourceFactory, listener));
        }
    };
}
