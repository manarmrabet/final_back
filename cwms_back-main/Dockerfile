# ─────────────────────────────────────────────
# 1) BUILD STAGE (Maven)
# ─────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Improve Maven performance
ENV MAVEN_OPTS="-Xmx1024m -Djava.net.preferIPv4Stack=true"

# Copy only pom first (better Docker cache)
COPY pom.xml ./

# Download dependencies offline (cached layer)
RUN mvn dependency:go-offline -B --no-transfer-progress

# Copy source code
COPY src ./src

# Convert ALL .properties files to UTF-8 before Maven processes them
RUN find src/main/resources -name "*.properties" | while read f; do \
      iconv -f ISO-8859-1 -t UTF-8 "$f" -o "$f.utf8" && mv "$f.utf8" "$f"; \
    done

# Build application (NO -U, avoid forced re-downloads)
RUN mvn clean package -DskipTests -B --no-transfer-progress

# ─────────────────────────────────────────────
# 2) RUNTIME STAGE (Lightweight JRE)
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre

# Create non-root user (Debian-based image)
RUN groupadd -r cofat && useradd -r -g cofat cofat

WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Logs directory with correct permissions
RUN mkdir -p /app/logs && chown -R cofat:cofat /app

# Switch to non-root user
USER cofat

# Spring Boot port
EXPOSE 8080

# Run application
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]