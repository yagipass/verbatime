# Changelog

## [v0.8.3](https://github.com/yagipass/verbatime/compare/v0.8.2...v0.8.3) - 2026-10-24

### Agent
- fix(agent): refuse a waitstart too long to wait for by @yagipass in https://github.com/yagipass/verbatime/pull/273
### CLI
- fix(cli): report a chunk ending mid-event as corrupt in a truncated file by @yagipass in https://github.com/yagipass/verbatime/pull/260
- fix(cli): report a bad signature in an unfinished CLASS record as corrupt by @yagipass in https://github.com/yagipass/verbatime/pull/267
- fix(cli): refuse a duration longer than a recording can hold by @yagipass in https://github.com/yagipass/verbatime/pull/269
- fix(cli): start a session at its first chunk even when it is empty by @yagipass in https://github.com/yagipass/verbatime/pull/271
- fix(cli): show control characters in recorded names as escapes by @yagipass in https://github.com/yagipass/verbatime/pull/275
### JMC Plugin
- fix(jmc): report a string longer than any Java array as corrupt by @yagipass in https://github.com/yagipass/verbatime/pull/263
- fix(jmc): report data after END and bad chunk lengths at vbtm's offsets by @yagipass in https://github.com/yagipass/verbatime/pull/265
### Maintenance
- test: share the reference decoder through the format test jar by @yagipass in https://github.com/yagipass/verbatime/pull/278
- test(format): fuzz TraceReader against the reference decoder by @yagipass in https://github.com/yagipass/verbatime/pull/279
- test(jmc): fuzz the JMC plugin's indexer against the reference decoder by @yagipass in https://github.com/yagipass/verbatime/pull/280
- test(cli): fuzz vbtm against the reference decoder by @yagipass in https://github.com/yagipass/verbatime/pull/281
- ci: fuzz the readers against the reference decoder every night by @yagipass in https://github.com/yagipass/verbatime/pull/282
- refactor(cli): check string lengths against the format's limit by @yagipass in https://github.com/yagipass/verbatime/pull/284
- build(nix): build the dev shells in nix flake check by @yagipass in https://github.com/yagipass/verbatime/pull/286
- build(github): keep the Cachix token out of jobs that run Maven or pnpm by @yagipass in https://github.com/yagipass/verbatime/pull/287
- build(github): push to the Cachix cache only from runs on main by @yagipass in https://github.com/yagipass/verbatime/pull/296
- chore: configure CodeRabbit reviews by @yagipass in https://github.com/yagipass/verbatime/pull/298
### Dependencies
- build(deps): bump cachix/install-nix-action from 31.11.1 to 31.11.2 in the actions group by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/293
- build(deps): bump org.sonatype.central:central-publishing-maven-plugin from 0.11.0 to 0.12.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/292
- build(deps-dev): bump com.github.ben-manes.caffeine:caffeine from 3.3.0 to 3.3.1 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/294
- build(deps): bump the flake-inputs group with 3 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/290
- build(deps-dev): bump @ox-content/vite-plugin from 3.2.14 to 3.3.0 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/289
- build(deps-dev): bump wrangler from 4.149.0 to 4.151.0 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/291

## [v0.8.2](https://github.com/yagipass/verbatime/compare/v0.8.1...v0.8.2) - 2026-10-19

