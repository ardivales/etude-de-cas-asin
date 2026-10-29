# Intervention TMA – Campagne d'inscription 2026

Classement établi au 21/11/2026 08 h 00 (heure du Bénin), avant analyse détaillée.
Critères : gravité (données personnelles, intégrité, droits des candidats, disponibilité) × urgence (l'incident se reproduit-il ? une échéance approche-t-elle ?).
Échéances : clôture Douanes et Impôts le 30/11 à 23 h 59, épreuves le 12/12 (Douanes, Impôts) et le 19/12 (Trésor).

| Rang | Incident | Gravité | Urgence | Justification |
|---|---|---|---|---|
| 0 | SEC-001 (non signalé) | Critique | Immédiate | Injection SQL sur la recherche par nom depuis une IP externe : le fichier des candidats (NPI, nom, date de naissance) a pu être exfiltré ; faille toujours ouverte, obligation de notification. |
| 1 | INC-206 | Critique | Immédiate | Plateforme entière bloquée dès que le service d'identification ralentit ; se reproduira à chaque lenteur et aggrave INC-201, 202 et 203. |
| 2 | INC-201 | Haute | Haute | Numéros de table en double : deux candidats à la même place le jour de l'épreuve ; le défaut se reproduit à chaque inscription simultanée. |
| 3 | INC-203 | Haute | Haute | Inscriptions en double : données faussées, place consommée deux fois, aucune contrainte en base pour l'empêcher. |
| 4 | INC-202 | Haute | Haute | Lokossa en surcapacité : décision métier à obtenir avant les épreuves, la surcapacité peut encore augmenter. |
| 5 | INC-205 | Haute | Haute | Candidats éligibles refusés pour l'âge : atteinte au droit à concourir, corrigeable avant la clôture du 30/11. |
| 6 | INC-204 | Haute | Moyenne | Trésor clos une heure trop tôt : préjudice déjà subi (clôture passée) mais la même erreur menace la clôture du 30/11 ; décision de réouverture à demander. |
| 7 | INC-207 | Moyenne | Basse | Liste lente (> 40 s) : gêne pour les agents, sans perte de données ni impact candidat. |
| 8 | INC-208 | Aucune | Aucune | Comportement conforme : la règle interdit deux concours dont les épreuves ont lieu le même jour (Douanes et Impôts le 12/12) ; réponse au candidat, pas de correction. |

## Liens pressentis

- INC-206 → INC-203 : la page ne répond plus, le candidat clique deux fois.
- INC-206 → INC-201, INC-202 : un appel lent élargit la fenêtre entre lecture (numéro, capacité) et écriture.
- INC-201, INC-202, INC-203 : même cause, contrôles « lire puis écrire » sans verrou ni contrainte d'unicité.
- INC-204 et INC-205 : erreurs de règle de calcul (fuseau horaire, années bissextiles).
