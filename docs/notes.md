# Notes d'apprentissage

Ce que j'apprends en construisant le projet, à relire avant les entretiens.
Une section par sujet, la plus récente en haut de chaque section.

---

## Secrets et configuration

### `.env` vs `.env.example` (séance 2)
- `.env` contient les **vraies valeurs** (mots de passe, clés). Il est dans `.gitignore` : **jamais sur git**.
  - Le repo est public, des robots scannent GitHub en continu pour voler des secrets.
  - Un secret poussé une fois reste dans l'historique git, même si on supprime le fichier après.
- `.env.example` contient les **noms des variables** avec de fausses valeurs. Il est **sur git**.
  - Il sert de modèle : quelqu'un qui clone fait `cp .env.example .env` puis met ses valeurs.
- Règle : **les noms des variables sont publics, leurs valeurs sont privées.**
- Même logique plus tard sur AWS : les secrets vivent dans SSM Parameter Store, pas dans le code.

| Fichier | Contenu | Sur git ? | Rôle |
| --- | --- | --- | --- |
| `.env` | vraies valeurs | non | ce que Docker lit |
| `.env.example` | noms + fausses valeurs | oui | modèle / documentation |

### Variables avec valeur par défaut dans Spring (séance 2)
- `${DB_USER:budget}` = « prends la variable d'environnement `DB_USER`, sinon `budget` ».
- En local ça marche sans rien configurer ; en prod on injecte les vraies valeurs.

---

## Java

### Lombok et les records (séance 2)

**Le problème** : en Java, une classe qui porte des données demande beaucoup de code répétitif
(« boilerplate ») : getters, setters, constructeurs, `equals`, `hashCode`, `toString`.
3 champs utiles peuvent faire 40 lignes.

**Lombok** : une librairie qui écrit ce code **à la compilation**, à partir d'annotations.
Le code n'apparaît pas dans le `.java` mais existe dans le `.class` (IntelliJ le connaît).

