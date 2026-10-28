\set n random(0, 159)
SELECT id AS pid FROM pro_picks WHERE tournament_id = 1 ORDER BY id OFFSET :n LIMIT 1 \gset
BEGIN;
DELETE FROM pro_pick_spells WHERE pick_id = :pid;
DELETE FROM pro_pick_items WHERE pick_id = :pid;
DELETE FROM pro_pick_runes WHERE pick_id = :pid;
INSERT INTO pro_pick_spells (pick_id, slot, spell_id) VALUES (:pid, 0, 21);
INSERT INTO pro_pick_spells (pick_id, slot, spell_id) VALUES (:pid, 1, 4);
INSERT INTO pro_pick_items (pick_id, slot, item_id) VALUES (:pid, 0, 3032);
INSERT INTO pro_pick_items (pick_id, slot, item_id) VALUES (:pid, 1, 1086);
INSERT INTO pro_pick_items (pick_id, slot, item_id) VALUES (:pid, 2, 6675);
INSERT INTO pro_pick_items (pick_id, slot, item_id) VALUES (:pid, 4, 3036);
INSERT INTO pro_pick_items (pick_id, slot, item_id) VALUES (:pid, 5, 3031);
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES (:pid, 0, 8008);
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES (:pid, 1, 8009);
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES (:pid, 2, 9103);
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES (:pid, 3, 8017);
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES (:pid, 4, 8347);
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES (:pid, 5, 8321);
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES (:pid, 6, 5005);
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES (:pid, 7, 5008);
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES (:pid, 8, 5011);
END;
