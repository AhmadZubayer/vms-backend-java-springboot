# Build the jar, then run it on a JRE-only image.
#
# Tests are skipped here on purpose: they need a reachable PostgreSQL, which a
# build container does not have.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

# Do not run as root.
RUN useradd --system --create-home --shell /usr/sbin/nologin app
USER app

COPY --from=build /build/target/*.jar /app/app.jar

# Stay inside the memory the host actually grants the container.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# The real port comes from PORT at runtime; this is documentation.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
