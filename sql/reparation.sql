\set ON_ERROR_STOP on
SET client_min_messages = warning;

BEGIN;

CREATE TABLE IF NOT EXISTS inscriptions_archivees (
  id INTEGER PRIMARY KEY,
  concours_id INTEGER NOT NULL,
  centre_id INTEGER NOT NULL,
  npi VARCHAR(10) NOT NULL,
  numero_table VARCHAR(6) NOT NULL,
  statut VARCHAR(30) NOT NULL,
  cree_le TIMESTAMP NOT NULL,
  conservee_id INTEGER NOT NULL,
  motif VARCHAR(50) NOT NULL,
  archivee_le TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

CREATE TABLE IF NOT EXISTS numeros_reattribues (
  inscription_id INTEGER PRIMARY KEY,
  ancien_numero VARCHAR(6) NOT NULL,
  nouveau_numero VARCHAR(6) NOT NULL,
  reattribue_le TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

WITH classees AS (
  SELECT i.*,
         first_value(id) OVER (PARTITION BY npi, concours_id ORDER BY cree_le, id) AS conservee_id
  FROM inscriptions i
)
INSERT INTO inscriptions_archivees (id, concours_id, centre_id, npi, numero_table, statut, cree_le, conservee_id, motif)
SELECT id, concours_id, centre_id, npi, numero_table, statut, cree_le, conservee_id, 'doublon npi/concours'
FROM classees
WHERE id <> conservee_id
ON CONFLICT (id) DO NOTHING;

DELETE FROM inscriptions WHERE id IN (SELECT id FROM inscriptions_archivees);

ALTER TABLE centres ADD COLUMN IF NOT EXISTS dernier_numero INTEGER NOT NULL DEFAULT 0;

UPDATE centres c
SET dernier_numero = m.plus_grand
FROM (
  SELECT centre_id, max(substring(numero_table FROM 3)::INTEGER) AS plus_grand
  FROM inscriptions
  GROUP BY centre_id
) m
WHERE m.centre_id = c.id AND c.dernier_numero < m.plus_grand;

WITH classees AS (
  SELECT id, centre_id, numero_table, cree_le,
         row_number() OVER (PARTITION BY centre_id, numero_table ORDER BY cree_le, id) AS rang
  FROM inscriptions
),
a_renumeroter AS (
  SELECT k.id, k.numero_table,
         c.code || '-' || lpad((c.dernier_numero
           + row_number() OVER (PARTITION BY k.centre_id ORDER BY k.cree_le, k.id))::TEXT, 4, '0') AS nouveau
  FROM classees k
  JOIN centres c ON c.id = k.centre_id
  WHERE k.rang > 1
)
INSERT INTO numeros_reattribues (inscription_id, ancien_numero, nouveau_numero)
SELECT id, numero_table, nouveau FROM a_renumeroter
ON CONFLICT (inscription_id) DO NOTHING;

UPDATE inscriptions i
SET numero_table = r.nouveau_numero
FROM numeros_reattribues r
WHERE r.inscription_id = i.id AND i.numero_table = r.ancien_numero;

UPDATE centres c
SET dernier_numero = m.plus_grand
FROM (
  SELECT centre_id, max(substring(numero_table FROM 3)::INTEGER) AS plus_grand
  FROM inscriptions
  GROUP BY centre_id
) m
WHERE m.centre_id = c.id AND c.dernier_numero < m.plus_grand;

CREATE SEQUENCE IF NOT EXISTS inscriptions_id_seq OWNED BY inscriptions.id;
CREATE SEQUENCE IF NOT EXISTS candidats_id_seq OWNED BY candidats.id;
ALTER TABLE inscriptions ALTER COLUMN id SET DEFAULT nextval('inscriptions_id_seq');
ALTER TABLE candidats ALTER COLUMN id SET DEFAULT nextval('candidats_id_seq');

CREATE UNIQUE INDEX IF NOT EXISTS ux_concours_code ON concours (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_centres_code ON centres (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_candidats_npi ON candidats (npi);
CREATE UNIQUE INDEX IF NOT EXISTS ux_inscriptions_centre_numero ON inscriptions (centre_id, numero_table);
CREATE UNIQUE INDEX IF NOT EXISTS ux_inscriptions_npi_concours ON inscriptions (npi, concours_id);
CREATE INDEX IF NOT EXISTS ix_inscriptions_centre_concours ON inscriptions (centre_id, concours_id);
CREATE INDEX IF NOT EXISTS ix_candidats_nom ON candidats (nom varchar_pattern_ops);

DO $$
BEGIN
  PERFORM setval('inscriptions_id_seq', greatest(
    (SELECT coalesce(max(id), 0) FROM inscriptions),
    (SELECT coalesce(max(id), 0) FROM inscriptions_archivees)) + 1, false);
  PERFORM setval('candidats_id_seq', (SELECT coalesce(max(id), 0) + 1 FROM candidats), false);
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_inscriptions_statut') THEN
    ALTER TABLE inscriptions ADD CONSTRAINT ck_inscriptions_statut
      CHECK (statut IN ('validee', 'en_attente_verification', 'rejetee'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_inscriptions_numero') THEN
    ALTER TABLE inscriptions ADD CONSTRAINT ck_inscriptions_numero
      CHECK (numero_table ~ '^[A-Z]-[0-9]{4}$');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_centres_numero') THEN
    ALTER TABLE centres ADD CONSTRAINT ck_centres_numero CHECK (dernier_numero BETWEEN 0 AND 9999);
  END IF;
END $$;

CREATE OR REPLACE VIEW inscriptions_hors_capacite AS
SELECT *
FROM (
  SELECT c.code AS centre, c.nom AS nom_centre, c.capacite,
         row_number() OVER (PARTITION BY i.centre_id ORDER BY i.cree_le, i.id) AS rang_arrivee,
         i.id AS inscription_id, i.numero_table, i.npi, co.code AS concours, i.cree_le AS cree_le_utc
  FROM inscriptions i
  JOIN centres c ON c.id = i.centre_id
  JOIN concours co ON co.id = i.concours_id
) t
WHERE rang_arrivee > capacite;

COMMIT;

\echo 'Inscriptions archivées (doublons) :'
SELECT id, npi, concours_id, numero_table, conservee_id, motif FROM inscriptions_archivees ORDER BY id;
\echo 'Numéros réattribués (convocations à rééditer, candidats à prévenir) :'
SELECT r.inscription_id, i.npi, r.ancien_numero, r.nouveau_numero
FROM numeros_reattribues r JOIN inscriptions i ON i.id = r.inscription_id
ORDER BY r.inscription_id;
\echo 'Inscriptions au-delà de la capacité (décision à demander, aucune suppression) :'
SELECT * FROM inscriptions_hors_capacite ORDER BY centre, rang_arrivee;
