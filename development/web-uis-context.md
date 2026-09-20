# Contexte global — Glue Web et le dépôt `web-uis`

État de référence des échanges : **18 septembre 2026**.

Ce document rassemble les objectifs, l'existant dans Glue Web et l'architecture envisagée pour
construire, distribuer et intégrer les interfaces web des mods. Il sert de point de départ au
dépôt frontend désigné génériquement par **`web-uis`**, appelé `occamy-uis` dans les premiers échanges.

Les sections « existant » décrivent le code présent dans l'arbre de travail de Glue lors de cette
discussion. Elles ne signifient pas que ces ajouts sont déjà publiés dans un artifact Maven.
Les structures, commandes et formats marqués « proposés » décrivent la cible définie pendant les
échanges ; leur avancement dans un autre dépôt doit être vérifié séparément.

Le fichier a été restauré dans `development/` après la réorganisation du dépôt. La documentation
publique de Glue se trouve désormais dans le dépôt séparé `glue-docs`.

## 1. Objectif initial

Permettre à plusieurs mods Minecraft de construire leur interface en HTML/CSS/JavaScript, notamment
avec React, et de la mettre à jour indépendamment de leur jar Java lorsque leur contrat reste
compatible.

Le mod déclare son application et les capacités Java qu'elle peut utiliser. Glue prend en charge
le choix d'une release, son téléchargement, sa vérification, son cache et le service local des fichiers.

Les garanties recherchées sont :

- Une interface disponible hors ligne grâce à une version embarquée dans le jar.
- Aucune attente réseau pour ouvrir une interface.
- Des mises à jour authentifiées et compatibles avec le Java réellement installé.
- Une release complète et isolée pour chaque surface ouverte.
- Une activation prévisible et un retour à une version précédente ou à l'embarqué.
- Un état observable pour présenter la progression ou les erreurs dans le mod.
- Un mécanisme partagé par application, sans trois téléchargements pour trois écrans.
- Un hébergement interchangeable : Garage/S3, serveur statique ou CDN exposant des objets HTTPS.

**Garage héberge les archives et métadonnées ; Chromium exécute les interfaces dans Minecraft.**
Les pages sont servies localement par Glue après leur sélection. Un backend métier éventuel reste
un service distinct de cette distribution de fichiers statiques.

### Exemple concret utilisé pour raisonner

| Élément | Valeur d'exemple |
|---|---|
| Mod Java | `diablo-hud`, reproduisant une interface de type Diablo |
| Application dans ce mod | `hud` |
| Identifiant d'application | `diablo-hud:hud` |
| Contrat initial | `diablo-hud.hud/1` |
| Dépôt frontend envisagé | `web-uis` |
| Stockage | Garage, bucket `occamy` |
| Domaine communiqué | `cdn.lacaleche.cc` |

Le routage public exact de Garage reste à confirmer. Les exemples de ce document supposent une
base publique **`https://cdn.lacaleche.cc/occamy`**. Le nom du bucket n'apparaît pas nécessairement
dans l'URL publique suivant la configuration du reverse proxy et de Garage.

## 2. Répartition des responsabilités

| Partie | Responsabilité |
|---|---|
| `glue-web` dans Minecraft | Héberger les surfaces, fournir le bridge, sélectionner les releases, vérifier et télécharger les bundles, gérer le cache et les états. |
| Mod Java consommateur | Déclarer l'app et son contrat, fournir les actions/états/slots, choisir quand recréer une interface et conserver l'état métier. |
| Application React | Présentation, interactions et utilisation du contrat Java. |
| UI kit de `web-uis` — proposé | Composants, styles, tokens et conventions réutilisables entre applications. |
| Outillage de `web-uis` — proposé | Configuration d'app, packaging, manifeste, signature et publication automatisée. |
| CI de `web-uis` — proposée | Construire, tester et publier les applications concernées, dans le bon ordre. |
| Plugin Gradle consommateur — proposé | Importer une release épinglée dans les ressources du mod Java et générer ses métadonnées embarquées. |
| Garage/S3 | Conserver les objets publiés et les rendre accessibles en HTTPS. |

Deux parcours sont complémentaires :

