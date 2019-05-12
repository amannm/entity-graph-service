FROM adoptopenjdk/openjdk11:latest
RUN mkdir /opt/app
COPY build/libs/app.jar /opt/app
COPY build/libs /opt/app
CMD ["java", "-jar", "/opt/app/app.jar"]
EXPOSE 8080