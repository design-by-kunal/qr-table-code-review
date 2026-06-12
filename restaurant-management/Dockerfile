FROM eclipse-temurin:17-jdk-alpine

# Install fonts so receipts can render Japanese/Thai text in PDFs
# - font-noto-cjk: CJK (includes Japanese)
# - font-noto-thai: Thai script
# - ttf-dejavu: good Latin fallback
RUN apk add --no-cache \
    font-noto-cjk \
    font-noto-thai \
    ttf-dejavu

WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