```text
Publication des interfaces

web-uis → build/test React → ZIP + métadonnées signées → Garage/S3
                                                        │
                       ┌────────────────────────────────┴───────────────────────────┐
                       │                                                            │
Construction du mod Java                                      Exécution de Minecraft
                       │                                                            │
release précise épinglée                                       canal stable signé
                       │                                                            │
plugin Gradle proposé                                         gestionnaire Glue existant
                       │                                                            │
ressources embarquées dans le jar                              cache local vérifié
                       │                                                            │
fallback hors ligne                                           sélection pour une ouverture
```

**Le build Java consomme une référence reproductible. La recherche de la dernière publication
compatible appartient au runtime Glue.**

## 3. Ce qui existe déjà dans `glue-web`

### 3.1 Hébergement et bridge

Glue Web fournit `WebSurface`, `WebScreen`, `WebHud`, `WebOverlay` et `WebWidget`. Les pages peuvent
utiliser des actions Java, des états observables, des événements et des zones de rendu natif.

Le bridge JavaScript est fourni par Glue à l'adresse locale :

```text
https://glue-web.glue/bridge.js
```

Les frontends React gardent cet import externe lors du build. Le bridge est accordé à l'origine
de confiance de la surface et à son document principal. JCEF reste une implémentation privée.

### 3.2 Déclaration d'une application versionnée

L'API existante permet de déclarer l'application pendant l'initialisation client. Exemple :

```java
WebApp app = WebApp.builder("diablo-hud", "hud")
        .embedded("1.0.1842", "web/hud")
        .contract("diablo-hud.hud/1")
        .updates(
                URI.create("https://cdn.lacaleche.cc/occamy/apps/diablo-hud/hud/stable.json"),
                Map.of("release", releaseKey)
        )
        .activation(WebApp.Activation.NEXT_OPEN)
        .register();
```

Dans cet exemple, `releaseKey` est un `PublicKey` Ed25519 fourni par le mod. Le fallback se trouve
sous `assets/diablo-hud/web/hud/` et contient un `index.html` à sa racine.

- L'identifiant est `diablo-hud:hud`.
- L'origine locale stable est `https://hud.diablo-hud.glue/`.
- Chaque application possède son origine, son cache et sa sélection.
- L'enregistrement d'une même origine deux fois est refusé ; les écrans partagent l'app enregistrée.
- `updates(...)` est facultatif pour une application locale sans canal distant.
- `WebApp.of(modId)` conserve son fonctionnement historique, local et non versionné.

Les hôtes utilisent toujours `app.page("index.html")`. Ils n'ont pas à sélectionner eux-mêmes un
répertoire de cache ou à connaître l'URL du ZIP.

### 3.3 Disponibilité, restauration et sélection

À l'enregistrement, Glue rend l'embarqué disponible immédiatement puis effectue en arrière-plan :

1. La restauration de la sélection persistée.
2. La vérification des signatures et archives déjà présentes dans le cache.
3. La consultation du canal distant, s'il existe.
4. Le téléchargement et l'installation de sa release compatible, si nécessaire.

Une surface ouverte pendant la restauration du cache utilise l'embarqué. Une fois le cache vérifié,
une release locale restaurée peut être utilisée même si le réseau est indisponible ou encore en cours
de vérification. Une erreur de mise à jour laisse une sélection locale utilisable.

La release est figée **à l'ouverture réelle de la surface**, et non à la création de son builder.
Elle reste la même pour les fichiers chargés plus tard, les imports différés et les rechargements.

### 3.4 Activation et rollback

| Mécanisme existant | Comportement |
|---|---|
| `NEXT_OPEN` | Une release installée est sélectionnée automatiquement pour les nouvelles surfaces. |
| `MANUAL` | Une release installée devient prête ; `activateUpdate()` la sélectionne. |
| `checkForUpdates()` | Lance une recherche ; plusieurs appels concurrents partagent une opération. |
| `useEmbedded()` | Persiste le choix de l'interface embarquée. |
| `usePrevious()` | Sélectionne et épingle la release précédemment sélectionnée. |
| `resumeUpdates()` | Rétablit la politique configurée et consulte le canal. |

Ces changements concernent les futures surfaces. Pour montrer la nouvelle sélection, le mod ferme
la surface courante et en crée une autre avec l'adresse logique de l'application. Il peut conserver
ses données métier en Java entre ces ouvertures.

