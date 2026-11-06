# Spécification et Guide de Développement des Extensions LABX

Bienvenue dans la documentation officielle du format et du système d'extension **Labx** pour l'écosystème **LABO**.

Les extensions **Labx** ne sont pas de simples archives ZIP passives : ce sont de véritables modules applicatifs autonomes capables d'étendre la Kitchen Android avec des binaires natifs compilés, des scripts avancés, des moteurs de traitement et des interfaces déclaratives dynamiques.

---

## 1. Philosophie & Principes Fondamentaux

1. **Adoption obligatoire du design LABO (Material 3)** :
   - Les extensions n'embarquent pas de moteur de rendu arbitraire ou de CSS hétérogène.
   - Elles s'expriment à travers le moteur d'affichage natif de LABO, garantissant un thème (Clair / Sombre / Système) et des composants (boutons, sélecteurs, jauges) 100% uniformes.
2. **Choix du Layout Immuable** :
   - Le manifest doit déclarer formellement son mode de disposition : soit **`vertical`** (liste d'actions empilées), soit **`grid`** (grille d'actions ergonomiques).
3. **Explorateur Popup Universel** :
   - Tout champ ou action nécessitant de sélectionner un fichier ou un répertoire invoque obligatoirement le gestionnaire popup intégré de LABO.
4. **Boîte à Outils Système & Binaires Partagés** :
   - Une extension Labx peut embarquer ses propres binaires dans `bin/<ABI>/` OU exploiter les binaires partagés préinstallés dans `data/data/com.labo.kitchen/home/bin`.
