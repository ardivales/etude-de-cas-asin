# Étude de cas TMA – Inscriptions aux concours administratifs

Intervention sur les incidents de la campagne 2026 : priorisation, analyse du journal, causes racines, correction de l'enregistrement d'une inscription, réparation des données, rapport d'incident et incident de sécurité non signalé.

## Contenu

| Chemin | Rôle |
|---|---|
| `INTERVENTION.md` | Classement des incidents par gravité et urgence (premier commit) |
| `analysis/analyze_log.py` | Analyse du journal de production, en heure du Bénin |
| `docs/CAUSES_RACINES.md` | Cause racine de chaque incident et liens entre eux |
| `registration/` | Application Spring Boot : `POST /api/inscriptions` corrigé, règles d'âge et de clôture, tests dont concurrence |
| `docker-compose.yml` | PostgreSQL, API et service d'identification simulé |
| `mock-identite/` | Service d'identification simulé, latence réglable |
| `sql/reparation.sql` | Réparation rejouable de l'export et contraintes |
| `docs/RAPPORT_INC-206.md` | Rapport d'incident INC-206 |
| `docs/SECURITE.md` | Bonus : injection SQL et exfiltration des candidats |

Le journal et l'export ne sont pas versionnés : ils contiennent des données personnelles. Placez `journal_production.log` et `base_inscriptions.sql` à la racine du dépôt (ils sont ignorés par Git).

## Prérequis

- Docker (avec Docker Compose)
- Python 3.10 ou plus, pour l'analyse du journal
- Facultatif : JDK 21 ou plus et Maven 3.9, pour lancer les tests hors conteneur

## 1. Analyser le journal

Aucune dépendance, bibliothèque standard uniquement.

```bash
python3 analysis/analyze_log.py journal_production.log
```

Résultats sur le journal fourni :

| Question | Résultat |
|---|---|
| Numéros de table attribués plusieurs fois | 10 (P-0464, L-0297 ×5, L-0298 ×6, C-3305…) |
| Inscriptions en double | 7 candidats, 7 inscriptions en trop |
| Lenteur du service d'identification | 19/11 de 09 h 09 min 48 à 09 h 39 min 49, 60 requêtes touchées |
| Refus à tort le soir de la clôture du Trésor | 147 requêtes, 100 candidats |
| Candidats refusés à tort pour l'âge | 36 |
| Liste des inscrits, moyenne / max | C 25,4 s / 46,5 s ; N 4,4 / 7,9 ; P 4,4 / 6,6 ; A 2,8 / 5,1 ; L 1,3 / 3,7 |

Le programme signale aussi les recherches suspectes (injection SQL).

## 2. Démarrer la pile Docker

PostgreSQL 16, l'API Spring Boot et un service d'identification simulé, exposés sur la machine locale seulement.

```bash
cp .env.example .env
sed -i "s/^DB_PASSWORD=.*/DB_PASSWORD=$(openssl rand -hex 16)/" .env
docker compose up -d --build
docker compose ps
```

Attendu : trois services `running` (`db`, `identite`, `api`). Le premier build prend quelques minutes. Si une pile tournait déjà avec un autre mot de passe, repartir de zéro avec `docker compose down -v`.

## 3. Réparer les données

Le script est appliqué à l'export dans une base `reprise` séparée, à l'intérieur du conteneur PostgreSQL : pas besoin d'installer `psql`. L'export doit être chargé dans une base vide : ses `DROP TABLE` sans `CASCADE` échouent sur une base déjà réparée.

```bash
docker compose exec -T db psql -U inscriptions -d postgres -c "DROP DATABASE IF EXISTS reprise" -c "CREATE DATABASE reprise"
docker compose exec -T db psql -U inscriptions -d reprise -q < base_inscriptions.sql
docker compose exec -T db psql -U inscriptions -d reprise < sql/reparation.sql
docker compose exec -T db psql -U inscriptions -d reprise < sql/reparation.sql
docker compose exec -T db psql -U inscriptions -d reprise -c \
  "SELECT count(*) AS inscriptions, count(DISTINCT (centre_id, numero_table)) AS numeros_distincts FROM inscriptions"
```

Attendu : 7 doublons archivés, 15 numéros réattribués, 7 inscriptions de Lokossa hors capacité, à l'identique au second passage ; 6 393 inscriptions pour 6 393 numéros distincts. Avec un `psql` local, `psql -f sql/reparation.sql` fonctionne de la même façon.

Le second passage ne modifie rien. Le script s'exécute dans une seule transaction : en cas d'erreur, rien n'est appliqué.

