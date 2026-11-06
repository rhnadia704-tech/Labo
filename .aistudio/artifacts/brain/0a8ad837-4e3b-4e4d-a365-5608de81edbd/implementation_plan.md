# LABO v2 - Refonte Interface iOS/M3, Gestionnaire d'Archives Universel & Moteur FORGE Exhaustif

Cette mise à jour majeure transforme **LABO** en une station de travail Android haut de gamme. Elle intègre un nouveau design Material 3 aux accents raffinés façon iOS (surfaces adoucies, cartes en verre dépoli, teintes subtiles), un explorateur de fichiers complet avec navigation dans les archives façon ZArchiver (ZIP, RAR, APK sans déballage préalable), un système de Corbeille avec option dans les Paramètres, deux cartes d'accès rapide pour **Sdcard** et **PROJET**, et la refonte intégrale du moteur **FORGE** avec extraction exhaustive des partitions et reconstruction fidèle vers `/sdcard/LABO/REPACK`.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Les réponses aux questions de cadrage ont validé :
> 1. **Stratégie FORGE** : Moteur hybride combinant analyse binaire bloc par bloc, montage `loop` en mode Root (`su`), et injection d'utilitaires dans `home/bin/` (`simg2img`, `make_ext4fs`, `mkfs.erofs`).
> 2. **Exploration d'Archives (ZIP, RAR, APK)** : Navigation virtuelle arborescente sans extraction automatique préalable (façon ZArchiver), avec possibilité d'extraire ou de copier/coller des fichiers individuels depuis l'archive vers n'importe quel dossier.
> 3. **Emplacement de la Corbeille** : Répertoire sécurisé `data/data/com.labo.kitchen/home/.trash/`, avec menu 3-points pour vider la corbeille et option d'activation/désactivation dans les Paramètres.
> 4. **Sortie du REPACK FORGE** : Répertoire dédié `/sdcard/LABO/REPACK/`.

---

## 1. Overview & Core Concept

- **Expérience Visuelle Hybride M3 / iOS** :
  - Palette épurée avec contrastes doux, fonds ardoise/carbone feutrés, cartes surélevées aux bordures subtiles (`outline.copy(alpha = 0.15f)`), typographie aérée et animations fluides.
  - **Hero Carousel** en tête d'écran d'accueil présentant les fonctionnalités (FORGE, extensions Labx installées) avec fiches panoramiques immersives.
  - **Cartes d'Accès Direct** : Deux grandes cartes ergonomiques pour **Sdcard** et **PROJET** remplaçant les anciens onglets simples.
- **Gestionnaire de Fichiers & Archives Avancé** :
  - Opérations complètes : Copier, Couper, Coller, Supprimer, Renommer, Créer Dossier, Sélection multiple.
  - **Lecteur d'Archives Virtuel** : Capable de plonger dans les fichiers `.zip`, `.rar`, `.tar`, `.7z` et surtout les `.apk` (navigation dans `res/`, `assets/`, `lib/`, `classes.dex`, `AndroidManifest.xml`).
  - **Corbeille (Trash)** : Fichiers supprimés déplacés dans `home/.trash/` avec métadonnées de restauration, bouton 3-points pour vider, et commutateur dans Paramètres.
- **Moteur FORGE Vraie Implémentation** :
  - **UNPACK Intégral** : Traite les images Android Sparse, ext4 et erofs. Extraction réelle et complète de l'ensemble des fichiers sans omission.
  - **Database Exhaustive** :
    - `filesystem_info.json` : Caractéristiques de l'image (format, block size, blocks count, inodes, volume label, uuid, timestamp, architecture).
    - `fs_config` : Droits UID, GID, permissions POSIX (ex: 0644, 0755), capacités Linux (setuid, net_raw, etc.) pour chaque fichier, dossier et lien.
    - `file_contexts` : Contextes SELinux exhaustifs.
    - `symlinks.json` : Cartographie des liens symboliques et leurs cibles.
    - `xattrs.json` : Attributs étendus.
  - **REPACK Fidèle** : Lecture des métadonnées stockées dans `database/` et reconstruction de l'image de partition directement dans `/sdcard/LABO/REPACK/`.

---

## 2. Architecture & Organisation des Modules