Pour un HUD, une « prochaine ouverture » correspond à la prochaine création de sa surface, par
exemple après avoir quitté puis rejoint un monde. Une publication n'impose pas un remplacement
instantané au milieu d'une partie.

### 3.5 État observable

`status()` fournit un snapshot immuable contenant :

- `selected` : release sélectionnée pour les prochaines ouvertures.
- `ready` : candidate préparée et en attente d'activation, ou absence de candidate.
- `phase` : `RESTORING`, `IDLE`, `CHECKING`, `DOWNLOADING`, `VERIFYING` ou `READY`.
- `selection` : `AUTOMATIC`, `EMBEDDED` ou `PINNED`.
- `error` : erreur de la dernière opération, si présente.

Les notifications `onStatus(...)` arrivent sur le thread client Minecraft et peuvent regrouper des
états intermédiaires. Les contrôles asynchrones renvoient un `CompletableFuture<WebAppStatus>`.

### 3.6 Installations et limites actuelles

Le cache est sous `<game directory>/glue-web/apps/<mod>/<app>/`. Glue vérifie la signature, la taille
et le SHA-256, extrait dans un emplacement temporaire, puis publie l'installation et les métadonnées
par déplacements atomiques. Un verrou empêche deux clients de modifier simultanément le même cache.

Les identifiants locaux de release incorporent la version et l'empreinte de l'archive. Les chemins
physiques `/_glue/<release>/...` sont internes ; les mods utilisent les URI de `app.page(...)`.
Les requêtes de scripts de service workers sont refusées sur les applications gérées.

Contraintes actuelles : enveloppe de 256 Kio maximum, ZIP de 64 Mio, extraction totale de 256 Mio,
32 Mio par fichier, 4096 entrées, chemins de 512 caractères et 32 composants maximum. Les chemins
dangereux, doublons et collisions de casse sont rejetés. L'extraction crée des fichiers ordinaires.

Limites de périmètre documentées :

- Vérification distante à l'enregistrement et sur appel explicite ; pas de polling périodique intégré.
- Recréation coordonnée des interfaces ouvertes pilotée par le mod.
- Pas de détection automatique de tous les défauts fonctionnels d'une interface.
- Conservation des releases installées sans éviction automatique du cache dans cette première version.
- Stockage navigateur partagé entre releases d'une app : ses migrations appartiennent à l'application.

## 4. Contrat, version et révision : trois notions différentes

| Notion | Exemple | Rôle |
|---|---|---|
| Identifiant d'app | `diablo-hud:hud` | Identifie l'application et son propriétaire. |
| Contrat | `diablo-hud.hud/1` | Identifie les échanges frontend/Java compatibles. |
| Version du bundle | `1.0.1843` | Identifie la release des fichiers web. |
| Révision du canal | `1843` | Ordonne les décisions de publication du canal. |
| Version du mod Java | Propre au mod | Identifie une release de son jar. |
| Version de Glue | Propre à Glue | Identifie le moteur et son API, pas le contrat métier de chaque mod. |

### Le contrat appartient au mod

Un contrat peut décrire, par exemple :

```text
diablo-hud.hud/1

État player : health, maxHealth, mana, maxMana
Action hud.togglePanel : reçoit { panel }
Événement combat.hit : reçoit les informations d'un coup
Slot portrait : zone de rendu natif du personnage
```

L'identifiant est déclaré et maintenu par le développeur. Glue effectue une comparaison exacte ;
il n'infère pas la compatibilité sémantique à partir des annotations Java ou des types TypeScript.

Une évolution de couleurs, de disposition ou de composants React peut conserver le contrat.
Une interface qui exige une nouvelle action absente des anciens mods, ou un changement incompatible
de structure des états, demande un nouveau contrat.

Un canal conserve **au maximum une release par contrat**. Il peut ainsi proposer simultanément :

```text
diablo-hud.hud/1 → UI 1.0.1843
diablo-hud.hud/2 → UI 2.0.1901
```

Un ancien mod déclarant `/1` sélectionne la première entrée. La publication de `/2` ne doit pas
effacer l'entrée `/1` tant que cette génération de Java reste supportée.

### Versions et rollback

