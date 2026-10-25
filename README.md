# THE BLACK PARASITE — mod Minecraft Forge 1.20.1

Un parasite extraterrestre noir se fixe à vous, absorbe les créatures, évolue et vous transforme en entité monstrueuse.
Forge 47.x · Java 17 · aucune dépendance (pas de GeckoLib : modèles 100 % vanilla `ModelPart`, compatible Embeddium).

> ## ⚠️ État de vérification — à lire
> **Ce projet n'a PAS été compilé ni lancé** : l'environnement de développement utilisé n'avait pas accès au dépôt
> Maven de Forge (HTTP 403), donc impossible d'exécuter Gradle / Minecraft.
> Ce qui a été vérifié : la syntaxe Java de tous les fichiers (`javac`), la cohérence entre mes propres classes, la génération
> de toutes les textures et de tous les sons. Ce qui ne l'a pas été : la compilation contre Forge, le chargement en jeu,
> le rendu des modèles, le multijoueur. **Attendez-vous à quelques corrections de noms d'API à la première compilation**
> (voir « Dépannage »).

## Installation / compilation
1. **Windows** : double-cliquez sur `COMPILER.bat` (télécharge Java 17 et Gradle tout seul). Linux/Mac : JDK 17 + Gradle 8.x puis `./compiler.sh`.
2. Le mod compilé apparaît dans `build/libs/blackparasite-0.1.0.jar`.
3. Copier le JAR dans `.minecraft/mods` d'une installation **Forge 1.20.1 (47.3.0+)**.
4. Test en développement : `gradle runClient` (ou importer le dossier dans IntelliJ / Eclipse).
5. Regénérer textures/sons si besoin : `python3 tools/generate_assets.py` (Pillow, numpy, ffmpeg).

## Jouer
* **Échantillon de parasite** (onglet Ingrédients, ou craft : 4 yeux d'araignée aux coins, 4 chair putréfiée sur les côtés, perle de l'Ender au centre) → clic droit = infection. Ou `/blackparasite infect <joueur>`.
* Touches (configurables, catégorie « The Black Parasite ») : **B** menu · **G** roue de pouvoirs · **V** capacité choisie · **X** absorber · **C** capacité suivante.
* Absorber : viser une créature à moins de 4,5 blocs **descendue sous 35 % de sa santé** (configurable), touche X. Pas de butin vanilla : biomasse + expérience + faim + énergie. Boss et villageois exclus par défaut.
* Faim : mangez de la viande crue ou absorbez. Énergie : se régénère selon la forme.
* Formes : Porteur (niv. 1) → Prédateur (15) → Fantôme noir (30) → Colosse (50) → Abomination (capacité *Transformation ultime*, niv. 80, 500 biomasse). Changer de forme coûte 10 énergie.

## Commandes (opérateurs, permission 2)
`/blackparasite stats [joueur]` · `infect <joueur>` · `unlock <joueur> <forme>` · `biomass add <joueur> <n>` · `reset <joueur>` · `checkconfig`

## Configuration
* `config/blackparasite-common.toml` : difficulté, biomasse, faim, coût d'énergie, seuil/portée d'absorption, événements, mutants, capacités activables, PvP des capacités.
* `config/blackparasite-client.toml` : HUD (activation, taille, position, opacité, chaque élément), animations avancées, sons, musique. Éditable aussi dans le menu **Configuration** et via Mods → Config.

## Fonctionnalités réellement implémentées (code écrit)
Serveur : infection, 100 niveaux (formule configurable, `8·n^1.5·difficulté`), biomasse/absorption avec table par espèce, bonus d'espèces, anti-doublon,
5 formes avec attributs réels (PV, vitesse, attaque, résistance, saut, repoussement), 11 capacités (coût, recharge, activation, validation serveur),
faim/énergie/état affaibli, 7 missions avec récompenses, journal de 8 entrées, créatures mutantes (monstres vanilla renforcés et luisants),
manifestations ambiantes configurables, sauvegarde NBT (capability, conservée à la mort/dimension/reconnexion), synchronisation (données, apparence visible des autres joueurs, animations), commandes.
Client : modèles 3D personnalisés pour Prédateur/Fantôme/Colosse/Abomination (mâchoire articulée, dents, bras à 2 segments + griffes, tentacules segmentés, pointes/cornes
apparaissant avec le niveau, yeux émissifs agrandis, 6 yeux pour l'Abomination), Porteur en calque sur le joueur (taches, yeux, griffes, tentacules dorsaux),
animations procédurales (respiration, marche, course, saut/chute, attaque, coup de tentacule, absorption, transformation, rugissement, dégâts, mort, apparition des yeux),
particules d'aura, 7 écrans (accueil avec modèle animé et œil suivant le curseur, pouvoirs, évolutions + missions, statistiques/collection/journal, configuration, roue, HUD animé),
15 icônes + 5 portraits, 8 textures de peau, 9 sons originaux générés.

## Reste à développer / limites connues
* **Compilation et tests en jeu non effectués** (voir plus haut). Multijoueur non testé.
* Mission « Découvrir un lieu contaminé » et structures/documents à trouver dans le monde : non faits.
* Les créatures mutantes sont des monstres vanilla modifiés (pas de modèle propre).
* Pas de hitbox agrandie pour Colosse/Abomination (échelle visuelle seulement : évite de coincer le joueur dans les blocs).
* Absorption instantanée (pas d'animation de canalisation longue) ; l'animation d'absorption du modèle est jouée.
* `en_us.json` ne contient que les touches/objet ; les textes d'interface sont en français.
* Équilibrage à ajuster en jeu.

## Dépannage
* *Erreur de compilation « cannot find symbol »* sur une méthode Minecraft : les mappings sont `official` 1.20.1 ; vérifier la signature dans l'IDE (les fichiers les plus exposés : `client/model/*`, `client/gui/*`).
* *Texture violette* : lancer `python3 tools/generate_assets.py` puis `gradle processResources`.
* *Pas de sons* : vérifier `assets/blackparasite/sounds/*.ogg` et `sounds.json`.
* *Le menu ne s'ouvre pas* : touche B libre ? (Options → Contrôles).
* *Rien ne se passe à l'utilisation d'une capacité* : infection requise, niveau/forme suffisants, énergie et recharge (écran Pouvoirs).
