FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system ledgerone && adduser --system --ingroup ledgerone ledgerone
COPY --from=build /workspace/target/*.jar app.jar
USER ledgerone
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