Les versions de bundle sont comparées selon l'ordre sémantique. Une release distante ou restaurée
doit être au moins aussi récente que la version actuellement embarquée dans le mod.

Dans cette limite, le canal fait autorité : une révision plus élevée peut pointer vers une version
de bundle antérieure. Un rollback publié signifie donc « nouvelle décision de canal, ancienne UI »,
et non « republier une ancienne révision de canal ».

## 5. Le rôle de `stable.json`

`stable.json` est le point d'entrée HTTPS stable que le mod consulte. Le nom `stable` est une
convention de publication, pas une valeur spéciale codée dans Glue.

Le fichier est une enveloppe JSON contenant :

```json
{
  "format": 1,
  "keyId": "release",
  "payload": "BASE64_DES_OCTETS_UTF8_DU_MANIFESTE",
  "signature": "BASE64_DE_LA_SIGNATURE_ED25519"
}
```

Les valeurs Base64 ci-dessus illustrent la structure ; l'outil de publication les calcule réellement.
La signature couvre les octets exacts du payload décodé, sans canonisation JSON.

Le payload signé contient :

- Le format du protocole.
- L'identifiant de l'application.
- L'URL du canal.
- Sa révision croissante.
- La liste des releases par contrat, chacune avec version, URL de ZIP, taille et SHA-256.

Règles existantes importantes :

1. L'app et l'URL du canal doivent correspondre exactement à la déclaration Java.
2. L'URL de `updates(...)` doit donc correspondre au `--channel` utilisé pour signer.
3. Le `keyId` doit désigner une clé publique acceptée par le mod.
4. Les adresses utilisent HTTPS et les redirections sont refusées.
5. Une révision inférieure à une révision déjà acceptée est rejetée.
6. Une même révision exige la même enveloppe, octet pour octet.
7. Les archives référencées doivent être disponibles avant la mise à jour du canal.

L'authenticité du bundle et sa compatibilité déclarée ne prouvent pas que tous ses boutons fonctionnent.
Le mod garde un moyen de sélectionner l'embarqué ou une release précédente si l'interface rencontre
un problème fonctionnel.

## 6. L'outillage de publication déjà disponible

Le script [publish-bundle.mjs](../glue-web/tools/publish-bundle.mjs) est actuellement une CLI Node
sans dépendance npm. Il attend **un ZIP déjà construit**.

Il reçoit notamment `--archive`, `--app`, `--contract`, `--version`, `--revision`, `--channel`,
`--url`, `--key`, `--key-id` et `--output`. L'option `--catalog` reprend un précédent manifeste
non signé pour conserver les entrées des autres contrats.

Il produit :

| Fichier | Usage |
|---|---|
| `bundle.zip` | Copie de l'archive à distribuer. |
| `manifest.json` | Payload non signé à conserver comme artifact de production pour la publication suivante. |
| `channel.json` | Enveloppe signée à publier sous l'URL du canal, par exemple `stable.json`. |

Le script calcule l'empreinte, la taille et la signature. Il ne lance pas le build React, ne fabrique
pas le ZIP et n'effectue pas l'upload S3. Il ne lit pas encore un fichier de configuration d'app
`glue-app.json`.

Il constitue la base de protocole à reprendre dans l'outillage de `web-uis`, plutôt que de concevoir
un second protocole incompatible.

## 7. Architecture théorique de `web-uis`

### 7.1 Monorepo pnpm proposé

```text
web-uis/
├── apps/
│   └── diablo-hud/
│       ├── src/
│       ├── public/
│       ├── package.json
│       ├── vite.config.ts
│       └── glue-app.json
├── packages/
│   ├── ui-kit/
│   │   ├── src/
│   │   └── package.json
│   └── tooling/
│       ├── src/
│       └── package.json
├── pnpm-workspace.yaml
├── pnpm-lock.yaml
└── .gitlab-ci.yml
```

Les noms de packages proposés sont `@web-uis/ui-kit` et `@web-uis/tooling`. Ils ne désignent pas
des packages déjà créés ou publiés dans ce contexte de référence.

### 7.2 UI kit

Le kit contient des composants React, styles, tokens visuels et conventions communes. Les interfaces
spécifiques, comme les orbes de vie et de mana de `diablo-hud`, restent composées dans leur application.

