# Fimory Java API - Local Run Guide

## Tech stack
- Java 21
- Spring Boot 3.x
- Spring Web + Security + JPA
- SQL Server JDBC
- Flyway

## 1) Prerequisites
- JDK 21
- Maven (or use `./mvnw`)
- SQL Server with existing Fimory schema

## 2) Environment variables
- `SQL_URL` (default: `jdbc:sqlserver://CHAUTHANH;instanceName=SQLEXPRESS;databaseName=Fimory;encrypt=false;trustServerCertificate=true`)
- `SQL_USER` (default: `fimory`)
- `SQL_PASSWORD` (no default in repo, must set in local env)
- `UPLOAD_DIR` (default: `uploads`)
- `TMDB_API_KEY` (for crawl module - TODO in current MVP)
- `GEMINI_CHAT_KEY` (Gemini key cho chatbot)
- `GEMINI_MODERATION_KEY` (Gemini key cho moderation AI)
- `EMAIL_USER` (SMTP account for email features)
- `EMAIL_PASSWORD` (SMTP app password)

## 3) Run app
```bash
./mvnw spring-boot:run
```

## 4) Health check
`GET http://localhost:8080/health`

## 5) Auth mode (current migration phase)
Current MVP keeps legacy auth behavior:
- Pass header `x-user-email` on protected APIs.
- Role is loaded from `Users` + `Roles`.

## 6) Notes
- Interaction/crawl/admin moderation endpoints are scaffolded with TODO bodies to preserve route contract.
- Next phase should replace header auth by JWT.
