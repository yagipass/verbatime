# Changelog

## [v0.8.0](https://github.com/yagipass/verbatime/compare/v0.7.2...v0.8.0) - 2026-09-26

- fix(agent): keep bundled verbatime-format out of dependent projects by @yagipass in https://github.com/yagipass/verbatime/pull/55
- build(maven): add the project URL, developers, and SCM to the POM by @yagipass in https://github.com/yagipass/verbatime/pull/56
- feat(agent): build sources and javadoc jars and sign them on release by @yagipass in https://github.com/yagipass/verbatime/pull/58
- feat(release): publish the agent to Maven Central on release by @yagipass in https://github.com/yagipass/verbatime/pull/59
- Release for v0.8.0 by @github-actions[bot] in https://github.com/yagipass/verbatime/pull/57

## [v0.8.0](https://github.com/yagipass/verbatime/compare/v0.7.2...v0.8.0) - 2026-09-26

- fix(agent): keep bundled verbatime-format out of dependent projects by @yagipass in https://github.com/yagipass/verbatime/pull/55
- build(maven): add the project URL, developers, and SCM to the POM by @yagipass in https://github.com/yagipass/verbatime/pull/56
- feat(agent): build sources and javadoc jars and sign them on release by @yagipass in https://github.com/yagipass/verbatime/pull/58
- feat(release): publish the agent to Maven Central on release by @yagipass in https://github.com/yagipass/verbatime/pull/59

## [v0.7.2](https://github.com/yagipass/verbatime/compare/v0.7.1...v0.7.2) - 2026-09-25

- feat(github): add issue forms for bugs, features, and docs by @yagipass in https://github.com/yagipass/verbatime/pull/36
- feat(github): prefill issue form titles with the issue type by @yagipass in https://github.com/yagipass/verbatime/pull/44
- perf(docs): serve docs site images at twice their shown size by @yagipass in https://github.com/yagipass/verbatime/pull/51
- fix(docs): give the docs home page an h1 and a descriptive title by @yagipass in https://github.com/yagipass/verbatime/pull/45
- docs(github): add a security policy by @yagipass in https://github.com/yagipass/verbatime/pull/46
- feat(release): attest release files so users can verify them by @yagipass in https://github.com/yagipass/verbatime/pull/47
- docs(agent): keep the documented JMX port on the local machine by @yagipass in https://github.com/yagipass/verbatime/pull/48
- fix(examples): publish the JMX port to the local machine only by @yagipass in https://github.com/yagipass/verbatime/pull/49

## [v0.7.1](https://github.com/yagipass/verbatime/compare/v0.7.0...v0.7.1) - 2026-09-25

- feat(docs): make the docs site indexable and add security headers by @yagipass in https://github.com/yagipass/verbatime/pull/23
- build(docs): build the docs site on every PR and update its npm deps by @yagipass in https://github.com/yagipass/verbatime/pull/25
- feat(docs): move the docs site to verbatime-docs.yagipass.com by @yagipass in https://github.com/yagipass/verbatime/pull/33
- build(deps-dev): bump wrangler from 4.133.0 to 4.134.0 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/31
- build(nix): run the mvnHash update only when Dependabot pushes by @yagipass in https://github.com/yagipass/verbatime/pull/34
- build(deps): bump org.apache.maven.plugins:maven-surefire-plugin from 3.2.5 to 3.6.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/26
- build(deps): bump org.codehaus.mojo:exec-maven-plugin from 3.5.0 to 3.6.4 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/27
- build(deps): bump org.apache.maven.plugins:maven-dependency-plugin from 3.8.1 to 3.11.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/28
- build(deps): bump org.apache.maven.plugins:maven-antrun-plugin from 3.1.0 to 3.2.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/29
- build(deps): bump io.micronaut.platform:micronaut-parent from 5.1.4 to 5.1.5 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/30
- build(deps-dev): bump io.github.ascopes:protobuf-maven-plugin from 5.1.9 to 5.1.10 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/32
- docs(examples): show the grpc PlaceOrder response field as tx_id by @yagipass in https://github.com/yagipass/verbatime/pull/35

## [v0.7.0](https://github.com/yagipass/verbatime/commits/v0.7.0) - 2026-09-24

- build(deps): bump helidon to 4.5.5 in helidon-se example by @yagipass in https://github.com/yagipass/verbatime/pull/13
- build(deps): run examples on JDK 26 where images allow by @yagipass in https://github.com/yagipass/verbatime/pull/14
- build(deps): bump ktor.version from 3.5.2 to 3.6.0 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/2
- build(deps): bump quarkus.platform.version from 3.39.3 to 3.39.4 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/3
- build(deps): bump com.google.protobuf:protobuf-java from 4.36.1 to 4.36.2 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/4
- build(deps-dev): bump io.github.ascopes:protobuf-maven-plugin from 5.1.8 to 5.1.9 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/5
- build(deps): bump the eclipse group with 8 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/6
- build(deps): bump org.junit.jupiter:junit-jupiter from 5.10.2 to 6.1.3 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/8
- build(deps): bump org.apache.maven.plugins:maven-resources-plugin from 3.3.1 to 3.5.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/9
- build(deps): bump org.apache.maven.plugins:maven-compiler-plugin from 3.13.0 to 3.16.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/10
- build(deps): bump org.apache.maven.plugins:maven-jar-plugin from 3.4.1 to 3.5.1 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/11
- style(docs): remove comments from the docs site sources by @yagipass in https://github.com/yagipass/verbatime/pull/15
- fix(examples): serve kafka-consumer POST /orders as text/plain by @yagipass in https://github.com/yagipass/verbatime/pull/16
- refactor(examples): split junit example tests into distinct packages by @yagipass in https://github.com/yagipass/verbatime/pull/17
- feat(cli): make vbtm installable with nix by @yagipass in https://github.com/yagipass/verbatime/pull/18
- build(nix): restructure the flake and build release binaries outside nix by @yagipass in https://github.com/yagipass/verbatime/pull/19
- docs(docs): restructure the docs site around a two-way quick start by @yagipass in https://github.com/yagipass/verbatime/pull/20
- build(deps): bump @ox-content/vite-plugin from 3.2.6 to 3.2.9 by @yagipass in https://github.com/yagipass/verbatime/pull/21
- build(docs): deploy the docs site to Cloudflare Workers on release by @yagipass in https://github.com/yagipass/verbatime/pull/22
