# Idées pour plus tard

- Niche possible après la V1 : coloc / couple (dépenses partagées), jeunes actifs, freelances.
- Virements internes entre ses propres comptes (courant → livret) : `to_account_id` optionnel ou deux transactions liées.
- Tags sur les transactions (relation plusieurs à plusieurs → table de liaison `transaction_tags`).
- Archiver un compte (le masquer sans le supprimer), pour les comptes fermés qui ont un historique (séance 4).
- Quand la page comptes existera (novembre) : passer `transactions.account_id` en `ON DELETE CASCADE` via une migration V2, avec une confirmation forte côté front (afficher le nombre de transactions supprimées, voire faire retaper le nom du compte). En V1 : défaut `NO ACTION` = refus, tant qu'il n'y a pas de front (séance 4).
- Nettoyer `application.properties` : il lit `DB_USER` / `DB_PASSWORD` alors que le `.env` définit `POSTGRES_*`, et le mot de passe par défaut `budget_dev_pwd` est en dur dans un fichier commité (à faire en séance 21, docker-compose complet).
