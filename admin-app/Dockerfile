FROM eclipse-temurin:11.0.21_9-jdk-nanoserver

USER ContainerUser

ARG ADMIN_VERSION
ARG API_PORT
ARG DEBUG_PORT
ARG SECURE_API_PORT
ARG JVM_ARGS
ARG MAIN_CLASS_NAME

ENV JVM_ARGS_ENV=${JVM_ARGS}
ENV MAIN_CLASS_NAME_ENV=${MAIN_CLASS_NAME}

RUN mkdir config && cd config && mkdir ssl
COPY build/resources/main/application.conf ./config/application.conf
COPY build/libs/admin-app-${ADMIN_VERSION}-all.jar ./app/drill-app.jar

EXPOSE ${API_PORT}
EXPOSE ${DEBUG_PORT}
EXPOSE ${SECURE_API_PORT}

ENTRYPOINT  java %JVM_ARGS_ENV% -cp /app/drill-app.jar %MAIN_CLASS_NAME_ENV%