| Étape | Effet sur l'export |
|---|---|
| Doublons `(npi, concours)` | 7 inscriptions copiées dans `inscriptions_archivees` puis retirées ; la plus ancienne est conservée |
| Numéros en double | 15 inscriptions renumérotées après le plus grand numéro du centre ; la première arrivée garde son numéro ; trace dans `numeros_reattribues` |
| Compteur | `centres.dernier_numero` pour numéroter sans recalcul |
| Contraintes | Unicité `(centre_id, numero_table)`, `(npi, concours_id)`, `candidats(npi)`, codes concours et centre ; contrôles du statut et du format de numéro ; index de la liste et de la recherche ; séquences pour les identifiants |
| Lokossa | Aucune suppression : la vue `inscriptions_hors_capacite` liste les 7 dernières inscriptions arrivées au-delà des 300 places |

Décision à demander à la Direction des concours pour Lokossa : relever la capacité (salle supplémentaire), ou transférer les 7 candidats vers un autre centre avec leur accord. Les candidats sont tous inscrits aux Impôts, épreuve du 12/12.

Personnes à prévenir :

- Direction des concours : décision Lokossa, réouverture pour les 100 candidats du Trésor et les 36 candidats refusés pour l'âge.
- Chefs de centre de Cotonou, Parakou, Lokossa et Porto-Novo : liste des numéros réattribués.
- Les 15 candidats renumérotés (nouvelle convocation) et les 7 candidats dont le doublon est archivé.
- RSSI, délégué à la protection des données et APDP pour l'incident de sécurité (`docs/SECURITE.md`).

## 4. Correction : application Spring Boot

Application monolithique Spring Boot 3.3, Java 21, Spring Data JPA, PostgreSQL 16, schéma versionné par Liquibase. Le schéma Liquibase décrit la base cible, celle que produit `sql/reparation.sql` à partir de l'export.

La pile démarrée à l'étape 2 sert l'API sur http://localhost:8080.

### Essayer l'API

```bash
curl -s -X POST localhost:8080/api/inscriptions -H 'Content-Type: application/json' \
  -d '{"npi":"3076016910","concours":"DOUANES","centre":"P","naissance":"1995-04-02"}'; echo
curl -s -X POST localhost:8080/api/inscriptions -H 'Content-Type: application/json' \
  -d '{"npi":"3076016910","concours":"DOUANES","centre":"P","naissance":"1995-04-02"}'; echo
curl -s -X POST localhost:8080/api/inscriptions -H 'Content-Type: application/json' \
  -d '{"npi":"1111111111","concours":"IMPOTS","centre":"C","naissance":"1991-06-20"}'; echo
curl -s -X POST localhost:8080/api/inscriptions -H 'Content-Type: application/json' \
  -d '{"npi":"0001234567","concours":"DOUANES","centre":"C","naissance":"1995-04-02"}'; echo
```

| Appel | Réponse attendue |
|---|---|
| Première inscription | `201 {"id":1,"numeroTable":"P-0001","statut":"validee"}` |
| Même appel une seconde fois | 409 « Vous êtes déjà inscrit à ce concours. » |
| Né le 20/06/1991 (INC-205) | 201 : la règle d'âge est corrigée |
| NPI commençant par `000` | 409 « NPI inconnu du service d'identification. » (le service simulé répond 404) |

Les refus suivent la RFC 7807 (`application/problem+json`) : 400 données invalides, 409 règle métier, 503 échec temporaire, sans détail technique. Un NPI ne s'inscrit qu'une fois : pour rejouer, changer ses chiffres.

### Liste des inscrits et recherche (INC-207, SEC-001)

```bash
curl -s 'localhost:8080/api/centres/P/inscrits?concours=DOUANES&page=0&size=100'; echo
curl -s 'localhost:8080/api/inscrits/recherche?nom=CAND'; echo
curl -s -o /dev/null -w '%{http_code}\n' "localhost:8080/api/inscrits/recherche?nom=%27%20OR%20%271%27%3D%271"
```

| Route | Correction |
|---|---|
| `GET /api/centres/{code}/inscrits?concours=…&page=…&size=…` | Une seule requête avec jointure au lieu d'une lecture de `candidats` par inscrit (N+1 d'INC-207), index `(centre_id, concours_id)` et `candidats(npi)`, tri par numéro, pages de 100 lignes au plus. Testé : 5 000 inscrits listés en moins de 2 s |
| `GET /api/inscrits/recherche?nom=…` | Requête paramétrée (Spring Data), nom limité aux lettres, espaces, tirets et apostrophes, 60 caractères, 50 résultats au plus. Les requêtes de SEC-001 sont refusées en 400 |

