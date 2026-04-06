FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/marks-system.jar marks-system.jar
EXPOSE 8080
CMD ["java", "-jar", "marks-system.jar"]
