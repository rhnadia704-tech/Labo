# Refonte Fondamentale de l'UNPACK et du REPACK Fidèle (Moteur FORGE de LABO)

Ce plan détaille la restructuration complète du moteur FORGE afin que l'UNPACK crée une représentation fidèle de l'image système d'origine dans `PROJET/`, et que le REPACK produise une image système conforme et immédiatement reconnue par tous les outils (ZArchiver, 7-Zip, noyau Linux, récupération Android).

## Décisions Clés & Réponses aux Questions

> [!IMPORTANT]
> **Synthèse des choix confirmés par l'utilisateur :**
> - **Type d'image source** : Image système GSI (brute ou sparse de type `system.img`).
> - **Structure attendue** : Détection automatique de la structure de l'image originale lors de l'UNPACK et reconstruction à l'identique lors du REPACK (le reflet exact de l'image originale).
> - **Stratégie d'exécution** : Combinaison hiérarchisée des méthodes 1 (outils natifs `mke2fs` / `e2fsdroid` / `debugfs` dans `home/bin`), 2 (moteur 100% autonome pur Kotlin fiabilisé), et 3 (montage loop direct avec privilèges Root si disponibles).

---

## 1. Vue d'Ensemble & Concept

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CYCLE DE VIE FORGE LABO                         │
└────────────────────────────────────────────────────────────────────────┘

 [ Image Source (.img) ] ── (GSI / Ext4 / EroFS / Sparse)
            │
            ▼
 ┌──────────────────────────────────────────────────────────────────────┐
 │                      PHASE 1 : UNPACK ENRICHI                        │
 │  1. Analyse & Dé-sparsage automatique (SparseConverter)             │
 │  2. Détection du container et du système de fichiers                 │
 │  3. Extraction Hiérarchisée :                                        │
 │     • Priorité 1 : Montage Loop natif si Root actif                  │
 │     • Priorité 2 : debugfs / outils natifs dans home/bin             │
 │     • Priorité 3 : Ext4Extractor pur Kotlin (extents + indirects)    │
 │  4. Documentation exhaustive dans database/ (fs_config, contexts)    │
 └──────────────────────────────────────────────────────────────────────┘
            │
            ▼
 [ PROJET/<nom_projet>/ ] ── Arborescence fidèle (system/, bin/, etc.)
 [ PROJET/<nom_projet>/database/ ] ── Métadonnées, permissions, xattrs
            │
            ▼ (Modifications éventuelles par l'utilisateur dans LABO)
            │
 ┌──────────────────────────────────────────────────────────────────────┐
 │                      PHASE 2 : REPACK MIROIR                         │
 │  1. Lecture de la database (filesystem_info, fs_config, contexts)    │
 │  2. Reconstruction Conforme :                                        │
 │     • Priorité 1 : mke2fs & e2fsdroid si disponibles                 │
 │     • Priorité 2 : Ext4Builder multi-groupes (blocks_per_group=32768)│
 │  3. Re-sparsage optionnel si l'original était un Sparse Android      │
 └──────────────────────────────────────────────────────────────────────┘
            │
            ▼
 [ /sdcard/LABO/REPACK/<nom_projet>_repacked_ext4.img ]
 (Reflet fidèle de l'original, reconnu à 100% par ZArchiver et recovery)
```

---

## 2. Analyse Technique du Problème Actuel

1. **Origine des fichiers APEX dans la capture ZArchiver** :
   - Dans Android 10 à 15, les partitions `system` contiennent des fichiers `.apex` (archives ZIP internes).
   - Lorsque le format EXT4 généré par le REPACK n'était pas strictement conforme aux spécifications standard (notamment `blocks_per_group` qui dépassait 65 536), le parseur EXT4 de ZArchiver rejetait le superbloc et se rabattait sur le scan de signatures ZIP internes, ouvrant l'APEX trouvé au lieu du système de fichiers complet.
2. **Structure de l'UNPACK** :
   - L'UNPACK doit extraire l'arborescence réelle sans créer de sous-dossiers superflus ni masquer la racine.
   - Si l'image source contient directement `system/`, `bin/`, `etc/`, ou des symlinks, ils doivent être placés fidèlement dans le dossier de travail du projet.

---

## 3. Architecture Technique Détaillée

### A. Phase UNPACK (Extraction Complète sans Perte)

1. **Pré-traitement Sparse** :
   - Détection du magic `0xED26FF3A`.
   - Si Sparse : conversion préalable avec `SparseConverter` en image brute temporaire de taille garantie.
2. **Extraction Tri-Niveaux** :
   - **Niveau 1 (Root)** : Si le mode Root est activé et que `su` est disponible, tentative de montage loop en lecture seule sur un point temporaire et copie intégrale avec `cp -a`.
   - **Niveau 2 (Binaire e2fsprogs)** : Utilisation de `debugfs -R 'rdump / <cible>' <imgFile>` si présent dans `home/bin/` ou `/system/bin/`.
   - **Niveau 3 (Ext4Extractor Autonome)** :
     - Lecture du Superblock (offset 1024, magic `0xEF53`).
     - Prise en charge des inodes avec extents (`0xF30A`) et des inodes à blocs directs/indirects (`i_block[0..11]`, `i_block[12]`).
     - Parcours complet des répertoires indexés HTree (`EXT4_INDEX_FL`).
     - Sauvegarde des liens symboliques et fichiers spéciaux.
3. **Génération de la Database Miroir** :
   - `filesystem_info.json` : architecture, taille de bloc (4096), volume label, original size, format exact.
   - `fs_config` : UID, GID, octal mode, capabilities de chaque fichier et dossier.
   - `file_contexts` : contextes SELinux exacts (`u:object_r:system_file:s0`, etc.).
   - `symlinks.json` : correspondance source $\rightarrow$ cible pour chaque lien symbolique.

### B. Phase REPACK (Génération Conforme & Miroir)

1. **Moteur Natif AOSP (Priorité 1)** :
   - Détection de `mke2fs` dans `home/bin/` ou `/system/bin/`.
   - Commande standard : `mke2fs -t ext4 -b 4096 -L <volumeLabel> -d <dossier_source> <outputFile>`.
   - Application des permissions via `fs_config`.
2. **Moteur Pur Kotlin Ext4Builder Multi-Groupes (Priorité 2)** :
   - `BLOCKS_PER_GROUP = 32768` (4096 $\times$ 8 bits par bloc de bitmap) impératif pour que ZArchiver et 7-Zip valident le système de fichiers.
   - `INODES_PER_GROUP = 8192`.
   - Table complète des descripteurs de groupes (GDT) pour chaque groupe de blocs.
   - Initialisation des bitmaps de blocs et d'inodes.
   - Reconstitution fidèle de l'Inode 2 (Root) et de tous les répertoires et fichiers enfants.
3. **Sortie & Rangement** :
   - Enregistrement dans `/sdcard/LABO/REPACK/<nom_projet>_repacked_ext4.img`.
   - Affichage du résumé d'opération dans la console FORGE avec le nombre exact de fichiers intégrés.

---

## 4. Plan d'Implémentation Étape par Étape

1. **Étape 1 : Fiabilisation de l'UNPACK dans `ForgeEngine.kt` & `Ext4Extractor.kt`**
   - Amélioration du parcours des répertoires pour supporter les blocs indirects et les HTree indexés sans perte.
   - Extraction directe au premier niveau du dossier projet (`PROJET/<nom_projet>/root/` ou dossier dédié) avec symlinks préservés.
2. **Étape 2 : Optimisation de la Database dans `ImageAnalyzer.kt`**
   - Capture rigoureuse de la structure de l'image originale (volume label, UUID, inodes, structure racine).
3. **Étape 3 : Perfections du REPACK dans `Ext4Builder.kt` & `ForgeEngine.kt`**
   - Découpage multi-groupes standardisé avec bitmaps valides.
   - Intégration prioritaire de `mke2fs` natif si présent sur le terminal Android.
4. **Étape 4 : Tests & Validation**
   - Test unitaire Robolectric couvrant l'analyse, l'extraction et la reconstruction fidèle.
   - Vérification de la compatibilité de l'image avec ZArchiver (reconnaissance immédiate en EXT4 sans détour APEX).