Les composants peuvent recevoir leurs valeurs et callbacks par props. Cela permet une prévisualisation
avec des données de démonstration dans un navigateur ordinaire, tandis que l'application connecte ces
composants au bridge Glue dans Minecraft. L'import `.glue` du bridge nécessite ce contexte d'exécution
ou un adaptateur de développement ; son fonctionnement n'est pas celui d'une URL internet ordinaire.

### 7.3 Librairie de tooling avec CLI

Le choix recommandé est une librairie TypeScript et une CLI installée comme dépendance de
développement. pnpm sert à gérer le workspace et exécuter ses commandes ; une extension interne de
pnpm n'est pas nécessaire.

Interface de commande envisagée, **à créer** :

```shell
pnpm exec web-uis pack
pnpm exec web-uis publish
```

Responsabilités attendues :

- Lire et valider une petite configuration d'application.
- Packager le frontend déjà construit et contrôler son format.
- Calculer version de publication, empreintes et emplacements d'objets.
- Produire les métadonnées et enveloppes compatibles avec Glue.
- Préserver les contrats encore supportés lors d'une nouvelle publication.
- Publier les objets immuables, puis le canal.
- Produire les artifacts nécessaires au build Java et aux publications suivantes.

Une configuration d'application minimale proposée serait :

```json
{
  "app": "diablo-hud:hud",
  "contract": "diablo-hud.hud/1"
}
```

Ce format n'est pas encore un contrat d'outillage. L'objectif est de laisser au développeur
l'identité et le contrat, et de dériver les URL, révisions et empreintes avec des conventions communes.

### 7.4 Configuration globale, une seule fois

La CI ou sa configuration partagée fournit :

| Information | Rôle |
|---|---|
| Endpoint API S3 | Adresse utilisée pour envoyer les objets à Garage. |
| Bucket `occamy` | Destination des publications. |
| Base URL publique HTTPS | Adresse de lecture pour les clients Minecraft et le build Java. |
| Identifiants S3 | Droits de publication de la CI. |
| Clé privée Ed25519 | Signature des métadonnées. |
| Identifiant de clé | Par exemple `release`, correspondant à la clé publique distribuée avec les mods. |

L'endpoint API S3 et la base URL publique sont distincts conceptuellement. Les credentials S3 et
la clé privée appartiennent au producteur ; les mods embarquent les clés publiques acceptées.

## 8. Organisation théorique des objets publiés

Avec l'hypothèse d'URL publique posée au début :

```text
https://cdn.lacaleche.cc/occamy/
└── apps/
    └── diablo-hud/
        └── hud/
            ├── stable.json
            ├── bundles/
            │   └── <sha256-de-l-archive>.zip
            └── releases/
                └── <identifiant-de-publication>/
                    └── manifest.signed.json
```

- Le ZIP est immuable et adressé par son contenu.
- `stable.json` est le canal mutable consulté à l'exécution.
- Le manifeste signé immuable par publication permet au build Java de retrouver une release précise.

La publication de ces métadonnées immuables reste à organiser dans le nouvel outillage. Le protocole
actuel permet de conserver une copie de l'enveloppe signée à cette adresse ; le consommateur devra
vérifier qu'elle contient exactement l'app, le contrat et la release épinglés.

Le manifeste non signé utilisé comme catalogue de production reste un artifact durable de CI. Si le
catalogue est reconstruit depuis le stockage distant, sa source doit être authentifiée avant de la
réutiliser pour signer une nouvelle publication.

Les objets immuables peuvent bénéficier d'un cache long. Le canal doit être revalidé ou avoir un
cache court pour que les publications soient découvertes rapidement.

## 9. Flow théorique attendu

### 9.1 Premier raccordement

1. Créer le workspace `web-uis`, son UI kit et sa première application.
2. Définir l'app `diablo-hud:hud` et le contrat Java initial.
3. Configurer une fois Garage, les URL publiques, les accès de publication et la signature dans CI.
4. Publier une première release vérifiée.
5. Épingler cette release comme fallback dans le projet Java.
6. Enregistrer la `WebApp`, fournir les états/actions/slots et ouvrir le HUD avec `app.page(...)`.

### 9.2 Modification purement frontend

