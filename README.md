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
- `psql`, pour le script de réparation
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

## 2. Démarrer une base PostgreSQL

Avec Docker, accessible depuis la machine locale seulement :

```bash
export PGPASSWORD='choisir-un-mot-de-passe'
docker run -d --name asin-tma-pg -e POSTGRES_USER=tma -e POSTGRES_PASSWORD="$PGPASSWORD" \
  -e POSTGRES_DB=inscriptions -p 127.0.0.1:5439:5432 postgres:16
```

## 3. Réparer les données

L'export doit être chargé dans une base vide : ses `DROP TABLE` sans `CASCADE` échouent sur une base déjà réparée.

```bash
psql -h 127.0.0.1 -p 5439 -U tma -d postgres -c "DROP DATABASE IF EXISTS reprise" -c "CREATE DATABASE reprise"
export PSQL="psql -h 127.0.0.1 -p 5439 -U tma -d reprise"
$PSQL -q -f base_inscriptions.sql
$PSQL -f sql/reparation.sql
$PSQL -f sql/reparation.sql
```

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

### Lancer avec Docker

```bash
cp .env.example .env                 # puis choisir DB_PASSWORD
docker compose up -d --build
curl -X POST localhost:8080/api/inscriptions -H 'Content-Type: application/json' \
  -d '{"npi":"3076016910","concours":"DOUANES","centre":"P","naissance":"1995-04-02"}'
```

Réponse : `201 {"id":1,"numeroTable":"P-0001","statut":"validee"}`. Les refus suivent la RFC 7807 (`application/problem+json`) : 400 données invalides, 409 règle métier, 503 échec temporaire, sans détail technique.

Le service d'identification simulé répond 404 pour un NPI commençant par `000`. Pour reproduire INC-206 :

```bash
IDENTITY_LATENCY_MS=8000 docker compose up -d identite
```

L'inscription répond alors en 5 s avec le statut `en_attente_verification`, au lieu de bloquer.

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
```

| Classe | Ce qui est vérifié |
|---|---|
| `RegistrationRulesTest` | Âge au 31 décembre (né le 20/06/1991 accepté, le 31/12/1990 refusé) ; clôture en heure du Bénin (ouvert à 23:59:00, fermé à 23:59:01) |
| `IdentityAdapterTest` | MockServer : identité connue, NPI inconnu, erreur 500, service injoignable, service lent coupé au délai total |
| `RegistrationApiTest` | API complète sur PostgreSQL : numérotation, doublon, même jour, centre complet, NPI inconnu sans écriture, mise en attente, âge, clôture du Trésor, entrées invalides et injection, instants stockés en UTC, délai configuré à 5 s |
| `ObservabilityTest` | Santé `UP` avec la base, contrat OpenAPI et Swagger servis, métriques par statut et par motif exposées à Prometheus |
| `ConcurrencyTest` | Vraies requêtes HTTP simultanées : 40 inscriptions pour 10 places (10 acceptées, numéros L-0001 à L-0010), 15 doubles clics (1 inscription), deux concours du même jour en parallèle (1 inscription) |

Test de mutation : sans les deux verrous, les trois tests de concurrence échouent.

### Organisation

| Paquet | Rôle |
|---|---|
| `controller` | `POST /api/inscriptions`, validation des entrées |
| `service` | `RegistrationService` : règles puis appel d'identification, hors transaction. `RegistrationWriter` : étapes 4 à 8 dans une transaction courte. `RegistrationRules` : âge et clôture |
| `integration.identity` | Client WebClient du service d'identification, délai total de 5 s |
| `repository`, `entity` | Spring Data JPA ; verrou du centre (`PESSIMISTIC_WRITE`) et verrou consultatif par NPI |
| `common.exception` | Erreurs RFC 7807, compteur des refus par motif |
| `config` | Client d'identification, horloge, description OpenAPI |

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
- Liste des inscrits en une requête avec jointure et pagination (INC-207).
- Clé d'idempotence sur `POST /api/inscriptions` et bouton désactivé pendant l'envoi.
- Authentification des routes d'agent, limitation de débit, alertes sur les erreurs 500 et les durées d'appel.
