![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)
![Maven](https://img.shields.io/badge/Maven-4.0.0-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-003153)
![Redis](https://img.shields.io/badge/Redis-7-7B001C)
![Kafka](https://img.shields.io/badge/Kafka-3.7-9400D3)
![Docker](https://img.shields.io/badge/Docker-Compose-0092CC)
![AWS S3](https://img.shields.io/badge/AWS%20S3-Yandex-orange)
![Nginx](https://img.shields.io/badge/Nginx-alpine-green)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI%203.0-85EA2D)
![ClamAV](https://img.shields.io/badge/ClamAV-antivirus-EFEF34)
![VPS](https://img.shields.io/badge/Deploy-VPS-47A248)
![CD](https://github.com/Vldr22/CloudFileHub/actions/workflows/cd.yml/badge.svg?style=flat-square)
![CI](https://github.com/Vldr22/CloudFileHub/actions/workflows/ci.yml/badge.svg?style=flat-square)

# CloudFileHub
> 🚀 **Live demo:** https://cloudfilehub.duckdns.org/swagger-ui/index.html

Файловый хостинг на S3 с JWT-аутентификацией, ролевой моделью и асинхронным антивирусным сканированием.

## Архитектура и технологии
```
┌─────────────┐     ┌──────────────────┐     ┌──────────────────┐
│    Nginx    │────▶│  s3-file-service │────▶│  Yandex Object   │
│  rate limit │     │  Swagger/OpenAPI │     │  Storage (S3)    │
│    gzip     │     │  PostgreSQL 16   │     └──────────────────┘
└─────────────┘     │  Redis 7 (JWT)   │
                    └────────┬─────────┘
                       Kafka │
                    ┌────────▼─────────┐     ┌──────────────────┐
                    │ antivirus-service│────▶│      ClamAV      │  
                    │ retry + DLT      │     │   (files scan)   │
                    └──────────────────┘     └──────────────────┘
```
## Ключевые особенности

- **Публичный доступ** — просмотр и скачивание файлов без регистрации
- **JWT + Redis** — HttpOnly cookies с whitelist токенов
- **Двухуровневая проверка** — валидация типа/размера (до 30MB) + антивирус через Kafka
- **Ролевая модель:**
    - USER — загрузка 1 файла, удаление только своих файлов
    - ADMIN — загрузка до 5 файлов, удаление любых файлов, управление пользователями, просмотр аудит-логов и статистики
- **Rate limiting** — Nginx (auth: 5r/min, upload: 2r/s, api: 10r/s)

**Доступ:**
- API: https://cloudfilehub.duckdns.org/api/home
- Swagger UI: https://cloudfilehub.duckdns.org/swagger-ui/index.html
- Kafka UI: https://cloudfilehub.duckdns.org/kafka-ui/

## API документация
![Swagger Overview](docs/images/swagger-title-screen.png)
![Swagger Endpoints](docs/images/swagger-endpoints-screen.png)

## Тестирование

Сервисный слой основного модуля `s3-file-service` покрыт unit и интеграционными тестами. JaCoCo собирает объединённый отчёт покрытия.

* **Unit-тесты** — Mockito, запускаются через Maven Surefire
* **Интеграционные тесты** — Testcontainers (PostgreSQL, Redis, Kafka, MinIO), запускаются через Maven Failsafe
```bash
# Требования: JDK 21, Docker
./mvnw verify -pl s3-file-service
```

![JaCoCo Coverage](docs/images/jacoco-screen.png)

Отчёт генерируется в `target/site/jacoco-merged/`.

## Быстрый старт
```bash
git clone https://github.com/Vldr22/CloudFileHub.git
cd CloudFileHub
cp .env.example .env                # заполнить переменные окружения
./scripts/docker-build-and-logs.sh  # 1) Собрать, 2) Поднять
```

## Структура проекта
```
CloudFileHub/
├── s3-file-service/      # REST API, авторизация, бизнес-логика
├── antivirus-service/    # Антивирусное сканирование, Kafka retry
├── common-kafka/         # Общие модели событий
├── docker-compose.yml
├── nginx.conf
├── scripts/
└── docs/
```
## Roadmap

- [x] Unit и integration тесты
- [x] Деплой на VPS
- [x] CI/CD (GitHub Actions)
- [x] Outbox pattern — гарантированная доставка событий в Kafka
- [ ] Вынести аутентификацию и авторизацию в отдельный сервис
- [ ] Фронтенд через AI
- [ ] Presigned URL для скачивания файлов напрямую из S3
- [ ] Email-уведомления о результатах сканирования
- [ ] Полнотекстовый поиск (Elasticsearch)