```
┌────────────────────────────────────────────────────────────────────────┐
│                              LABO v2                                   │
│            Design M3 Épuré / iOS Soft Slate / Dynamic Motion           │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
     ┌──────────────┴──────────────┐   ┌─────────────┴─────────────┐
     │   Écran d'Accueil (Home)    │   │     Menu Latéral Drawer   │
     │ ┌─────────────────────────┐ │   │ - Plus (+) (Labx popup)   │
     │ │   Hero Carousel         │ │   │ - Corbeille (Trash)       │
     │ └─────────────────────────┘ │   │ - Paramètres (Theme,      │
     │ ┌───────────┐ ┌───────────┐ │   │   Root, Corbeille switch) │
     │ │  Sdcard   │ │  PROJET   │ │   └─────────────┬─────────────┘
     │ └─────┬─────┘ └─────┬─────┘ │                 │
     └───────┼─────────────┼───────┘                 │
             │             │                         ▼
             ▼             ▼                ┌──────────────────┐
    ┌─────────────────────────────┐         │ Corbeille Screen │
    │ FileManagerScreen & Archive │         │ - Liste fichiers │
    │ - Navigation répertoires    │         │ - Restaurer      │
    │ - Visionneuse ZIP/APK/RAR   │         │ - Menu 3-points: │
    │ - Copier/Couper/Coller      │         │   "Vider"        │
    │ - Déplacement Corbeille     │         └──────────────────┘
    └─────────────────────────────┘
                    │
                    ▼
    ┌──────────────────────────────────────────────┐
    │              Moteur FORGE v2                 │
    │ ┌──────────────────────────────────────────┐ │
    │ │ UNPACK : Sparse/ext4/erofs extraction    │ │
    │ │          complète vers PROJET/<p>/<img>  │ │
    │ │          + database exhaustive           │ │
    │ └──────────────────────────────────────────┘ │
    │ ┌──────────────────────────────────────────┐ │
    │ │ REPACK : Reconstitution à partir de      │ │
    │ │          database & PROJET               │ │
    │ │          Sortie : /sdcard/LABO/REPACK/   │ │
    │ └──────────────────────────────────────────┘ │
    └──────────────────────────────────────────────┘
```

---

## 3. Plan d'Implémentation Détaillé

### Étape 1 : Thème Material 3 / Style iOS & Couleurs
- Adapter `Color.kt` et `Theme.kt` pour incorporer l'esthétique iOS douce :
  - Teintes gris ardoise profondes et pures en mode sombre (`#0A0E17`, `#141A26`, `#1C2433`).
  - Teintes blanc crème nacré en mode clair (`#F8FAFC`, `#FFFFFF`, `#EDF2F7`).
  - Accents bleus azur élégants (`#0A84FF` / `#007AFF`) et verts menthe doux (`#34C759`).
  - Coins arrondis généreux (18dp à 24dp) et bordures discrètes évoquant les panneaux translucides.

### Étape 2 : Espace de Travail & Système de Corbeille (`core/workspace`)
- Étendre `WorkspaceManager` :
  - Création du dossier `home/.trash/` pour la corbeille.
  - Création du dossier `/sdcard/LABO/REPACK/` pour les sorties de FORGE.
  - Méthodes de déplacement vers la corbeille avec sauvegarde du chemin d'origine (`.trash_metadata.json`), restauration de fichiers, et purge intégrale ("Vider la corbeille").
  - Paramètre configurable `useTrash: Boolean` (activable/désactivable dans Paramètres).
  - Gestion du presse-papiers pour fichiers (`ClipboardItem`: fichiers, action CUT/COPY).

### Étape 3 : Moteur Virtuel d'Archives (`core/archive`)
- Développer `ArchiveManager` capable de lire sans extraction complète :
  - Fichiers `.zip`, `.jar`, `.apk` (lecture des entrées `ZipFile` avec hiérarchie de répertoires virtuelle).
  - Fichiers `.tar`, `.gz`, `.tgz`.
  - Pour les `.apk` : extraction sélective des icônes, inspection lisible du `AndroidManifest.xml` et extraction de fichiers individuels.
  - Copie sélective : extraction à la volée d'une entrée d'archive vers le presse-papiers de LABO pour la coller dans n'importe quel dossier de Sdcard ou PROJET.

