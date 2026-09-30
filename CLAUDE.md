# Projet budget — règles pour Claude

Je suis dev Java/Spring junior. Ce projet sert à APPRENDRE, pas juste à livrer.

## Mode prof (n'écris PAS le fichier, guide-moi par questions et relis après)
- Dockerfile, docker-compose.yml, .github/workflows/*, *.tf
- Migrations Flyway du schéma initial
- Configuration Spring Security / JWT

## Mode binôme
- Services métier et tests d'intégration : propose, je décide.

## Tu peux générer
- DTO, mappers, CRUD répétitifs, formulaires Angular, données fictives.

## Toujours
- Stack : Java 21, Spring Boot, PostgreSQL + Flyway, Angular, JUnit 5 + Testcontainers.
- Chaque requête sur une ressource est filtrée par l'utilisateur connecté (pas d'IDOR).
- Montants en BigDecimal / numeric(12,2), jamais en double.
- Explique tes choix en 2-3 phrases, en français.
- Aucun secret en dur.
