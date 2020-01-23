
package com.tofik.coolexoplayer.exoplayer;

import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.tofik.coolexoplayer.exoplayer.cool.CoolPlayer;
import com.tofik.coolexoplayer.exoplayer.cool.annotations.Beta;
import com.tofik.coolexoplayer.exoplayer.cool.widget.PressablePlayerSelector;


/**
 * @author tofik 23/1/2020.
 * @since 3.6.0.2802
 *
 * Work with {@link PressablePlayerSelector} and {@link PlayerView} to handle user's custom playback
 * interaction. A common use-case is when user clicks the Play button to manually start a playback.
 * We should respect this by putting the {@link CoolPlayer}'s priority to highest, and request a
 * refresh for all {@link CoolPlayer}.
 *
 * The same behaviour should be handled for the case user clicks the Pause button.
 *
 * All behaviour should be cleared once user scroll the selection out of playable region. This is
 * already handled by {@link PressablePlayerSelector}.
 */
@Beta //
public class ExoPlayerDispatcher extends DefaultControlDispatcher {

  private final PressablePlayerSelector playerSelector;
  private final com.tofik.coolexoplayer.exoplayer.cool.CoolPlayer CoolPlayer;

  public ExoPlayerDispatcher(PressablePlayerSelector playerSelector, CoolPlayer CoolPlayer) {
    this.playerSelector = playerSelector;
    this.CoolPlayer = CoolPlayer;
  }

  @Override
  public boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady) {
    if (playWhenReady) {
      // Container will handle the call to play.
      return playerSelector.toPlay(CoolPlayer.getPlayerOrder());
    } else {
      player.setPlayWhenReady(false);
      playerSelector.toPause(CoolPlayer.getPlayerOrder());
      return true;
    }
  }
}
