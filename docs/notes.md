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

## Modélisation de la base de données

### Écrire la migration `V1__init.sql` (séance 4)

**Pourquoi un fichier SQL plutôt que pgAdmin** : une table créée par clics n'existe que dans ma base locale.
Le fichier est versionné avec le code et Flyway l'applique partout (mon Mac, Testcontainers, CI, AWS) : le schéma devient du code.
pgAdmin reste utile pour **regarder** les tables une fois la migration appliquée (`localhost:5432`).

| Dans pgAdmin | En SQL |
| --- | --- |
| Create → Table | `CREATE TABLE nom (...);` |
| une ligne de l'onglet Columns | `nom_colonne TYPE,` |
| case « Not NULL? » | `NOT NULL` (sans = colonne optionnelle) |
| champ « Default » | `DEFAULT valeur` |
| case « Primary key? » | `PRIMARY KEY` |
| Constraints → Unique / Check | `CONSTRAINT nom UNIQUE (col)` / `CONSTRAINT nom CHECK (condition)` |
| Constraints → Foreign Key | `REFERENCES autre_table (col) ON DELETE ...` sur la ligne de la colonne |

**Pièges de syntaxe rencontrés**
- Une virgule entre chaque ligne, **pas** après la dernière ; un `;` à la fin de chaque `CREATE TABLE`.
- `TIMESTAMPTZ` (pas `TIMESTAMPZ`).
- Un `CHECK` contient une **condition** vrai/faux : `CHECK (lower(email) = email)`, pas `CHECK (email)`. Pour une liste : `CHECK (type IN ('A', 'B'))`.
- Convention : mots-clés SQL en MAJUSCULES, noms en minuscules (Postgres met de toute façon les noms en minuscules).
- Nommer les contraintes `table_colonne_type` : `users_email_uq`, `accounts_type_chk`.
- Une FK s'appelle `<entité au singulier>_id` : `user_id`, pas `users_id`. Un même nom partout (`email` en base = en Java = en JSON).

**Ordre des tables** : une table ne peut pointer que vers des tables déjà créées
(sinon `relation "accounts" does not exist` et Flyway annule tout) → `users` → `accounts`, `categories` → `transactions`, `budgets`.

**UUID** : `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` (intégré depuis Postgres 13, UUID v4).

**Texte** : en Postgres, `TEXT` et `VARCHAR(n)` sont stockés pareil (mêmes perfs), `(n)` ajoute juste une limite.
Choix du projet : `TEXT` partout, longueur validée en Java (`@Size`). Un email peut faire 254 caractères (64 = seulement la partie avant le `@`).

**Argent** : `INT` arrondit **en silence** (1 234,56 → 1 235). Toujours `NUMERIC(12,2)`.
`initial_balance` sans `CHECK (> 0)` : un compte peut démarrer à découvert.

**Ordre des colonnes d'un index composite (l'annuaire)** : un annuaire trié par (nom, prénom) trouve vite tous les « Dupont », jamais toutes les « Marie ».
`UNIQUE (user_id, name)` range les lignes d'un même user ensemble → sert aussi à `WHERE user_id = ?`. `(name, user_id)` ne servirait à rien pour ça.

**`UNIQUE (id, user_id)` : redondant pour la logique, nécessaire pour la technique**
- `id` est unique, donc le couple l'est forcément : aucune règle métier en plus.
- Mais Postgres exige qu'une FK pointe vers des colonnes avec **exactement** un `UNIQUE` / `PRIMARY KEY` déclaré
  (sinon `there is no unique constraint matching given keys`) : il lui faut un index sur ce couple pour vérifier vite.
- À mettre sur chaque table **cible** d'une FK composite : `accounts` et `categories`.
- Exemple : accounts = (A1, ilyas), (B1, sœur). Transaction (user sœur, compte A1) → couple (A1, sœur) absent → refusée.

**Les `ON DELETE` restants**
- `transactions.account_id` : refus (on ne supprime pas un compte qui a des transactions). Dans le doute, **l'option qu'on peut assouplir plus tard** :
  passer en `CASCADE` = migration de 2 lignes ; des données effacées = perdues pour toujours.
  `CASCADE` + confirmation forte côté front se défend aussi → noté dans `idees.md` pour novembre.
