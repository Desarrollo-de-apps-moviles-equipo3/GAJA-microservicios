# Compila el servicio con Maven y lo ejecuta con Java 17.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Render gratis tiene 512 MB: se limita la memoria de Java para que no se pase.
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:+UseSerialGC -Xss512k"
# El servicio lee el puerto de la variable PORT que pone Render.
CMD ["java", "-jar", "app.jar"]