| Annotation | Ce qu'elle génère |
| --- | --- |
| `@Getter` / `@Setter` | les getters / setters |
| `@NoArgsConstructor` | le constructeur vide |
| `@AllArgsConstructor` | un constructeur avec tous les champs |
| `@RequiredArgsConstructor` | un constructeur avec les champs `final` (très utilisé dans les services Spring pour l'injection) |
| `@ToString`, `@EqualsAndHashCode` | ces méthodes |
| `@Data` | le pack : getters, setters, toString, equals, hashCode |
| `@Builder` | `Objet.builder().champ(...).build()` |

**Piège à connaître (classique en entretien)** : jamais `@Data` sur une **entité JPA**.
Son `toString` / `equals` / `hashCode` parcourent les relations (transaction → compte → transactions → ...) :
requêtes SQL en cascade (lazy loading), voire boucle infinie / `StackOverflowError`, et bugs dans les `Set`.
Si Lombok sur une entité : seulement `@Getter` / `@Setter`.

**Les records (Java 16+)** : la solution intégrée à Java pour les **objets de données immuables**.
```java
public record TransactionResponse(Long id, BigDecimal amount, String label) {}
```
Cette ligne donne : le constructeur avec tous les champs, les accesseurs `id()`, `amount()`, `label()`
(sans préfixe `get`), `equals`, `hashCode`, `toString`. Les champs ne peuvent plus changer après la création.

**Ce que les records ne font pas** : pas de setters, pas de constructeur vide, pas de builder,
et inutilisables pour les entités JPA (JPA a besoin d'un constructeur vide et de champs modifiables).

| Besoin | Java 21 seul | Avec Lombok |
| --- | --- | --- |
| Objet de données immuable (DTO) | `record` ✅ | `@Value` (inutile aujourd'hui) |
| Getters / setters sur une classe modifiable (entité JPA) | à écrire (IntelliJ : Cmd+N → Getter and Setter) | `@Getter` / `@Setter` |
| Constructeur vide | à écrire | `@NoArgsConstructor` |
| Builder | à écrire | `@Builder` |

**Subtilité Java** : sans aucun constructeur écrit, Java ajoute un constructeur vide invisible.
Dès qu'on écrit un constructeur avec paramètres, il disparaît : il faut alors écrire le vide soi-même (JPA en a besoin).

**Injection sans Lombok** (ce qu'on fait dans le projet) :
```java
@Service
public class TransactionService {
    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;   // Spring injecte via ce constructeur
    }
}
```

**Choix du projet** : pas de Lombok. Records pour les DTO, entités écrites normalement (code généré par IntelliJ).
Si un jour on l'ajoute : `@Getter` / `@Setter` sur les entités uniquement, jamais `@Data`.

---

## Docker

### Les concepts de base (séance 2)
- **Image** : un modèle tout prêt (la « recette »). Ex : `postgres:17`.
- **Conteneur** : une image qui tourne, un mini-ordinateur isolé qu'on peut lancer, arrêter, jeter.
- **Port** (`"5432:5432"`) : un « trou » dans l'isolation. Gauche = mon Mac, droite = le conteneur.
- **Volume** : un disque géré par Docker, séparé du conteneur, pour que les données survivent.
- **docker compose** : décrire ses conteneurs dans un fichier YAML au lieu de longues commandes `docker run`.

### Écrire un `docker-compose.yml` (séance 2)

**À quoi il sert** : décrire dans un seul fichier tous les conteneurs d'un projet (base, API, front...),
leur configuration et comment ils se parlent. Une commande (`docker compose up`) lance tout,
pareil sur n'importe quelle machine. C'est la « recette » de l'environnement, versionnée avec le code.

**La structure générale**
```yaml
services:        # la liste des conteneurs à lancer (obligatoire)
  <nom>:         # un service = un conteneur, le nom sert aussi d'adresse réseau
    ...
volumes:         # les volumes nommés à créer (optionnel)
  <nom>:
```

**Les clés d'un service, une par une**

| Clé | Rôle | Exemple |
| --- | --- | --- |
| `image` | l'image à utiliser, téléchargée depuis Docker Hub | `postgres:17` |
| `build` | à la place de `image` : construire l'image depuis un Dockerfile du projet (pour l'API et le front, en novembre) | `build: ./backend` |
| `environment` | les variables d'environnement passées au conteneur ; `${VAR}` est lu depuis le `.env` | `POSTGRES_DB: ${POSTGRES_DB}` |
| `env_file` | variante : charger toutes les variables d'un fichier d'un coup | `env_file: .env` |
| `ports` | ouvrir un port vers ma machine, `"mac:conteneur"` | `"5432:5432"` |
| `volumes` | brancher un volume (ou un dossier) dans le conteneur, `nom:chemin_dans_le_conteneur` | `budget-db-data:/var/lib/postgresql/data` |
| `healthcheck` | une commande que Docker lance régulièrement pour savoir si le service est vraiment prêt | `pg_isready ...` |
| `depends_on` | l'ordre de démarrage ; avec `condition: service_healthy`, attend que l'autre soit `healthy` | l'API attend la base |
| `restart` | redémarrer automatiquement si le conteneur plante | `restart: unless-stopped` |

**Le réseau** : Compose crée un réseau privé pour le projet. Entre conteneurs, on s'appelle **par le nom du service** :
l'API dans un conteneur joindra la base sur `db:5432`, pas `localhost:5432`.
(Aujourd'hui mon API tourne sur le Mac, donc elle passe par le port ouvert : `localhost:5432`.)

**Mon fichier commenté**
```yaml
services:
  db:                                   # le service s'appelle "db"
    image: postgres:17                  # Postgres 17 officiel
    environment:                        # lu au 1er démarrage pour créer base + user
      POSTGRES_DB: ${POSTGRES_DB}       # valeurs prises dans .env
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"                     # Mac:5432 → conteneur:5432
    volumes:
      - budget-db-data:/var/lib/postgresql/data   # les données vivent dans le volume
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s                      # vérifie toutes les 5 s
      timeout: 5s                       # une vérif qui dure plus de 5 s = échec
      retries: 5                        # 5 échecs de suite = "unhealthy"

volumes:
  budget-db-data:                       # déclaration du volume nommé
```

**Méthode pour en écrire un**
1. Lister les conteneurs nécessaires (un par brique : base, API, front).
2. Pour chacun : quelle image ? quelles variables (voir la doc de l'image sur Docker Hub) ? quel port ouvrir ? quelles données garder (volume) ?
3. Ajouter un `healthcheck` sur ce dont les autres dépendent, et `depends_on` chez ceux qui attendent.
4. Secrets dans `.env`, jamais en dur dans le compose.
5. Tester : `docker compose up -d`, `docker compose ps`, `docker compose logs <service>`.

### Commandes utiles
**Lancer**
- `docker compose up -d` : crée réseau + volumes + conteneurs et démarre tout en arrière-plan (`-d`).
  La 1re fois, télécharge l'image.

**Lister / vérifier**
- `docker compose ps` : les conteneurs du projet et leur état (attendre `healthy`).
- `docker compose logs db` : ce que raconte un service (utile s'il reste `unhealthy`).

**Arrêter / supprimer**
- `docker compose down` : arrête et supprime les conteneurs + le réseau. **Les données restent** dans le volume.
- `docker compose down -v` : pareil **+ supprime les volumes** = toutes les données effacées, on repart de zéro.
  ⚠️ Postgres ne lit `POSTGRES_USER/PASSWORD/DB` qu'au 1er démarrage (volume vide) :
  si on change le `.env`, il faut un `down -v` pour qu'il les prenne en compte.

**Entrer dans la base**
- `docker compose exec db psql -U budget -d budget` : console SQL dans le conteneur (`\q` pour sortir).
- `... -c "select version();"` : exécute une requête et quitte (test rapide que la base répond).

### Les deux `volumes:` dans un compose (séance 2)
- Celui **dans le service** (`db.volumes`) : « ce service utilise ce volume, monté à tel chemin ».
- Celui **à la racine** (collé à gauche) : « ce volume existe dans le projet ».
- Si on indente celui du bas, YAML le met dans `db` → erreur « clé dupliquée ».

### Healthcheck
- `pg_isready` répond quand Postgres est prêt à accepter des connexions.
- `$$VAR` dans un compose = variable lue **dans le conteneur**, pas sur ma machine.

---

## YAML

### Pièges (séance 2)
- L'indentation définit la structure : 2 espaces par niveau, **jamais de tabulation**.
- Pas d'espace après `:` dans une chaîne « a:b » : `- volume:/chemin` et non `- volume: /chemin`
  (avec l'espace, YAML lit une clé/valeur au lieu d'une chaîne).

---

## Environnement de dev (Mac)

### Plusieurs versions de Java / Node (séance 1)
- La commande `java` lancée est la première trouvée dans le `PATH`, pas forcément celle de `JAVA_HOME`.
- Fix : `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` puis `export PATH="$JAVA_HOME/bin:$PATH"` dans `~/.zshrc`.
- Brew installe certaines versions en « keg-only » (ex. `node@24`) : il faut les ajouter au `PATH` à la main.
- Une appli macOS lancée depuis Téléchargements est en lecture seule → la déplacer dans Applications.
