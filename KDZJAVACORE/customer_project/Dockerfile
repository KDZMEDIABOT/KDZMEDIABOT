# Build stage: install generic_backend then build customer_project
FROM eclipse-temurin:17-jammy AS builder
WORKDIR /build

# Copy generic_backend and install to local repo
COPY generic_backend /build/generic_backend
RUN apt-get update -qq && apt-get install -y -qq maven > /dev/null && \
    mvn -f generic_backend clean install -DskipTests -q

# Copy customer_project and package
COPY customer_project /build/customer_project
RUN mvn -f customer_project package -DskipTests -q

# Run stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN useradd -m app && chown -R app:app /app
USER app

COPY --from=builder /build/customer_project/target/aisystem-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
