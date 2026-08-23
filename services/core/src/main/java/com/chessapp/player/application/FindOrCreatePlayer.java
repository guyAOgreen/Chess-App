package com.chessapp.player.application;

import com.chessapp.player.domain.NewPlayer;
import com.chessapp.player.domain.Player;
import com.chessapp.player.domain.PlayerRepository;
import org.springframework.stereotype.Service;

/**
 * Resolves a player name, as it appears in a PGN tag, to a stored {@link Player}.
 *
 * <p>Consumed by PGN import (#7). Constructing the {@link NewPlayer} validates and
 * normalises before the database is touched, so invalid input fails with a domain
 * error rather than a constraint violation.
 */
@Service
public class FindOrCreatePlayer {

    private final PlayerRepository players;

    public FindOrCreatePlayer(PlayerRepository players) {
        this.players = players;
    }

    public Player execute(String displayName, String fideId, String federation) {
        return players.createOrFind(new NewPlayer(displayName, fideId, federation));
    }
}
