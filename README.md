# 🍽️ QR Table Order Management - Microservices Architecture

This system is developed for **Gulfnet**'s fine-dine restaurant chain to manage day-to-day operations using a modern microservices-based architecture. The system is containerized, service-discovered, and secured using JWT at the gateway level.

---

## 🧱 Technology Stack Overview

* **Language**: Java 17
* **Framework**: Spring Boot 3.5.3
* **Build Tool**: Gradle
* **Database**: PostgreSQL 15 (Dockerized)
* **Service Discovery**: Eureka Server
* **Security**: JWT-based authentication (handled via Edge Gateway)
* **Containerization**: Dockerfile & Docker Compose
* **Shared Module**: Shared Library (used for common DTOs and JPA entities)

---

## Configuration (environment files and Firebase)

Secrets are **not** committed to git. Each service uses `application.properties` with `${VAR}` placeholders; real values live in per-environment env files.

### Environment files

| File | Purpose |
|------|---------|
| `.env.example` | Tracked template — copy and rename; lists every key with placeholders |
| `.env.local` | Local development (gitignored) |
| `.env.staging` | Staging (gitignored) |
| `.env.uat` | UAT (gitignored) |

**Modules with env files:**

- `edge-gateway`
- `user-management`
- `restaurant-management`
- `integration-management`
- `shared-library`
- `rabbitmq`

**Setup (per module):**

```bash
cd <module>   # e.g. user-management
cp .env.example .env.local
# Edit .env.local and set real values
```

Load env vars when starting the service (IDE run config, `export $(grep -v '^#' .env.local | xargs)`, Docker Compose `env_file`, or your deployment secret store).

### Firebase service account (integration-management only)

Push notifications use a Firebase Admin SDK JSON file. It is **gitignored** (`*-firebase*.json`, `*firebase-adminsdk*.json`).

1. Download the service account JSON from Firebase Console.
2. Place it under `integration-management/src/main/resources/` (e.g. `my-project-firebase-adminsdk-xxxxx.json`).
3. Set the filename in your env file (not the file contents):

```bash
# integration-management/.env.local
FIREBASE_CONFIGURATION_FILE=my-project-firebase-adminsdk-xxxxx.json
```

`application.properties` references it as `app.firebase-configuration-file=${FIREBASE_CONFIGURATION_FILE}`.

On servers (staging/UAT/production), copy the same JSON to that path on the host or mount it in the container; keep it out of git.

### What must not be committed

- `.env.local`, `.env.staging`, `.env.uat`
- Firebase `*.json` credentials under `integration-management/src/main/resources/`
- `*.pem` keys

Only `.env.example` and `application.properties` (placeholders only) belong in the repository.

---

## 🔗 Shared Library

**Path**: `backend/shared-library`

### ✅ Responsibilities:

* Contains shared entity classes and DTOs (e.g., `User`, `Restaurant`, `Role`).
* Helps maintain consistency across microservices.

### 🧱 Dependencies:

```gradle
implementation 'org.springframework.boot:spring-boot-starter'
```

Used in other modules via:

```gradle
implementation project(':shared-library')
```

---

## 🔐 Edge Gateway Service

**Path**: `backend/edge-gateway`

### ✅ Responsibilities:

* Acts as the unified entry point.
* Validates JWT tokens in headers.
* Routes requests to appropriate services (User, Restaurant, Integration).
* Uses **Spring Cloud Gateway** with **WebFlux** to avoid bottlenecks.

### 🧱 Dependencies:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-webflux'
implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
implementation 'io.jsonwebtoken:jjwt:0.9.1'
```

### 📁 application.properties:

```properties
server.port=8080
spring.application.name=edge-gateway
eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka/