Attendu : la liste en JSON, la recherche sur le début du nom, puis `400` pour l'injection. En production, ces deux routes d'agent sont réservées aux agents authentifiés (JWT, comme dans le reste du système d'information).

### Reproduire INC-206 : service d'identification lent

```bash
IDENTITY_LATENCY_MS=8000 docker compose up -d identite
sleep 2
curl -s -w '  -> %{http_code} en %{time_total}s\n' -X POST localhost:8080/api/inscriptions -H 'Content-Type: application/json' \
  -d '{"npi":"4444444444","concours":"DOUANES","centre":"A","naissance":"1995-04-02"}'
docker compose up -d identite
```

Attendu : réponse 201 en 5 s environ, statut `en_attente_verification`, au lieu de bloquer jusqu'à 62 s comme le 19/11. La dernière commande remet le service à 200 ms.

### Swagger et métriques

| Adresse | Contenu |
|---|---|
| http://localhost:8080/swagger-ui.html | Documentation interactive : « Try it out » envoie une inscription préremplie |
| http://localhost:8080/v3/api-docs | Contrat OpenAPI |
| http://localhost:8080/actuator/health | État de l'application et de la base (sondes `liveness` et `readiness`) |
| http://localhost:8080/actuator/prometheus | Métriques au format Prometheus |
| http://localhost:8080/actuator/metrics | Mêmes métriques, consultables une par une |

Métriques utiles pour l'exploitation :

| Métrique | Ce qu'elle surveille |
|---|---|
| `inscriptions_enregistrees_total{statut}` | Inscriptions par statut ; une hausse de `en_attente_verification` signale un service d'identification lent (INC-206) |
| `inscriptions_refusees_total{motif}` | Refus par motif : `DEJA_INSCRIT`, `MEME_JOUR`, `CENTRE_COMPLET`, `CLOTURE`, `AGE`, `NPI_INCONNU` ; un pic de `CLOTURE` avant l'heure aurait révélé INC-204, un pic de `AGE` INC-205 |
| `http_client_requests_seconds{client_name="identite"}` | Durée des appels au service d'identification ; alerte proposée au-delà de 2 s au 95e centile |
| `http_server_requests_seconds{uri="/api/inscriptions"}` | Durée et codes de réponse de l'API |

En production, ces adresses ne sont pas publiques : Actuator est servi sur un port de gestion réservé au réseau interne (`management.server.port`) et Swagger est désactivé (`springdoc.swagger-ui.enabled=false`).

### Concurrence à la main

On laisse 10 places libres à Lokossa, puis 40 candidats différents s'inscrivent au même instant :

```bash
docker compose exec -T db psql -U inscriptions -d inscriptions -c \
  "UPDATE centres SET capacite = (SELECT count(*) FROM inscriptions WHERE centre_id = 3) + 10 WHERE code = 'L'"
seq -w 0 39 | xargs -P 40 -I{} curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/inscriptions \
  -H 'Content-Type: application/json' -d '{"npi":"70000000{}","concours":"DOUANES","centre":"L","naissance":"1998-05-05"}' | sort | uniq -c
docker compose exec -T db psql -U inscriptions -d inscriptions -c \
  "SELECT c.capacite, count(i.id) AS inscrits, count(DISTINCT i.numero_table) AS numeros_distincts FROM centres c LEFT JOIN inscriptions i ON i.centre_id = c.id WHERE c.code = 'L' GROUP BY c.capacite"
```

Attendu : `10 201` et `30 409` ; autant d'inscrits que de places, autant de numéros distincts que d'inscrits.

Double clic, 15 envois simultanés du même candidat :

```bash
seq 1 15 | xargs -P 15 -I{} curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/inscriptions \
  -H 'Content-Type: application/json' -d '{"npi":"8000000001","concours":"IMPOTS","centre":"C","naissance":"1998-05-05"}' | sort | uniq -c
```

Attendu : `1 201` et `14 409`. Pour rejouer, changer le préfixe des NPI.

### Lancer les tests

Les tests démarrent leur propre PostgreSQL 16 avec Testcontainers : seul Docker est nécessaire.

```bash
cd registration && mvn -B verify
```

Sans Java ni Maven sur la machine :

```bash
docker run --rm -v "$PWD/registration":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal --add-host=host.docker.internal:host-gateway \
  maven:3.9-eclipse-temurin-21 mvn -B verify
docker run --rm -v "$PWD/registration":/app alpine chown -R "$(id -u):$(id -g)" /app/target
```

Attendu : `Tests run: 42, Failures: 0, Errors: 0` puis `BUILD SUCCESS`. La dernière commande rend à votre utilisateur le dossier `target` créé par le conteneur.

| Classe | Ce qui est vérifié |
|---|---|
| `RegistrationRulesTest` | Âge au 31 décembre (né le 20/06/1991 accepté, le 31/12/1990 refusé) ; clôture en heure du Bénin (ouvert à 23:59:00, fermé à 23:59:01) |
| `IdentityAdapterTest` | MockServer : identité connue, NPI inconnu, erreur 500, service injoignable, service lent coupé au délai total |
| `RegistrationApiTest` | API complète sur PostgreSQL : numérotation, doublon, même jour, centre complet, NPI inconnu sans écriture, mise en attente, âge, clôture du Trésor, entrées invalides et injection, instants stockés en UTC, délai configuré à 5 s |
| `ObservabilityTest` | Santé `UP` avec la base, contrat OpenAPI et Swagger servis, métriques par statut et par motif exposées à Prometheus |
| `RegistrantApiTest` | Liste en une jointure avec noms et statuts, pagination, 5 000 inscrits en moins de 2 s, recherche par début de nom, 50 résultats au plus, injections de SEC-001 refusées |
| `ConcurrencyTest` | Vraies requêtes HTTP simultanées : 40 inscriptions pour 10 places (10 acceptées, numéros L-0001 à L-0010), 15 doubles clics (1 inscription), deux concours du même jour en parallèle (1 inscription) |

### Test de mutation

Pour prouver que les tests de concurrence détectent le défaut d'origine, on retire les deux verrous, on relance, puis on restaure :

```bash
sed -i '/@Lock(LockModeType.PESSIMISTIC_WRITE)/d' registration/src/main/java/bj/asin/registration/repository/CenterRepository.java
sed -i '/lockCandidate(/d' registration/src/main/java/bj/asin/registration/service/RegistrationWriter.java
(cd registration && mvn -B test -Dtest=ConcurrencyTest)
git checkout -- registration/src/main
```

Attendu : `Tests run: 3, Failures: 3` (5 inscrits au lieu de 10, 2 inscriptions le même jour). Après `git checkout`, tout redevient vert.

### Organisation

| Paquet | Rôle |
|---|---|
| `controller` | `POST /api/inscriptions`, liste des inscrits, recherche ; validation des entrées |
| `service` | `RegistrationService` : règles puis appel d'identification, hors transaction. `RegistrationWriter` : étapes 4 à 8 dans une transaction courte. `RegistrationRules` : âge et clôture |
| `integration.identity` | Client WebClient du service d'identification, délai total de 5 s |
| `repository`, `entity` | Spring Data JPA ; verrou du centre (`PESSIMISTIC_WRITE`) et verrou consultatif par NPI |
| `common.exception` | Erreurs RFC 7807, compteur des refus par motif |
| `config` | Client d'identification, horloge, description OpenAPI |

### Arrêter et nettoyer

```bash
docker compose down -v
```

`-v` supprime aussi le volume de la base : le prochain démarrage repart d'un schéma neuf.

## Choix de la correction

1. Le service d'identification est appelé **avant** la transaction, avec un délai total de 5 s (`Mono.timeout`, en plus des délais de connexion et de réponse). Au-delà, l'inscription est enregistrée `en_attente_verification`. Une lenteur ne bloque plus personne et n'allonge plus la transaction.
2. L'appel et l'écriture sont dans deux beans distincts : un appel interne à une méthode `@Transactional` contournerait le proxy Spring et remettrait l'appel lent dans la transaction.
3. La transaction est courte : verrou consultatif sur le NPI (double clic, concours du même jour), puis verrou sur la ligne du centre (`SELECT … FOR UPDATE`) pour la capacité et le numéro, pris dans le même ordre par toutes les requêtes. Le numéro vient du compteur `centres.dernier_numero`, jamais d'un `max() + 1`.
4. Les index uniques restent le dernier rempart. Une violation est traduite en 409 (déjà inscrit) ou 503 (réessayer), jamais en erreur SQL.
5. Âge au 31 décembre = année du concours − année de naissance. Clôture : comparaison de deux `Instant`. Tous les instants sont des `Instant` : un `LocalDateTime` serait converti depuis le fuseau de la JVM, la même famille d'erreur qu'INC-204.
6. Requêtes paramétrées partout, NPI validé (10 chiffres) avant tout usage, erreurs sans détail technique, conteneur exécuté sans droits root, base et API exposées sur la machine locale seulement, mot de passe hors du dépôt.

## Évolutions proposées

- Traitement de reprise des inscriptions `en_attente_verification`.
- Convocation lue depuis `candidats`, sans appel au service d'identification.
- Clé d'idempotence sur `POST /api/inscriptions` et bouton désactivé pendant l'envoi.
- Authentification des routes d'agent, limitation de débit, alertes sur les erreurs 500 et les durées d'appel.
