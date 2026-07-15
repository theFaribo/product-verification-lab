# Product Verification Lab

Production-oriented учебный проект для сравнения двух стеков:

- Spring MVC + JPA + JDBC;
- Spring WebFlux + Kotlin Coroutines + R2DBC.

Обе реализации используют:

- единый доменный модуль;
- единый HTTP-контракт;
- единую PostgreSQL-схему;
- одинаковые внешние stub-сервисы;
- одинаковые функциональные сценарии;
- одинаковые нагрузочные ограничения.

## Requirements

- JDK 21
- Docker Desktop
- Git

## Start PostgreSQL

```bash
docker compose up -d
```

Назначение модулей:
* domain — чистая бизнес-логика без Spring.
* db-migrations — единый набор Flyway-миграций.
* mvc-app — Spring MVC + JPA + JDBC.
* reactive-app — WebFlux + Coroutines + R2DBC.
* stubs-app — заглушки внешних систем.
* load-tests — будущие k6/Gatling-тесты.
* docs/openapi — единый HTTP-контракт.
