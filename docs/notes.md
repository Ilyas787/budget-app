# Notes d'apprentissage

Ce que j'apprends en construisant le projet, à relire avant les entretiens.
Organisé **par thème** (pas par séance) ; réorganisé chaque samedi. Dernière réorganisation : 17/10/2026 (séances 1 à 8).
Les séances où une notion a été vue sont indiquées entre parenthèses, pour retrouver le contexte dans `journal.md`.

## Sommaire

0. [Les phrases d'entretien](#0-les-phrases-dentretien)
1. [Modéliser une base : les principes](#1-modéliser-une-base--les-principes)
2. [Le schéma du projet et ses choix](#2-le-schéma-du-projet-et-ses-choix)
3. [Écrire du SQL (une migration)](#3-écrire-du-sql-une-migration)
4. [Flyway et les migrations](#4-flyway-et-les-migrations)
5. [psql](#5-psql)
6. [JPA : entités, relations, repositories, tests](#6-jpa--entités-relations-repositories-tests)
7. [Java : records, Lombok, injection](#7-java--records-lombok-injection)
8. [Docker et docker compose](#8-docker-et-docker-compose)
9. [YAML](#9-yaml)
10. [Secrets et configuration](#10-secrets-et-configuration)
11. [Environnement de dev et débogage](#11-environnement-de-dev-et-débogage)

---

## 0. Les phrases d'entretien

À savoir dire à voix haute, chacune renvoie à sa section.

| Sujet | La phrase | § |
| --- | --- | --- |
| Pas de colonne solde | « Le solde se déduit des transactions. Le stocker créerait une deuxième source de vérité qui peut se contredire au moindre oubli dans le code. Donc on le calcule. » | 1.2 |
| Argent | « Jamais de `float`/`double` : ils sont approximatifs. `NUMERIC(12,2)` en base, `BigDecimal` en Java, comparé avec `compareTo`. » | 1.3 |
| Email unique | « Je normalise côté appli, et la base garantit la règle avec un CHECK. » | 2.2 |
| UUID | « Le UUID n'empêche pas l'IDOR, c'est le filtre par utilisateur qui le fait. Le UUID ajoute une couche : on ne peut pas deviner les ids. » | 1.5 |
| Anti-IDOR en écriture | « Le filtre par utilisateur est fait dans le service, et la base le garantit avec des clés étrangères composites. » | 2.4 |
| `@OneToMany` | « J'évite le `@OneToMany` parce qu'un simple getter peut charger toute une collection sans que ça se voie dans le code. Quand j'ai besoin du côté "plusieurs", je passe par le repository, avec une pagination. » | 6 |
| `@Data` sur une entité | « Jamais : son `toString`/`equals`/`hashCode` parcourent les relations → requêtes en cascade, voire boucle infinie. » | 7 |
| Constructeur `protected` | « Obligatoire pour JPA, qui instancie l'entité puis la remplit par réflexion. `protected` : mon code ne peut pas créer d'entité incomplète, Hibernate et ses proxies peuvent l'utiliser. » | 6.1 |
| `Optional` | « `Optional` rend l'absence explicite : l'appelant est obligé de la gérer, au lieu d'un `null` qui finit en `NullPointerException`. » | 6.3 |
| Test anti-IDOR | « Je teste le cas négatif ET le cas positif : un test qui ne vérifie que l'absence passerait avec une méthode qui ne renvoie jamais rien. » | 6.4 |
| Schéma en entreprise | « Migrations versionnées (Flyway/Liquibase) et Hibernate en `validate` : `ddl-auto=update` n'a ni historique ni review, et ne sait pas renommer. » | 4 |
| Dans le doute | « Je choisis l'option qu'on peut assouplir plus tard : une erreur se corrige, des données effacées sont perdues. » | 2.5 |

---

## 1. Modéliser une base : les principes

### 1.1 Les relations (séance 2)

| Relation | Exemple | En SQL |
| --- | --- | --- |
| **Un à plusieurs** (1-N) | un compte → plusieurs transactions | clé étrangère côté « plusieurs » (`transactions.account_id`) |
| **Un à un** (1-1) | un user → un seul profil détaillé | clé étrangère + `UNIQUE` dessus (rare) |
| **Plusieurs à plusieurs** (N-N) | transactions ↔ tags | une **table de liaison** `transaction_tags(transaction_id, tag_id)` |

- **Toujours poser la question dans les deux sens.** « Une transaction a combien de catégories ? » → 1 (ou 0). « Une catégorie a combien de transactions ? » → plein. ⇒ **plusieurs à un**.
  « Plein » dans les deux sens → N-N, table de liaison. « 1 » dans les deux sens → 1-1 (vérifier que ce n'est pas la même table).
- **La clé étrangère est toujours du côté « plusieurs ».** `accounts` n'a pas de colonne « transactions » : une colonne contient une seule valeur, pas une liste.
  Pour avoir les transactions d'un compte : `SELECT * FROM transactions WHERE account_id = ...`.
- **Piège (ma réflexion)** : une transaction avec un débiteur et un créditeur n'est **pas** du N-N. C'est **deux** FK vers la même table
  (`from_account_id`, `to_account_id`), chacune « plusieurs à un » : le nombre de comptes est fixe (2), pas variable.
  C'est l'idée de la comptabilité en partie double.

### 1.2 Une information à un seul endroit (séances 2, 3)

- Une info stockée deux fois finit par se contredire.
- **Le solde n'est pas stocké** : `solde = initial_balance + revenus − dépenses`. Le stocker obligerait à le mettre à jour sur chaque chemin
  (créer, modifier, changer de compte, supprimer…) ; un seul oubli = solde faux pour toujours, et on ne sait plus lequel croire.
  Prix : un `SUM` à l'affichage (quelques ms avec un index). Stocker un calcul pour la perf = **dénormalisation**, seulement comme choix conscient.
  Ne pas confondre avec l'**atomicité** (« crédité d'un côté, pas débité de l'autre ») : ça, c'est `@Transactional`.
- **La devise est sur le compte**, pas sur la transaction : la transaction en hérite.
- **Redondances assumées** (choix conscients, jamais des accidents) :
  - `user_id` sur toutes les tables (on pourrait le retrouver via le compte) → chaque requête filtre `WHERE user_id = moi`, sécurité simple.
  - `kind` sur la transaction **et** sur la catégorie (voir 2.2).

### 1.3 L'argent (séances 2, 4)

- `float`/`double` sont **approximatifs** : `0.1 + 0.2 = 0.30000000000000004`. Sur des milliers d'opérations, des centimes se perdent.
- En base : **`NUMERIC(12,2)`** (exact, 2 décimales, jusqu'à 9 999 999 999,99). En Java : **`BigDecimal`**,
  comparé avec `compareTo` (pas `equals` : `2.0` et `2.00` ne sont pas `equals`).
- `INT` est pire : il **arrondit en silence** (1 234,56 → 1 235), sans erreur.

- **Pourquoi `BigDecimal` est exact** (séance 7) : pas de virgule stockée, deux **entiers** : `12,34 = 1234 × 10⁻²` (`unscaledValue` = `BigInteger` illimité, `scale`). Base 10 → `0,1` exact.
  Un `double` est en **base 2**, où `0,1` est infini (`0,000110011…`, comme `1/3` en base 10) → tronqué.
  ⚠️ `new BigDecimal(0.1)` recopie l'erreur du `double` → `new BigDecimal("0.10")`, `BigDecimal.valueOf(...)`, `BigDecimal.ZERO`.
  Division : `1/3` → `ArithmeticException` sans règle d'arrondi (`divide(x, 2, RoundingMode.HALF_UP)`) : en finance, c'est moi qui choisis l'arrondi.

### 1.4 Les dates (séance 3)

- **Un jour → `DATE`, un instant → `TIMESTAMPTZ`.**
- `TIMESTAMPTZ` stocke un **instant** (converti en UTC) : même sens sur mon Mac à Paris et sur un serveur AWS en UTC. En Java : `Instant`.
  Malgré son nom, il ne garde pas le fuseau d'origine. `TIMESTAMP` (sans TZ) = une heure sans fuseau, ambiguë.
- Piège : un 1er novembre stocké en `TIMESTAMPTZ` = 01/11 00h Paris = 31/10 23h UTC → rangé en **octobre** côté serveur.
- **`date` ≠ `created_at`** : courses samedi 31/10 saisies lundi 02/11 → `date` = 31/10 (compte dans le budget d'octobre),
  `created_at` = l'instant de saisie (technique : débogage, départager deux transactions du même jour).

### 1.5 Les identifiants : UUID (séance 3)

- 128 bits (v4 : 122 aléatoires), générables n'importe où sans demander à la base. Un compteur (`BIGSERIAL`) oblige à demander le suivant à la base.
- Avantages : impossible à deviner / énumérer (`/accounts/42` → `/accounts/43`), ne révèle pas le nombre d'utilisateurs.
- ⚠️ **Le UUID ne protège PAS de l'IDOR.** La vraie protection : `findByIdAndUserId(id, moi)` (une ressource d'un autre → 404). Le UUID = **défense en profondeur**.
- Coût : 16 octets au lieu de 8, illisible, index rempli dans le désordre (le UUID **v7**, trié par date, corrige ça ; Postgres 17 ne génère que du v4).
- En SQL : `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` (intégré depuis Postgres 13).

### 1.6 Mettre les règles dans la base (séances 2 à 5)

Le code peut avoir un bug, la base refusera quand même une donnée invalide.

| Contrainte | Rôle |
| --- | --- |
| `PRIMARY KEY` | identifiant unique de la ligne |
| `NOT NULL` | colonne obligatoire (sans = optionnelle) |
| `UNIQUE (a, b)` | pas de doublon sur une colonne ou une **combinaison** |
| `REFERENCES` / `FOREIGN KEY` | impossible de pointer vers une ligne qui n'existe pas |
| `CHECK (condition)` | règle métier : `amount > 0`, `type IN (...)` |
| `DEFAULT valeur` | valeur si on n'en donne pas |

- Un `CHECK` ne voit que **sa propre ligne** : il ne peut pas aller vérifier dans une autre table (ex. « un budget seulement sur une catégorie DEPENSE » → règle portée par le Java).
- **Pas de `DEFAULT` sans valeur « naturelle »** (séance 5) : `created_at` (maintenant) et `currency` (EUR) en ont une.
  `amount` non : `DEFAULT 0` + `CHECK (amount > 0)` serait toujours refusé. Sans défaut, un `INSERT` sans montant échoue clairement (`violates not-null constraint`).
- **Texte libre pour l'humain, valeur contrôlée pour le code** : `name` (« Cagnotte ») ne permet aucun traitement ; `type` (3 valeurs) permet `GROUP BY`, icônes, règles.

---

## 2. Le schéma du projet et ses choix

La source de vérité est `backend/src/main/resources/db/migration/V1__init.sql` (écrit à la main, séances 4 et 5).

### 2.1 Vue d'ensemble

```
users        id, email (minuscules, unique), password_hash, display_name, created_at
accounts     id, user_id, name, type (COURANT/EPARGNE/ESPECES), currency CHAR(3) 'EUR', initial_balance NUMERIC(12,2) 0, created_at
categories   id, user_id, name, kind (DEPENSE/REVENU), color (optionnel)
transactions id, user_id, account_id, category_id (optionnel), kind, amount > 0, date DATE, label, note (optionnel), created_at
budgets      id, user_id, category_id, month DATE (1er du mois), amount_limit > 0
```

```
users 1 ──< N accounts / categories / transactions / budgets
accounts   1 ──< N transactions
categories 1 ──< N transactions   (côté transaction : optionnel)
categories 1 ──< N budgets
```

Ordre de création (une table ne peut pointer que vers des tables déjà créées) : `users` → `accounts`, `categories` → `transactions`, `budgets`.

### 2.2 Les choix, table par table (séance 3)

**`users`**
- `display_name` seul, pas nom + prénom : l'appli n'en a besoin que pour « Salut Ilyas ». **Minimisation RGPD** : une donnée non stockée ne peut pas fuiter.
- `password_hash` et pas `password` : le nom protège de l'erreur de stocker un mot de passe en clair (on stocke l'empreinte BCrypt).
- **Email** : pour Postgres, `Ilyas@gmail.com` ≠ `ilyas@gmail.com`, donc `UNIQUE` seul laisse passer les deux.
  - Java normalise : `email.trim().toLowerCase()` à l'inscription **et** au login.
  - La base garantit : `CHECK (lower(email) = email)` + `UNIQUE (email)`.
  - Alternative : index unique sur `lower(email)`, quand on ne maîtrise pas les données qui arrivent.
  - Longueur : jusqu'à 254 caractères (64 = seulement la partie avant le `@`).

**`accounts`**
- Pas de colonne solde (1.2). `initial_balance` sans `CHECK (> 0)` : un compte peut démarrer à découvert.
- `UNIQUE (user_id, name)` : moi et ma sœur pouvons chacun avoir un « Compte courant », mais pas moi deux fois.
- `type` gardé (valeur contrôlée, voir 1.6 et 2.3). `currency CHAR(3)` : code **ISO 4217** (`EUR`, `USD`, `MAD`).

**`categories`**
- **Copiées par utilisateur** à l'inscription plutôt que partagées (`user_id = NULL`) : pas de cas particulier dans le filtre anti-IDOR
  (sinon `user_id = moi OU user_id IS NULL` + règle « lecture seule »), et chacun peut renommer / recolorer. Coût : ~10 lignes par user.
- **Par user, pas par compte** : « Courses » est la même catégorie, payée en carte ou en espèces. La catégorie dit à quoi sert l'argent, le compte d'où il sort ; la transaction fait le lien.
- `kind` sur la catégorie : la liste « nouvelle dépense » ne propose pas « Salaire », pas de budget sur un revenu.

**`transactions`**
- **Montant toujours > 0 + `kind`** plutôt que montant signé. Les deux se défendent :
  signé = solde plus simple, colle aux relevés CSV, gère bien les remboursements ;
  `kind` = sens écrit en toutes lettres (enum Java), pas de convention « négatif = sortie ». **Choix : `kind`.**
- `kind` **sur la transaction ET sur la catégorie** : obligatoire sur la transaction car `category_id` est **optionnel**.
  Incohérence possible acceptée (remboursement Zara = REVENU rangé dans « Vêtements » qui est DEPENSE).
- `category_id` optionnel : transactions « non catégorisées » (import CSV puis catégorisation par l'IA, plus tard).

**`budgets`**
- Lié à une catégorie + un mois, **pas à un compte** : 300 € de courses, quel que soit le compte qui paie.
- `month` = toujours le 1er du mois, imposé par `CHECK (EXTRACT(DAY FROM month) = 1)` : sinon `2026-10-01` et `2026-10-15`
  contourneraient `UNIQUE (user_id, category_id, month)` → deux budgets « Courses » en octobre.

**Reporté** (voir `idees.md`) : virements internes (`to_account_id` optionnel ou deux transactions liées), archivage d'un compte, tags (table de liaison).

### 2.3 Limiter une colonne à une liste de valeurs (séance 3)

| Option | Pour | Contre |
| --- | --- | --- |
| `CHECK (type IN (...))` ✅ choisi | simple, Hibernate sans config | changer la liste = supprimer + recréer la contrainte (migration V2) |
| `ENUM` Postgres | ajouter une valeur facile | retirer / renommer pénible, config Hibernate en plus |
| Table de référence + FK | valeurs avec données (libellé, couleur), ajout sans migration | une jointure en plus ; c'est le cas des **catégories** |

Côté Java : `enum` + `@Enumerated(EnumType.STRING)`. **Jamais `ORDINAL`** (stocke 0, 1, 2 : insérer une valeur dans l'enum change le sens des données).

### 2.4 Anti-IDOR en écriture : les FK composites (séances 3, 4, 5)

**Le problème** : `account_id REFERENCES accounts (id)` vérifie que le compte **existe**, pas **à qui il est**.
Ma sœur peut faire `POST /transactions` avec l'id de mon compte → ligne `(user sœur, mon compte, 500 €)` acceptée → mon solde faussé.

**Deux protections, il faut les deux :**
1. **Java (obligatoire)** : `findByIdAndUserId(accountId, moi)` (et pour `categoryId`) à la création **et à la modification** (un PUT qui change de compte) → 404 propre.
2. **Base (le filet)** : la FK porte sur le **couple** → la base refuse même si le Java a un bug, sur tous les chemins (POST, PUT, import CSV, scripts), y compris ceux pas encore écrits.

**FK simple ou composite : d'où vient la valeur ?**

| Colonne | Qui fournit la valeur ? | Pointe vers quelque chose qui a un propriétaire ? | FK |
| --- | --- | --- | --- |
| `user_id` | le backend (token de l'utilisateur connecté) | non, c'est le propriétaire | simple : `REFERENCES users (id) ON DELETE CASCADE` sur la ligne |
| `account_id` | le client (JSON de la requête) | oui | composite, en bas de la table |
| `category_id` | le client (JSON de la requête) | oui | composite, en bas de la table |

```sql
-- dans transactions
CONSTRAINT transactions_account_fk  FOREIGN KEY (account_id, user_id)  REFERENCES accounts (id, user_id),
CONSTRAINT transactions_category_fk FOREIGN KEY (category_id, user_id) REFERENCES categories (id, user_id) ON DELETE SET NULL (category_id)
```

- Les parenthèses vont **par paires, dans l'ordre** : `account_id` ↔ `id`, `user_id` ↔ `user_id`, dans la **même ligne** de `accounts`.
- Exemple : accounts = (A1, ilyas), (B1, sœur). Transaction (user sœur, compte A1) → couple (A1, sœur) absent → refusée.
- Une colonne de la FK à `NULL` (transaction sans catégorie) → Postgres ne vérifie pas → l'optionnel marche.

**Deux sortes de `UNIQUE`**

| Sorte | Rôle | Contient `id` ? | Dans le projet |
| --- | --- | --- | --- |
| règle métier | empêcher un doublon | **jamais** (avec `id`, déjà unique, elle ne bloquerait rien) | `users_email_uq`, `accounts_name_uq`, `categories_name_uq`, `budgets_user_category_month_uq` |
| technique | cible d'une FK composite | oui | `accounts_id_user_uq (id, user_id)`, `categories_id_user_uq (id, user_id)` |

La technique est **redondante pour la logique** (`id` est unique, donc le couple aussi) mais **nécessaire pour Postgres** : une FK doit pointer vers
des colonnes avec exactement un `UNIQUE`/`PRIMARY KEY` déclaré (sinon `there is no unique constraint matching given keys`), ce qui lui donne l'index pour vérifier vite.
À mettre seulement sur les tables **cibles** d'une FK composite : `budgets` n'en est pas une.

### 2.5 `ON DELETE` : que deviennent les lignes qui pointent vers une ligne supprimée ? (séances 3, 4)

| Option | Effet |
| --- | --- |
| `NO ACTION` (défaut, on n'écrit rien) / `RESTRICT` | refuse la suppression |
| `CASCADE` | supprime aussi les lignes qui pointent dessus |
| `SET NULL` | garde les lignes, vide leur FK |

| FK | Choix | Pourquoi |
| --- | --- | --- |
| tous les `user_id` | `CASCADE` | RGPD, droit à l'effacement : les données sont à l'utilisateur, les garder serait illégal |
| `transactions.account_id` | rien (`NO ACTION` = refus) | 2 ans d'historique ne partent pas sur un clic ; en V1 il n'y a pas de front pour confirmer. `CASCADE` + confirmation forte en novembre (`idees.md`) |
| `transactions.category_id` | `SET NULL (category_id)` | l'historique reste, les transactions deviennent « non catégorisées » |
| `budgets.category_id` | `CASCADE` | un budget sans sa catégorie ne veut plus rien dire |

- Règle : **de l'argent qui a vraiment bougé → on garde ; un objectif rattaché → on supprime avec.**
- **Dans le doute, l'option qu'on peut assouplir plus tard** : refus → `CASCADE` = une migration de 2 lignes ; des données effacées = perdues pour toujours.
- ⚠️ `SET NULL` tout court sur une FK composite vide **toutes** ses colonnes (dont `user_id`, NOT NULL → erreur) → préciser `SET NULL (category_id)` (Postgres 15+).
- ⚠️ **`NO ACTION` plutôt que `RESTRICT`** : supprimer un user fait cascader comptes + transactions dans la même opération.
  `RESTRICT` vérifie tout de suite et peut bloquer selon l'ordre de la cascade ; `NO ACTION` vérifie à la **fin** → passe.

### 2.6 Les index (séances 3, 4, 5)

**Un index, c'est l'index d'un livre / un dictionnaire** : au lieu de lire toute la table (*full scan*), Postgres va directement aux bonnes lignes.
Prix : de la place, et une mise à jour à chaque écriture → seulement là où on **cherche** souvent.

- `PRIMARY KEY` et `UNIQUE` créent un index automatiquement. **Une FK, non.** Sans index, `WHERE account_id = ...` et les `SET NULL`/`CASCADE` lisent toute la table.
- **Un index composite sert pour sa première colonne seule, pas pour la deuxième** (l'annuaire trié par (nom, prénom) trouve vite tous les « Dupont », jamais toutes les « Marie »).
  D'où `UNIQUE (user_id, name)` et pas `(name, user_id)` : il range les lignes d'un même user ensemble et sert aussi à `WHERE user_id = ?`.
- Réflexe : pour chaque FK, est-elle déjà couverte par un index qui **commence** par cette colonne ? Sinon, en créer un.

| FK | Couverte par |
| --- | --- |
| `accounts.user_id`, `categories.user_id` | `(user_id, name)` |
| `budgets.user_id` | `(user_id, category_id, month)` |
| `transactions.user_id` | **créé** : `transactions_user_date_idx (user_id, date)` (sert aussi « mes transactions par date / par mois ») |
| `transactions.account_id` | **créé** : `transactions_account_idx` |
| `transactions.category_id` | **créé** : `transactions_category_idx` |
| `budgets.category_id` | **créé** : `budgets_category_idx` |

---

## 3. Écrire du SQL (une migration)

### 3.1 De pgAdmin au SQL (séance 4)

| Dans pgAdmin | En SQL |
| --- | --- |
| Create → Table | `CREATE TABLE nom (...);` |
| une ligne de l'onglet Columns | `nom_colonne TYPE,` |
| case « Not NULL? » | `NOT NULL` |
| champ « Default » | `DEFAULT valeur` |
| case « Primary key? » | `PRIMARY KEY` |
| Constraints → Unique / Check | `CONSTRAINT nom UNIQUE (col)` / `CONSTRAINT nom CHECK (condition)` |
| Constraints → Foreign Key | `REFERENCES autre_table (col) ON DELETE ...` sur la ligne (1 colonne) ou `CONSTRAINT nom FOREIGN KEY (...) REFERENCES ...` en bas (plusieurs) |
| (pas d'équivalent simple) | `CREATE INDEX nom ON table (colonnes);` après les `CREATE TABLE` |

### 3.2 Pièges de syntaxe rencontrés (séances 4, 5)

- Une virgule entre chaque ligne, **pas** après la dernière ; un `;` à la fin de chaque instruction.
- `TIMESTAMPTZ` (pas `TIMESTAMPZ`). Pour un jour : `date DATE NOT NULL` (oui, ça se lit bizarrement).
- Un `CHECK` contient une **condition** vrai/faux : `CHECK (lower(email) = email)`, pas `CHECK (email)`. Pour une liste : `CHECK (type IN ('A', 'B'))`.
- Deux contraintes ne peuvent pas avoir le même nom (`constraint ... already exists`).

### 3.3 Conventions du projet

- Mots-clés SQL en MAJUSCULES, noms en minuscules (Postgres met de toute façon les noms non quotés en minuscules). Tables au pluriel.
- Contraintes nommées `table_colonnes_type` : `users_email_uq`, `accounts_type_chk`, `transactions_account_fk`, `transactions_account_idx`.
  Le nom apparaît dans les messages d'erreur (`violates check constraint "users_email_lower_chk"`) ; une contrainte non nommée reçoit un nom généré (`budgets_user_id_fkey`), moins parlant. Et on peut la supprimer plus tard par son nom.
- Une FK s'appelle `<entité au singulier>_id` : `user_id`, pas `users_id`. Un même nom partout (`email` en base = en Java = en JSON).
- `TEXT` partout : en Postgres, `TEXT` et `VARCHAR(n)` sont stockés pareil (mêmes perfs), `(n)` ajoute juste une limite. Longueurs validées en Java (`@Size`).

---

## 4. Flyway et les migrations

- **Pourquoi un fichier plutôt que pgAdmin** (séance 4) : une table créée par clics n'existe que dans ma base locale. Le fichier est versionné avec le code et Flyway l'applique partout (mon Mac, Testcontainers, CI, AWS) : le schéma devient du code.
- Emplacement : `backend/src/main/resources/db/migration/V1__init.sql`. Appliqué au démarrage de l'appli : log `Successfully applied 1 migration ... now at version v1`.
- **Checksum** : Flyway enregistre une empreinte de chaque fichier dans `flyway_schema_history`. Si un fichier appliqué change, l'appli refuse de démarrer.
  - Une migration **partagée** (autre machine, CI, prod) ne se modifie **jamais** : on écrit une `V2__...sql` (ex. ajouter `CARTE_CREDIT` = supprimer et recréer la contrainte).
  - Tant qu'elle n'existe **que sur ma base locale** : je la modifie, `docker compose down -v`, `docker compose up -d`, je relance l'appli.
  - Donc : ne pas lancer l'appli tant que le fichier n'est pas fini.
- **Migration transactionnelle** (Postgres) : une erreur dans le fichier annule **tout** le fichier ; on corrige et on relance, sans rien nettoyer.
- `spring.jpa.hibernate.ddl-auto=validate` : c'est Flyway qui crée le schéma, Hibernate vérifie seulement qu'il correspond aux entités.

- **Ma première `V2`** (séance 7, `V2__currency_en_text.sql`) : `V1` étant mergée sur `main`, on n'y touche plus, on ajoute :
  ```sql
  ALTER TABLE accounts ALTER COLUMN currency TYPE TEXT;
  ALTER TABLE accounts ADD CONSTRAINT accounts_currency_chk CHECK (char_length(currency) = 3 AND currency ~ '^[A-Z]{3}$');
  ```
  Pourquoi : `CHAR(3)` ≠ `varchar` attendu par un `String` (erreur de validation) ; cohérence « `TEXT` partout » ; Postgres déconseille `CHAR(n)` (complète avec des espaces).
  `AND` combine deux conditions dans un `CHECK` (deux contraintes séparées = messages d'erreur plus précis). `upper(x) = x` laisse passer `'E1R'` ; la regex `~ '^[A-Z]{3}$'` impose 3 lettres majuscules (le `char_length` devient redondant).
- **`checksum mismatch for migration version 1`** (séance 8) : le fichier `V1` a été modifié **par accident** (un copier-coller dans le mauvais onglet).
  `git status` → `M V1__init.sql`, `git diff` → la ligne cassée, `git restore <fichier>` → réparé. Flyway a joué son rôle : sans checksum, deux bases auraient divergé en silence.
- **En entreprise** : très majoritairement « migrations d'abord » (Flyway ou Liquibase, Hibernate en `validate`/`none`). `ddl-auto=update` est banni en prod
  (ne sait ni renommer ni supprimer, aucun historique ni review, ignore les contraintes fines, modifie la prod au démarrage). Variante : générer un script depuis les entités puis le corriger à la main.
  À regarder sur ABACUS : `db/migration` (Flyway) ou `db/changelog` (Liquibase), et la valeur de `ddl-auto`.

---

## 5. psql

Console SQL dans le conteneur : `docker compose exec db psql -U budget -d budget` (séances 2, 5).

| Commande | Effet |
| --- | --- |
| `\dt` | liste les tables |
| `\d transactions` | colonnes, contraintes, index d'une table |
| `SELECT * FROM users;` | contenu (ne pas oublier le `;`) |
| `q` | sortir de l'afficheur `(END)` |
| `\q` | quitter psql |

- Invite `budget-#` (avec un tiret) = il manque le `;`.
- Test rapide sans entrer : `docker compose exec db psql -U budget -d budget -c "select version();"`.
- Test de contrainte : `INSERT` avec `'Ilyas@Test.fr'` → `violates check constraint "users_email_lower_chk"`.

---

## 6. JPA : entités, relations, repositories, tests

(séances 3, 6, 7, 8)

### 6.1 Une entité

**Une entité = une classe Java liée à une table** (un objet = une ligne, un champ = une colonne).
Avec `ddl-auto=validate`, Hibernate vérifie au démarrage que chaque entité colle à la table créée par Flyway :
`Started BudgetApplication` = l'entité est bonne ; sinon l'erreur nomme la colonne fautive (voir 11.2 pour la lire).

```java
@Entity
@Table(name = "users")          // obligatoire : sinon Hibernate cherche "user", mot réservé en Postgres
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)   // UUID généré en Java juste avant l'INSERT (id = null avant le save)
    private UUID id;                                   // java.util.UUID ↔ uuid Postgres
    private String passwordHash;                       // camelCase → password_hash automatiquement
    private Instant createdAt = Instant.now();         // valeur donnée en Java (piège ci-dessous)

    protected User() {}                                // pour JPA
    public User(String email, String passwordHash, String displayName) {
        this.email = email.trim().toLowerCase(Locale.ROOT);   // normalisation de l'email
        ...
    }
    // getters seulement (générés avec Cmd + N) : pas de setter sur id ni createdAt
}
```

- **Piège des `DEFAULT`** : Hibernate envoie **toutes** les colonnes dans l'`INSERT`, même à `null`. Un `DEFAULT` ne s'applique que quand la colonne **manque** de l'`INSERT` → jamais ici, et le `NOT NULL` refuse.
  → Donner la valeur en Java pour **chaque** colonne qui a un `DEFAULT` : `createdAt = Instant.now()`, `currency = "EUR"`, `initialBalance = BigDecimal.ZERO`.
- **Le constructeur sans argument, en `protected`** :
  - JPA crée l'objet **vide** puis remplit les champs par **réflexion** (il ne sait pas quel paramètre va à quelle colonne).
  - Pas `public` : mon code ne peut pas créer une entité incomplète (`new User()` sans email).
  - Pas `private` : pour le `LAZY`, Hibernate crée des **proxies** = des sous-classes de l'entité, qui doivent appeler le constructeur parent.
  - Phrase d'entretien : « Le constructeur sans argument est obligatoire pour JPA, qui instancie l'entité puis la remplit par réflexion. Je le mets en `protected` pour que mon code ne puisse pas créer d'entité incomplète, tout en laissant Hibernate et ses proxies l'utiliser. »
- **Un champ optionnel** (`Category.color`) peut être `null` sans problème (colonne nullable, pas de `DEFAULT` à contourner) ; c'est à la **lecture** qu'il faut gérer l'absence.
  Option : deux constructeurs chaînés (`this(user, name, kind, null)`) ; ou un getter `Optional<String>` (`Optional.ofNullable(color)`) — le **champ** reste un `String`.
- **Inutiles ici** : `@Column(name = ...)` quand le nom suit la convention ; `@Column(nullable = false)` et `@Table(indexes = ...)`, qui ne servent que si Hibernate crée les tables (`validate` ne vérifie ni le `NOT NULL` ni les index). La règle vit dans la base (garantie) et dans le DTO (`@NotBlank`, `@Email` → 400 clair).
- **Normaliser l'email dans le constructeur** : impossible de créer un `User` avec des majuscules. `Locale.ROOT` évite le piège du turc (`"I".toLowerCase()` ≠ `"i"`). Il faudra aussi normaliser l'email reçu au **login**.

### 6.2 Les relations

**L'idée** : en SQL, une relation = une colonne FK du côté « plusieurs ». En Java on manipule des objets, JPA fait le pont.

```java
@ManyToOne(fetch = FetchType.LAZY)   // plusieurs comptes → un user ; ⚠️ EAGER par défaut
@JoinColumn(name = "user_id")        // la colonne FK dans MA table (accounts)
private User user;                   // un objet, pas un UUID

@Enumerated(EnumType.STRING)         // écrit le NOM de la valeur ("COURANT") → identique au CHECK de la base. Jamais ORDINAL.
private AccountType type;            // enum plutôt que String : le compilateur refuse "Courant"
```

- **Lire le nom de l'annotation** : 1er mot = la classe où je suis. `@ManyToOne` dans `Account` = « plusieurs comptes → un user ».
  Poser la question **dans les deux sens** (mon erreur : `Transaction.category` en `@OneToOne`).
- **Comment Hibernate fait la jointure** : `@JoinColumn` donne la colonne de **ma** table ; le **type** du champ (`User`) donne l'entité visée, dont `@Table` et `@Id` donnent la cible → `accounts.user_id` → `users.id`.
  Sans `@JoinColumn`, nom par défaut = `<champ>_<id cible>` = `user_id` (on le garde pour être explicite).
- **`LAZY` en pratique** : au chargement d'un compte, Hibernate crée un proxy de `User` qui ne connaît que son id. `getUser().getId()` → aucune requête ; `getUser().getEmail()` → `SELECT … FROM users WHERE id = ?`.
- **`@ManyToOne` plutôt que `UUID userId`** : les deux se défendent, mais `userId`, `accountId`, `categoryId` sont tous des `UUID` → les inverser compile sans erreur ; avec des objets, le compilateur protège.
  Créer un `Account` depuis l'id du token : `userRepository.getReferenceById(userId)` donne un proxy **sans requête SQL**.
- **Propriétaire / inverse** : le `@ManyToOne` (avec `@JoinColumn`) écrit la FK ; un `@OneToMany(mappedBy = ...)` serait l'inverse, en lecture seule. Les deux = bidirectionnelle.
- **Pas de `@OneToMany` dans le projet** : `account.getTransactions()` ressemble à un getter mais lance en cachette `SELECT * FROM transactions WHERE account_id = ...` (5 000 objets, invisible dans le code).
  On passe par le repository, visible et paginé. Nuance : acceptable si le « plusieurs » est petit et toujours utile (une commande et ses 3 lignes).
  Phrase d'entretien dans le §0. **Choix du projet : unidirectionnel, seulement des `@ManyToOne(fetch = LAZY)`.**
- **Un enum partagé** (`DEPENSE`/`REVENU` pour les catégories et les transactions) : un seul, rangé dans le domaine qui le « possède » → `transaction/TransactionKind`.
  Pas de package « tous les enums » (rangement par nature technique au lieu de par fonctionnalité) ; et `enum` est un mot réservé, impossible comme nom de package.

### 6.3 Les repositories (Spring Data)

```java
public interface AccountRepository extends JpaRepository<Account, UUID> {   // INTERFACE, <entité, type de l'id>
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);   // un compte, seulement s'il est à moi (anti-IDOR)
    List<Account> findAllByUserId(UUID userId);                  // mes comptes
}
// UserRepository : Optional<User> findByEmail(String email) ; boolean existsByEmail(String email) (409 à l'inscription)
```

- Une **interface** : Spring génère l'implémentation au démarrage. `JpaRepository` donne gratuitement `save`, `saveAll(List.of(a, b))`, `findById`, `findAll`, `deleteById`, `count`…
- La requête est déduite du **nom** : mot-clé (`find…By`, `exists…By`, `count…By`) + nom du **champ Java**. `findByEmail` plutôt que `findUserByEmail`.
- **Traversée de relation** : pas de champ `userId` → Spring coupe `User` + `Id` → `user.id` → `where a.id = ?1 and a.user_id = ?2`, filtré directement sur la colonne FK (pas de jointure). Explicite : `findByIdAndUser_Id`.
- Le **nom dit le type des paramètres** : `...AndUserId` → un `UUID`, pas un `User`.
- **Types de retour** : `Optional` = 0 ou 1 résultat (l'appelant est **obligé** de gérer l'absence : `orElseThrow`, `isPresent`) ; `List` = 0 à n (liste vide, jamais `null`). Un `User` nu = `null` possible → `NullPointerException` un jour.
- Spring analyse tous les noms au **démarrage** : une faute → `No property 'usrId' found for type 'Account'`. Requête trop compliquée pour un nom lisible → `@Query` (dashboard).
- **Les index ne se déclarent pas ici** : ils vivent dans la base (Flyway), et le **planificateur** de Postgres les choisit tout seul pour chaque requête.
  Ce qui compte : écrire des méthodes dont le `WHERE` correspond aux index (`findAllByUserIdOrderByDateDesc` ↔ `(user_id, date)`). Vérifiable avec `EXPLAIN`.

### 6.4 Les tests de repository avec Testcontainers (séance 8)

**Le principe** : un Postgres **jetable** lancé dans Docker pour le test ; Flyway y joue `V1` et `V2` → le vrai schéma ; on crée des données, on appelle le repository, on vérifie ; tout est annulé à la fin.

```java
@DataJpaTest                                                                 // seulement la partie JPA ; chaque test est rollbacké
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // garder la vraie base, pas une base en mémoire
@Testcontainers
class AccountRepositoryTest {

    @Container                                                               // démarré avant les tests, arrêté après
    @ServiceConnection                                                       // Spring branche la connexion tout seul
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");   // static = un seul conteneur pour la classe

    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;

    @Test
    void un_user_ne_voit_pas_le_compte_d_un_autre() {
        // given : deux users, un compte à A — sauvegardés (sinon ils n'existent qu'en mémoire, id = null)
        User userA = new User("usera@test.com", "password", "userA");
        User userB = new User("userb@test.com", "password", "userB");
        Account accountA = new Account(userA, "accountA", COURANT, BigDecimal.ZERO);
        userRepository.save(userA);            // les users d'abord (le compte a une FK vers son user)
        userRepository.save(userB);
        accountRepository.save(accountA);
        // when
        Optional<Account> vuParA = accountRepository.findByIdAndUserId(accountA.getId(), userA.getId());
        Optional<Account> vuParB = accountRepository.findByIdAndUserId(accountA.getId(), userB.getId());
        // then
        assertThat(vuParA).isPresent();        // sinon le test passerait aussi avec une méthode qui ne renvoie JAMAIS rien
        assertThat(vuParB).isEmpty();
    }
}
```

- **Given / when / then** : préparer les données / appeler ce qu'on teste / vérifier.
- Toujours tester **le cas positif ET le cas négatif** : un test qui ne vérifie que `isEmpty()` passerait avec un bug qui ne renvoie jamais rien.
- `save` d'une **nouvelle** entité remplit l'`id` dans l'objet lui-même (on peut réutiliser `userA`). Bonne habitude plus tard : utiliser la valeur renvoyée (autre instance possible pour une entité déjà existante).
- `assertThat` = méthode **statique** d'AssertJ : `import static org.assertj.core.api.Assertions.assertThat;` (déjà fournie par les dépendances de test de Spring Boot).
- Les logs `Hibernate: insert ... / select ... where a1_0.id=? and a1_0.user_id=?` montrent le SQL généré : on y **voit** la traversée `user.id` → colonne `user_id`.
- Lancer : triangle vert dans IntelliJ, ou `./mvnw test` dans `backend/`. Premier lancement plus long (démarrage du conteneur) ; Docker doit tourner.
- Noms de variables qui disent ce que teste le test : `vuParA` / `vuParB` plutôt que `accountAtest`.

---

## 7. Java : records, Lombok, injection

(séance 2)

**Le problème** : une classe de données demande beaucoup de code répétitif (getters, setters, constructeurs, `equals`, `hashCode`, `toString`).

**Les records (Java 16+)** : objets de données **immuables**, intégrés à Java.
```java
public record TransactionResponse(UUID id, BigDecimal amount, String label) {}
```
Donne le constructeur complet, les accesseurs `id()`, `amount()`, `label()` (sans `get`), `equals`, `hashCode`, `toString`.
Pas de setters, pas de constructeur vide, pas de builder → **inutilisables pour les entités JPA** (JPA veut un constructeur vide et des champs modifiables).

**Lombok** : génère ce code à la compilation à partir d'annotations (`@Getter`/`@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`,
`@RequiredArgsConstructor` pour l'injection, `@Data` = le pack, `@Builder`).
⚠️ **Jamais `@Data` sur une entité JPA** : son `toString`/`equals`/`hashCode` parcourent les relations → requêtes en cascade, boucle infinie (`StackOverflowError`), bugs dans les `Set`.

| Besoin | Java 21 seul | Avec Lombok |
| --- | --- | --- |
| DTO immuable | `record` ✅ | `@Value` (inutile aujourd'hui) |
| Getters / setters (entité) | IntelliJ : Cmd+N → Getter and Setter | `@Getter` / `@Setter` |
| Constructeur vide | à écrire | `@NoArgsConstructor` |
| Builder | à écrire | `@Builder` |

**Subtilité** : sans constructeur écrit, Java ajoute un constructeur vide invisible ; dès qu'on en écrit un avec paramètres, il disparaît (JPA en a besoin → l'écrire).

**Injection par constructeur** (sans Lombok) :
```java
@Service
public class TransactionService {
    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;   // Spring injecte via ce constructeur
    }
}
```

**Choix du projet** : pas de Lombok. Records pour les DTO, entités écrites normalement. Si un jour on l'ajoute : `@Getter`/`@Setter` sur les entités, jamais `@Data`.

**`this.` implicite** (séance 8) : pour un nom seul, Java cherche d'abord une variable locale ou un paramètre, puis un champ de l'objet (`this.` implicite).
Dans un getter, `return name;` = `return this.name;`. Dans le constructeur, le **paramètre** `name` masque le champ (*shadowing*) → `this.name = name;` obligatoire (`name = name;` ne ferait rien).

**Chaînage de constructeurs** : `public Category(User user, String name, TransactionKind kind) { this(user, name, kind, null); }` → la logique reste à un seul endroit.

**`Optional<T>`, `List<T>` sont génériques** : n'importe quel objet (`Optional<String>`, `Optional<User>`), pas de primitif (`Optional<int>` impossible → `Optional<Integer>` ou `OptionalInt`).

---

## 8. Docker et docker compose

(séance 2)

**Concepts**
- **Image** : un modèle tout prêt (la « recette »), ex. `postgres:17`.
- **Conteneur** : une image qui tourne, un mini-ordinateur isolé qu'on lance, arrête, jette.
- **Port** `"5432:5432"` : un trou dans l'isolation, gauche = mon Mac, droite = le conteneur.
- **Volume** : un disque géré par Docker, séparé du conteneur, pour que les données survivent.
- **docker compose** : décrire tous les conteneurs d'un projet dans un YAML versionné ; `docker compose up` lance tout, pareil sur n'importe quelle machine.

**Les clés d'un service**

| Clé | Rôle | Exemple |
| --- | --- | --- |
| `image` | l'image Docker Hub | `postgres:17` |
| `build` | construire depuis un Dockerfile du projet (API, front : novembre) | `build: ./backend` |
| `environment` | variables passées au conteneur ; `${VAR}` lu dans `.env` | `POSTGRES_DB: ${POSTGRES_DB}` |
| `env_file` | charger tout un fichier de variables | `env_file: .env` |
| `ports` | `"mac:conteneur"` | `"5432:5432"` |
| `volumes` | `nom:chemin_dans_le_conteneur` | `budget-db-data:/var/lib/postgresql/data` |
| `healthcheck` | commande qui dit si le service est vraiment prêt | `pg_isready ...` |
| `depends_on` | ordre de démarrage ; `condition: service_healthy` attend le `healthy` | l'API attend la base |
| `restart` | redémarrer si ça plante | `unless-stopped` |

**Mon fichier commenté**
```yaml
services:
  db:                                   # le service s'appelle "db" (= son adresse réseau)
    image: postgres:17
    environment:                        # lu au 1er démarrage seulement (volume vide)
      POSTGRES_DB: ${POSTGRES_DB}       # valeurs prises dans .env
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - budget-db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]   # $$ = variable lue dans le conteneur
      interval: 5s
      timeout: 5s
      retries: 5                        # 5 échecs de suite = "unhealthy"

volumes:                                # à la racine : « ce volume existe dans le projet »
  budget-db-data:
```

- **Réseau** : entre conteneurs on s'appelle par le nom du service (`db:5432`) ; depuis mon Mac, par le port ouvert (`localhost:5432`).
- **Les deux `volumes:`** : celui du service = « ce service utilise ce volume, monté à tel chemin » ; celui de la racine = « ce volume existe ». Indenter celui du bas → « clé dupliquée ».

**Méthode pour en écrire un** : 1) un conteneur par brique ; 2) pour chacun : image, variables (doc Docker Hub), port, données à garder ; 3) `healthcheck` sur ce dont les autres dépendent, `depends_on` chez ceux qui attendent ; 4) secrets dans `.env` ; 5) tester.

**Commandes**

| Commande | Effet |
| --- | --- |
| `docker compose up -d` | crée réseau + volumes + conteneurs, démarre en arrière-plan |
| `docker compose ps` | état des conteneurs (attendre `healthy`) |
| `docker compose logs db` | ce que raconte un service |
| `docker compose down` | arrête et supprime conteneurs + réseau, **les données restent** |
| `docker compose down -v` | idem **+ supprime les volumes** : base effacée, on repart de zéro |

⚠️ Postgres ne lit `POSTGRES_USER/PASSWORD/DB` qu'au 1er démarrage : si on change le `.env`, il faut un `down -v`.

---

## 9. YAML

(séance 2)
- L'indentation définit la structure : 2 espaces par niveau, **jamais de tabulation**.
- Dans une chaîne « a:b », pas d'espace après `:` : `- volume:/chemin` et non `- volume: /chemin` (avec l'espace, YAML lit une clé/valeur).

---

## 10. Secrets et configuration

(séance 2)

| Fichier | Contenu | Sur git ? | Rôle |
| --- | --- | --- | --- |
| `.env` | vraies valeurs | **non** (`.gitignore`) | ce que Docker lit |
| `.env.example` | noms + fausses valeurs | oui | modèle : `cp .env.example .env` |

- **Les noms des variables sont publics, leurs valeurs sont privées.** Repo public = des robots scannent GitHub en continu ; un secret poussé une fois reste dans l'historique.
- Spring : `${DB_USER:budget}` = « la variable `DB_USER`, sinon `budget` » (marche en local sans config, la prod injecte les vraies valeurs).
- Plus tard sur AWS : les secrets dans SSM Parameter Store, jamais dans le code.
- Rien de personnel non plus dans un repo public (le PDF du plan est dans `.gitignore`).

---

## 11. Environnement de dev et débogage

### 11.1 Environnement (Mac)

(séances 1, 5)
- La commande `java` lancée est la première trouvée dans le `PATH`, pas forcément `JAVA_HOME`.
  Fix dans `~/.zshrc` : `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` puis `export PATH="$JAVA_HOME/bin:$PATH"`.
- Brew installe certaines versions en « keg-only » (`node@24`) : à ajouter au `PATH` à la main.
- Une appli macOS lancée depuis Téléchargements est en lecture seule → la mettre dans Applications.
- `./mvnw spring-boot:run` occupe le terminal : nouvel onglet (`Cmd + T`) ou `Ctrl + C` pour l'arrêter (la base et ses tables restent).

### 11.2 Déboguer

- **Lire une erreur de démarrage** : descendre au **dernier** `Caused by:` (la cause racine), sauter les noms de classes, lire le message.
  Ex. : `wrong column type encountered in column [currency] in table [accounts]; found [bpchar (Types#CHAR)], but expecting [varchar(255) (Types#VARCHAR)]` → `String` attend `varchar`, la base avait `CHAR(3)` (`bpchar` = nom interne Postgres).
  Corrections possibles : côté Java (`@JdbcTypeCode(SqlTypes.CHAR)` + `@Column(length = 3)`) ou côté base (choisi : `V2`, voir §4).
- **« Ça marchait avant »** : `git status` (qu'est-ce qui a changé ?) puis `git diff` (comment ?). C'est comme ça qu'on a trouvé le `V1` modifié par accident.
- **Tout est rouge d'un coup dans IntelliJ** : souvent une seule erreur sur la ligne de déclaration (`class` au lieu de `interface`).
- **Lire le SQL généré** : les logs `Hibernate: insert/select ...` (actifs dans `@DataJpaTest`) montrent exactement ce qui part en base.
- **git depuis le bon dossier** : l'invite doit afficher `budget-app %`, pas `backend %` (sinon `git add backend/...` → « ne correspond à aucun fichier »).
  `git status` avant chaque commit : IntelliJ ajoute les **premières versions** des fichiers à leur création, il faut refaire `git add` avant de commiter.
  zsh ne comprend pas les commentaires `# ...` collés dans une commande.
