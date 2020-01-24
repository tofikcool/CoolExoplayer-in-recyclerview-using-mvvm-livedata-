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


@SuppressWarnings({"unused", "ConstantConditions"}) //
public class Container extends RecyclerView {

    static final int SOME_BLINKS = 50;  // 3 frames ...
    private static final String TAG = "tofik-lib:Container";
    // This instance is to mark a RecyclerListenerImpl to be set by cool, not by user.
    private static final RecyclerListener NULL = new RecyclerListener() {
        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            // No-ops
        }
    };
    /* package */ final PlayerManager playerManager;
    /* package */ final ChildLayoutChangeListener childLayoutChangeListener;
    //// PlaybackInfo Cache implementation
    /* pkg */ final PlaybackInfoCache playbackInfoCache = new PlaybackInfoCache(this);
    /**
     * Temporary save current playback infos when the App is stopped but not re-created. (For example:
     * User press App Stack). If not {@code empty} then user is back from a living-but-stopped state.
     */
    final SparseArray<PlaybackInfo> tmpStates = new SparseArray<>();
    /**
     * See {@link ToroDataObserver}
     */
    private final ToroDataObserver dataObserver = new ToroDataObserver();
    /* package */ PlayerDispatcher playerDispatcher = PlayerDispatcher.DEFAULT;
    /* package */ RecyclerListenerImpl recyclerListener;  // null = not attached/detached
    /* package */ PlayerSelector playerSelector = PlayerSelector.DEFAULT;   // null = do nothing
    /* package */ Handler animatorFinishHandler;  // null = not attached/detached
    /* package */ BehaviorCallback behaviorCallback;
    /* pkg */ Initializer playerInitializer = Initializer.DEFAULT;
    private CacheManager cacheManager = null; // null by default
    private int screenState;

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

    static boolean layoutDidChange(int left, int top, int right, int bottom, int oldLeft, int oldTop,
                                   int oldRight, int oldBottom) {
        return left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom;
    }

    @Override
    public final void setRecyclerListener(RecyclerListener listener) {
        if (recyclerListener == null) recyclerListener = new RecyclerListenerImpl(this);
        recyclerListener.delegate = listener;
        super.setRecyclerListener(recyclerListener);
    }

    @CallSuper
    @Override
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

    @CallSuper
    @Override
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


    @NonNull
    public final List<CoolPlayer> filterBy(Filter filter) {
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
    @CallSuper
    @Override
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

    ////// Adapter Data Observer setup

    @CallSuper
    @Override
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

    @CallSuper
    @Override
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


    @Nullable
    public final PlayerSelector getPlayerSelector() {
        return playerSelector;
    }


    public final void setPlayerSelector(@Nullable PlayerSelector playerSelector) {
        if (this.playerSelector == playerSelector) return;
        this.playerSelector = playerSelector;
        // dispatchUpdateOnAnimationFinished(true); // doesn't work well :(
        // Immediately update.
        this.onScrollStateChanged(SCROLL_STATE_IDLE);
    }

    public final void setPlayerDispatcher(@NonNull PlayerDispatcher playerDispatcher) {
        this.playerDispatcher = checkNotNull(playerDispatcher);
    }

    /**
     * Define the callback that to be used later by {@link Behavior} if setup.
     */
    public final void setBehaviorCallback(@Nullable BehaviorCallback behaviorCallback) {
        this.behaviorCallback = behaviorCallback;
    }

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


    @CallSuper
    @Override
    public void setAdapter(Adapter adapter) {
        super.setAdapter(adapter);
        dataObserver.registerAdapter(adapter);
    }


    @CallSuper
    @Override
    public void swapAdapter(Adapter adapter,
                            boolean removeAndRecycleExistingViews) {
        super.swapAdapter(adapter, removeAndRecycleExistingViews);
        dataObserver.registerAdapter(adapter);
    }

    public final void setPlayerInitializer(@NonNull Initializer playerInitializer) {
        this.playerInitializer = playerInitializer;
    }


    public final void savePlaybackInfo(int order, @Nullable PlaybackInfo playbackInfo) {
        if (playbackInfo != null) playbackInfoCache.savePlaybackInfo(order, playbackInfo);
    }


    @NonNull
    public final PlaybackInfo getPlaybackInfo(int order) {
        return playbackInfoCache.getPlaybackInfo(order);
    }


    @RemoveIn(version = "3.6.0")
    @Deprecated  //
    @NonNull
    public List<Integer> getSavedPlayerOrders() {
        return new ArrayList<>(playbackInfoCache.coldKeyToOrderMap.keySet());
    }


    @NonNull
    public SparseArray<PlaybackInfo> getLatestPlaybackInfos() {
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


    @Nullable
    public final CacheManager getCacheManager() {
        return cacheManager;
    }


    public final void setCacheManager(@Nullable CacheManager cacheManager) {
        if (this.cacheManager == cacheManager) return;
        this.playbackInfoCache.clearCache();
        this.cacheManager = cacheManager;
    }


    @CallSuper
    @Override
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


    public interface Filter {


        Filter PLAYING = new Filter() {
            @Override
            public boolean accept(@NonNull CoolPlayer player) {
                return player.isPlaying();
            }
        };

        Filter MANAGING = new Filter() {
            @Override
            public boolean accept(@NonNull CoolPlayer player) {
                return true;
            }
        };


        boolean accept(@NonNull CoolPlayer player);
    }


    public interface BehaviorCallback {

        void onFinishInteraction();
    }

    public interface Initializer {

        Initializer DEFAULT = new Initializer() {
            @NonNull
            @Override
            public PlaybackInfo initPlaybackInfo(int order) {
                return new PlaybackInfo();
            }
        };

        @NonNull
        PlaybackInfo initPlaybackInfo(int order);
    }


    public static class PlayerViewState extends AbsSavedState {

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

        @NonNull
        @Override
        public String toString() {
            return "Cache{" + "states=" + statesCache + '}';
        }
    }


    private static class AnimatorHelper implements Handler.Callback {

        @NonNull
        private final Container container;

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

    public static class Behavior extends CoordinatorLayout.Behavior<Container>
            implements Handler.Callback {

        static final int EVENT_IDLE = 1;
        static final int EVENT_SCROLL = 2;
        static final int EVENT_TOUCH = 3;
        static final int EVENT_DELAY = 150;
        @NonNull
        final CoordinatorLayout.Behavior<? super Container> delegate;
        final AtomicBoolean scrollConsumed = new AtomicBoolean(false);
        @Nullable
        BehaviorCallback callback;
        Handler handler;

        public Behavior(@NonNull CoordinatorLayout.Behavior<Container> delegate) {
            this.delegate = checkNotNull(delegate, "Behavior is null.");
        }

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

        /* No default constructors. Using this class from xml will result in error. */

        // public Behavior() {
        // }
        //
        // public Behavior(Context context, AttributeSet attrs) {
        //   super(context, attrs);
        // }

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
}
