# syntax=docker/dockerfile:1
# Build stage: install generic_backend then build customer_project
FROM eclipse-temurin:17-jammy AS builder
WORKDIR /build

# Copy generic_backend and install to local repo
COPY generic_backend /build/generic_backend
RUN apt-get update && apt-get install -y maven wget

# Download required POM files with cache mount for Maven dependencies
RUN wget -t0 https://repository.jboss.org/nexus/content/groups/public/org/kie/kie-user-bom-parent/7.74.1.Final/kie-user-bom-parent-7.74.1.Final.pom https://repository.jboss.org/nexus/content/groups/public/org/kie/soup/kie-soup-bom/7.74.1.Final/kie-soup-bom-7.74.1.Final.pom

# Install KIE dependencies with Maven cache
RUN --mount=type=cache,target=/root/.m2 mvn install:install-file \
    -DgroupId=org.kie \
    -DartifactId=kie-user-bom-parent \
    -Dversion=7.74.1.Final \
    -Dpackaging=pom \
    -Dfile=./kie-user-bom-parent-7.74.1.Final.pom

RUN --mount=type=cache,target=/root/.m2 mvn install:install-file \
    -DgroupId=org.kie.soup \
    -DartifactId=kie-soup-bom \
    -Dversion=7.74.1.Final \
    -Dpackaging=pom \
    -Dfile=./kie-soup-bom-7.74.1.Final.pom

# Build generic_backend with Maven cache (skip repackage so it can be used as a dependency)
RUN --mount=type=cache,target=/root/.m2 mvn -f generic_backend -Dmaven.wagon.httpconnectionManager.ttlSeconds=30 \
    -Dmaven.wagon.http.socketTimeout=30000 \
    -Dmaven.wagon.http.connectionTimeout=30000 -Djava.net.preferIPv4Stack=true \
    -Dspring.boot.repackage.skip=true -X clean install -DskipTests

RUN echo done generic

# Copy customer_project and package with Maven cache
COPY customer_project /build/customer_project
RUN --mount=type=cache,target=/root/.m2 mvn -f customer_project -Dmaven.wagon.httpconnectionManager.ttlSeconds=30 -Djava.net.preferIPv4Stack=true \
    -Dmaven.wagon.http.socketTimeout=30000 \
    -Dmaven.wagon.http.connectionTimeout=30000 \
    -X package -DskipTests

RUN echo done customer

# Run stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN useradd -m app && chown -R app:app /app
USER app

COPY --from=builder /build/customer_project/target/aisystem-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
