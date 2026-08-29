# syntax=docker/dockerfile:1

# Etapa de build: Maven 3.9.11 sobre Eclipse Temurin JDK 25 (tag verificado
# contra Docker Hub - imagen oficial "maven", variante eclipse-temurin-25).
# Se usa "mvn" de la imagen, no el wrapper del repo, para no depender de que
# ./mvnw pueda descargar su propia distribucion de Maven durante el build.
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build

# Capa de dependencias separada del codigo: cachea 'dependency:go-offline'
# mientras el pom.xml no cambie, para que un rebuild tras editar solo /src
# no vuelva a bajar todo el arbol de Maven Central.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# Etapa de runtime: solo JRE, sin JDK ni Maven (imagen final mas chica y con
# menos superficie). Alpine reduce aun mas el tamano.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Usuario sin privilegios: el proceso de la app no necesita ser root.
RUN addgroup -S hrsaas && adduser -S hrsaas -G hrsaas
COPY --from=build /build/target/*.jar app.jar
RUN chown hrsaas:hrsaas app.jar
USER hrsaas

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