```text
Modification React
    → merge dans main
    → sélection des apps concernées
    → installation des dépendances verrouillées
    → tests et build
    → ZIP et métadonnées signées
    → upload des objets immuables
    → mise à jour du canal stable
    → découverte par Glue
    → sélection pour la prochaine ouverture
```

Le développeur ne crée pas manuellement le ZIP, ne calcule pas le SHA-256 et ne remplit pas les URL
à chaque publication. Les changements dans l'UI kit partagé doivent également déclencher le build
des applications qui en dépendent.

La version peut être générée, par exemple `1.0.<CI_PIPELINE_IID>`, et la révision dérivée d'un compteur
croissant du producteur. La convention exacte reste à figer. Elle doit rester compatible avec le
plancher de version embarqué et conserver la monotonie si l'infrastructure CI évolue.

La publication doit être sérialisée par application/canal. Un pipeline ancien terminé tardivement
ne doit pas remplacer un canal plus récent. Une reprise d'upload réutilise les artifacts signés de
la publication ; une nouvelle enveloppe différente exige une nouvelle révision.

### 9.3 Construction reproductible du mod Java

Un **plugin Gradle proposé** prendrait en charge :

1. La résolution de la publication UI précisément épinglée par le projet Java.
2. Le téléchargement de son manifeste signé et de son archive.
3. La vérification de l'identité, du contrat, de la version, de la signature, de la taille et du digest.
4. L'extraction dans les ressources générées du mod.
5. La génération des métadonnées décrivant l'interface embarquée.

Les clés de confiance proviennent de la configuration du projet ; le téléchargement de métadonnées
ne doit pas pouvoir introduire de lui-même une nouvelle clé acceptée.

Le build Java ne nécessite ainsi ni les sources React ni Node. Ce plugin est un outil de build :
il doit fonctionner sans charger Minecraft ou CEF et sans exposer leurs dépendances dans Gradle.

Une tâche explicite pourrait actualiser la référence épinglée pour une prochaine release du mod.
Le `build` normal consomme la référence déclarée ; il ne suit pas silencieusement `stable.json`.

### 9.4 Connexion entre les métadonnées générées et `WebApp`

Aujourd'hui, le builder reçoit séparément version embarquée, ressources, contrat, canal et clés.
Pour limiter les doubles déclarations, le plugin Gradle pourrait produire un **descripteur embarqué**
consommé par une petite API de chargement côté Glue.

Le format de ce descripteur et l'API de chargement **n'existent pas encore dans l'état de référence**.
Ils devront être définis avec le tooling et le plugin, en conservant la déclaration explicite des
actions Java, des états et des slots dans le mod.

### 9.5 Évolution incompatible de l'interface Java

1. Faire évoluer le code Java et définir le nouveau contrat, par exemple `diablo-hud.hud/2`.
2. Adapter le frontend à ce contrat.
3. Publier sa release en conservant l'entrée du contrat `/1` tant qu'elle est supportée.
4. Construire le nouveau jar avec un fallback `/2` compatible et précisément épinglé.
5. Les anciennes installations Java continuent à sélectionner `/1` ; les nouvelles sélectionnent `/2`.

Cette évolution coordonnée est un changement de capacité du mod. Une mise à jour CSS ou un correctif
frontend compatible n'a pas besoin de cette procédure.

### 9.6 Retour arrière

- **Local** : le mod utilise `usePrevious()` ou `useEmbedded()` et choisit quand recréer la surface.
- **Publié** : la CI publie une nouvelle révision du canal désignant un ancien bundle compatible.

Un rollback distant reste soumis au contrat et au plancher de version embarqué. Le stockage doit
conserver les archives et métadonnées nécessaires aux retours arrière et aux builds épinglés.

## 10. État des travaux et vérifications

| Élément | État au moment du contexte initial |
|---|---|
| Runtime versionné dans `glue-web` | Implémenté dans l'arbre de travail. |
| Protocole signé format 1 | Implémenté et documenté. |
| Script `publish-bundle.mjs` | Implémenté ; préparation locale des artifacts, sans upload. |
| Démo `/web bundles` | Implémentée ; offline par défaut, canal HTTPS optionnel. |
| GameTest `glue-test:web-bundles` | Implémenté et exécuté. |
| Dépôt `web-uis` et UI kit | Architecture proposée. |
| CLI de packaging/publication intégrée au workspace | À construire à partir du protocole existant. |
| CI multi-app avec upload Garage | À construire. |
| Métadonnées signées immuables par publication | Publication à organiser dans le tooling. |
| Plugin Gradle d'import du fallback | Proposé, pas encore implémenté. |
| Descripteur embarqué et chargement automatique par Glue | Proposés, pas encore implémentés. |
| Validation de bout en bout sur le bucket Garage réel | À réaliser avec la première application. |