### Agent
- fix(agent): record GC pauses at the time they happened by @yagipass in https://github.com/yagipass/verbatime/pull/87
- fix(agent): start each session with a small buffer that grows as needed by @yagipass in https://github.com/yagipass/verbatime/pull/89
- fix(agent): release the event buffer when a stop cuts a session by @yagipass in https://github.com/yagipass/verbatime/pull/91
- fix(agent): instrument methods in place so caller lookups still work by @yagipass in https://github.com/yagipass/verbatime/pull/99
- fix(agent): count only sessions whose root returned as completed by @yagipass in https://github.com/yagipass/verbatime/pull/103
### CLI
- fix(cli): print the root path of tree --merge whatever the floor by @yagipass in https://github.com/yagipass/verbatime/pull/209
- fix(cli): choose the tree floor from the lines it prints by @yagipass in https://github.com/yagipass/verbatime/pull/210
- fix(cli): fold and count calls deeper than --depth in tree --merge by @yagipass in https://github.com/yagipass/verbatime/pull/211
- fix(cli): count only the gc time inside the printed tree by @yagipass in https://github.com/yagipass/verbatime/pull/216
- fix(cli): print gc pauses in tree --merge by @yagipass in https://github.com/yagipass/verbatime/pull/217
### Examples
- build(deps-dev): bump com.diffplug.spotless:spotless-maven-plugin from 3.10.2 to 3.10.3 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/93
- build(deps): bump the images group across 2 directories with 2 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/97
- build(deps): bump quarkus.platform.version from 3.39.4 to 3.39.5 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/95
- build(deps): bump io.micronaut.platform:micronaut-parent from 5.1.5 to 5.2.1 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/126
- build(deps): bump quarkus.platform.version from 3.39.5 to 3.40.1 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/133
- build(deps-dev): bump io.github.ascopes:protobuf-maven-plugin from 5.1.10 to 5.1.11 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/130
- build(deps): bump io.undertow:undertow-core from 2.4.3.Final to 2.4.4.Final in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/164
- build(deps-dev): bump com.diffplug.spotless:spotless-maven-plugin from 3.10.3 to 3.10.4 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/224
- build(deps): bump io.micronaut.platform:micronaut-parent from 5.2.1 to 5.2.2 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/226
- build(deps): bump kotlin.version from 2.4.20 to 2.4.21 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/222
- build(deps): bump jetty from 12.1.13-jdk25-eclipse-temurin to 12.1.14-jdk25-eclipse-temurin in /examples/jetty-war in the images group across 1 directory by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/223
- feat(examples): publish the example images to ghcr.io by @yagipass in https://github.com/yagipass/verbatime/pull/253
### New Features
- feat(release): publish the release only after every file is uploaded by @yagipass in https://github.com/yagipass/verbatime/pull/71
- feat(github): add Checked so far and align issue form field names by @yagipass in https://github.com/yagipass/verbatime/pull/81
- feat(docs): match the docs site to the brand and add a landing page by @yagipass in https://github.com/yagipass/verbatime/pull/113
- feat(github): show the release each pinned action SHA points to by @yagipass in https://github.com/yagipass/verbatime/pull/117
- feat: date release jars with the commit they are built from by @yagipass in https://github.com/yagipass/verbatime/pull/146
- feat(docs): move the docs site to verbatime.yagipass.com by @yagipass in https://github.com/yagipass/verbatime/pull/205
- feat(release): group the changelog by component and type of change by @yagipass in https://github.com/yagipass/verbatime/pull/256
- feat(release): group example and benchmark changes in the changelog by @yagipass in https://github.com/yagipass/verbatime/pull/258
### Documentation
- docs: gather the download checks on a Verifying downloads page by @yagipass in https://github.com/yagipass/verbatime/pull/69
- docs: introduce Verbatime as a Java method tracing profiler by @yagipass in https://github.com/yagipass/verbatime/pull/75
- docs: note that class loading looks slower in a recording by @yagipass in https://github.com/yagipass/verbatime/pull/78
- docs: show build, release, license, and Java badges in the README by @yagipass in https://github.com/yagipass/verbatime/pull/82
- docs: show the commands that install the vbtm skill by @yagipass in https://github.com/yagipass/verbatime/pull/85
- docs(examples): link example READMEs to headings that exist by @yagipass in https://github.com/yagipass/verbatime/pull/196
- docs: tell contributors how to get a pull request through CI by @yagipass in https://github.com/yagipass/verbatime/pull/251
### Maintenance
- build(nix): check the GitHub Actions workflows with actionlint by @yagipass in https://github.com/yagipass/verbatime/pull/73
- refactor: follow Error Prone's @Var style instead of final locals by @yagipass in https://github.com/yagipass/verbatime/pull/83
- build(jmc): stop turning off Error Prone's StringConcatToTextBlock by @yagipass in https://github.com/yagipass/verbatime/pull/84
- build(nix): load the docs dev shell when direnv enters docs/ by @yagipass in https://github.com/yagipass/verbatime/pull/106
- build(docs): install the docs site dependencies with pnpm by @yagipass in https://github.com/yagipass/verbatime/pull/108
- build(nix): update nixpkgs and git-hooks.nix by @yagipass in https://github.com/yagipass/verbatime/pull/110
- build(nix): open a weekly PR that updates flake.lock by @yagipass in https://github.com/yagipass/verbatime/pull/111
- build(nix): add ajmx and its agent skill to the Java dev shells by @yagipass in https://github.com/yagipass/verbatime/pull/114
- build(nix): update the vbtm mvnHash in the weekly flake.lock PR by @yagipass in https://github.com/yagipass/verbatime/pull/118
- build: check nullness of main code with NullAway and JSpecify by @yagipass in https://github.com/yagipass/verbatime/pull/120
- refactor: keep values that are always set before use non-null by @yagipass in https://github.com/yagipass/verbatime/pull/121
- build(nix): update flake.lock inputs with Dependabot by @yagipass in https://github.com/yagipass/verbatime/pull/123
- build(agent): keep corpus jars on the versions kept for their bytecode by @yagipass in https://github.com/yagipass/verbatime/pull/138
- build(agent): keep the Scala 3 corpus jars on one Scala release by @yagipass in https://github.com/yagipass/verbatime/pull/147
- build(agent): run the corpus check on the -Pcorpus-full jars in CI by @yagipass in https://github.com/yagipass/verbatime/pull/150
- build(agent): update corpus jars of one release in one pull request by @yagipass in https://github.com/yagipass/verbatime/pull/151
- build: check main and test code with errorprone-tidy by @yagipass in https://github.com/yagipass/verbatime/pull/161
- build: let Dependabot open up to 30 pull requests per Maven scope by @yagipass in https://github.com/yagipass/verbatime/pull/163
- chore: stop tracking .vscode settings by @yagipass in https://github.com/yagipass/verbatime/pull/192
- build(nix): add mvnd to the Java dev shells by @yagipass in https://github.com/yagipass/verbatime/pull/193
- build: format Java with google-java-format by @yagipass in https://github.com/yagipass/verbatime/pull/194
- chore: ignore agent skills that dev shells install in subdirectories by @yagipass in https://github.com/yagipass/verbatime/pull/197
- build: check the order of Java members with spotless-member-order by @yagipass in https://github.com/yagipass/verbatime/pull/198
- build(deps): bump spotless-member-order from 0.1.0 to 0.2.0 by @yagipass in https://github.com/yagipass/verbatime/pull/200
- build: group framework-called methods such as @Bean in the member order by @yagipass in https://github.com/yagipass/verbatime/pull/201
- build(deps-dev): bump wrangler from 4.145.0 to 4.149.0 in /docs by @yagipass in https://github.com/yagipass/verbatime/pull/203
- build(nix): use the gitleaks hook that git-hooks.nix now ships by @yagipass in https://github.com/yagipass/verbatime/pull/235
- build(github): lock GitHub Actions dependencies with a workflow lockfile by @yagipass in https://github.com/yagipass/verbatime/pull/237
- build(github): address the findings of jactionlint in the workflows by @yagipass in https://github.com/yagipass/verbatime/pull/241
- build(github): read the Maven Central secrets from an environment by @yagipass in https://github.com/yagipass/verbatime/pull/245
- build(github): read the Cloudflare secrets from an environment by @yagipass in https://github.com/yagipass/verbatime/pull/247
- build(github): manage the repository settings with gh-infra by @yagipass in https://github.com/yagipass/verbatime/pull/249
- build(github): delete the example images instead of a dry run by @yagipass in https://github.com/yagipass/verbatime/pull/254
### Dependencies
- build(deps-dev): bump wrangler from 4.134.0 to 4.136.3 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/94
- build(deps): bump Songmu/tagpr from 1.20.3 to 1.21.0 in the actions group by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/96
- build(deps): bump com.diffplug.spotless:spotless-maven-plugin from 3.10.2 to 3.10.3 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/92
- build(deps-dev): bump dompurify from 3.4.15 to 3.4.16 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/100
- build(deps): bump undici and wrangler in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/101
- build(deps): bump flake-parts from `31729ca` to `024633c` in the flake-inputs group by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/131
- build(deps-dev): bump net.sf.saxon:Saxon-HE from 12.9 to 13.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/129
- build(deps-dev): bump @ox-content/vite-plugin from 3.2.9 to 3.2.11 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/134
- build(deps-dev): bump vite from 8.3.0 to 8.3.1 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/135
- build(deps-dev): bump ch.qos.logback:logback-core from 1.5.38 to 1.6.5 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/127
- build(deps): bump the actions group with 2 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/132
- build(deps-dev): bump commons-logging:commons-logging from 1.3.5 to 1.4.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/143
- build(deps-dev): bump org.jboss.marshalling:jboss-marshalling from 2.2.3.Final to 2.3.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/141
- build(deps-dev): bump com.github.ben-manes.caffeine:caffeine from 3.2.4 to 3.3.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/142
- build(deps-dev): bump io.undertow:undertow-core from 2.3.26.Final to 2.4.3.Final by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/139
- build(deps-dev): bump org.bouncycastle:bcprov-jdk18on from 1.84 to 1.86 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/148
- build(deps): bump the flake-inputs group with 2 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/157
- build(deps-dev): bump the jackson group with 2 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/152
- build(deps-dev): bump org.jboss.marshalling:jboss-marshalling-river from 2.2.3.Final to 2.3.0 in the jboss-marshalling group by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/153
- build(deps-dev): bump the log4j group with 2 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/154
- build(deps-dev): bump ch.qos.logback:logback-classic from 1.5.38 to 1.6.5 in the logback group by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/155
- build(deps-dev): bump the micrometer group with 2 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/156
- build(deps): bump source-map-js from 1.2.1 to 1.2.2 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/159
- build(deps): bump smol-toml from 1.8.0 to 1.9.0 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/160
- build(deps-dev): bump @ox-content/vite-plugin from 3.2.11 to 3.2.12 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/165
- build(deps-dev): bump org.apache.groovy:groovy from 4.0.33 to 6.0.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/184
- build(deps-dev): bump com.google.guava:guava from 33.6.0-jre to 33.7.2-jre by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/174
- build(deps-dev): bump com.h2database:h2 from 2.3.232 to 2.5.252 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/180
- build(deps-dev): bump org.aspectj:aspectjweaver from 1.9.25 to 1.9.25.1 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/171
- build(deps-dev): bump org.clojure:spec.alpha from 0.5.238 to 0.6.249 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/179
- build(deps-dev): bump org.hibernate.orm:hibernate-core from 7.4.5.Final to 7.4.11.Final by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/176
- build(deps-dev): bump org.scala-lang.modules:scala-asm from 9.8.0-scala-1 to 9.10.1-scala-1 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/167
- build(deps-dev): bump org.apache.poi:poi-ooxml-lite from 5.4.1 to 5.5.1 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/182
- build(deps-dev): bump the spring-boot group with 2 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/166
- build(deps-dev): bump org.apache.kafka:kafka-clients from 4.1.2 to 4.3.1 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/181
- build(deps-dev): bump io.undertow:undertow-core from 2.4.3.Final to 2.4.4.Final by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/177
- build(deps-dev): bump org.clojure:core.specs.alpha from 0.4.74 to 0.5.81 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/186
- build(deps-dev): bump org.eclipse.jdt:ecj from 3.46.0 to 3.46.100 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/183
- build(deps-dev): bump com.oracle.database.jdbc:ojdbc11 from 23.9.0.25.07 to 23.26.3.0.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/169
- build(deps-dev): bump org.hibernate.models:hibernate-models from 1.1.1 to 1.3.4 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/185
- build(deps-dev): bump org.scala-sbt:compiler-interface from 1.10.7 to 2.0.4 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/175
- build(deps-dev): bump org.apache.xmlbeans:xmlbeans from 5.3.0 to 5.4.1 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/168
- build(deps-dev): bump org.mockito:mockito-core from 5.20.0 to 5.24.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/172
- build(deps-dev): bump org.slf4j:slf4j-api from 2.0.17 to 2.0.20 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/170
- build(deps-dev): bump jakarta.xml.bind:jakarta.xml.bind-api from 4.0.4 to 4.0.5 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/173
- build(deps-dev): bump org.jboss.logging:jboss-logging from 3.6.1.Final to 3.6.3.Final by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/178
- build(deps): bump ajmx from `2ee5c81` to `16eefbc` in the flake-inputs group by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/188
- build(deps-dev): bump org.apache.lucene:lucene-core from 10.5.1 to 10.5.2 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/191
- build(deps-dev): bump org.hibernate.orm:hibernate-core from 7.4.11.Final to 7.4.12.Final by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/190
- build(deps-dev): bump @ox-content/vite-plugin from 3.2.12 to 3.2.13 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/189
- build(deps-dev): bump org.apache.kafka:kafka-clients from 4.3.1 to 4.4.0 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/233
- build(deps-dev): bump org.postgresql:postgresql from 42.7.13 to 42.7.14 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/232
- build(deps): bump com.diffplug.spotless:spotless-maven-plugin from 3.10.3 to 3.10.4 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/231
- build(deps-dev): bump org.apache.tomcat.embed:tomcat-embed-core from 11.0.26 to 11.0.27 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/230
- build(deps-dev): bump org.hibernate.orm:hibernate-core from 7.4.12.Final to 7.4.13.Final by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/229
- build(deps-dev): bump the netty group with 8 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/228
- build(deps-dev): bump vite from 8.3.1 to 8.3.3 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/221
- build(deps): bump the flake-inputs group with 4 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/225
- build(deps): bump Songmu/tagpr from 1.21.1 to 1.21.2 in the actions group by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/219
- build(deps-dev): bump the jetty group across 1 directory with 4 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/220
- build(deps-dev): bump the kotlin group across 1 directory with 5 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/227
- build(deps-dev): bump vite from 8.3.3 to 8.3.4 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/242
- build(deps-dev): bump @ox-content/vite-plugin from 3.2.13 to 3.2.14 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/243

## [v0.8.1](https://github.com/yagipass/verbatime/compare/v0.8.0...v0.8.1) - 2026-09-27

- feat(cli): add an install script that makes vbtm executable by @yagipass in https://github.com/yagipass/verbatime/pull/62
- feat(release): update the Homebrew formula on release by @yagipass in https://github.com/yagipass/verbatime/pull/65
- feat(cli): install vbtm with an insmith-generated script by @yagipass in https://github.com/yagipass/verbatime/pull/67
- docs(cli): show the Homebrew and Nix installs for vbtm by @yagipass in https://github.com/yagipass/verbatime/pull/68

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
