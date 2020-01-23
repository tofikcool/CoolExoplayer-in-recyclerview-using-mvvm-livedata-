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

package com.tofik.coolexoplayer.exoplayer.cool.widget;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.WindowInsetsCompat;
import androidx.customview.view.AbsSavedState;
import androidx.recyclerview.widget.RecyclerView;

import com.tofik.coolexoplayer.exoplayer.cool.CacheManager;
import com.tofik.coolexoplayer.exoplayer.cool.CoolPlayer;
import com.tofik.coolexoplayer.exoplayer.cool.PlayerDispatcher;
import com.tofik.coolexoplayer.exoplayer.cool.PlayerSelector;
import com.tofik.coolexoplayer.exoplayer.cool.annotations.RemoveIn;
import com.tofik.coolexoplayer.exoplayer.cool.media.PlaybackInfo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.content.Context.POWER_SERVICE;
import static com.tofik.coolexoplayer.exoplayer.cool.CoolUtil.checkNotNull;
import static com.tofik.coolexoplayer.exoplayer.cool.widget.Common.max;


@SuppressWarnings({ "unused", "ConstantConditions" }) //
public class Container extends RecyclerView {

  private static final String TAG = "tofik-lib:Container";

  static final int SOME_BLINKS = 50;  // 3 frames ...

  /* package */ final PlayerManager playerManager;
  /* package */ final ChildLayoutChangeListener childLayoutChangeListener;
  /* package */ PlayerDispatcher playerDispatcher = PlayerDispatcher.DEFAULT;
  /* package */ RecyclerListenerImpl recyclerListener;  // null = not attached/detached
  /* package */ PlayerSelector playerSelector = PlayerSelector.DEFAULT;   // null = do nothing
  /* package */ Handler animatorFinishHandler;  // null = not attached/detached
  /* package */ BehaviorCallback behaviorCallback;

  public Container(Context context) {
    this(context, null);
  }

