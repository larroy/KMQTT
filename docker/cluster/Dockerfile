FROM openjdk:11-jre-slim

ENV JAVA_TOOL_OPTIONS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000
ADD build/libs/KMQTT-0.2.3.jar KMQTT.jar
EXPOSE 1883
EXPOSE 22222
EXPOSE 22223
ENTRYPOINT [ "java", "-server", "-XX:+UseContainerSupport", "-Djava.security.egd=file:/dev/./urandom", "-jar", "KMQTT.jar" ]
