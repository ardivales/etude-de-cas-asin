--liquibase formatted sql

--changeset tma:002-reference-data
INSERT INTO concours (id, code, libelle, date_epreuve, cloture) VALUES
  (1, 'DOUANES', 'Concours des inspecteurs des douanes', '2026-12-12', '2026-11-30 22:59:00'),
  (2, 'IMPOTS', 'Concours des contrôleurs des impôts', '2026-12-12', '2026-11-30 22:59:00'),
  (3, 'TRESOR', 'Concours des agents du Trésor public', '2026-12-19', '2026-11-20 22:59:00');

INSERT INTO centres (id, code, nom, capacite) VALUES
  (1, 'C', 'Cotonou', 6000),
  (2, 'P', 'Parakou', 700),
  (3, 'L', 'Lokossa', 300),
  (4, 'N', 'Porto-Novo', 800),
  (5, 'A', 'Abomey', 600);
