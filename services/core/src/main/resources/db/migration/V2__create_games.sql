-- A confirmed chess game.
--
-- movetext is canonical: validated SAN with move numbers, no tag pairs and no
-- terminal result token. The canonical PGN document is assembled on demand from
-- the metadata columns plus movetext, so the tags have no stored form that could
-- drift from the authoritative metadata.
--
-- source_pgn is provenance: the exact document a PGN import submitted, and NULL
-- for a scoresheet import, which has no source document. Nothing in the
-- application reads it to answer a product question.
--
-- The CHECK constraints mirror the rules the domain enforces, so a row written by
-- a migration or a fix-up script cannot violate them silently.
CREATE TABLE games (
    id              UUID        PRIMARY KEY DEFAULT uuidv7(),
    white_player_id UUID        NOT NULL REFERENCES players (id),
    black_player_id UUID        NOT NULL REFERENCES players (id),
    white_name      TEXT        NOT NULL,
    black_name      TEXT        NOT NULL,
    white_rating    INT         NULL,
    black_rating    INT         NULL,
    event           TEXT        NULL,
    site            TEXT        NULL,
    round           TEXT        NULL,
    played_on       DATE        NULL,
    result          TEXT        NOT NULL,
    eco             TEXT        NULL,
    source          TEXT        NOT NULL,
    movetext        TEXT        NOT NULL,
    source_pgn      TEXT        NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Game-time name snapshots. '?' is rejected rather than stored: a game with
    -- an unknown player cannot resolve to a players row at all.
    CONSTRAINT games_white_name_trimmed     CHECK (white_name = btrim(white_name)),
    CONSTRAINT games_white_name_not_blank   CHECK (btrim(white_name) <> ''),
    CONSTRAINT games_white_name_not_unknown CHECK (white_name <> '?'),
    CONSTRAINT games_black_name_trimmed     CHECK (black_name = btrim(black_name)),
    CONSTRAINT games_black_name_not_blank   CHECK (btrim(black_name) <> ''),
    CONSTRAINT games_black_name_not_unknown CHECK (black_name <> '?'),

    CONSTRAINT games_white_rating_positive  CHECK (white_rating IS NULL OR white_rating > 0),
    CONSTRAINT games_black_rating_positive  CHECK (black_rating IS NULL OR black_rating > 0),

    -- Optional PGN tags. Absent, blank and the unknown marker all mean the same
    -- thing, so only NULL is permitted to express it.
    CONSTRAINT games_event_normalised CHECK (
        event IS NULL OR (event = btrim(event) AND event <> '' AND event <> '?')),
    CONSTRAINT games_site_normalised CHECK (
        site IS NULL OR (site = btrim(site) AND site <> '' AND site <> '?')),
    -- TEXT, not numeric: PGN rounds are not numeric. '1.2' and '?' are both legal.
    CONSTRAINT games_round_normalised CHECK (
        round IS NULL OR (round = btrim(round) AND round <> '' AND round <> '?')),

    CONSTRAINT games_eco_format CHECK (eco IS NULL OR eco ~ '^[A-E][0-9]{2}$'),

    -- CHECK rather than a Postgres enum type, so adding a value is an ordinary
    -- migration rather than a type alteration.
    CONSTRAINT games_result_valid CHECK (
        result IN ('WHITE_WON', 'BLACK_WON', 'DRAW', 'UNFINISHED')),
    CONSTRAINT games_source_valid CHECK (
        source IN ('PERSONAL', 'CLUB', 'PGN_IMPORT', 'LICHESS', 'CHESS_COM',
                   'MEGA_DATABASE', 'OTHER')),

    CONSTRAINT games_movetext_trimmed   CHECK (movetext = btrim(movetext)),
    CONSTRAINT games_movetext_not_blank CHECK (btrim(movetext) <> ''),
    -- A whole PGN document pasted into the column would put derived tag data
    -- alongside canonical move data.
    CONSTRAINT games_movetext_no_tag_pairs CHECK (strpos(movetext, '[') = 0),
    -- The result column is authoritative; assembly appends the terminal token
    -- from it. Anchored on a token boundary so 'O-O-O' and 'Qxf7*' are unaffected.
    CONSTRAINT games_movetext_no_result_token CHECK (
        movetext !~ '(^|[[:space:]])(1-0|0-1|1/2-1/2|\*)$')
);

-- Games for a player as a given colour, most recent first: the shape opponent
-- preparation and the games-by-player views ask for.
CREATE INDEX games_white_player_played_on_idx ON games (white_player_id, played_on DESC);
CREATE INDEX games_black_player_played_on_idx ON games (black_player_id, played_on DESC);
CREATE INDEX games_played_on_idx ON games (played_on DESC);

-- event is deliberately unindexed. A personal game database is small enough that
-- a scan costs nothing, and the right index depends on whether the filter turns
-- out to be exact-match or prefix-search.