spring.cloud.gateway.routes[0].id=user-service
spring.cloud.gateway.routes[0].uri=http://user-management:8080
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/users/**

spring.cloud.gateway.routes[1].id=restaurant-service
spring.cloud.gateway.routes[1].uri=http://restaurant-management:8080
spring.cloud.gateway.routes[1].predicates[0]=Path=/api/restaurants/**

spring.cloud.gateway.routes[2].id=integration-service
spring.cloud.gateway.routes[2].uri=http://integration-management:8080
spring.cloud.gateway.routes[2].predicates[0]=Path=/api/integrations/**
```

---

## 🧭 Eureka Server

**Path**: `backend/eureka-server`

### ✅ Responsibilities:

* Registers and monitors all services.
* Enables service discovery for communication.

### 🧱 Dependencies:

```gradle
implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
```

### 📁 application.properties:

```properties
server.port=8761
spring.application.name=eureka-server
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

---

## 👤 User Management Service

**Path**: `backend/user-management`

### ✅ Responsibilities:

* Manages all users.
* Handles authentication (Login, JWT generation).
* Assigns roles and permissions.

### 🧱 Dependencies:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
implementation 'io.jsonwebtoken:jjwt:0.9.1'
implementation project(':shared-library')
runtimeOnly 'org.postgresql:postgresql'
```

### 📁 application.properties:

```properties
server.port=8081
spring.application.name=user-management
eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka/
spring.datasource.url=jdbc:postgresql://postgres:5432/QR_table_order_management
spring.datasource.username=postgres
spring.datasource.password=postgres
```

---

## 🍴 Restaurant Management Service

**Path**: `backend/restaurant-management`

### ✅ Responsibilities:

* Handles restaurant-level configurations like:
  - Table layouts
  - Menus
  - Promotions
* Used by managers/HQ admins for operational setup.

### 🧱 Dependencies:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
implementation project(':shared-library')
runtimeOnly 'org.postgresql:postgresql'
```

### 📁 application.properties:

```properties
server.port=8082
spring.application.name=restaurant-management
eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka/
spring.datasource.url=jdbc:postgresql://postgres:5432/QR_table_order_management
spring.datasource.username=postgres
spring.datasource.password=postgres
```

---

## 🔄 Integration Management Service

**Path**: `backend/integration-management`

### ✅ Responsibilities:

* Manages third-party tool integrations:
  - Power BI
  - Printer sdk
  - Notification triggers

### 🧱 Dependencies:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
implementation project(':shared-library')
runtimeOnly 'org.postgresql:postgresql'
```

### 📁 application.properties:

```properties
server.port=8083
spring.application.name=integration-management
eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka/
spring.datasource.url=jdbc:postgresql://postgres:5432/QR_table_order_management
spring.datasource.username=postgres
spring.datasource.password=postgres
```
---

## 📁 Folder Structure Example

```plaintext
restaurant-system/
├── docker-compose.yml
└── backend/
    ├── eureka-server/
    │   ├── Dockerfile
    │   └── ...
    ├── edge-gateway/
    │   ├── Dockerfile
    │   └── ...
    ├── user-management/
    │   ├── Dockerfile
    │   └── ...
    ├── restaurant-management/
    │   ├── Dockerfile
    │   └── ...
    ├── integration-management/
    │   ├── Dockerfile
    │   └── ...
    └── shared-library/
```
---

## 🐳 Docker Setup

### Docker Compose File

```yaml
version: '3'

services:

  postgres:
    image: postgres:15
    restart: always
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: QR_table_order_management
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  eureka-server:
    build: ./backend/eureka-server
    ports:
      - "8761:8761"

  edge-gateway:
    build: ./backend/edge-gateway
    ports:
      - "8080:8080"
    depends_on:
      - eureka-server

  user-management:
    build: ./backend/user-management
    ports:
      - "8081:8080"
    depends_on:
      - postgres
      - eureka-server

  restaurant-management:
    build: ./backend/restaurant-management
    ports:
      - "8082:8080"
    depends_on:
      - postgres
      - eureka-server

  integration-management:
    build: ./backend/integration-management
    ports:
      - "8083:8080"
    depends_on:
      - postgres
      - eureka-server

volumes:
  postgres_data:
```

---


## 📦 Sample Dockerfile (used for each service)

Place this file inside the respective service folder (e.g., `backend/user-management/Dockerfile`):

```properties
# Use a lightweight JDK base image
FROM eclipse-temurin:17-jdk-alpine

# Set working directory
WORKDIR /app

# Copy built JAR from Gradle output (ensure `./gradlew build` ran first)
COPY build/libs/*.jar app.jar

# Expose application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
```




