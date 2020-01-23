

package com.tofik.coolexoplayer.exoplayer;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.tofik.coolexoplayer.exoplayer.cool.CoolUtil;


/**
 * Abstract the {@link DefaultBandwidthMeter}, provide a wider use.
 *
 * @author Tofik 23/1/2020
 * @since 3.4.0
 */


@SuppressWarnings("WeakerAccess") //
public final class BaseMeter<T extends BandwidthMeter> implements BandwidthMeter, TransferListener {

    @NonNull
    protected final T bandwidthMeter;
    @NonNull
    protected final TransferListener transferListener;

    /**
     * @deprecated use {@link #BaseMeter(BandwidthMeter)} instead.
     */
    @SuppressWarnings({"unused"}) //
    @Deprecated //
    public BaseMeter(@NonNull T bandwidthMeter, @NonNull TransferListener transferListener) {
        this(bandwidthMeter);
    }

    public BaseMeter(@NonNull T bandwidthMeter) {
        this.bandwidthMeter = CoolUtil.checkNotNull(bandwidthMeter);
        this.transferListener = CoolUtil.checkNotNull(this.bandwidthMeter.getTransferListener());
    }

    @Override
    public long getBitrateEstimate() {
        return bandwidthMeter.getBitrateEstimate();
    }

    @Override
    @Nullable
    public TransferListener getTransferListener() {
        return bandwidthMeter.getTransferListener();
    }

    @Override
    public void addEventListener(Handler eventHandler, EventListener eventListener) {
        bandwidthMeter.addEventListener(eventHandler, eventListener);
    }

    @Override
    public void removeEventListener(EventListener eventListener) {
        bandwidthMeter.removeEventListener(eventListener);
    }

    @Override
    public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        transferListener.onTransferInitializing(source, dataSpec, isNetwork);
    }

    @Override
    public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        transferListener.onTransferStart(source, dataSpec, isNetwork);
    }

    @Override
    public void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork,
                                   int bytesTransferred) {
        transferListener.onBytesTransferred(source, dataSpec, isNetwork, bytesTransferred);
    }

    @Override
    public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        transferListener.onTransferEnd(source, dataSpec, isNetwork);
    }
}