- Tous les `user_id` : `CASCADE`. RGPD, droit à l'effacement : les données appartiennent au user, s'il demande la suppression on efface tout (les garder serait illégal).
- ⚠️ **`NO ACTION` plutôt que `RESTRICT`** : supprimer un user fait cascader comptes + transactions dans la même opération.
  `RESTRICT` vérifie immédiatement et peut bloquer selon l'ordre de la cascade ; `NO ACTION` (le défaut, on n'écrit rien) vérifie à la **fin** de l'opération → passe.

| FK | `ON DELETE` |
| --- | --- |
| tous les `user_id` | `CASCADE` |
| `transactions.account_id` | rien (défaut `NO ACTION` = refus) |
| `transactions.category_id` | `SET NULL (category_id)` |
| `budgets.category_id` | `CASCADE` |

**Flyway : ne pas lancer l'appli tant que `V1__init.sql` n'est pas fini.** Une fois appliqué, le fichier est figé (checksum).
Si ça arrive en dev : `docker compose down -v` (efface la base) puis relancer.

### Décisions finales du schéma (séance 3)

Le schéma de la séance 2 (plus bas) reste la base ; voici les choix tranchés et leur justification.

**`users`**
- `display_name` seul, pas de nom + prénom : l'appli en a besoin uniquement pour afficher « Salut Ilyas ».
  **Minimisation RGPD** : une donnée qu'on ne stocke pas ne peut pas fuiter.
- Ids en **UUID** (v4, généré par Postgres avec `gen_random_uuid()`) : 122 bits aléatoires, générables n'importe où sans demander à la base.
  - Avantages : impossible à deviner / énumérer (`/accounts/42` → `/accounts/43`), ne révèle pas le nombre d'utilisateurs.
  - ⚠️ **Le UUID ne protège PAS de l'IDOR.** La vraie protection reste `findByIdAndUserId(id, moi)`. Le UUID est une couche en plus (**défense en profondeur**).
  - Coût : 16 octets au lieu de 8, illisible, index rempli dans le désordre (le UUID v7, trié par date, corrige ça ; Postgres 17 ne fait que du v4).
- `created_at` en **`TIMESTAMPTZ`** : stocke un **instant** (converti en UTC), même sens sur mon Mac à Paris et sur un serveur AWS en UTC.
  `TIMESTAMP` = une heure sans fuseau, ambiguë. Malgré le nom, `TIMESTAMPTZ` ne garde pas le fuseau d'origine. En Java : `Instant`.
- **Email** : pour Postgres, `Ilyas@gmail.com` ≠ `ilyas@gmail.com`, donc `UNIQUE` seul laisse passer les deux (= deux comptes pour la même personne).
  - Java normalise : `email.trim().toLowerCase()` à l'inscription **et** au login.
  - La base garantit : `CHECK (email = lower(email))` (+ `UNIQUE(email)` normal).
  - Alternative : index unique sur `lower(email)`, utile quand on ne maîtrise pas les données qui arrivent en base.
  - Phrase d'entretien : « Je normalise côté appli, et la base garantit la règle avec un CHECK. »

**`accounts`**
- **Pas de colonne solde** : « Le solde se déduit des transactions. Le stocker créerait une deuxième **source de vérité** qui peut se contredire au moindre oubli dans le code (créer, modifier, changer de compte, supprimer…). Donc on le calcule. »
  Prix : un `SUM` à l'affichage (quelques ms avec un index). Stocker un calcul pour la perf = **dénormalisation**, à faire seulement comme choix conscient.
  (Ne pas confondre avec l'**atomicité** « crédité d'un côté, pas débité de l'autre » : ça, c'est `@Transactional`.)
- `UNIQUE (user_id, name)` = **contrainte d'unicité composite** : l'unicité porte sur la combinaison.
  Moi et ma sœur pouvons chacun avoir un « Compte courant », mais pas moi deux fois.
  Postgres crée un index `(user_id, name)` ; un index composite sert aussi pour sa **1re** colonne seule (`WHERE user_id = ?`), pas pour la 2e seule.
- `type` gardé : **texte libre pour l'humain** (`name` : « Cagnotte »), **valeur contrôlée pour le code** (`type` : `GROUP BY`, icônes, règles futures comme plafond / intérêts). Même logique pour `kind`.
- `currency CHAR(3)` : code **ISO 4217** (`EUR`, `USD`, `MAD`), toujours 3 lettres.

