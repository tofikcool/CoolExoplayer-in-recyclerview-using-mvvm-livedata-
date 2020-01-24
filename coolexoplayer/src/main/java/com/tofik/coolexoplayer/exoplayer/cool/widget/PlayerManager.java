package com.tofik.coolexoplayer.exoplayer.cool.widget;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import com.tofik.coolexoplayer.exoplayer.cool.CoolPlayer;
import com.tofik.coolexoplayer.exoplayer.cool.PlayerDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"unused", "UnusedReturnValue", "StatementWithEmptyBody"}) //
final class PlayerManager implements Handler.Callback {

    @SuppressWarnings("WeakerAccess")
    static final int MSG_PLAY = 100;
    private static final String TAG = "ToroLib:Manager";
    // Make sure each CoolPlayer will present only once in this Manager.
    private final Set<CoolPlayer> players = new ArraySet<>();
    private Handler handler;

    boolean attachPlayer(@NonNull CoolPlayer player) {
        return players.add(player);
    }

    boolean detachPlayer(@NonNull CoolPlayer player) {
        if (handler != null) handler.removeCallbacksAndMessages(player);
        return players.remove(player);
    }

    boolean manages(@NonNull CoolPlayer player) {
        return players.contains(player);
    }

    /**
     * Return a "Copy" of the collection of players this manager is managing.
     *
     * @return a non null collection of Players those a managed.
     */
    @NonNull
    List<CoolPlayer> getPlayers() {
        return new ArrayList<>(this.players);
    }

    void initialize(@NonNull CoolPlayer player, Container container) {
        player.initialize(container, container.getPlaybackInfo(player.getPlayerOrder()));
    }

    // 2018.07.02 Directly pass PlayerDispatcher so that we can easily expand the ability in the future.
    void play(@NonNull CoolPlayer player, PlayerDispatcher dispatcher) {
        this.play(player, dispatcher.getDelayToPlay(player));
    }

    private void play(@NonNull CoolPlayer player, int delay) {
        if (delay < PlayerDispatcher.DELAY_INFINITE)
            throw new IllegalArgumentException("Too negative");
        if (handler == null) return;  // equals to that this is not attached yet.
        handler.removeMessages(MSG_PLAY, player); // remove undone msg for this player
        if (delay == PlayerDispatcher.DELAY_INFINITE) {
            // do nothing
        } else if (delay == PlayerDispatcher.DELAY_NONE) {
            player.play();
        } else {
            handler.sendMessageDelayed(handler.obtainMessage(MSG_PLAY, player), delay);
        }
    }

    void pause(@NonNull CoolPlayer player) {
        // remove all msg sent for the player
        if (handler != null) handler.removeCallbacksAndMessages(player);
        player.pause();
    }

    // return false if this manager could not release the player.
    // normally when this manager doesn't manage the player.
    boolean release(@NonNull CoolPlayer player) {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (manages(player)) {
            player.release();
            return true;
        } else {
            return false;
        }
    }

    void recycle(CoolPlayer player) {
        if (handler != null) handler.removeCallbacksAndMessages(player);
    }

    void clear() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        this.players.clear();
    }

    void deferPlaybacks() {
        if (handler != null) handler.removeMessages(MSG_PLAY);
    }

    void onAttach() {
        // do nothing
        if (handler == null) handler = new Handler(Looper.getMainLooper(), this);
    }

    void onDetach() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == MSG_PLAY && msg.obj instanceof CoolPlayer) {
            CoolPlayer player = (CoolPlayer) msg.obj;
            player.play();
        }
        return true;
    }
}
