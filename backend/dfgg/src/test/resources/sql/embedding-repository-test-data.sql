DELETE FROM embeddings;

INSERT INTO embeddings (entity_type, entity_id, algorithm_version, vector, trained_at) VALUES
    ('CHAMPION', 266, 'v1', '0.1,0.2', '2026-08-01T00:00:00'),
    ('ITEM', 3071, 'v1', '0.3,0.4', '2026-08-01T00:00:00'),
    ('ITEM', 9999, 'v2', '0.5,0.6', '2026-08-01T00:00:00');