La campagne de vérification de l'implémentation avait validé :

- 52 tests `glue-web`, dont 14 consacrés aux bundles, avec un vrai serveur HTTPS local et des clés
  éphémères : cache, signatures, contrats, rollback, deux releases ouvertes, archives invalides et arrêt.
- 4 tests showcase et la compilation/remappage des jars Web et Showcase.
- 3 tests de l'outil de publication.
- 16 étapes du GameTest natif sous Windows, avec Iris/Sodium présents et sans shaderpack actif :
  routage, imports différés, bridge, refus des service workers, reload et fermeture propre de CEF.
- Le build de la documentation publique.

Ces résultats couvrent le runtime et les outils existants à cette date. Ils ne constituent pas une
validation du futur pipeline `web-uis` ni de l'upload et du routage du bucket `occamy`. Ils ne désignent
pas une nouvelle exécution de tests lors de la restauration de ce document.

## 11. Ordre de réalisation recommandé

1. **Créer `web-uis` et la première UI** : workspace pnpm, UI kit minimal, app `diablo-hud`, preview
   et build React utilisable dans Glue.
2. **Construire le tooling** : déclaration d'app, conventions de version/URL, ZIP, signatures,
   métadonnées immuables et conservation des anciens contrats.
3. **Brancher GitLab et Garage** : config globale, publication sérialisée, artifacts durables et
   un premier cycle réel de mise à jour/rollback.
4. **Automatiser le fallback Java** : plugin Gradle consommant une release épinglée et générant les
   ressources/métadonnées sans dépendance Node.
5. **Ajouter le raccord ergonomique dans Glue** : lecture du descripteur généré, une fois son format stabilisé.

Les choix encore à figer sont le routage public exact, les conventions de publication, la forme
du descripteur, les coordonnées de distribution du tooling/plugin et la politique de conservation
des objets. Ils n'empêchent pas de commencer l'UI kit et une première application.

### Résultat attendu au quotidien

> Pour une modification frontend compatible : modifier React, merger et laisser la CI publier.
> Le mod Java conserve son fallback reproductible ; Glue découvre la mise à jour et l'utilise selon
> sa politique d'activation. Une modification du contrat Java déclenche une évolution coordonnée.

## 12. Références

- [Documentation publique Glue](https://gitlab.lacaleche.cc/loccamy/java/glue-docs).
- [Guide public du protocole et de l'API](https://gitlab.lacaleche.cc/loccamy/java/glue-docs/-/blob/main/src/content/docs/web/bundles.md).
- [Guide des interfaces web et du bridge](https://gitlab.lacaleche.cc/loccamy/java/glue-docs/-/blob/main/src/content/docs/web/index.md).
- [Architecture interne de Glue Web](glue-web.md).
- [API WebApp](../glue-web/src/main/java/fr/lacaleche/glue/web/app/WebApp.java).
- [Gestionnaire BundleApp](../glue-web/src/main/java/fr/lacaleche/glue/web/internal/app/BundleApp.java).
- [Vérification des manifestes](../glue-web/src/main/java/fr/lacaleche/glue/web/internal/app/BundleManifest.java).
- [Stockage et extraction](../glue-web/src/main/java/fr/lacaleche/glue/web/internal/app/BundleStore.java).
- [Outil de publication actuel](../glue-web/tools/publish-bundle.mjs).
- [Démo Java](../glue-showcase/src/main/java/fr/lacaleche/glue/testmod/web/BundleDemo.java).
- [Tests du gestionnaire](../glue-web/src/test/java/fr/lacaleche/glue/web/internal/app/BundleAppTest.java).
- [GameTest natif](../glue-showcase/src/main/java/fr/lacaleche/glue/testmod/gametest/web/BundleGameTest.java).