5. **Espace de Travail Structuré** :
   - Toutes les opérations et projets résident dans :
     `data/data/com.labo.kitchen/home/`
     ├── `bin/` (Outils système exécutables partagés)
     ├── `extensions/<id>/` (Dossier racine de l'extension installée)
     ├── `PROJET/<nom_projet>/` (Projets déballés par FORGE et les Labx)
     └── `tmp/` (Zone tampon volatile)

---

## 2. Anatomie d'un Paquet `.labx`

Un fichier `.labx` est une archive compressée dont l'extension est `.labx`.

```text
mon_extension.labx
├── manifest.json            [OBLIGATOIRE] Contrat de configuration et déclarations d'actions
├── README.md                [RECOMMANDE] Documentation de l'extension
├── icon.png                 [OPTIONNEL] Icône personnalisée de l'extension
├── scripts/                 [OPTIONNEL] Scripts shell bash/sh exécutés par LABO
│   ├── run.sh
│   └── post_install.sh
├── bin/                     [OPTIONNEL] Binaires natifs compilés pour Android
│   ├── arm64-v8a/
│   │   └── mon_binaire
│   ├── armeabi-v7a/
│   │   └── mon_binaire
│   └── x86_64/
│       └── mon_binaire
└── assets/                  [OPTIONNEL] Fichiers modèles, dictionnaires, patchs
    └── template.xml
```

---

## 3. Schéma du Contrat `manifest.json`

Le fichier `manifest.json` situé à la racine du `.labx` est le cœur de l'extension.

```json
{
  "$schema": "https://labo.kitchen/schemas/labx-v1.json",
  "id": "com.labx.romoptimizer",
  "name": "ROM Optimizer Pro",
  "version": "1.2.0",
  "versionCode": 12,
  "author": "LABO Core Team",
  "description": "Optimiseur d'images de partitions avec nettoyage des bloatwares et recalcul de hash.",
  "category": "optimization",
  "layout": "vertical",
  "requiresRoot": false,
  "requiresStorage": true,
  "minLaboVersion": "1.0.0",
  "binaries": [
    {
      "name": "img_trimmer",
      "source": "bin/arm64-v8a/img_trimmer",
      "executable": true
    }
  ],
  "actions": [
    {
      "id": "analyze_project",
      "title": "Analyser un Projet Déballé",
      "description": "Scanne les APKs et fichiers d'un projet extrait par FORGE",
      "layoutStyle": "button",
      "requiresFilePicker": true,
      "filePickerTarget": "project",
      "allowedExtensions": ["*"],
      "script": "scripts/run.sh",
      "args": ["--mode=analyze", "--target=${SELECTED_FILE}"]
    },
    {
      "id": "clean_cache",
      "title": "Nettoyer les Résidus Temporaires",
      "description": "Purge la zone tampon home/tmp",
      "layoutStyle": "button",
      "requiresFilePicker": false,
      "script": "scripts/clean.sh"
    }
  ]
}
```

### Détail des champs du Manifeste :

| Propriété | Type | Description |
|---|---|---|
| `id` | String | Identifiant unique (format package Java, ex: `com.labx.exemple`). |
| `name` | String | Nom lisible affiché dans le carrousel et le gestionnaire. |
| `layout` | String | **`vertical`** ou **`grid`**. Fixe le mode d'affichage des options dans l'interface LABO. |
| `requiresRoot` | Boolean | `true` si l'extension nécessite `su` pour exécuter ses tâches. |
| `requiresStorage` | Boolean | `true` pour accéder à `/sdcard` et aux fichiers externes. |
| `binaries` | Array | Déclaration des exécutables embarqués qui seront extraits avec `chmod 755`. |
| `actions` | Array | Liste des fonctionnalités offertes à l'utilisateur. |

---

## 4. Variables d'Environnement Injectées

Lorsqu'une action ou un script est invoqué par LABO, les variables d'environnement suivantes sont automatiquement disponibles :

- `LABO_HOME` : Chemin absolu vers `/data/data/com.labo.kitchen/home`
- `LABO_BIN` : Chemin vers `/data/data/com.labo.kitchen/home/bin`
- `LABO_PROJECTS` : Chemin vers `/data/data/com.labo.kitchen/home/PROJET`
- `LABO_TMP` : Chemin vers `/data/data/com.labo.kitchen/home/tmp`
- `LABX_ROOT` : Chemin vers `/data/data/com.labo.kitchen/home/extensions/<id>`
- `SELECTED_FILE` : Chemin du fichier ou dossier choisi dans le gestionnaire popup
- `IS_ROOT` : `1` si le mode root est activé et accordé, sinon `0`

---

## 5. Comment Créer et Empaqueter un Labx

### Étape 1 : Créer l'arborescence
```bash
mkdir -p mon_labx/scripts
cd mon_labx
```

### Étape 2 : Rédiger le Manifeste
Créez `manifest.json` avec les informations requises (voir schéma ci-dessus).

### Étape 3 : Ajouter les scripts
Créez `scripts/run.sh` :
```bash
#!/system/bin/sh
echo "[LABX] Démarrage du script..."
echo "[LABX] Espace de travail : $LABO_HOME"
echo "[LABX] Cible sélectionnée : $SELECTED_FILE"

# Logique de traitement...
exit 0
```

### Étape 4 : Compiler l'archive `.labx`
```bash
zip -r mon_module.labx manifest.json scripts/ README.md
```

### Étape 5 : Installation dans LABO
1. Transférez `mon_module.labx` dans la mémoire interne (`/sdcard`).
2. Ouvrez la barre latérale de LABO.
3. Cliquez sur **Plus (+)**.
4. Le gestionnaire popup s'ouvre : naviguez et sélectionnez votre fichier `.labx`.
5. LABO valide l'intégrité, extrait les binaires dans `home/extensions/<id>`, configure les permissions `chmod 755`, et ajoute instantanément votre module au carrousel d'accueil !

---

## 6. Moteur FORGE et Base de Données `database/`

FORGE extrait les images vers :
`data/data/com.labo.kitchen/home/PROJET/<nom_projet>/<nom_img>/`

Chaque projet contient obligatoirement son dossier de métadonnées :
`data/data/com.labo.kitchen/home/PROJET/<nom_projet>/database/`
- `filesystem_info.json` : format détecté (`ext4`, `erofs`), taille originale, blocs.
- `fs_config` : liste complète des permissions et UID/GID originaux.
- `file_contexts` : contextes de sécurité SELinux préservés pour le repack.

Les extensions Labx peuvent interagir directement avec ce dossier `database` pour ajouter des fichiers de configuration, modifier les tables de partition ou créer des images repoudrées.
