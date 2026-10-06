# Journal

## 2026-09-30 · Séance 1
- Fait : outils installés (Java 21, Node 24, gh), repo GitHub public, README, CLAUDE.md, Spring Boot démarre (/actuator/health UP), Angular 22 démarre, IntelliJ + VS Code configurés
- Bloqué / appris : conflit Java 25 vs 21 → PATH + JAVA_HOME dans .zshrc ; Node 25 vs 24 (keg-only brew) ; VS Code lancé depuis Téléchargements = lecture seule
- Idée notée : niche possible plus tard (coloc, jeunes actifs, freelances), V1 reste généraliste
- Prochaine séance (sam. 10h30) : installer Docker Desktop, docker-compose.yml avec Postgres seul (écrit à la main), ajouter JPA/PostgreSQL/Flyway/Testcontainers au pom, schéma de la base sur papier

## 2026-10-03 · Séance 2
- Fait : Postgres 17 en docker-compose (écrit à la main, healthy), .env / .env.example, JPA + Flyway + Testcontainers dans le pom, Spring connecté à la base (flyway_schema_history créée), 1re PR mergée, modèle de données des 5 tables discuté
- Bloqué / appris : YAML (indentation, espace après « : »), .env vs .env.example, pas de float pour l'argent, clé étrangère côté « plusieurs », 1-N vs N-N, pas de Lombok (records)
- Prochaine séance (mar.) : écrire V1__init.sql (les 5 tables, d'après docs/notes.md), le relire avec Claude, puis l'appliquer

## 2026-10-06 · Séance 3
- Fait : pas de SQL écrit (choix assumé) ; schéma revu table par table avec la justification de chaque choix (notes.md « Décisions finales du schéma ») ; décisions : display_name seul, email en minuscules (CHECK), kind gardé, FK composites, CHECK 1er du mois sur budgets ; 2 ON DELETE tranchés ; bases des relations JPA
- Bloqué / appris : UUID ≠ protection anti-IDOR (défense en profondeur) ; TIMESTAMPTZ = un instant, DATE = un jour ; pas de colonne solde (une seule source de vérité) ; FK simple ne vérifie pas le propriétaire → FK composites ; `@OneToOne` vs `@ManyToOne` (poser la question dans les deux sens) ; `@OneToMany` cache une requête → repository + pagination ; Postgres n'indexe pas les FK
- Prochaine séance (mer. 19h) : trancher les ON DELETE du compte et du user, puis écrire V1__init.sql (ordre des tables, gen_random_uuid(), contraintes nommées, index sur les FK), le relire avec Claude, l'appliquer
