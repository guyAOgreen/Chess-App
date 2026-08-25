-- Values that become PGN tag values must not contain a Unicode line separator.
--
-- V3 rejected control characters with [[:cntrl:]], which does NOT match U+2028
-- LINE SEPARATOR or U+2029 PARAGRAPH SEPARATOR — confirmed by running the
-- predicate on PostgreSQL 18 rather than assuming it. Java's Character.isISOControl
-- had the same hole, so both layers accepted them.
--
-- They are not harmless. Java's \R treats both as line terminators, as do many PGN
-- readers, so a name or tag carrying one is emitted as a tag value spread over two
-- lines: a document the assembler produces and no line-oriented reader can read
-- back. The PGN specification defines a string token as printing characters between
-- quotation marks, so such a value is not legal in a tag whatever the escaping.
--
-- E'...' spells the two characters as escapes rather than embedding them literally,
-- because a migration nobody can see the content of is a migration nobody can
-- review. It requires a UTF8 database, which this application already requires.
--
-- Separate constraints rather than a rewrite of the V3 ones: an applied migration is
-- never edited, and a distinct name tells a reader which rule a row broke.
--
-- movetext is exempt for the same reason it is exempt from V3: PGN wraps long games
-- across lines, and V2 already accepts a line break between moves.
ALTER TABLE games
    ADD CONSTRAINT games_white_name_no_line_separator
        CHECK (white_name !~ E'[\u2028\u2029]'),
    ADD CONSTRAINT games_black_name_no_line_separator
        CHECK (black_name !~ E'[\u2028\u2029]'),
    ADD CONSTRAINT games_event_no_line_separator
        CHECK (event IS NULL OR event !~ E'[\u2028\u2029]'),
    ADD CONSTRAINT games_site_no_line_separator
        CHECK (site IS NULL OR site !~ E'[\u2028\u2029]'),
    ADD CONSTRAINT games_round_no_line_separator
        CHECK (round IS NULL OR round !~ E'[\u2028\u2029]');

-- eco needs no constraint: games_eco_format already requires ^[A-E][0-9]{2}$.
