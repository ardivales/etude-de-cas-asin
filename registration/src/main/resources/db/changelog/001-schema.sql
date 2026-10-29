--liquibase formatted sql

--changeset tma:001-schema
CREATE TABLE concours (
  id INTEGER PRIMARY KEY,
  code VARCHAR(10) NOT NULL,
  libelle VARCHAR(100) NOT NULL,
  date_epreuve DATE NOT NULL,
  cloture TIMESTAMP NOT NULL
);

CREATE TABLE centres (
  id INTEGER PRIMARY KEY,
  code VARCHAR(1) NOT NULL,
  nom VARCHAR(50) NOT NULL,
  capacite INTEGER NOT NULL,
  dernier_numero INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT ck_centres_numero CHECK (dernier_numero BETWEEN 0 AND 9999)
);

CREATE TABLE candidats (
  id SERIAL PRIMARY KEY,
  npi VARCHAR(10) NOT NULL,
  nom VARCHAR(60) NOT NULL,
  prenoms VARCHAR(80) NOT NULL,
  date_naissance DATE NOT NULL,
  cree_le TIMESTAMP NOT NULL
);

CREATE TABLE inscriptions (
  id SERIAL PRIMARY KEY,
  concours_id INTEGER NOT NULL REFERENCES concours (id),
  centre_id INTEGER NOT NULL REFERENCES centres (id),
  npi VARCHAR(10) NOT NULL,
  numero_table VARCHAR(6) NOT NULL,
  statut VARCHAR(30) NOT NULL,
  cree_le TIMESTAMP NOT NULL,
  CONSTRAINT ck_inscriptions_statut CHECK (statut IN ('validee', 'en_attente_verification', 'rejetee')),
  CONSTRAINT ck_inscriptions_numero CHECK (numero_table ~ '^[A-Z]-[0-9]{4}$')
);

CREATE UNIQUE INDEX ux_concours_code ON concours (code);
CREATE UNIQUE INDEX ux_centres_code ON centres (code);
CREATE UNIQUE INDEX ux_candidats_npi ON candidats (npi);
CREATE UNIQUE INDEX ux_inscriptions_centre_numero ON inscriptions (centre_id, numero_table);
CREATE UNIQUE INDEX ux_inscriptions_npi_concours ON inscriptions (npi, concours_id);
CREATE INDEX ix_inscriptions_centre_concours ON inscriptions (centre_id, concours_id);
CREATE INDEX ix_candidats_nom ON candidats (nom varchar_pattern_ops);
