# FixConnect AI - container image (used by Render; works on any Docker host)

# ---- 1. Build the jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- 2. Run it ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/fixconnect-backend-1.0.0.jar app.jar
# Fit the app in a small (512 MB) free instance
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
EXPOSE 8080
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
