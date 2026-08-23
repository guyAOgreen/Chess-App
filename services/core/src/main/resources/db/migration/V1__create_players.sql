-- A real-world chess player, who need not be an application user.
--
-- display_name is the identity key: PGN import matches on it exactly, after
-- trimming. The trimmed constraint keeps database uniqueness aligned with that
-- rule, and stops a padded ' ? ' from slipping past the unknown-marker check.
CREATE TABLE players (
    id           UUID        PRIMARY KEY DEFAULT uuidv7(),
    display_name TEXT        NOT NULL,
    fide_id      TEXT        NULL,
    federation   TEXT        NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT players_display_name_trimmed     CHECK (display_name = btrim(display_name)),
    CONSTRAINT players_display_name_not_blank   CHECK (btrim(display_name) <> ''),
    CONSTRAINT players_display_name_not_unknown CHECK (display_name <> '?'),
    CONSTRAINT players_fide_id_digits           CHECK (fide_id IS NULL OR fide_id ~ '^[0-9]+$'),
    CONSTRAINT players_federation_format        CHECK (federation IS NULL OR federation ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX players_display_name_idx ON players (display_name);

-- Partial: most players have no FIDE ID, and NULLs must not collide.
CREATE UNIQUE INDEX players_fide_id_idx ON players (fide_id) WHERE fide_id IS NOT NULL;
