# Idées pour plus tard

- Niche possible après la V1 : coloc / couple (dépenses partagées), jeunes actifs, freelances.
- Virements internes entre ses propres comptes (courant → livret) : `to_account_id` optionnel ou deux transactions liées.
- Tags sur les transactions (relation plusieurs à plusieurs → table de liaison `transaction_tags`).
- Archiver un compte (le masquer sans le supprimer), pour les comptes fermés qui ont un historique (séance 4).
- Quand la page comptes existera (novembre) : passer `transactions.account_id` en `ON DELETE CASCADE` via une migration V2, avec une confirmation forte côté front (afficher le nombre de transactions supprimées, voire faire retaper le nom du compte). En V1 : défaut `NO ACTION` = refus, tant qu'il n'y a pas de front (séance 4).
- Nettoyer `application.properties` : il lit `DB_USER` / `DB_PASSWORD` alors que le `.env` définit `POSTGRES_*`, et le mot de passe par défaut `budget_dev_pwd` est en dur dans un fichier commité (à faire en séance 21, docker-compose complet).
- Installer pgAdmin (`brew install --cask pgadmin4`), connexion sur `localhost:5432` avec les identifiants du `.env`, pour voir les tables et tester les contraintes (ex. INSERT d'un email en majuscules → refusé par `users_email_lower_chk`) (séance 5).
- Le test `contextLoads` (`BudgetApplicationTests`) se connecte à la base du docker compose : en CI (décembre) il échouera sans base → le passer sur Testcontainers comme `AccountRepositoryTest` (séance 8).
- Spring Modulith : vérifie dans les tests que les packages par fonctionnalité respectent leurs frontières (pas de cycle, pas d'accès aux classes internes d'un autre package). À regarder une fois le back fini (séance 9).
- Barre de recherche des transactions par libellé (`label`), recherche partielle et insensible à la casse : `findAllByUserIdAndLabelContainingIgnoreCase`, ou un critère texte dans les filtres de la séance 9 (séance 9 du 20/10).
