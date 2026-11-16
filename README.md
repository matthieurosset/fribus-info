# FriBus Info

Prochains bus urbains de Fribourg, hors ligne, avec retards en temps réel.

L'application affiche les prochains départs des arrêts proches de vous, une recherche d'arrêts et vos favoris. Sans réseau, elle utilise l'horaire théorique ; avec réseau, elle ajoute les retards en temps réel de l'arrêt affiché. Pas de compte, pas de publicité, pas de traceurs.

## Installer

Téléchargez l'APK le plus récent depuis les [Releases](../../releases) et ouvrez-le sur votre téléphone Android (autorisez l'installation depuis cette source si Android le demande).

## Horaires

Une base d'horaires est reconstruite le mercredi et le samedi par GitHub Actions (`.github/workflows/timetable.yml`) à partir du GTFS suisse et publiée dans la release `timetable` :

- [version.json](https://github.com/matthieurosset/fribus-info/releases/download/timetable/version.json)
- [timetable.db](https://github.com/matthieurosset/fribus-info/releases/download/timetable/timetable.db)

## Sources de données

- Horaires : [opentransportdata.swiss](https://opentransportdata.swiss) (GTFS)
- Temps réel : [transport.opendata.ch](https://transport.opendata.ch)

## Avertissement

Projet indépendant, non affilié à aucune compagnie de transport.