  public Container(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public Container(Context context, @Nullable AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    playerManager = new PlayerManager();
    childLayoutChangeListener = new ChildLayoutChangeListener(this);
    requestDisallowInterceptTouchEvent(true);
  }

  @Override
  public final void setRecyclerListener(RecyclerListener listener) {
    if (recyclerListener == null) recyclerListener = new RecyclerListenerImpl(this);
    recyclerListener.delegate = listener;
    super.setRecyclerListener(recyclerListener);
  }

  @CallSuper @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (getAdapter() != null) dataObserver.registerAdapter(getAdapter());
    if (animatorFinishHandler == null) {
      animatorFinishHandler = new Handler(new AnimatorHelper(this));
    }

    PowerManager powerManager = (PowerManager) getContext().getSystemService(POWER_SERVICE);
    if (powerManager != null && powerManager.isScreenOn()) {
      this.screenState = View.SCREEN_STATE_ON;
    } else {
      this.screenState = View.SCREEN_STATE_OFF;
    }

    /* setRecyclerListener can be called before this, it is considered as user-setup */
    if (recyclerListener == null) {
      recyclerListener = new RecyclerListenerImpl(this);
      recyclerListener.delegate = NULL; // mark as it is set by cool, not user.
      super.setRecyclerListener(recyclerListener);  // must be a super call
    }

    playbackInfoCache.onAttach();
    playerManager.onAttach();

    ViewGroup.LayoutParams params = getLayoutParams();
    if (params instanceof CoordinatorLayout.LayoutParams) {
      CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) params).getBehavior();
      if (behavior instanceof Behavior) {
        ((Behavior) behavior).onViewAttached(this);
      }
    }
  }

  @CallSuper @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    ViewGroup.LayoutParams params = getLayoutParams();
    if (params instanceof CoordinatorLayout.LayoutParams) {
      CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) params).getBehavior();
      if (behavior instanceof Behavior) {
        ((Behavior) behavior).onViewDetached(this);
      }
    }

    if (recyclerListener != null && recyclerListener.delegate == NULL) {  // set by cool, not user.
      super.setRecyclerListener(null);  // must be a super call
      recyclerListener = null;
    }

    if (animatorFinishHandler != null) {
      animatorFinishHandler.removeCallbacksAndMessages(null);
      animatorFinishHandler = null;
    }

    List<CoolPlayer> players = playerManager.getPlayers();
    if (!players.isEmpty()) {
      for (int size = players.size(), i = size - 1; i >= 0; i--) {
        CoolPlayer player = players.get(i);
        if (player.isPlaying()) {
          this.savePlaybackInfo(player.getPlayerOrder(), player.getCurrentPlaybackInfo());
          playerManager.pause(player);
        }
        playerManager.release(player);
      }
      playerManager.clear();
    }
    playerManager.onDetach();
    playbackInfoCache.onDetach();
    dataObserver.registerAdapter(null);
    childLayoutChangeListener.containerRef.clear();
  }

  /**
   * Filter current managed {@link CoolPlayer}s using {@link Filter}. Result is sorted by Player
   * order obtained from {@link CoolPlayer#getPlayerOrder()}.
   *
   * @param filter the {@link Filter} to a {@link CoolPlayer}.
   * @return list of players accepted by {@link Filter}. Empty list if there is no available player.
   */
  @NonNull public final List<CoolPlayer> filterBy(Filter filter) {
    List<CoolPlayer> result = new ArrayList<>();
    for (CoolPlayer player : playerManager.getPlayers()) {
      if (filter.accept(player)) result.add(player);
    }
    Collections.sort(result, Common.ORDER_COMPARATOR);
    return result;
  }

  // This method is called when:
  // [1] A ViewHolder is newly created, bound and then attached to RecyclerView.
  // [2] A ViewHolder is detached before, but still in bound state, not be recycled,
  // and now be re-attached to RecyclerView.
  // In either cases, PlayerManager should not manage the ViewHolder before this point.
  @CallSuper @Override
  public void onChildAttachedToWindow(@NonNull final View child) {
    super.onChildAttachedToWindow(child);
    child.addOnLayoutChangeListener(childLayoutChangeListener);
    final ViewHolder holder = getChildViewHolder(child);
    if (!(holder instanceof CoolPlayer)) return;

    final CoolPlayer player = (CoolPlayer) holder;
    final View playerView = player.getPlayerView();
    if (playerView == null) {
      throw new NullPointerException("Expected non-null playerView, found null for: " + player);
    }

    playbackInfoCache.onPlayerAttached(player);
    if (playerManager.manages(player)) {
      // I don't expect this to be called. If this happens, make sure to note the scenario.
      Log.w(TAG, "!!Already managed: player = [" + player + "]");
      // Only if container is in idle state and player is not playing.
      if (getScrollState() == SCROLL_STATE_IDLE && !player.isPlaying()) {
        playerManager.play(player, playerDispatcher);
      }
    } else {
      // LeakCanary report a leak of OnGlobalLayoutListener but I cannot figure out why ...
      child.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            child.getViewTreeObserver().removeOnGlobalLayoutListener(this);
          }
          if (Common.allowsToPlay(player)) {
            if (playerManager.attachPlayer(player)) {
              dispatchUpdateOnAnimationFinished(false);
            }
          }
        }
      });
    }
  }

  @CallSuper @Override
  public void onChildDetachedFromWindow(@NonNull View child) {
    super.onChildDetachedFromWindow(child);
    child.removeOnLayoutChangeListener(childLayoutChangeListener);
    ViewHolder holder = getChildViewHolder(child);
    if (!(holder instanceof CoolPlayer)) return;
    final CoolPlayer player = (CoolPlayer) holder;

    boolean playerManaged = playerManager.manages(player);
    if (player.isPlaying()) {
      if (!playerManaged) {
        player.pause(); // Unstable state, so forcefully pause this by itself.
        /* throw new IllegalStateException(
            "Player is playing while it is not in managed state: " + player); */
      }
      this.savePlaybackInfo(player.getPlayerOrder(), player.getCurrentPlaybackInfo());
      playerManager.pause(player);
    }
    if (playerManaged) {
      playerManager.detachPlayer(player);
    }
    playbackInfoCache.onPlayerDetached(player);
    // RecyclerView#onChildDetachedFromWindow(View) is called after other removal finishes, so
    // sometime it happens after all Animation, but we also need to update playback here.
    // If there is no anymore child view, this call will end early.
    dispatchUpdateOnAnimationFinished(true);
    // finally release the player
    // if player manager could not manager player, release by itself.
    if (!playerManager.release(player)) player.release();
  }

  @CallSuper @Override
  public void onScrollStateChanged(int state) {
    super.onScrollStateChanged(state);
    // Need to handle the dead playback even when the Container is still scrolling/flinging.
    List<CoolPlayer> players = playerManager.getPlayers();
    // 1. Find players those are managed but not qualified to play anymore.
    for (int i = 0, size = players.size(); i < size; i++) {
      CoolPlayer player = players.get(i);
      if (Common.allowsToPlay(player)) continue;
      if (player.isPlaying()) {
        this.savePlaybackInfo(player.getPlayerOrder(), player.getCurrentPlaybackInfo());
        playerManager.pause(player);
      }
      if (!playerManager.release(player)) player.release();
      playerManager.detachPlayer(player);
    }

    // 2. Refresh the good players list.
    LayoutManager layout = super.getLayoutManager();
    // current number of visible 'Virtual Children', or zero if there is no LayoutManager available.
    int childCount = layout != null ? layout.getChildCount() : 0;
    if (childCount <= 0 || state != SCROLL_STATE_IDLE) {
      playerManager.deferPlaybacks();
      return;
    }

    for (int i = 0; i < childCount; i++) {
      View child = layout.getChildAt(i);
      ViewHolder holder = super.getChildViewHolder(child);
      if (holder instanceof CoolPlayer) {
        CoolPlayer player = (CoolPlayer) holder;
        // Check candidate's condition
        if (Common.allowsToPlay(player)) {
          if (!playerManager.manages(player)) {
            playerManager.attachPlayer(player);
          }
          // Don't check the attach result, because the player may be managed already.
          if (!player.isPlaying()) {  // not playing or not ready to play.
            playerManager.initialize(player, Container.this);
          }
        }
      }
    }

    final List<CoolPlayer> source = playerManager.getPlayers();
    int count = source.size();
    if (count < 1) return;  // No available player, return.

    List<CoolPlayer> candidates = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      CoolPlayer player = source.get(i);
      if (player.wantsToPlay()) candidates.add(player);
    }
    Collections.sort(candidates, Common.ORDER_COMPARATOR);

    Collection<CoolPlayer> toPlay = playerSelector != null ? playerSelector.select(this, candidates)
        : Collections.<CoolPlayer>emptyList();
    for (CoolPlayer player : toPlay) {
      if (!player.isPlaying()) playerManager.play(player, playerDispatcher);
    }

    source.removeAll(toPlay);
    // Now 'source' contains only ones need to be paused.
    for (CoolPlayer player : source) {
      if (player.isPlaying()) {
        this.savePlaybackInfo(player.getPlayerOrder(), player.getCurrentPlaybackInfo());
        playerManager.pause(player);
      }
    }
  }

  /**
   * Setup a {@link PlayerSelector}. Set a {@code null} {@link PlayerSelector} will stop all
   * playback.
   *
   * @param playerSelector new {@link PlayerSelector} for this {@link Container}.
   */
  public final void setPlayerSelector(@Nullable PlayerSelector playerSelector) {
    if (this.playerSelector == playerSelector) return;
    this.playerSelector = playerSelector;
    // dispatchUpdateOnAnimationFinished(true); // doesn't work well :(
    // Immediately update.
    this.onScrollStateChanged(SCROLL_STATE_IDLE);
  }

  /**
   * Get current {@link PlayerSelector}. Can be {@code null}.
   *
   * @return current {@link #playerSelector}
   */
  @Nullable public final PlayerSelector getPlayerSelector() {
    return playerSelector;
  }

  public final void setPlayerDispatcher(@NonNull PlayerDispatcher playerDispatcher) {
    this.playerDispatcher = checkNotNull(playerDispatcher);
  }

  /** Define the callback that to be used later by {@link Behavior} if setup. */
  public final void setBehaviorCallback(@Nullable BehaviorCallback behaviorCallback) {
    this.behaviorCallback = behaviorCallback;
  }

  ////// Handle update after data change animation

  long getMaxAnimationDuration() {
    ItemAnimator animator = getItemAnimator();
    if (animator == null) return SOME_BLINKS;
    return max(animator.getAddDuration(), animator.getMoveDuration(), animator.getRemoveDuration(),
        animator.getChangeDuration());
  }

  void dispatchUpdateOnAnimationFinished(boolean immediate) {
    if (getScrollState() != SCROLL_STATE_IDLE) return;
    if (animatorFinishHandler == null) return;
    final long duration = immediate ? SOME_BLINKS : getMaxAnimationDuration();
    if (getItemAnimator() != null) {
      getItemAnimator().isRunning(new ItemAnimator.ItemAnimatorFinishedListener() {
        @Override
        public void onAnimationsFinished() {
          animatorFinishHandler.removeCallbacksAndMessages(null);
          animatorFinishHandler.sendEmptyMessageDelayed(-1, duration);
        }
      });
    } else {
      animatorFinishHandler.removeCallbacksAndMessages(null);
      animatorFinishHandler.sendEmptyMessageDelayed(-1, duration);
    }
  }

  ////// Adapter Data Observer setup

  /**
   * See {@link ToroDataObserver}
   */
  private final ToroDataObserver dataObserver = new ToroDataObserver();

  /**
   * See {@link Adapter#registerAdapterDataObserver(AdapterDataObserver)}
   * See {@link Adapter#unregisterAdapterDataObserver(AdapterDataObserver)}
   */
  @CallSuper @Override
  public void setAdapter(Adapter adapter) {
    super.setAdapter(adapter);
    dataObserver.registerAdapter(adapter);
  }

  /**
   * See {@link Container#setAdapter(Adapter)}
   */
  @CallSuper @Override
  public void swapAdapter(Adapter adapter,
                          boolean removeAndRecycleExistingViews) {
    super.swapAdapter(adapter, removeAndRecycleExistingViews);
    dataObserver.registerAdapter(adapter);
  }

  //// PlaybackInfo Cache implementation
  /* pkg */ final PlaybackInfoCache playbackInfoCache = new PlaybackInfoCache(this);
  /* pkg */ Initializer playerInitializer = Initializer.DEFAULT;
  private CacheManager cacheManager = null; // null by default

  public final void setPlayerInitializer(@NonNull Initializer playerInitializer) {
    this.playerInitializer = playerInitializer;
  }

  /**
   * Save {@link PlaybackInfo} for the current {@link CoolPlayer} of a specific order.
   * If called with {@link PlaybackInfo#SCRAP}, it is a hint that the Player is completed and need
   * to be re-initialized.
   *
   * @param order order of the {@link CoolPlayer}.
   * @param playbackInfo current {@link PlaybackInfo} of the {@link CoolPlayer}. Null info will be ignored.
   */
  public final void savePlaybackInfo(int order, @Nullable PlaybackInfo playbackInfo) {
    if (playbackInfo != null) playbackInfoCache.savePlaybackInfo(order, playbackInfo);
  }

  /**
   * Get the cached {@link PlaybackInfo} at a specific order.
   *
   * @param order order of the {@link CoolPlayer} to get the cached {@link PlaybackInfo}.
   * @return cached {@link PlaybackInfo} if available, a new one if there is no cached one.
   */
  @NonNull public final PlaybackInfo getPlaybackInfo(int order) {
    return playbackInfoCache.getPlaybackInfo(order);
  }

  /**
   * Get current list of {@link CoolPlayer}s' orders whose {@link PlaybackInfo} are cached.
   * Returning an empty list will disable the save/restore of player's position.
   *
   * @return list of {@link CoolPlayer}s' orders.
   * @deprecated Use {@link #getLatestPlaybackInfos()} for the same purpose.
   */
  @RemoveIn(version = "3.6.0") @Deprecated  //
  @NonNull public List<Integer> getSavedPlayerOrders() {
    return new ArrayList<>(playbackInfoCache.coldKeyToOrderMap.keySet());
  }

  /**
   * Get a {@link SparseArray} contains cached {@link PlaybackInfo} of {@link CoolPlayer}s managed
   * by this {@link Container}. If there is non-null {@link CacheManager}, this method should
   * return the list of all {@link PlaybackInfo} cached by {@link PlaybackInfoCache}, otherwise,
   * this method returns current {@link PlaybackInfo} of attached {@link CoolPlayer}s only.
   */
  @NonNull public SparseArray<PlaybackInfo> getLatestPlaybackInfos() {
    SparseArray<PlaybackInfo> cache = new SparseArray<>();
    List<CoolPlayer> activePlayers = this.filterBy(Container.Filter.PLAYING);
    // This will update hotCache and coldCache if they are available.
    for (CoolPlayer player : activePlayers) {
      this.savePlaybackInfo(player.getPlayerOrder(), player.getCurrentPlaybackInfo());
    }

    if (cacheManager == null) {
      if (playbackInfoCache.hotCache != null) {
        for (Map.Entry<Integer, PlaybackInfo> entry : playbackInfoCache.hotCache.entrySet()) {
          cache.put(entry.getKey(), entry.getValue());
        }
      }
    } else {
      for (Map.Entry<Integer, Object> entry : playbackInfoCache.coldKeyToOrderMap.entrySet()) {
        cache.put(entry.getKey(), playbackInfoCache.coldCache.get(entry.getValue()));
      }
    }

    return cache;
  }

  /**
   * Set a {@link CacheManager} to this {@link Container}. A {@link CacheManager} will
   * allow this {@link Container} to save/restore {@link PlaybackInfo} on various states or life
   * cycle events. Setting a {@code null} {@link CacheManager} will remove that ability.
   * {@link Container} doesn't have a non-null {@link CacheManager} by default.
   *
   * Setting this while there is a {@code non-null} {@link CacheManager} available will clear
   * current {@link PlaybackInfo} cache.
   *
   * @param cacheManager The {@link CacheManager} to set to the {@link Container}.
   */
  public final void setCacheManager(@Nullable CacheManager cacheManager) {
    if (this.cacheManager == cacheManager) return;
    this.playbackInfoCache.clearCache();
    this.cacheManager = cacheManager;
  }

  /**
   * Get current {@link CacheManager} of the {@link Container}.
   *
   * @return current {@link CacheManager} of the {@link Container}. Can be {@code null}.
   */
  @Nullable public final CacheManager getCacheManager() {
    return cacheManager;
  }

  /**
   * Temporary save current playback infos when the App is stopped but not re-created. (For example:
   * User press App Stack). If not {@code empty} then user is back from a living-but-stopped state.
   */
  final SparseArray<PlaybackInfo> tmpStates = new SparseArray<>();

  /**
   * In case user press "App Stack" button, this View's window will have visibility change from
   * {@link #VISIBLE} to {@link #INVISIBLE} to {@link #GONE}. When user is back from that state,
   * the visibility changes from {@link #GONE} to {@link #INVISIBLE} to {@link #VISIBLE}. A proper
   * playback needs to handle this case too.
   */
  @CallSuper @Override
  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    if (visibility == View.GONE) {
      List<CoolPlayer> players = playerManager.getPlayers();
      // if onSaveInstanceState is called before, source will contain no item, just fine.
      for (CoolPlayer player : players) {
        if (player.isPlaying()) {
          this.savePlaybackInfo(player.getPlayerOrder(), player.getCurrentPlaybackInfo());
          playerManager.pause(player);
        }
      }
    } else if (visibility == View.VISIBLE) {
      if (tmpStates.size() > 0) {
        for (int i = 0; i < tmpStates.size(); i++) {
          int order = tmpStates.keyAt(i);
          PlaybackInfo playbackInfo = tmpStates.get(order);
          this.savePlaybackInfo(order, playbackInfo);
        }
      }
      tmpStates.clear();
      dispatchUpdateOnAnimationFinished(true);
    }

    dispatchWindowVisibilityMayChange();
  }

  private int screenState;

  @Override
  public void onScreenStateChanged(int screenState) {
    super.onScreenStateChanged(screenState);
    this.screenState = screenState;
    dispatchWindowVisibilityMayChange();
  }

  @Override
  public void onWindowFocusChanged(boolean hasWindowFocus) {
    super.onWindowFocusChanged(hasWindowFocus);
    dispatchWindowVisibilityMayChange();
  }

  /**
   * This method supports the case that by some reasons, Container should changes it behaviour not
   * caused by any Activity recreation (so {@link #onSaveInstanceState()} and
   * {@link #onRestoreInstanceState(Parcelable)} could not help).
   *
   * This method is called when:
   * - Screen state changed.
   * or - Window focus changed.
   * or - Window visibility changed.
   *
   * For each of that event, Screen may be turned off or Window's focus state may change, we need
   * to decide if Container should keep current playback state or change it.
   *
   * <strong>Discussion</strong>: In fact, we expect that: Container will be playing if the
   * following conditions are all satisfied:
   * - Current window is visible. (but not necessarily focused).
   * - Container is visible in Window (partly is fine, we care about the Media player).
   * - Container is focused in Window. (so we don't screw up other components' focuses).
   *
   * In lower API (eg: 16), {@link #getWindowVisibility()} always returns {@link #VISIBLE}, which
   * cannot tell much. We need to investigate this flag in various APIs in various Scenarios.
   */
  private void dispatchWindowVisibilityMayChange() {
    if (screenState == SCREEN_STATE_OFF) {
      List<CoolPlayer> players = playerManager.getPlayers();
      for (CoolPlayer player : players) {
        if (player.isPlaying()) {
          this.savePlaybackInfo(player.getPlayerOrder(), player.getCurrentPlaybackInfo());
          playerManager.pause(player);
        }
      }
    } else if (screenState == SCREEN_STATE_ON
        // Container is focused in current Window
        && hasFocus()
        // In fact, Android 24+ supports multi-window mode in which visible Window may not have focus.
        // In that case, other triggers are supposed to be called and we are safe here.
        // Need further investigation if need.
        && hasWindowFocus()) {
      // tmpStates may be consumed already, if there is a good reason for that, so not a big deal.
      if (tmpStates.size() > 0) {
        for (int i = 0, size = tmpStates.size(); i < size; i++) {
          int order = tmpStates.keyAt(i);
          this.savePlaybackInfo(order, tmpStates.get(order));
        }
      }
      tmpStates.clear();
      dispatchUpdateOnAnimationFinished(true);
    }
  }

  @Override
  protected Parcelable onSaveInstanceState() {
    Parcelable superState = super.onSaveInstanceState();
    List<CoolPlayer> source = playerManager.getPlayers();
    for (CoolPlayer player : source) {
      if (player.isPlaying()) {
        this.savePlaybackInfo(player.getPlayerOrder(), player.getCurrentPlaybackInfo());
        playerManager.pause(player);
      }
    }

    final SparseArray<PlaybackInfo> states = playbackInfoCache.saveStates();

    boolean recreating =
        getContext() instanceof Activity && ((Activity) getContext()).isChangingConfigurations();

    // Release current players on recreation event only.
    // Note that there are cases where this method is called without the activity destroying/recreating.
    // For example: in API 26 (my test mostly run on 8.0), when user click to "Current App" button,
    // current Activity will enter the "Stop" state but not be destroyed/recreated and View hierarchy
    // state will be saved (this method is called).
    //
    // We only need to release current resources when the recreation happens.
    if (recreating) {
      for (CoolPlayer player : source) {
        if (!playerManager.release(player)) player.release();
        playerManager.detachPlayer(player);
      }
    }

    // Client must consider this behavior using CacheManager implement.
    PlayerViewState playerViewState = new PlayerViewState(superState);
    playerViewState.statesCache = states;

    // To mark that this method was called. An activity recreation will clear this.
    if (states != null && states.size() > 0) {
      for (int i = 0; i < states.size(); i++) {
        PlaybackInfo value = states.valueAt(i);
        if (value != null) tmpStates.put(states.keyAt(i), value);
      }
    }

    return playerViewState;
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    if (!(state instanceof PlayerViewState)) {
      super.onRestoreInstanceState(state);
      return;
    }

    PlayerViewState viewState = (PlayerViewState) state;
    super.onRestoreInstanceState(viewState.getSuperState());
    SparseArray<?> saveStates = viewState.statesCache;
    if (saveStates != null) playbackInfoCache.restoreStates(saveStates);
  }

  /**
   * Store the array of {@link PlaybackInfo} of recently cached playback. This state will be used
   * only when {@link #cacheManager} is not {@code null}. Extension of {@link Container} must
   * also have its own version of {@link SavedState} extends this {@link PlayerViewState}.
   */
  public static class PlayerViewState extends AbsSavedState {

    SparseArray<?> statesCache;

    /**
     * Called by onSaveInstanceState
     */
    PlayerViewState(Parcelable superState) {
      super(superState);
    }

    /**
     * Called by CREATOR
     */
    PlayerViewState(Parcel in, ClassLoader loader) {
      super(in, loader);
      statesCache = in.readSparseArray(loader);
    }

    PlayerViewState(Parcel in) {
      super(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      super.writeToParcel(dest, flags);
      //noinspection unchecked
      dest.writeSparseArray((SparseArray<Object>) statesCache);
    }

    public static final Creator<PlayerViewState> CREATOR =
        new ClassLoaderCreator<PlayerViewState>() { // Added from API 13
          @Override
          public PlayerViewState createFromParcel(Parcel in, ClassLoader loader) {
            return new PlayerViewState(in, loader);
          }

          @Override
          public PlayerViewState createFromParcel(Parcel source) {
            return new PlayerViewState(source);
          }

          @Override
          public PlayerViewState[] newArray(int size) {
            return new PlayerViewState[size];
          }
        };

    @NonNull @Override
    public String toString() {
      return "Cache{" + "states=" + statesCache + '}';
    }
  }

  private final class ToroDataObserver extends AdapterDataObserver {

    private Adapter adapter;

    ToroDataObserver() {
    }

    final void registerAdapter(Adapter adapter) {
      if (this.adapter == adapter) return;
      if (this.adapter != null) {
        this.adapter.unregisterAdapterDataObserver(this);
        this.adapter.unregisterAdapterDataObserver(playbackInfoCache);
      }

      this.adapter = adapter;
      if (this.adapter != null) {
        this.adapter.registerAdapterDataObserver(this);
        this.adapter.registerAdapterDataObserver(playbackInfoCache);
      }
    }

    @Override
    public void onChanged() {
      dispatchUpdateOnAnimationFinished(true);
    }

    @Override
    public void onItemRangeChanged(int positionStart, int itemCount) {
      dispatchUpdateOnAnimationFinished(false);
    }

    @Override
    public void onItemRangeInserted(int positionStart, int itemCount) {
      dispatchUpdateOnAnimationFinished(false);
    }

    @Override
    public void onItemRangeRemoved(int positionStart, int itemCount) {
      dispatchUpdateOnAnimationFinished(false);
    }

    @Override
    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
      dispatchUpdateOnAnimationFinished(false);
    }
  }

  /**
   * A {@link Handler.Callback} that will fake a scroll with {@link #SCROLL_STATE_IDLE} to refresh
   * all the playback. This is relatively expensive.
   */
  private static class AnimatorHelper implements Handler.Callback {

    @NonNull private final Container container;

    AnimatorHelper(@NonNull Container container) {
      this.container = container;
    }

    @Override
    public boolean handleMessage(Message msg) {
      this.container.onScrollStateChanged(SCROLL_STATE_IDLE);
      return true;
    }
  }

  private static class RecyclerListenerImpl implements RecyclerView.RecyclerListener {

    final Container container;
    RecyclerListener delegate;

    RecyclerListenerImpl(@NonNull Container container) {
      this.container = container;
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
      if (this.delegate != null) this.delegate.onViewRecycled(holder);
      if (holder instanceof CoolPlayer) {
        CoolPlayer player = (CoolPlayer) holder;
        this.container.playbackInfoCache.onPlayerRecycled(player);
        this.container.playerManager.recycle(player);
      }
    }
  }

  // This instance is to mark a RecyclerListenerImpl to be set by cool, not by user.
  private static final RecyclerListener NULL = new RecyclerListener() {
    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
      // No-ops
    }
  };

  /**
   * An utility interface, used by {@link Container} to filter for {@link CoolPlayer}.
   */
  public interface Filter {

    /**
     * Check a {@link CoolPlayer} for a condition.
     *
     * @param player the {@link CoolPlayer} to check.
     * @return {@code true} if this accepts the {@link CoolPlayer}, {@code false} otherwise.
     */
    boolean accept(@NonNull CoolPlayer player);

    /**
     * A built-in {@link Filter} that accepts only {@link CoolPlayer} that is playing.
     */
    Filter PLAYING = new Filter() {
      @Override
      public boolean accept(@NonNull CoolPlayer player) {
        return player.isPlaying();
      }
    };

    /**
     * A built-in {@link Filter} that accepts only {@link CoolPlayer} that is managed by Container.
     * Actually any {@link CoolPlayer} to be filtered is already managed.
     */
    Filter MANAGING = new Filter() {
      @Override
      public boolean accept(@NonNull CoolPlayer player) {
        return true;
      }
    };
  }

  public static class Behavior extends CoordinatorLayout.Behavior<Container>
      implements Handler.Callback {

    @NonNull final CoordinatorLayout.Behavior<? super Container> delegate;
    @Nullable BehaviorCallback callback;

    static final int EVENT_IDLE = 1;
    static final int EVENT_SCROLL = 2;
    static final int EVENT_TOUCH = 3;
    static final int EVENT_DELAY = 150;

    final AtomicBoolean scrollConsumed = new AtomicBoolean(false);

    Handler handler;

    void onViewAttached(Container container) {
      if (handler == null) handler = new Handler(this);
      this.callback = container.behaviorCallback;
    }

    void onViewDetached(Container container) {
      if (handler != null) {
        handler.removeCallbacksAndMessages(null);
        handler = null;
      }
      this.callback = null;
    }

    @Override
    public boolean handleMessage(Message msg) {
      if (callback == null) return true;
      switch (msg.what) {
        case EVENT_SCROLL:
        case EVENT_TOUCH:
          scrollConsumed.set(false);
          handler.removeMessages(EVENT_IDLE);
          handler.sendEmptyMessageDelayed(EVENT_IDLE, EVENT_DELAY);
          break;
        case EVENT_IDLE:
          // idle --> consume it.
          if (!scrollConsumed.getAndSet(true)) callback.onFinishInteraction();
          break;
      }
      return true;
    }

    /* No default constructors. Using this class from xml will result in error. */

    // public Behavior() {
    // }
    //
    // public Behavior(Context context, AttributeSet attrs) {
    //   super(context, attrs);
    // }

    public Behavior(@NonNull CoordinatorLayout.Behavior<Container> delegate) {
      this.delegate = checkNotNull(delegate, "Behavior is null.");
    }

    /// We only need to intercept the following 3 methods:

    @Override
    public boolean onInterceptTouchEvent(@NonNull CoordinatorLayout parent,
                                         @NonNull Container child, @NonNull MotionEvent ev) {
      if (this.handler != null) {
        this.handler.removeCallbacksAndMessages(null);
        this.handler.sendEmptyMessage(EVENT_TOUCH);
      }
      return delegate.onInterceptTouchEvent(parent, child, ev);
    }

    @Override
    public boolean onTouchEvent(@NonNull CoordinatorLayout parent, @NonNull Container child,
                                @NonNull MotionEvent ev) {
      if (this.handler != null) {
        this.handler.removeCallbacksAndMessages(null);
        this.handler.sendEmptyMessage(EVENT_TOUCH);
      }
      return delegate.onTouchEvent(parent, child, ev);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout layout, @NonNull Container child,
                                       @NonNull View directTargetChild, @NonNull View target, int axes, int type) {
      if (this.handler != null) {
        this.handler.removeCallbacksAndMessages(null);
        this.handler.sendEmptyMessage(EVENT_SCROLL);
      }
      return delegate.onStartNestedScroll(layout, child, directTargetChild, target, axes, type);
    }

    /// Other methods

    @Override
    public void onAttachedToLayoutParams(@NonNull CoordinatorLayout.LayoutParams params) {
      if (handler == null) handler = new Handler(this);
      delegate.onAttachedToLayoutParams(params);
    }

    @Override
    public void onDetachedFromLayoutParams() {
      if (handler != null) {
        handler.removeCallbacksAndMessages(null);
        handler = null;
      }
      delegate.onDetachedFromLayoutParams();
    }

    @Override
    @ColorInt
    public int getScrimColor(@NonNull CoordinatorLayout parent, @NonNull Container child) {
      return delegate.getScrimColor(parent, child);
    }

    @Override
    public float getScrimOpacity(@NonNull CoordinatorLayout parent, @NonNull Container child) {
      return delegate.getScrimOpacity(parent, child);
    }

    @Override
    public boolean blocksInteractionBelow(@NonNull CoordinatorLayout parent,
                                          @NonNull Container child) {
      return delegate.blocksInteractionBelow(parent, child);
    }

    @Override
    public boolean layoutDependsOn(@NonNull CoordinatorLayout parent, @NonNull Container child,
                                   @NonNull View dependency) {
      return delegate.layoutDependsOn(parent, child, dependency);
    }

    @Override
    public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent,
                                          @NonNull Container child, @NonNull View dependency) {
      return delegate.onDependentViewChanged(parent, child, dependency);
    }

    @Override
    public void onDependentViewRemoved(@NonNull CoordinatorLayout parent, @NonNull Container child,
                                       @NonNull View dependency) {
      delegate.onDependentViewRemoved(parent, child, dependency);
    }

    @Override
    public boolean onMeasureChild(@NonNull CoordinatorLayout parent, @NonNull Container child,
                                  int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
      return delegate.onMeasureChild(parent, child, parentWidthMeasureSpec, widthUsed,
          parentHeightMeasureSpec, heightUsed);
    }

    @Override
    public boolean onLayoutChild(@NonNull CoordinatorLayout parent, @NonNull Container child,
                                 int layoutDirection) {
      return delegate.onLayoutChild(parent, child, layoutDirection);
    }

    @Override
    public void onNestedScrollAccepted(@NonNull CoordinatorLayout layout, @NonNull Container child,
                                       @NonNull View directTargetChild, @NonNull View target, int axes, int type) {
      delegate.onNestedScrollAccepted(layout, child, directTargetChild, target, axes, type);
    }

    @Override
    public void onStopNestedScroll(@NonNull CoordinatorLayout layout, @NonNull Container child,
                                   @NonNull View target, int type) {
      delegate.onStopNestedScroll(layout, child, target, type);
    }

    @Override
    public void onNestedScroll(@NonNull CoordinatorLayout layout, @NonNull Container child,
                               @NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed,
                               int type) {
      delegate.onNestedScroll(layout, child, target, dxConsumed, dyConsumed, dxUnconsumed,
          dyUnconsumed, type);
    }

    @Override
    public void onNestedPreScroll(@NonNull CoordinatorLayout layout, @NonNull Container child,
                                  @NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
      delegate.onNestedPreScroll(layout, child, target, dx, dy, consumed, type);
    }

    @Override
    public boolean onNestedFling(@NonNull CoordinatorLayout layout, @NonNull Container child,
                                 @NonNull View target, float velocityX, float velocityY, boolean consumed) {
      return delegate.onNestedFling(layout, child, target, velocityX, velocityY, consumed);
    }

    @Override
    public boolean onNestedPreFling(@NonNull CoordinatorLayout layout, @NonNull Container child,
                                    @NonNull View target, float velocityX, float velocityY) {
      return delegate.onNestedPreFling(layout, child, target, velocityX, velocityY);
    }

    @Override
    @NonNull
    public WindowInsetsCompat onApplyWindowInsets(@NonNull CoordinatorLayout layout,
                                                  @NonNull Container child, @NonNull WindowInsetsCompat insets) {
      return delegate.onApplyWindowInsets(layout, child, insets);
    }

    @Override
    public boolean onRequestChildRectangleOnScreen(@NonNull CoordinatorLayout layout,
                                                   @NonNull Container child, @NonNull Rect rectangle, boolean immediate) {
      return delegate.onRequestChildRectangleOnScreen(layout, child, rectangle, immediate);
    }

    @Override
    public void onRestoreInstanceState(@NonNull CoordinatorLayout parent, @NonNull Container child,
                                       @NonNull Parcelable state) {
      delegate.onRestoreInstanceState(parent, child, state);
    }

    @Override
    public Parcelable onSaveInstanceState(@NonNull CoordinatorLayout parent,
                                          @NonNull Container child) {
      return delegate.onSaveInstanceState(parent, child);
    }

    @Override
    public boolean getInsetDodgeRect(@NonNull CoordinatorLayout parent, @NonNull Container child,
                                     @NonNull Rect rect) {
      return delegate.getInsetDodgeRect(parent, child, rect);
    }
  }

  /**
   * Callback for {@link Behavior} to tell the Client that User has finished the interaction for
   * enough amount of time, so it (the Client) should do something. Normally, we ask Container to
   * dispatch an 'idle scroll' to refresh the player list.
   */
  public interface BehaviorCallback {

    void onFinishInteraction();
  }

  public interface Initializer {

    @NonNull
    PlaybackInfo initPlaybackInfo(int order);

    Initializer DEFAULT = new Initializer() {
      @NonNull @Override
      public PlaybackInfo initPlaybackInfo(int order) {
        return new PlaybackInfo();
      }
    };
  }

  static class ChildLayoutChangeListener implements View.OnLayoutChangeListener {

    final WeakReference<Container> containerRef;

    ChildLayoutChangeListener(Container container) {
      this.containerRef = new WeakReference<>(container);
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                               int oldTop, int oldRight, int oldBottom) {
      Container container = containerRef.get();
      if (container == null) return;
      if (layoutDidChange(left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom)) {
        container.dispatchUpdateOnAnimationFinished(false);
      }
    }
  }

  static boolean layoutDidChange(int left, int top, int right, int bottom, int oldLeft, int oldTop,
      int oldRight, int oldBottom) {
    return left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom;
  }
}