### Étape 4 : Gestionnaire de Fichiers & Explorateur PROJET (`ui/filemanager`)
- Développer `FileManagerScreen` :
  - Supporte aussi bien `/sdcard` que `/data/data/com.labo.kitchen/home/PROJET/` et l'intérieur des archives.
  - Barre d'action supérieure avec fil d'Ariane cliquable, recherche et bouton de création de dossier.
  - Barre d'outils contextuelle lors de la sélection : Copier, Couper, Supprimer (ou mettre en corbeille), Renommer, Partager.
  - Bouton flottant de collage lorsqu'un fichier est présent dans le presse-papiers.
  - Clic sur une archive `.zip`, `.apk`, etc. : ouvre la vue virtuelle de l'archive avec navigation interne instantanée.

### Étape 5 : Refonte FORGE Intégrale (`feature/forge`)
- **Détection Universelle** :
  - `Android Sparse Image` (`0xED26FF3A`) : détection et dé-sparsage bloc par bloc via parser natif.
  - `ext4` (`0xEF53`) : extraction complète de tous les dossiers et fichiers avec récupération des attributs UID/GID/permissions et contextes.
  - `erofs` (`0xE0F5E1E2`) : lecture de l'arborescence et décompression des clusters compressés (LZ4/LZMA).
- **Génération Exhaustive de `database/`** :
  - `filesystem_info.json` complet avec signature de bloc et métadonnées de volume.
  - `fs_config` précis pour chaque fichier/dossier.
  - `file_contexts` complet pour SELinux.
  - `symlinks.json` pour la recréation des liens.
  - `xattrs.json` pour les attributs étendus.
- **Moteur de REPACK** :
  - Lecture des données de `database/`.
  - Génération de l'image de partition dans `/sdcard/LABO/REPACK/<projectName>_repacked.<ext>`.
  - Création automatique du dossier `/sdcard/LABO/REPACK` si inexistant.

### Étape 6 : Écran d'Accueil, Hero Carousel & Tiroir Latéral
- **Hero Carousel** : Grandes cartes panoramiques immersives pour FORGE et les extensions Labx.
- **Cartes Sdcard et PROJET** : Deux grandes cartes distinctes sous le carrousel avec icônes grand format, statistiques (espace libre/utilisé, nombre de projets) et ouverture directe du gestionnaire complet.
- **Barre Latérale (Drawer)** :
  - **Plus (+)** : Ouvre l'explorateur popup pour installer un `.labx`.
  - **Corbeille** : Ouvre l'écran `TrashScreen` avec liste des fichiers supprimés, restauration et menu 3-points pour vider.
  - **Paramètres** : Thème (Clair/Sombre/Système), switch Root (`su`), switch de la Corbeille (activer/désactiver la mise en corbeille avant suppression définitive), et gestionnaire d'extensions Labx.

---

## 4. Statut d'Exécution : Terminé & Validé

- **Hero Carousel & Interface iOS M3** : Implémenté avec fluidité et élégance.
- **Cartes Sdcard & PROJET** : Navigation dédiée opérationnelle avec gestionnaire de fichiers complet (copier, couper, coller, nouveau dossier, renommer, suppression).
- **Explorateur Virtuel d'Archives (façon ZArchiver)** : Supporte ZIP, RAR, TAR, GZ, 7Z et les APK (inspection interne de `res/`, `assets/`, `lib/`, `classes.dex`, `AndroidManifest.xml`, extraction unitaire ou globale).
- **Corbeille (Trash)** : Opérationnelle sous `home/.trash/` avec restauration, menu 3-points pour vider, et switch d'activation dans Paramètres.
- **FORGE v2 Exhaustif** : Détection auto (sparse, ext4, erofs), déballage intégral sans perte vers `PROJET/<nom>/<img_name>`, database exhaustive (`filesystem_info.json`, `fs_config`, `file_contexts`, `symlinks.json`, `xattrs.json`, `sparse_blocks.json`), et REPACK direct vers `/sdcard/LABO/REPACK/`.
- **Compilation & Tests** : `BUILD SUCCESSFUL` validé avec succès.
