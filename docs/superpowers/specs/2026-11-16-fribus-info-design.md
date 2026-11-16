# FriBus Info — document de conception

Date : 2026-11-16 · Statut : brouillon à valider

## 1. Intention

Matthieu utilisait l'app Android « TPF Info » (v2.9.10, Netcetera/Wemlin) pour voir les prochains
bus à Fribourg. Elle est morte : ses horaires embarqués datent de 2021 et ses deux serveurs
(`data.wemlin.com` pour le temps réel, `updates.wemlin.com/wus/manifest.xml` pour les paquets
d'horaires) ne répondent plus. L'app officielle actuelle est jugée « une catastrophe ergonomique ».

FriBus Info reprend **le fonctionnement et le look & feel de l'ancienne app**, simplifiés aux usages
de base, avec des données publiques à jour. Ce n'est pas un nouveau concept : en cas de doute,
faire comme l'ancienne app (captures dans `docs/reference/ancienne-app/`).

Distribution : APK installée à la main, transmise à qui la veut. **Pas de Play Store.**

## 2. Objectifs

1. À l'ouverture : les arrêts à proximité, avec les numéros des lignes qui y passent.
2. Appui sur un arrêt : ses prochains départs (ligne, destination, minutes restantes).
3. Appui sur un départ : le trajet de cette course (chaîne d'arrêts).
4. Recherche d'un arrêt par nom ; favoris.
5. Retard en temps réel quand le réseau est là ; sinon, l'horaire théorique, c'est tout.
6. Horaires toujours à jour sans intervention de Matthieu.

## 3. Non-objectifs

- Calcul d'itinéraire, plans du réseau, billets, informations trafic, réglages, page « À propos »,
  historique « Récents ».
- Lignes régionales qui traversent l'agglo (groupe D ci-dessous), trains.
- Autre réseau que l'agglo de Fribourg.

## 4. Anti-objectifs (échec même si « ça marche »)

- Une ergonomie différente de l'ancienne app, ou des écrans/concepts en plus.
- Le nom, le logo ou la marque TPF dans l'app (risque de retrait). Les couleurs des lignes restent.
- Un écran vide ou bloqué quand il n'y a pas de réseau.
- Dépendre du NAS de Matthieu (accessible seulement via Tailscale, donc pas pour les autres).
- Une clé d'API personnelle embarquée dans l'APK.

## 5. Périmètre des lignes

Source : horaire GTFS officiel 2026 (opentransportdata.swiss), agences TPF. Lignes retenues :

| Groupe | Lignes |
|---|---|
| A — urbaines | 1, 2, 3, 4, 5, 6, 8, 9, funiculaire Neuveville–St-Pierre (GTFS `route_short_name` 2030, type FUN) |
| B — locales de l'agglo élargie | 11 (gare–Matran–Rosé), 12 et 13 (Düdingen), 542 (Belfaux–Corminboeuf–La Corbaz) |
| C — nuit urbaines | N1, N2, N3, N5 |

Exclues : groupe D (120–130, 181–182, 231–245, 336–340, 361, 470, 544–545, N11–N25) et les
trains (S20, S21, RE2, RE3, R). Notes : la ligne 7 et la N4 n'existent plus en 2026.

La liste est **une liste blanche de numéros** dans la configuration du pipeline de données
(§8) : si l'horaire 2027 (dès le 13.12.2026) ajoute ou renomme des lignes, on modifie cette liste.
Un arrêt n'apparaît dans l'app que s'il est desservi par au moins une ligne retenue, et seules
ces lignes y sont affichées (badges et départs).

## 6. Écrans (reprendre l'ancienne app)

Barre du haut bordeaux comme l'ancienne app, nom « FriBus Info » à la place du logo TPF.
Langue de l'interface : français.

**6.1 Arrêts à proximité** (écran d'accueil, capture `1-arrets-proches.png`)
Liste triée par distance : icône de repère + distance (« 350 m ») à gauche, nom de l'arrêt en
gras, commune en dessous, badges colorés des lignes à droite (deux rangées max, puis « … »).
Bouton loupe (recherche) et bouton de relocalisation dans la barre.
Noms GTFS du type `Fribourg, Tilleul/Cathédrale` : partie après la virgule = nom, avant = commune.
*Choix de Claude* : sans position (permission refusée, GPS coupé) ou sans arrêt dans le rayon
(hors agglo), l'écran affiche un court texte centré, comme l'ancienne app (« Position
indisponible » / « Aucun arrêt à proximité »). La loupe et le menu restent utilisables.

**6.2 Départs d'un arrêt** (`2-departs-arret.png`, `3-menu-arret.png`)
Une ligne par départ : badge de ligne 35 × 35 dp (libellé de 3 caractères ou plus : 16 sp, comprimé à
70 %), destination en gras, commune en dessous, bouton bleu (dégradé #0085B9 → #008DC5, 50 dp) à
droite. Minutes = différence de minutes d'horloge entre maintenant et l'heure réelle (10:04:30 →
10:05 = « 1' »), « 1h05' » dès 60 min, jamais d'heure absolue automatique. Retard (temps réel,
§7) : en mode minutes, minutes en jaune #FFFF00 si le retard arrondi vers zéro est non nul ; en
mode heure, le bouton montre l'heure prévue (jaune si retard) et une pastille bleue à gauche
affiche « +N' » (jaune), « +0' » (blanc) ou « -N' » (jaune, bus en avance). « 0' » : le fond du
bouton clignote (alpha 1 ↔ 0,5, 1 s). Départ passé (temps réel seulement, une minute de grâce) :
bouton gris #D4D4D4 → #DDDDDD, destination #8F8F8F ; la liste défile pour mettre le premier
départ non passé en haut, sauf si l'utilisateur a fait défiler. Ordre : heure réelle (secondes)
puis destination ; 30 départs max après tri, fenêtre de 120 min. Rafraîchi à chaque minute
pile. Liste vide : « Pas de départ ces 2 prochaines heures » (gras, #8F8F8F). Menu ⋮ : *Ajouter
aux favoris* / *Supprimer des favoris*, *Heure de départ absolue* / *Heure de départ en minutes*.
Pas de bandeau rouge « données hors ligne », pas de bouton billets.

**6.3 Trajet d'une course** (`4-trajet-course.png`)
Titre = badge + destination. Liste verticale des arrêts reliés par une ligne (« chaîne de
perles »), badges des lignes retenues à chaque arrêt, arrêt de départ surligné, terminus marqué.
Minutes de chaque arrêt = minutes depuis maintenant (arrêts suivants décalés du retard du
départ, arrêts précédents non décalés). Avant l'arrêt choisi : trait #DDDDDD, anneau #AAAAAA,
texte #8F8F8F ; après : trait #999696, anneau #666666 ; anneau de l'arrêt choisi #891313 ; pas de
trait au-dessus du premier arrêt ni sous le terminus ; terminus : anneau #423E3E et point #891313.
Tant que le terminus n'est pas visible, une copie est épinglée en bas de l'écran. La liste
s'ouvre sur l'arrêt choisi, le précédent dépassant en haut.

**6.4 Recherche** (`5-recherche.png`)
Champ dans la barre, résultats au fur et à mesure, même présentation que 6.1 sans distance.

**6.5 Menu latéral** (`6-menu-lateral.png`, simplifié)
Trois entrées seulement : *À proximité*, *Rechercher*, *Favoris*. Favoris = liste comme 6.1.

## 7. Temps réel

- Les départs s'affichent immédiatement depuis l'horaire local.
- En parallèle, l'app appelle `https://transport.opendata.ch/v1/stationboard` pour **l'arrêt
  affiché uniquement** (gratuit, sans clé). Identifiant : le numéro d'arrêt suisse (DIDOK/UIC, ex.
  `8587255` pour Tilleul/Cathédrale), disponible dans les `stops.txt` du GTFS.
- Rapprochement d'un départ API avec un départ local : même numéro de ligne, même heure prévue,
  même destination. Si un retard est connu (signé, à la seconde, avance comprise), l'affichage
  suit l'ancienne app (§6.2) : minutes jaunes ou pastille bleue « +N' » ; plus d'étiquette jaune.
- Réponse gardée ~30 s, rafraîchie tant que l'écran est ouvert et visible (jamais en
  arrière-plan). L'écran de trajet applique le retard connu de la course à ses arrêts suivants.
- Pas de réseau ou API en erreur : horaire théorique, sans message d'erreur.
- *Choix de Claude* : si l'API répond « trop de requêtes » (limite par adresse IP, donc par
  téléphone), l'app espace ses appels et reste sur l'horaire théorique en attendant. Pas de
  proxy ni de clé : si l'API devenait inutilisable, l'app resterait simplement en horaire seul.

## 8. Données d'horaires

**Pipeline (GitHub Actions, hebdomadaire + manuel)**
1. Télécharger le GTFS suisse (`https://data.opentransportdata.swiss/de/dataset/timetable-2026-gtfs2020/permalink`,
   ~290 Mo zippé, `stop_times.txt` ~3,7 Go décompressé — traiter en flux). En décembre, prendre
   aussi le jeu de l'année suivante (`timetable-2027-gtfs2020`) pour couvrir le changement d'horaire.
2. Garder les agences TPF (`agency_id` 53, 834, 3004) et les lignes de la liste blanche (§5).
3. Produire une base SQLite compacte : arrêts (nom, commune, coordonnées, numéro DIDOK), lignes
   (numéro, couleurs), courses, passages, calendriers (`calendar` + `calendar_dates`).
4. Publier la base comme asset d'une release GitHub, avec un petit fichier de version.

**Dans l'app** : une base est embarquée dans l'APK (premier lancement hors ligne possible). Au
lancement, au plus une fois par jour et si le réseau est là, l'app vérifie la version publiée et
télécharge la nouvelle base en arrière-plan. Échec = on garde l'ancienne, sans message.

**Couleurs des lignes** : le GTFS n'en fournit pas. Table fixe dans le pipeline, reprise de
l'ancienne app (base 2021) :

| Ligne | Fond | Texte |
|---|---|---|
| 1 | #8CA029 | blanc |
| 2 | #FF2440 | blanc |
| 3 | #FED100 | noir |
| 4 | #F74E21 | noir |
| 5 | #0089C6 | #F4E500 |
| 6 | #503A87 | blanc |
| 8 | #E43689 | noir |
| 9 | #A83889 | blanc |
| 11 | #00A321 | blanc |
| 12 | #B41A2F | blanc |
| 13 | absente en 2021 → même que 12 (à confirmer) | blanc |
| 542 | #B41A2F | blanc |
| N1–N5 | #0091CF | blanc |
| Funiculaire | blanc, bordure noire, libellé « FUN » | noir |

## 9. Choix techniques (décidés par Claude)

- Android natif, Kotlin + Jetpack Compose, minSdk 26.
- Dépôt `C:\git\fribus-info`, **dépôt GitHub public** de Matthieu (décidé par Matthieu) : la base
  d'horaires et l'APK se téléchargent sans identifiant depuis les releases. APK signée construite par GitHub Actions (keystore
  en secret du dépôt, copie de sauvegarde chez Matthieu) et attachée aux releases.
- Pas de mise à jour automatique de l'app elle-même : nouvelle version = nouvelle APK sur la page
  des releases.
- Aucune statistique, publicité ni traceur (l'ancienne app embarquait Flurry).
- Mention de la source des données (opentransportdata.swiss, transport.opendata.ch) : une ligne
  discrète en bas du menu latéral et dans la description des releases, si les conditions
  d'utilisation l'exigent (à vérifier pendant la construction).

## 10. Laissé au builder

Structure du code, schéma SQLite exact, bibliothèques, rayon et nombre d'arrêts « à proximité »
(l'ancienne app montrait ~11 arrêts jusqu'à ~450 m), fenêtre de départs affichée, regroupement des
quais d'un même arrêt (afficher un seul arrêt par nom, comme l'ancienne app), tests.

## 11. Recette finale (demandée par Matthieu)

Dernière étape avant de rendre le travail : faire tourner **l'ancienne APK** (émulateur, horloge
reculée à juin 2021 pour qu'elle accepte son horaire embarqué) et **FriBus Info** côte à côte,
écran par écran (§6.1 à §6.5) et au même endroit (ex. position Neuveville-Motta, arrêt
Tilleul-Cathédrale). Capturer chaque paire et lister chaque différence. Chaque écart doit être
soit voulu par ce document (§3, §4, §6), soit corrigé. Rendre le tableau des paires et la
conclusion « aucun écart non souhaité ».

## 12. À vérifier pendant la construction

- Limites d'usage de transport.opendata.ch (nombre de requêtes) et comportement si dépassé.
- Qualité du rapprochement temps réel ↔ horaire local sur quelques arrêts réels.
- Taille de la base produite (cible : quelques Mo).
