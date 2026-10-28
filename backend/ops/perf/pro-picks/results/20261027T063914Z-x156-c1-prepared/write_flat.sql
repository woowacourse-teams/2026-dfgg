\set n random(0, 159)
SELECT id AS pid FROM pro_picks WHERE tournament_id = 1 ORDER BY id OFFSET :n LIMIT 1 \gset
BEGIN;
UPDATE pro_picks_flat SET spell_ids = '{21,4}', item_ids = '{3032,1086,6675,NULL,3036,3031,NULL}', rune_ids = '{8008,8009,9103,8017,8347,8321,5005,5008,5011}' WHERE id = :pid;
END;
