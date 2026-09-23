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

FROM icr.io/appcafe/open-liberty:26.0.0.9-full-java25-openj9-ubi-minimal
COPY --chown=1001:0 examples/open-liberty-war/config/ /config/
COPY --from=agent --chown=1001:0 /src/modules/agent/target/verbatime-agent.jar /config/verbatime-agent.jar
COPY --from=app --chown=1001:0 /src/examples/tomcat-war/target/ROOT.war /config/apps/ROOT.war
EXPOSE 8080 7091
