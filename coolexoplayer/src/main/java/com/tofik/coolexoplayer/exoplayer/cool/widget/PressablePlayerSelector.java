
package com.tofik.coolexoplayer.exoplayer.cool.widget;

import android.view.View;
import android.view.View.OnLongClickListener;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tofik.coolexoplayer.exoplayer.cool.CoolPlayer;
import com.tofik.coolexoplayer.exoplayer.cool.CoolUtil;
import com.tofik.coolexoplayer.exoplayer.cool.PlayerSelector;
import com.tofik.coolexoplayer.exoplayer.cool.annotations.Beta;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;
import static com.tofik.coolexoplayer.exoplayer.cool.widget.Common.allowsToPlay;
import static com.tofik.coolexoplayer.exoplayer.cool.widget.Common.findFirst;
import static java.util.Collections.singletonList;

/**
 * @author eneim (2018/08/17).
 *
 * A 'Press to Play' {@link PlayerSelector}.
 *
 * This is a {@link OnLongClickListener} that co-operates with {@link Container} to selectively
 * trigger the playback. The common usecase is to allow user to long click on a {@link CoolPlayer}
 * to trigger its playback. In that case, we should set that {@link CoolPlayer} to highest priority
 * among the candidates, and also to clear its priority when user scroll it out of the playable region.
 *
 * This class also have a {@link #toPause} field to handle the case where User want to specifically
 * pause a {@link CoolPlayer}. This selection will also be cleared with the same rules of toPlay.
 * @since 3.6.0.2802
 */
@SuppressWarnings("WeakerAccess") @Beta //
public  class PressablePlayerSelector implements PlayerSelector, OnLongClickListener {

  protected final WeakReference<Container> weakContainer;
  protected final PlayerSelector delegate;
  protected final AtomicInteger toPlay = new AtomicInteger(NO_POSITION);
  protected final AtomicInteger toPause = new AtomicInteger(NO_POSITION);

  final Common.Filter<CoolPlayer> filterToPlay = new Common.Filter<CoolPlayer>() {
    @Override
    public boolean accept(CoolPlayer target) {
      return target.getPlayerOrder() == toPlay.get();
    }
  };

  final Common.Filter<CoolPlayer> filterToPause = new Common.Filter<CoolPlayer>() {
    @Override
    public boolean accept(CoolPlayer target) {
      return target.getPlayerOrder() == toPause.get();
    }
  };

  public PressablePlayerSelector(Container container) {
    this(container, DEFAULT);
  }

  public PressablePlayerSelector(Container container, PlayerSelector delegate) {
    this(new WeakReference<>(CoolUtil.checkNotNull(container)), CoolUtil.checkNotNull(delegate));
  }

  PressablePlayerSelector(WeakReference<Container> container, PlayerSelector delegate) {
    this.weakContainer = container;
    this.delegate = CoolUtil.checkNotNull(delegate);
  }

  @Override
  public boolean onLongClick(View v) {
    Container container = weakContainer.get();
    if (container == null) return false;  // fail fast

    toPause.set(NO_POSITION); // long click will always mean to 'press to play'.

    RecyclerView.ViewHolder viewHolder = container.findContainingViewHolder(v);
    boolean handled = viewHolder instanceof CoolPlayer;
    if (handled) handled = allowsToPlay((CoolPlayer) viewHolder);

    int position = handled ? viewHolder.getAdapterPosition() : NO_POSITION;
    if (handled) handled = position != toPlay.getAndSet(position);

    if (handled) container.onScrollStateChanged(RecyclerView.SCROLL_STATE_IDLE);
    return handled;
  }

  @Override
  @NonNull public Collection<CoolPlayer> select(@NonNull Container container,
                                                @NonNull List<CoolPlayer> items) {
    // Make sure client doesn't use this instance to wrong Container.
    if (container != this.weakContainer.get()) return new ArrayList<>();

    // If there is a request to pause, we need to prioritize that first.
    CoolPlayer toPauseCandidate = null;
    if (toPause.get() >= 0) {
      toPauseCandidate = findFirst(items, filterToPause);
      if (toPauseCandidate == null) {
        // the order to pause doesn't present in candidate, we clear the selection.
        toPause.set(NO_POSITION); // remove the paused one.
      }
    }

    if (toPlay.get() >= 0) {
      CoolPlayer toPlayCandidate = findFirst(items, filterToPlay);
      if (toPlayCandidate != null) {
        if (allowsToPlay(toPlayCandidate)) {
          return singletonList(toPlayCandidate);
        }
      }
    }
    // In the list of candidates, selected item no longer presents or is not allowed to play,
    // we should reset the selection.
    toPlay.set(NO_POSITION);
    // Wrap by an ArrayList to make it modifiable.
    Collection<CoolPlayer> selected = new ArrayList<>(delegate.select(container, items));
    if (toPauseCandidate != null) selected.remove(toPauseCandidate);
    return selected;
  }

  @Override
  @NonNull public PlayerSelector reverse() {
    return new PressablePlayerSelector(this.weakContainer, delegate.reverse()) {
      @NonNull
      @Override
      public Collection<CoolPlayer> select(@NonNull Container container, @NonNull List<CoolPlayer> items) {
        return null;
      }
    };
  }

  public boolean toPlay(int position) {
    if (toPause.get() == position) toPause.set(NO_POSITION);
    Container container = weakContainer.get();
    if (container == null) return false;
    if (position != toPlay.getAndSet(position)) {
      container.onScrollStateChanged(RecyclerView.SCROLL_STATE_IDLE);
      return true;
    }

    return false;
  }

  public void toPause(int position) {
    toPlay.set(NO_POSITION);
    toPause.set(position);
  }
}
