FROM adoptopenjdk/openjdk11 as build
RUN mkdir /opt/app
WORKDIR /opt/app
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN ./gradlew build || return 0
COPY src ./src
RUN ./gradlew assemble

FROM adoptopenjdk/openjdk11
RUN mkdir /opt/app
WORKDIR /opt/app
COPY --from=build /opt/app/build/libs/app.jar ./
COPY --from=build /opt/app/build/libs/libs ./libs

EXPOSE 8080
CMD ["java", "-jar", "/opt/app/app.jar"]