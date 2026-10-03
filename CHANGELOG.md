# Changelog

## [v0.8.2](https://github.com/yagipass/verbatime/compare/v0.8.1...v0.8.2) - 2026-10-03

- docs: gather the download checks on a Verifying downloads page by @yagipass in https://github.com/yagipass/verbatime/pull/69
- feat(release): publish the release only after every file is uploaded by @yagipass in https://github.com/yagipass/verbatime/pull/71
- build(nix): check the GitHub Actions workflows with actionlint by @yagipass in https://github.com/yagipass/verbatime/pull/73
- docs: introduce Verbatime as a Java method tracing profiler by @yagipass in https://github.com/yagipass/verbatime/pull/75
- docs: note that class loading looks slower in a recording by @yagipass in https://github.com/yagipass/verbatime/pull/78
- feat(github): add Checked so far and align issue form field names by @yagipass in https://github.com/yagipass/verbatime/pull/81
- docs: show build, release, license, and Java badges in the README by @yagipass in https://github.com/yagipass/verbatime/pull/82
- refactor: follow Error Prone's @Var style instead of final locals by @yagipass in https://github.com/yagipass/verbatime/pull/83
- build(jmc): stop turning off Error Prone's StringConcatToTextBlock by @yagipass in https://github.com/yagipass/verbatime/pull/84
- docs: show the commands that install the vbtm skill by @yagipass in https://github.com/yagipass/verbatime/pull/85
- fix(agent): record GC pauses at the time they happened by @yagipass in https://github.com/yagipass/verbatime/pull/87
- fix(agent): start each session with a small buffer that grows as needed by @yagipass in https://github.com/yagipass/verbatime/pull/89
- fix(agent): release the event buffer when a stop cuts a session by @yagipass in https://github.com/yagipass/verbatime/pull/91
- fix(agent): instrument methods in place so caller lookups still work by @yagipass in https://github.com/yagipass/verbatime/pull/99
- fix(agent): count only sessions whose root returned as completed by @yagipass in https://github.com/yagipass/verbatime/pull/103
- build(deps-dev): bump wrangler from 4.134.0 to 4.136.3 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/94
- build(deps): bump Songmu/tagpr from 1.20.3 to 1.21.0 in the actions group by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/96
- build(deps): bump com.diffplug.spotless:spotless-maven-plugin from 3.10.2 to 3.10.3 by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/92
- build(deps-dev): bump com.diffplug.spotless:spotless-maven-plugin from 3.10.2 to 3.10.3 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/93
- build(deps): bump the images group across 2 directories with 2 updates by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/97
- build(deps): bump quarkus.platform.version from 3.39.4 to 3.39.5 in /examples by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/95
- build(deps-dev): bump dompurify from 3.4.15 to 3.4.16 in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/100
- build(deps): bump undici and wrangler in /docs by @dependabot[bot] in https://github.com/yagipass/verbatime/pull/101
- build(nix): load the docs dev shell when direnv enters docs/ by @yagipass in https://github.com/yagipass/verbatime/pull/106
- build(docs): install the docs site dependencies with pnpm by @yagipass in https://github.com/yagipass/verbatime/pull/108
- build(nix): update nixpkgs and git-hooks.nix by @yagipass in https://github.com/yagipass/verbatime/pull/110
- build(nix): open a weekly PR that updates flake.lock by @yagipass in https://github.com/yagipass/verbatime/pull/111
- feat(docs): match the docs site to the brand and add a landing page by @yagipass in https://github.com/yagipass/verbatime/pull/113
- build(nix): add ajmx and its agent skill to the Java dev shells by @yagipass in https://github.com/yagipass/verbatime/pull/114
- feat(github): show the release each pinned action SHA points to by @yagipass in https://github.com/yagipass/verbatime/pull/117
- build(nix): update the vbtm mvnHash in the weekly flake.lock PR by @yagipass in https://github.com/yagipass/verbatime/pull/118

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
