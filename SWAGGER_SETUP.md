# Swagger Documentation Setup

This document describes the Swagger/OpenAPI documentation setup for the QR Table microservices architecture.

## Overview

Swagger documentation has been added to all microservices without requiring annotations on controllers. The documentation is automatically generated based on the existing REST endpoints.

## Services with Swagger Documentation

### 1. User Management Service
- **Port**: 8084 (direct), 8080/user (via gateway)
- **Swagger UI**: http://localhost:8084/swagger-ui.html
- **API Docs**: http://localhost:8084/v3/api-docs
- **Gateway Access**: http://localhost:8080/user/swagger-ui.html

### 2. Restaurant Management Service
- **Port**: 8082 (direct), 8080/restaurant (via gateway)
- **Swagger UI**: http://localhost:8082/swagger-ui.html
- **API Docs**: http://localhost:8082/v3/api-docs
- **Gateway Access**: http://localhost:8080/restaurant/swagger-ui.html

### 3. Integration Management Service
- **Port**: 8081 (direct), 8080/integration (via gateway)
- **Swagger UI**: http://localhost:8081/swagger-ui.html
- **API Docs**: http://localhost:8081/v3/api-docs
- **Gateway Access**: http://localhost:8080/integration/swagger-ui.html

## JWT Token Bypass

The Edge Gateway has been configured to bypass JWT token validation for Swagger URLs. The following paths are whitelisted:

- `/user/swagger-ui/**`
- `/user/v3/api-docs/**`
- `/restaurant/swagger-ui/**`
- `/restaurant/v3/api-docs/**`
- `/integration/swagger-ui/**`
- `/integration/v3/api-docs/**`

## Configuration Details

### Dependencies Added
All services now include the following dependency:
```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
```

### Application Properties
Each service includes the following Swagger configuration:
```properties
# Swagger Configuration
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.enabled=true
springdoc.api-docs.enabled=true
```

### Swagger Configuration Classes
Each service has a `SwaggerConfig.java` class that provides:
- API title and version
- Service description
- Contact information
- License information
- Server configurations (both direct and gateway access)

## Accessing Documentation

### Via Gateway (Recommended)
1. Start all services including the Edge Gateway
2. Access Swagger UI through the gateway URLs:
   - User Management: http://localhost:8080/user/swagger-ui.html
   - Restaurant Management: http://localhost:8080/restaurant/swagger-ui.html
   - Integration Management: http://localhost:8080/integration/swagger-ui.html

### Direct Access
1. Start individual services
2. Access Swagger UI directly:
   - User Management: http://localhost:8084/swagger-ui.html
   - Restaurant Management: http://localhost:8082/swagger-ui.html
   - Integration Management: http://localhost:8081/swagger-ui.html

## Features

- **No Annotations Required**: Documentation is generated automatically without modifying existing controllers
- **JWT Bypass**: Swagger URLs are accessible without authentication tokens
- **Gateway Integration**: Documentation is accessible through the Edge Gateway
- **Multiple Server Support**: Each service shows both direct and gateway server options
- **Comprehensive API Info**: Includes contact details, license, and service descriptions

## Troubleshooting

### Common Issues

1. **Swagger UI not loading**: Ensure the service is running and the port is correct
2. **JWT token required**: Verify the Edge Gateway is running and the whitelist is properly configured
3. **Dependencies not found**: Run `./gradlew build` to download dependencies

### Verification Steps

1. Check if services are running:
   ```bash
   curl http://localhost:8084/actuator/health  # User Management
   curl http://localhost:8082/actuator/health  # Restaurant Management
   curl http://localhost:8081/actuator/health  # Integration Management
   ```

2. Verify Swagger endpoints:
   ```bash
   curl http://localhost:8084/v3/api-docs      # User Management
   curl http://localhost:8082/v3/api-docs      # Restaurant Management
   curl http://localhost:8081/v3/api-docs      # Integration Management
   ```

## Security Considerations

- Swagger UI is only accessible in development environments
- Production deployments should disable Swagger UI
- JWT bypass is only for Swagger-related endpoints
- All other API endpoints still require proper authentication

## Next Steps

1. Customize API documentation with more detailed descriptions
2. Add request/response examples
3. Configure environment-specific Swagger settings
4. Add API versioning support 