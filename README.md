
## Requirements
```
Java 16
sbt 1.5.6
```
## Running the sample code
1. Initialize docker with postgres tables
   ```shell
   docker-compose up -d
   docker exec -i btc-logger_postgres-db_1 psql -U btc-logger -t < ddl-scripts/create_tables.sql
   docker exec -i btc-logger_postgres-db_1 psql -U btc-logger -t < ddl-scripts/create_user_tables.sql
   ```

2. Start a first node:

    ```shell
    sbt -Dconfig.resource=local1.conf run
    ```
