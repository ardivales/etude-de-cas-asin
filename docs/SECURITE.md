# SEC-001 – Injection SQL et exfiltration du fichier des candidats (non signalé)

## Faits (journal, heure du Bénin, 20/11/2026)

| Heure | Requête depuis 102.64.217.33 (IP publique) | Résultat |
|---|---|---|
| 04 h 12 min 40 | `recherche?nom=KPADONOU` | 200, 390 lignes : reconnaissance |
| 04 h 13 min 05 | `nom='` | 500, l'erreur SQL brute est renvoyée : la faille est confirmée |
| 04 h 13 min 27 | `nom=' OR '1'='1` | 200, 50 lignes : le filtre est contourné |
| 04 h 13 min 50 | `nom=' UNION SELECT npi, nom, date_naissance FROM candidats --` | 200, **18 535 lignes** |
| 04 h 14 min 16 | même requête | 200, 18 535 lignes |

Toutes les autres recherches viennent de 10.20.1.15 (poste interne). La recherche, écran d'agent, est joignable depuis Internet sans authentification. L'erreur 500 révèle le SGBD et la requête.

## Ce qui a pu être exposé

- Certainement : NPI, nom et date de naissance des candidats, campagnes précédentes comprises (`candidats` en compte 19 303 dans l'export ; 18 535 au moment de l'attaque). Le NPI et la date de naissance sont exactement les données demandées pour s'inscrire : usurpation possible d'inscriptions et de convocations.
- Possiblement : tout ce que lit le compte SQL de l'application. Une `UNION` ouvre toutes les tables accessibles (inscriptions, centres, numéros de table) et, selon les droits du compte, d'autres schémas. Si le compte peut écrire, une modification des données n'est pas exclue : à vérifier dans les journaux PostgreSQL.

## Mesures immédiates

1. Couper `GET /api/inscrits/recherche` depuis Internet (règle sur le serveur frontal), le restreindre au réseau interne.
2. Bloquer 102.64.217.33 et rechercher ses autres requêtes dans tous les journaux (serveur frontal, PostgreSQL) avant et après le 20/11.
3. Corriger la requête par paramètres liés, avec `nom` limité à des lettres, espaces, tirets et apostrophes, 60 caractères au plus. C'est fait dans `GET /api/inscrits/recherche` de l'application Spring : les quatre requêtes de l'attaque sont refusées en 400 (`RegistrantApiTest`).
4. Ne plus renvoyer d'erreur SQL au client : message générique et identifiant de requête.
5. Compte SQL de l'application en lecture/écriture sur ses seules tables, sans droit DDL ; changer son mot de passe.
6. Conserver les preuves : copie des journaux, horodatée et protégée.
7. Contrôler l'intégrité des données (comparaison avec la sauvegarde la plus récente).

## Qui prévenir

- RSSI de l'ASIN et bjCSIRT, immédiatement.
- Délégué à la protection des données, puis l'APDP (Autorité de protection des données personnelles) dans les délais légaux.
- Direction des concours : décision d'informer les candidats et de vérifier les inscriptions faites après le 20/11 04 h 14.

## Mesures durables

- Authentification et contrôle des rôles sur toutes les routes d'agent.
- Limitation du débit sur les routes publiques, pare-feu applicatif.
- Revue du code de toutes les requêtes SQL, analyse statique dans l'intégration continue.
- Alerte sur toute erreur 500 et sur tout volume de réponse anormal.