**Restreindre une colonne à une liste de valeurs (`type`, `kind`)**

| Option | Pour | Contre |
| --- | --- | --- |
| `CHECK (type IN (...))` ✅ choisi | simple, Hibernate sans config | changer la liste = supprimer + recréer la contrainte |
| `ENUM` Postgres (`CREATE TYPE ... AS ENUM`) | ajouter une valeur facile | retirer / renommer pénible, config Hibernate en plus |
| Table de référence + FK | valeurs avec données (libellé, couleur), ajout sans migration | jointure en plus ; c'est le cas des **catégories** |

- Côté Java : `enum` + `@Enumerated(EnumType.STRING)`. **Jamais `ORDINAL`** (stocke 0, 1, 2 : insérer une valeur dans l'enum change le sens des données).
- **Nommer ses contraintes** : `CONSTRAINT accounts_type_chk CHECK (...)`, pour pouvoir les supprimer plus tard.
- **Flyway : on ne modifie JAMAIS une migration déjà appliquée** (checksum dans `flyway_schema_history`, l'appli refuse de démarrer).
  Ajouter `CARTE_CREDIT` = nouvelle migration `V2__...sql` qui supprime et recrée la contrainte.

**`categories`**
- **Copiées par utilisateur** à l'inscription plutôt que partagées (`user_id = NULL`) : pas de cas particulier dans le filtre anti-IDOR
  (sinon `user_id = moi OU user_id IS NULL`, + règle « lecture seule »), et chacun peut renommer / recolorer. Coût : ~10 lignes par user.
- `kind` sur la catégorie : à quel type de transaction elle sert → la liste « nouvelle dépense » ne propose pas « Salaire », pas de budget sur un revenu.

**`transactions`**
- **Montant toujours > 0 + `kind`** plutôt que montant signé. Les deux sont défendables :
  signé = solde plus simple (`initial + SUM(amount)`), colle aux relevés CSV, gère bien les remboursements ;
  `kind` = sens écrit en toutes lettres (enum Java), pas de convention « négatif = sortie » à connaître. **Choix : `kind`.**
- `kind` **sur la transaction ET sur la catégorie** : obligatoire sur la transaction car `category_id` est **optionnel** (sinon une transaction sans catégorie n'a pas de sens).
  Duplication voulue ; incohérence possible acceptée (ex. remboursement Zara = REVENU rangé dans « Vêtements » qui est DEPENSE).
- **`date` ≠ `created_at`** : courses samedi 31/10 saisies lundi 02/11 → `date` = 31/10 (compte dans le budget d'octobre), `created_at` = l'instant de saisie (technique).
- **Un jour → `DATE`, un instant → `TIMESTAMPTZ`.** Un 1er novembre stocké en `TIMESTAMPTZ` = 01/11 00h Paris = 31/10 23h UTC → rangé en octobre côté serveur.
- Index `(user_id, date)` : la requête la plus fréquente = mes transactions par date / par mois.

**Anti-IDOR en écriture : FK composites**
- Problème : `account_id REFERENCES accounts(id)` vérifie que le compte **existe**, pas **à qui il est**.
  Ma sœur (B) peut faire `POST /transactions` avec l'id de mon compte → ligne `(user B, compte A, 500 €)` acceptée → mon solde faussé ou transaction « fantôme ».
- **Java (obligatoire)** : vérifier `findByIdAndUserId(accountId, moi)` (et `categoryId`) à la création **et à la modification** (PUT qui change de compte) → 404.
- **Base (le filet)** :
  ```sql
  -- dans accounts et categories
  UNIQUE (id, user_id)
  -- dans transactions
  FOREIGN KEY (account_id, user_id)  REFERENCES accounts (id, user_id)
  FOREIGN KEY (category_id, user_id) REFERENCES categories (id, user_id)
  -- dans budgets
  FOREIGN KEY (category_id, user_id) REFERENCES categories (id, user_id)
  ```
  Si une colonne de la FK est `NULL` (transaction sans catégorie), Postgres ne vérifie pas → l'optionnel marche.
- Pourquoi les deux : chaque chemin d'écriture (POST, PUT, import CSV en V2, données de démo, script SQL) doit penser au contrôle Java ; la contrainte s'écrit **une fois** et protège tout, même le code pas encore écrit.
- Phrase d'entretien : « Le filtre par utilisateur est fait dans le service, et la base le garantit avec des clés étrangères composites. »

**`budgets`**
- Lié à une catégorie + un mois, **pas à un compte** : 300 € de courses, payées en carte ou en espèces.
- `CONSTRAINT budgets_month_first_day_chk CHECK (EXTRACT(DAY FROM month) = 1)` : sinon `2026-10-01` et `2026-10-15` contournent le `UNIQUE (user_id, category_id, month)` → deux budgets « Courses » en octobre.
- Limite : un `CHECK` ne voit que **sa propre ligne**, il ne peut pas vérifier dans `categories` que la catégorie est une DEPENSE → règle portée par le Java.

**`ON DELETE` : que faire des lignes qui pointent vers une ligne supprimée ?**

| Option | Ce que fait la base |
| --- | --- |
| `RESTRICT` / `NO ACTION` (défaut) | refuse la suppression (erreur) |
| `CASCADE` | supprime aussi les lignes qui pointent dessus |
| `SET NULL` | garde les lignes, vide leur FK |

- Suppression d'une catégorie → `transactions.category_id` : **`SET NULL`**. Les transactions = l'historique et le solde, on n'y touche pas ; elles deviennent « non catégorisées ».
  ⚠️ Avec la FK composite `(category_id, user_id)`, un `SET NULL` simple viderait aussi `user_id` (NOT NULL → erreur). Écrire **`ON DELETE SET NULL (category_id)`** (Postgres 15+).
- Suppression d'une catégorie → `budgets.category_id` : **`CASCADE`**. Un budget est un objectif lié à la catégorie, sans elle il ne veut plus rien dire.
- Règle : **historique (de l'argent qui a vraiment bougé) → on garde ; objectif rattaché → on supprime avec.**
- À trancher : suppression d'un **compte** (ses transactions ?) et d'un **user** (RGPD, droit à l'effacement).

**Postgres n'indexe PAS les clés étrangères**
- `PRIMARY KEY` et `UNIQUE` créent un index automatiquement, une FK non.
- Sans index, `WHERE account_id = ...` et le `SET NULL` / `CASCADE` lisent toute la table.
- Réflexe : pour chaque FK, se demander si elle est déjà couverte par un index existant (un index composite sert pour sa 1re colonne), sinon en créer un.

**Reste à trancher en écrivant `V1__init.sql`** : ordre de création des tables, `ON DELETE` du compte et du user, index sur les FK.


### Les 5 tables de la V1 (séance 2, version de travail)

```
users
- id : UUID, clé primaire
- email : texte, obligatoire, UNIQUE
- password_hash : texte, obligatoire      (jamais le mot de passe en clair, seulement l'empreinte BCrypt)
- display_name : texte, obligatoire        (ou nom + prénom : choix à trancher, cf. minimisation RGPD)
- created_at : TIMESTAMPTZ, défaut now()

accounts
- id : UUID
- user_id → users, obligatoire
- name : texte, obligatoire               (« Compte courant », « Livret A »)
- type : COURANT / EPARGNE / ESPECES      (CHECK)
- currency : CHAR(3), défaut 'EUR'
- initial_balance : NUMERIC(12,2), défaut 0
- created_at : TIMESTAMPTZ
- UNIQUE (user_id, name)

categories
- id : UUID
- user_id → users, obligatoire
- name : texte, obligatoire               (« Courses », « Loyer », « Salaire »)
- kind : DEPENSE / REVENU                  (CHECK)
- color : texte, optionnel
- UNIQUE (user_id, name)

transactions
- id : UUID
- user_id → users, obligatoire
- account_id → accounts, obligatoire
- category_id → categories, OPTIONNEL     (« non catégorisée » → futur job de l'IA)
- kind : DEPENSE / REVENU
- amount : NUMERIC(12,2), CHECK (amount > 0)
- date : DATE                              (jour de l'opération)
- label : texte, obligatoire
- note : texte, optionnel
- created_at : TIMESTAMPTZ                 (moment de la saisie)

budgets
- id : UUID
- user_id → users, obligatoire
- category_id → categories, obligatoire
- month : DATE (toujours le 1er du mois)
- amount_limit : NUMERIC(12,2), CHECK (> 0)
- UNIQUE (user_id, category_id, month)     (un seul budget par catégorie et par mois)
```

**Les liens**
```
users 1 ──< N accounts
users 1 ──< N categories
users 1 ──< N transactions
users 1 ──< N budgets
accounts   1 ──< N transactions
categories 1 ──< N transactions   (côté transaction : optionnel)
categories 1 ──< N budgets
```

### Règle 1 : jamais de `float` / `double` pour de l'argent
- Les flottants sont **approximatifs** : en Java, `0.1 + 0.2 = 0.30000000000000004`. Sur des milliers d'opérations, des centimes se perdent.
- En base : `NUMERIC(12,2)` (nombre exact, 2 décimales, jusqu'à 9 999 999 999,99).
- En Java : `BigDecimal` (et comparer avec `compareTo`, pas `equals` : `2.0` et `2.00` ne sont pas `equals`).
- Question d'entretien très classique.

### Règle 2 : une information à un seul endroit
- Si une info est stockée deux fois, un jour les deux versions se contredisent.
- **Le solde d'un compte n'est pas stocké** : solde = `initial_balance` + revenus − dépenses, calculé depuis les transactions.
  Stocker le solde obligerait à le mettre à jour à chaque transaction ; une mise à jour ratée = solde faux pour toujours.
- **La devise est sur le compte, pas sur la transaction** : une transaction hérite de la devise de son compte.
- Exception assumée : `user_id` est répété sur toutes les tables (on pourrait le retrouver via le compte).
  C'est volontaire : chaque requête filtre simplement `WHERE user_id = <moi>` → sécurité plus simple (anti-IDOR).
  Une redondance doit toujours être un **choix conscient et justifié**, jamais un accident.

### Règle 3 : la clé étrangère est toujours du côté « plusieurs »
- Un compte a plusieurs transactions → c'est `transactions` qui a une colonne `account_id`.
- `accounts` n'a **pas** de colonne « transactions » : une colonne SQL contient une seule valeur, pas une liste.
- Pour avoir les transactions d'un compte : `SELECT * FROM transactions WHERE account_id = ...`.

### Les types de relations
| Relation | Exemple | Comment en SQL |
| --- | --- | --- |
| **Un à plusieurs** (1-N) | un compte → plusieurs transactions | clé étrangère côté « plusieurs » (`transactions.account_id`) |
| **Un à un** (1-1) | un user → un seul profil détaillé | clé étrangère + `UNIQUE` dessus (rare) |
| **Plusieurs à plusieurs** (N-N) | transactions ↔ tags (une transaction a 0..n tags, un tag est sur n transactions) | une **table de liaison** `transaction_tags(transaction_id, tag_id)` |

**Comment trouver le bon type** : se poser la question **dans les deux sens**.
- « Une transaction a combien de catégories ? » → 1 (ou 0). « Une catégorie a combien de transactions ? » → plein. ⇒ **plusieurs à un**.
- Si la réponse est « plein » dans les deux sens → plusieurs à plusieurs → table de liaison.
- Si c'est « 1 » dans les deux sens → un à un (vérifier que ce n'est pas juste la même table).

**Piège (ma réflexion de la séance 2)** : une transaction avec un débiteur et un créditeur, ce n'est **pas** du plusieurs à plusieurs.
C'est **deux** clés étrangères vers la même table (`from_account_id`, `to_account_id`), chacune « plusieurs à un ».
Le nombre de comptes par transaction est fixe (2), pas variable → pas de table de liaison.
(C'est l'idée de la comptabilité en partie double : chaque mouvement a un débit et un crédit.)

### Choix de conception à savoir défendre
- **Montant toujours positif + `kind` (DEPENSE / REVENU)** plutôt qu'un montant signé : plus lisible, et la base l'impose (`CHECK (amount > 0)`).
- **`date` ≠ `created_at`** : la date de l'opération (samedi) n'est pas la date de saisie (lundi).
- **Catégorie optionnelle sur une transaction** : permet les transactions « non catégorisées » (import CSV, puis catégorisation par l'IA).
- **Budget lié à une catégorie + un mois, pas à un compte** : « 300 € de courses en octobre », quel que soit le compte qui paie.
  `month` = toujours le 1er du mois, et `UNIQUE (user_id, category_id, month)` empêche deux budgets pour la même chose.
- **Catégories copiées par utilisateur** (à l'inscription) plutôt que partagées : pas de cas particulier « catégorie système ».
- **Virements internes (courant → livret) : pas en V1.** Deux options plus tard :
  ajouter un `to_account_id` optionnel, ou créer deux transactions liées (une dépense + un revenu).
- **`password_hash`** et pas `password` : le nom protège contre l'erreur de stocker un mot de passe en clair.
- **Minimisation des données (RGPD)** : ne stocker que ce dont l'appli a besoin (un `display_name` plutôt que nom + prénom ?).

### Les contraintes SQL utilisées
- `PRIMARY KEY` : identifiant unique de la ligne.
- `NOT NULL` : colonne obligatoire, garantie **par la base**, pas seulement par le Java.
- `UNIQUE` / `UNIQUE (a, b)` : pas de doublon (sur une colonne ou une combinaison).
- `REFERENCES autre_table(id)` : clé étrangère, impossible de pointer vers une ligne qui n'existe pas.
- `CHECK (condition)` : règle métier imposée par la base (`amount > 0`, `type IN ('COURANT', ...)`).
- `DEFAULT valeur` : valeur si on n'en donne pas (`now()`, `'EUR'`, `0`).
- Principe : **mettre les règles dans la base**. Le code peut avoir un bug, la base refusera quand même une donnée invalide.

---

## Java

### Relations JPA (séance 3)

**L'idée** : en SQL, une relation = une seule colonne FK du côté « plusieurs ». En Java on manipule des objets
(`transaction.getAccount().getName()`), JPA fait le pont :
```java
// dans Transaction
@ManyToOne
@JoinColumn(name = "account_id")   // la colonne FK en base
private Account account;           // un objet, pas un UUID
```

**Lire le nom de l'annotation** : le 1er mot = la classe où je suis, le 2e = le champ.
`@ManyToOne` dans `Transaction` = « **plusieurs** transactions → **un** compte ».

| Champ | Annotation |
| --- | --- |
| `Transaction.account` | `@ManyToOne` |
| `Transaction.category` | `@ManyToOne` (pas `@OneToOne` !) |
| `Budget.category` | `@ManyToOne` |
| `Account.user` | `@ManyToOne` |
| `Account.transactions` (liste) | `@OneToMany(mappedBy = "account")` |

**Mon erreur** : `Transaction.category` en `@OneToOne`, parce que j'ai regardé dans un seul sens (« une transaction a une catégorie »).
Toujours poser la question **dans les deux sens** : une catégorie a combien de transactions ? Plein → `@ManyToOne`.
Un vrai `@OneToOne` = chaque catégorie ne servirait qu'à une seule transaction (rare : un user et sa photo de profil).

**Côté propriétaire / côté inverse**
- Une seule colonne en base, mais on peut avoir un champ des deux côtés en Java (relation **bidirectionnelle**).
- Le `@ManyToOne` (avec `@JoinColumn`) est le **propriétaire** : c'est lui qui écrit la FK.
- Le `@OneToMany(mappedBy = "account")` est le côté **inverse** : « c'est le champ `account` de l'autre classe qui gère », lecture seule.
- Un seul côté déclaré = relation **unidirectionnelle**.

**Pourquoi pas de `@OneToMany` dans le projet**
- `account.getTransactions()` ressemble à un simple getter, mais JPA lance en cachette
  `SELECT * FROM transactions WHERE account_id = ...` → 5 000 objets chargés pour rien, et rien dans le code ne le montre.
- Avec le repository, la requête est visible et limitée :
  `transactionRepository.findByAccountIdAndUserId(accountId, userId, PageRequest.of(0, 20))`.
- Phrase d'entretien : « J'évite le `@OneToMany` parce qu'un simple getter peut charger toute une collection sans que ça se voie dans le code.
  Quand j'ai besoin du côté "plusieurs", je passe par le repository, avec une pagination. »
- Nuance : acceptable quand le « plusieurs » est **petit** et **toujours** utile avec le parent (une commande et ses 3 lignes).
- **Choix du projet : relations unidirectionnelles, seulement des `@ManyToOne`.**

**À voir en séance 4, avec les requêtes SQL affichées dans la console** : `LAZY` / `EAGER`
(⚠️ `@ManyToOne` est `EAGER` par défaut → écrire `@ManyToOne(fetch = FetchType.LAZY)`), le problème N+1, `LazyInitializationException`.

---

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
