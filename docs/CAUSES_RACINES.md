# Causes racines et liens entre incidents

Sources : dossier technique, `analysis/analyze_log.py` sur le journal, export de la base au 21/11/2026 08 h 00. Heures du Bénin.

## Vue d'ensemble

```
                 Client HTTP sans délai (config par défaut)
                                │
     Service d'identification lent le 19/11 de 09 h 09 à 09 h 39
                                │
           ┌────────────────────┴─────────────────────┐
           ▼                                          ▼
   INC-206 pages bloquées                  Étape 7 appelée APRÈS l'étape 6,
   (inscriptions + convocations)           fenêtre de 20 à 60 s entre lecture
           │                                et écriture, sans verrou ni contrainte
           ▼                                          │
   INC-203 double clic ─────────────────┐   ┌─────────┼──────────┐
                                        ▼   ▼         ▼          ▼
                                     INC-203       INC-201    INC-202
                                     doublons      numéros    Lokossa 307/300
                                                   en double
```

INC-201, 202 et 203 sont un seul défaut de conception, « lire puis écrire » sans verrou ni contrainte d'unicité, révélé par INC-206. Neuf des dix numéros en double et six des sept doublons datent de la demi-heure de lenteur. Le dixième cas (N-0574, 18/11 16 h 42, deux requêtes à la même milliseconde) prouve que la course existe aussi sans lenteur.

## Par incident

| Incident | Faits établis | Cause racine | Correction |
|---|---|---|---|
| INC-206 | 60 requêtes touchées (41 inscriptions, 19 convocations), appels jusqu'à 61,7 s | Client HTTP dans sa configuration par défaut, donc sans délai d'expiration ; chaque requête attend le service et occupe un fil du serveur. La convocation rappelle aussi le service d'identification. | Délai total de 5 s, statut `en_attente_verification`. Évolution proposée : convocation lue depuis `candidats`. |
| INC-201 | 10 numéros attribués plusieurs fois (P-0464, L-0297 ×5, L-0298 ×6, C-3305…) | Étape 6 « plus grand numéro + 1 » lu hors verrou ; l'étape 7 lente entre le calcul et l'écriture ; pas d'unicité `(centre, numéro)`. | Compteur `centres.dernier_numero` incrémenté sous `SELECT … FOR UPDATE` ; index unique. |
| INC-202 | Lokossa : 307 inscriptions pour 300 places, les 11 lignes en cause créées entre 09 h 11 et 09 h 12 | Étape 5 compte hors verrou : 11 requêtes ont lu moins de 300 inscrits avant que les autres n'écrivent. | Comptage sous le verrou du centre. |
| INC-203 | 7 candidats inscrits deux fois au même concours | Étape 4 hors verrou, pas d'unicité `(npi, concours)`, et l'utilisateur relance parce que la page ne répond pas (INC-206). | Verrou consultatif par NPI, index unique, réponse en moins de 5 s. |
| INC-204 | 147 refus « Inscriptions closes » entre 22 h 59 et 23 h 58, 100 candidats | `cloture` est en UTC, le serveur compare avec son heure locale (UTC+1) : la clôture tombe une heure trop tôt. | Comparaison d'instants avec fuseau explicite (`RegistrationRules.isRegistrationOpen`). |
| INC-205 | 36 candidats refusés à tort, nés entre le 01/01/1991 et le 08/01/1992 | `jours / 365` ignore les années bissextiles et mesure un âge exact au lieu de l'âge atteint dans l'année : tout candidat de plus de 12 775 jours (35 × 365) est refusé, soit toute naissance avant le 09/01/1992, alors que la règle accepte jusqu'au 01/01/1991. Des candidats de 34 ans sont refusés. | Âge au 31 décembre = année du concours − année de naissance. |
| INC-207 | Cotonou : moyenne 25 s, maximum 46 s pour 2 408 lignes ; les autres centres de 1 à 8 s | Requête N+1 (une lecture de `candidats` par inscrit) et aucun index hors clés primaires : chaque lecture parcourt 19 303 candidats. | Une seule requête avec jointure, index `(centre_id, concours_id)` et unique `candidats(npi)`, pagination. |
| INC-208 | Aucune inscription en base à deux concours du même jour | Comportement conforme : Douanes et Impôts ont lieu le 12/12. | Aucune ; réponse au candidat. |
| SEC-001 | Injection SQL réussie depuis 102.64.217.33, 18 535 lignes extraites | Paramètre `nom` concaténé dans la requête SQL. | Voir `SECURITE.md`. |

## Liens à retenir

- Une correction d'INC-206 seule ne suffit pas : elle réduit la fenêtre de course mais ne la ferme pas (cas N-0574).
- Les contraintes d'unicité ne règlent pas la capacité : elle exige le verrou du centre.
- INC-204 et INC-205 menacent encore Douanes et Impôts (clôture le 30/11) : corriger avant cette date.
