# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-25-noble AS agent
WORKDIR /src
COPY pom.xml LICENSE NOTICE ./
COPY .mvn .mvn
COPY modules/jmc/pom.xml modules/jmc/pom.xml
COPY modules/cli/pom.xml modules/cli/pom.xml
COPY modules/format modules/format
COPY modules/agent modules/agent
RUN --mount=type=cache,id=verbatime-m2,target=/root/.m2/repository,sharing=locked \
    mvn -B -pl modules/agent -am package -DskipTests -Dmaven.test.skip=true -Dmaven.antrun.skip=true

FROM maven:3.9.16-eclipse-temurin-25-noble AS app
WORKDIR /src
COPY examples/workload examples/workload
RUN --mount=type=cache,id=verbatime-m2,target=/root/.m2/repository,sharing=locked \
    mvn -B -f examples/workload/pom.xml install -DskipTests
COPY examples/tomcat-war examples/tomcat-war
RUN --mount=type=cache,id=verbatime-m2,target=/root/.m2/repository,sharing=locked \
    mvn -B -f examples/tomcat-war/pom.xml package -DskipTests

FROM jetty:12.1.12-jdk25-eclipse-temurin
RUN java -jar "$JETTY_HOME/start.jar" --add-modules=ee11-deploy,ee11-annotations
COPY --from=agent --chown=jetty:jetty /src/modules/agent/target/verbatime-agent.jar /var/lib/jetty/verbatime-agent.jar
COPY --from=app --chown=jetty:jetty /src/examples/tomcat-war/target/ROOT.war /var/lib/jetty/webapps/ROOT.war
EXPOSE 8080 7091
