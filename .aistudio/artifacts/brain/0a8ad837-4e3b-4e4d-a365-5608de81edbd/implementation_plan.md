# LABO - Android Kitchen & Environnement Modulaire Labx

LABO est un environnement Android hybride alliant un gestionnaire de fichiers avancé et une "Android Kitchen" modulaire et extensible. L'application fournit un espace de travail structuré sous `data/data/com.labo.kitchen/home`, une suite d'outils système (binaires natifs, permissions root et stockage), le moteur intégré **FORGE** (analyse, unpack et repack d'images ext4/erofs avec répertoire database), et une plateforme d'extensions **Labx** pour enrichir dynamiquement l'écosystème.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Les réponses aux clarifications ont validé les choix clés :
> - **Package ID & Application ID** : `com.labo.kitchen`
> - **Format Labx** : Archive structurée avec `manifest.json`, actions, UI déclarative et binaires/scripts optionnels pouvant réutiliser la boîte à outils interne de LABO (`home/bin`).
> - **Moteur FORGE** : Support initial ciblé sur la détection automatique et l'extraction `ext4` et `erofs` avec génération complète du dossier `database` pour un repack fidèle.

---

## 1. Overview & Core Concept

- **Rôle principal** : Offrir aux développeurs, moddeurs et bidouilleurs Android une plateforme unifiée pour déballer/repackager des partitions système (`.img`), explorer le stockage local (`/sdcard`) et les projets internes, et exécuter des outils avancés via des extensions `.labx`.
- **Espace de travail autonome** : Tout l'environnement opère dans `/data/data/com.labo.kitchen/home` :
  - `home/bin` : Outils et binaires de base (outils d'extraction ext4, erofs, utilitaires d'analyse).
  - `home/extensions` : Extensions `.labx` installées et validées.
  - `home/PROJET` : Répertoire maître des projets créés par FORGE et les modules.
  - `home/tmp` : Zone tampon d'extraction et de travail temporaire.
- **Uniformité stricte** : Chaque extension Labx et chaque fonctionnalité interne adopte les composants de design de LABO (thème M3 dynamique, boutons uniformes, disposition verticale ou grille, et modal d'exploration de fichiers universel).

---

## 2. User Experience & Visual Design

### Thème et Direction Artistique
- **Design System** : Material Design 3 (M3) avec support complet des modes **Clair**, **Sombre** et **Système** (sauvegardé en mémoire / DataStore).
- **Palette** : Style "Industrial Cyber Kitchen" élégant, teintes sombres ardoise/carbone avec accents bleu cyan/indigo électrique, surfaces surélevées avec coins arrondis réguliers (16dp à 24dp).
- **Typographie & Hiérarchie** : En-têtes nets, libellés concis et compteurs techniques lisibles.

### Écrans & Navigation Principaux
1. **Écran d'Accueil (Home)** :
   - **En-tête** : Titre de l'espace de travail avec indicateurs d'état (Statut Root, Permissions Stockage, Espace disque disponible).
   - **Carrousel horizontal des fonctionnalités** : Cartes panoramiques fluides mettant en avant **FORGE** et les extensions Labx installées.
   - **Onglets / Sections inférieures** :
     - **Sdcard** : Explorateur fluide de la mémoire interne de l'appareil (`/sdcard` / stockage externe).
     - **PROJET** : Navigateur des projets déballés sous `home/PROJET`, avec statut de chaque projet (format détecté, taille, présence du `database`).
2. **Tiroir Latéral (Navigation Drawer)** :
   - **Plus (+)** : Déclenche immédiatement le sélecteur universel en popup pour naviguer dans `/sdcard` et sélectionner un fichier `.labx` à installer.
   - **Paramètres** :
     - *Thème d'affichage* : Sélecteur Clair / Sombre / Système.
     - *Privilèges Système* : Interrupteur Root (détection `su`) et gestion des permissions de stockage (`READ/WRITE/MANAGE_EXTERNAL_STORAGE`).
     - *Gestionnaire d'extensions Labx* : Liste complète des extensions installées avec version, auteur, description, statut d'intégration et bouton de désinstallation rapide.
3. **Composant Fondamental : Explorateur Popup Universel** :
   - Modal bottom sheet ou boîte de dialogue plein écran animée.
   - Fil d'Ariane (Breadcrumbs) dynamique permettant de remonter rapidement les répertoires.
   - Filtre par extension (ex: `.img`, `.labx`, ou tous fichiers).
   - Utilisé systématiquement par toutes les fonctionnalités (UNPACK de FORGE, ajout de Labx, imports).
4. **Moteur FORGE** :
   - En-tête avec sélecteur de mode ergonomique : **UNPACK** et **REPACK**.
   - **Mode UNPACK** :
     - Champ de sélection d'image avec bouton uniforme ouvrant l'explorateur popup.
     - Champ de nom de projet (généré automatiquement d'après l'image).
     - Bouton d'action uniforme « Déballer » avec jauge de progression et log dynamique.
     - Déballage automatique vers `home/PROJET/<nom du projet>/<nom de l'img>` et création de `home/PROJET/<nom du projet>/database`.
   - **Mode REPACK** :
     - Menu déroulant / sélecteur interactif des projets disponibles dans `home/PROJET`.
     - Fiche récapitulative du projet (type de système de fichiers original ext4/erofs, taille estimée, date d'extraction).
     - Bouton uniforme « Repacker » pour reconstruire l'image selon les métadonnées de `database`.

---

## 3. Architecture Technique & Modularité

```
┌─────────────────────────────────────────────────────────────────┐
│                          LABO Application                       │
│  (Theme M3 Clair/Sombre/Système + Shell Root + Workspace Hub)   │
└───────────────┬─────────────────────────────────┬───────────────┘
                │                                 │
     ┌──────────┴──────────┐           ┌──────────┴──────────┐
     │ Composants Communs  │           │   Workspace Engine  │
     │ - Universal Popup   │           │ - data/data/.../home│
     │ - Uniform Buttons   │           │   ├── bin/          │
     │ - Navigation & Hero │           │   ├── extensions/   │
     └──────────┬──────────┘           │   ├── PROJET/       │
                │                      │   └── tmp/          │
                │                      └──────────┬──────────┘
                │                                 │
     ┌──────────┴──────────┐           ┌──────────┴──────────┐
     │    Module FORGE     │           │     Moteur LABX     │
     │ - Auto-detect fs    │           │ - Parser .labx      │
     │ - Unpack ext4/erofs │           │ - Manifest Engine   │
     │ - Database builder  │           │ - Binary Loader     │
     │ - Repack generator  │           │ - Extension Manager │
     └─────────────────────┘           └─────────────────────┘
```

### Structure des Données et Espaces de Stockage
1. **Initialisation de l'Espace de Travail** :
   - Détection et création systématique des répertoires au démarrage :
     - `/data/data/com.labo.kitchen/home/bin`
     - `/data/data/com.labo.kitchen/home/extensions`
     - `/data/data/com.labo.kitchen/home/PROJET`
     - `/data/data/com.labo.kitchen/home/tmp`
2. **Structure d'un Projet FORGE** :
   - `PROJET/<nom_projet>/<nom_img>/` : Arborescence complète extraite de la partition (fichiers système, répertoires).
   - `PROJET/<nom_projet>/database/` :
     - `filesystem_info.json` : Format (`ext4`, `erofs`), taille de bloc, taille totale de l'image, label de volume.
     - `file_contexts` : Données SELinux associées aux fichiers déballés.
     - `fs_config` : Droits UID, GID, permissions POSIX et capacités pour chaque nœud.
3. **Format et Contrat de Spécification des Extensions LABX** :
   - Fichier archive `.labx` (compression standard contenant l'arbre d'exécution) :
     - `manifest.json` : Métadonnées, identifiant unique, version, titre, type de disposition (`vertical` ou `grid`), actions déclarées, binaires requis/embarqués.
     - `bin/` (optionnel) : Fichiers exécutables spécifiques à l'architecture (armeabi-v7a, arm64-v8a, x86_64).
     - `assets/` (optionnel) : Icônes, scripts shell, modèles.
     - `README.md` : Guide d'utilisation de l'extension.
   - Système de documentation dédié : création du fichier `LABX_SPECIFICATION.md` à la racine pour guider tout développeur d'extension externe.
4. **CI/CD GitHub Actions** :
   - Création du workflow `.github/workflows/android.yml` configuré pour compiler automatiquement l'application LABO en APK prêt au déploiement via GitHub Actions.

---

## 4. Plan de Développement par Étapes

1. **Configuration du Projet & Manifeste** :
   - Définir `applicationId = "com.labo.kitchen"` et `namespace = "com.labo.kitchen"` dans `app/build.gradle.kts`.
   - Ajuster `res/values/strings.xml` et `metadata.json` vers le nom officiel **LABO**.
   - Déclarer les permissions dans `AndroidManifest.xml` (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `INTERNET`).
2. **Couche Système & Workspace (`core/workspace`)** :
   - Implémenter `WorkspaceManager` gérant l'arborescence `data/data/com.labo.kitchen/home/...`.
   - Implémenter `ShellExecutor` gérant l'exécution de commandes ordinaires et root (`su`).
   - Implémenter `ImageAnalyzer` capable d'identifier les signatures magiques `ext4` (`0xEF53`) et `erofs` (`0xE0F5E1E2`).
3. **Composants d'Interface Réutilisables & Thème (`core/ui`)** :
   - Configuration du thème Material 3 avec gestion du mode clair/sombre/système.
   - Création de `LaboFilePickerModal` (l'explorateur popup universel avec navigation de dossiers, filtres d'extension, et breadcrumb).
   - Création de `LaboButton` et `LaboCard` pour garantir une interface 100% cohérente dans toute l'application.
4. **Module FORGE (`feature/forge`)** :
   - Logique d'unpack : lecture de l'image, analyse du format, extraction vers `PROJET/<nom>/<img_name>`, création du dossier `database` avec `filesystem_info.json`, `fs_config`, et `file_contexts`.
   - Logique de repack : sélection du projet, reconstruction de l'image de destination en respectant le format initial et les métadonnées.
   - Interface FORGE dédiée (layout uniforme, champ de sélection avec popup, bouton déballer, sélecteur de repack).
5. **Moteur d'Extensions LABX (`feature/labx`)** :
   - `LabxInstaller` : décompression sécurisée, validation du `manifest.json`, installation vers `home/extensions/<id>`.
   - `LabxManager` : listing des extensions, extraction des binaires avec attribution des droits exécutables (`chmod 755`), désinstallation propre.
   - Rendu dynamique de l'interface des Labx selon le mode choisi (`vertical` ou `grid`).
6. **Écran d'Accueil & Drawer Principal (`app/ui`)** :
   - Carrousel horizontal des fonctionnalités.
   - Onglets dynamiques pour `/sdcard` et `PROJET`.
   - Tiroir latéral avec gestionnaire de thème, statut Root/Stockage, gestionnaire Labx et raccourci (+) avec popup direct.
7. **Documentation & CI/CD** :
   - Rédaction du document complet `LABX_SPECIFICATION.md`.
   - Configuration du workflow `.github/workflows/android.yml`.
   - Compilation et validation du build réussies (build APK et tests unitaires/Robolectric validés).

---

## 5. Statut d'Exécution : Terminé & Validé

- **Compilation** : Validée avec succès (`compile_applet`).
- **Tests** : 100% passés avec succès (`gradle :app:testDebugUnitTest`).
- **Icône applicative personnalisée** : Générée et configurée pour tous les paliers de densité (adaptive + PNG).
- **GitHub Ready** : Workflow CI GitHub Actions créé sous `.github/workflows/android.yml` et documentation `LABX_SPECIFICATION.md` prête.
