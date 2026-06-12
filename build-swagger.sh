#!/bin/bash

echo "Building QR Table Services with Swagger Documentation..."

# Build all services
echo "Building Edge Gateway..."
cd edge-gateway
gradle clean build -x test
cd ..

echo "Building User Management..."
cd user-management
gradle clean build -x test
cd ..

echo "Building Restaurant Management..."
cd restaurant-management
gradle clean build -x test
cd ..

echo "Building Integration Management..."
cd integration-management
gradle clean build -x test
cd ..

echo "Build completed!"
echo ""
echo "Swagger Documentation URLs:"
echo "=========================="
echo "User Management:"
echo "  Direct: http://localhost:8084/swagger-ui.html"
echo "  Gateway: http://localhost:8080/user/swagger-ui.html"
echo ""
echo "Restaurant Management:"
echo "  Direct: http://localhost:8082/swagger-ui.html"
echo "  Gateway: http://localhost:8080/restaurant/swagger-ui.html"
echo ""
echo "Integration Management:"
echo "  Direct: http://localhost:8081/swagger-ui.html"
echo "  Gateway: http://localhost:8080/integration/swagger-ui.html"
echo ""
echo "To start services, run:"
echo "  docker-compose up"
echo ""
echo "Or start individually:"
echo "  cd edge-gateway && gradle bootRun"
echo "  cd user-management && gradle bootRun"
echo "  cd restaurant-management && gradle bootRun"
echo "  cd integration-management && gradle bootRun" 