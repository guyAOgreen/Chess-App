-- Values that become PGN tag values must not contain control characters.
--
-- PGN defines a string token as printing characters between quotation marks, so an
-- embedded newline makes the emitted document invalid whatever the escaping, and
-- line-oriented readers mis-parse it. Escaping quotes and backslashes prevents a
-- value forging a tag; it does not make a newline legal.
--
-- The btrim idiom used in V2 cannot express this: PostgreSQL's single-argument
-- btrim strips spaces only, while Java's trim() strips every character up to and
-- including the space, so the two disagree on tabs and newlines.
--
-- movetext is deliberately exempt. PGN wraps long games across lines, and V2
-- already accepts a line break between moves.
ALTER TABLE games
    ADD CONSTRAINT games_white_name_no_control CHECK (white_name !~ '[[:cntrl:]]'),
    ADD CONSTRAINT games_black_name_no_control CHECK (black_name !~ '[[:cntrl:]]'),
    ADD CONSTRAINT games_event_no_control CHECK (event IS NULL OR event !~ '[[:cntrl:]]'),
    ADD CONSTRAINT games_site_no_control  CHECK (site  IS NULL OR site  !~ '[[:cntrl:]]'),
    ADD CONSTRAINT games_round_no_control CHECK (round IS NULL OR round !~ '[[:cntrl:]]');

-- eco needs no constraint: games_eco_format already requires ^[A-E][0-9]{2}$.
