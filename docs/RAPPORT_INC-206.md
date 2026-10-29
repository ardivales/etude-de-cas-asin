# Rapport d'incident INC-206 – Blocage des inscriptions et des convocations

| | |
|---|---|
| Date | 19/11/2026, de 09 h 09 à 09 h 40 (heure du Bénin) |
| Signalé par | Exploitation |
| Gravité | Critique : service indisponible pour tous les candidats, données abîmées |
| Statut | Cause identifiée, correction codée et testée, données réparées par `sql/reparation.sql` |

## Chronologie

| Heure | Fait (journal) |
|---|---|
| 09 h 09 min 48 | Premier appel au service d'identification au-delà de 5 s (5,2 s). |
| 09 h 11 – 09 h 12 | Les appels durent de 19 à 62 s. 11 inscriptions simultanées à Lokossa : numéros L-0297 et L-0298 attribués 11 fois, centre porté à 307 inscrits. |
| 09 h 13 – 09 h 34 | Doubles clics : 6 candidats inscrits deux fois ; numéros C-3305 à C-3313 et P-0464 en double (INC-201, 203). |
| 09 h 39 min 49 | Dernier appel lent (40,4 s). |
| 09 h 40 | Retour à la normale (appels de 1 à 3 s). |
| 21/11 08 h 00 | Analyse TMA, correction et réparation. |

## Cause racine

Le service d'identification est appelé avec un client HTTP dans sa configuration par défaut, qui n'impose aucun délai d'expiration. Quand le service a ralenti, chaque inscription et chaque convocation a attendu sa réponse, jusqu'à 62 s. L'appel se trouve en plus à l'étape 7, entre le calcul du numéro (étape 6) et l'enregistrement (étape 8), sans verrou ni contrainte en base : la lenteur a ouvert une fenêtre de course de 20 à 60 s. La règle « une lenteur ne doit jamais bloquer les candidats » n'était pas implémentée.

Causes contributives : la convocation dépend du service d'identification alors que les noms sont déjà dans `candidats` ; aucune alerte sur la durée des appels ; l'écran ne bloque pas le bouton « Valider » pendant le traitement.

## Impact chiffré

- 60 requêtes bloquées de 5 à 62 s : 41 inscriptions, 19 convocations.
- 9 numéros de table attribués plusieurs fois (30 inscriptions concernées), sur 10 au total.
- 6 inscriptions en double, sur 7 au total.
- Lokossa : 7 inscriptions au-delà de la capacité de 300.
- 5 candidats refusés « centre complet » pendant la fenêtre.
- Aucun service tiers en erreur : le service d'identification a toujours répondu 200, lentement.

## Correction

- Délai total de 5 s sur l'appel (`registration/.../integration/identity/IdentityAdapter.java`). Au-delà, l'inscription est enregistrée `en_attente_verification`.
- Appel placé avant la transaction, dans un bean distinct de l'écriture (`RegistrationService`, `RegistrationWriter`).
- Verrou par NPI et verrou du centre dans une transaction courte ; index uniques en base.
- Tests automatisés : service lent coupé au délai, 40 requêtes HTTP simultanées pour 10 places, 15 doubles clics simultanés.
- Données : `sql/reparation.sql` archive 7 doublons, réattribue 15 numéros, liste les 7 inscriptions de Lokossa hors capacité.

## Actions pour éviter la récidive

| Action | Responsable | Échéance |
|---|---|---|
| Déployer la correction et les contraintes avant la clôture du 30/11 | TMA | 25/11 |
| Traitement de reprise des inscriptions `en_attente_verification` | TMA | 28/11 |
| Convocation lue depuis `candidats`, sans appel au service | TMA | 28/11 |
| Alerte si le 95e centile de l'appel dépasse 2 s, tableau de bord des durées | Exploitation | 28/11 |
| Bouton « Valider » désactivé pendant l'envoi, clé d'idempotence côté API | TMA | 05/12 |
| Convention de niveau de service avec le service national d'identification | Direction / ASIN | 15/12 |
| Test de charge avec un service d'identification lent avant chaque campagne | TMA | campagne suivante |

## Personnes à prévenir

- Direction des concours : décision sur Lokossa, convocations à rééditer.
- Chefs de centre de Parakou, Cotonou, Lokossa et Porto-Novo : nouveaux numéros.
- Les 15 candidats dont le numéro change et les 7 candidats dont le doublon est archivé.
