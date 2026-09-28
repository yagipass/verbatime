---
description: Record a Java application's startup from main() as one call tree, then find which startup phase and which Spring Boot beans took the time, with vbtm.
---

# Finding why startup is slow

Verbatime can record from the start of `main()`, so the whole startup becomes one session. You
can see which phase took the time, then which calls inside it.

## Record

:::: steps

1. Add `waitstart=60s` to the agent, and use the startup method as the root, such as
   `org.springframework.boot.SpringApplication::run` for Spring Boot.

   ```text
   -javaagent:/path/to/verbatime-agent.jar=waitstart=60s,roots=org.springframework.boot.SpringApplication::run
   ```

   Add the JMX flags from [Quick start](../quick-start.md#with-jdk-mission-control) too.

2. Start the application. It waits at `main()` until you press Start recording in
   [JDK Mission Control](../jmc.md#record), for up to 60 seconds.

3. When the application has started, press Stop recording.

::::

To record without JDK Mission Control, use the [agent alone](../quick-start.md#agent-alone) with
`record=startup` and `out=`. The file is written when the JVM exits, so stop the application once
it has started.

## Find the slow phase

```sh
vbtm sessions rec.vbtm --root SpringApplication::run
```

```text
file: rec.vbtm  status: complete  recorded: 2026-09-28T05:51:09.347+00:00
length: 25376.0537 ms  threads: 11  sessions: 5,818  calls: 20,230,812  methods: 30,331  gc: 31 pauses, 104.0000 ms
units: ms, 0.0001 ms = 1 tick of 100 ns
1 of 5,818 sessions match, sorted by start, showing 1

id     start        dur      calls  depth  throws    gc_ms  thread  root
 1  288.8474  2046.4070  8,508,752    178  65,891  30.0000  main    SpringApplication.run#2

names printed alike:
  SpringApplication.run#2 = org.springframework.boot.SpringApplication.run(Ljava/lang/Class;[Ljava/lang/String;)Lorg/springframework/context/ConfigurableApplicationContext;
```

This is a recording of the
[`spring-boot-mvc`](https://github.com/yagipass/verbatime/tree/main/examples/spring-boot-mvc)
example. Startup is session 1, with 8.5 million calls. Print only its calls of 200 ms or more.

```sh
vbtm tree rec.vbtm 1 --floor 200ms
```

The call lines of the output are below. The header and the lines that sum up shorter calls are
left out.

```text
1.0 0.0000 2046.4070 0 SpringApplication.run#2 self 0.0101
1.1 0.0033 2046.3969 1 SpringApplication.run#3 self 0.4389
1.336576 107.6264 1938.7736 2 SpringApplication.run self 0.2002
1.503412 152.6995 289.8052 3 SpringApplication.prepareEnvironment self 0.1250
1.2244223 622.0811 1404.0738 3 SpringApplication.refreshContext self 0.0039
1.2244231 622.0999 1404.0547 4 SpringApplication.refresh self 0.0026
1.2244232 622.1021 1404.0521 5 ServletWebServerApplicationContext.refresh self 0.0019
1.2244233 622.1038 1404.0502 6 AbstractApplicationContext.refresh self 0.0387
1.2355076 645.1287 596.6986 7 AbstractApplicationContext.invokeBeanFactoryPostProcessors self 0.0715
1.2357202 645.8468 595.9750 8 PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors self 0.0767
1.2520049 704.6216 463.4225 9 PostProcessorRegistrationDelegate.invokeBeanDefinitionRegistryPostProcessors self 0.0942
1.2520052 704.7145 463.3278 10 ConfigurationClassPostProcessor.postProcessBeanDefinitionRegistry self 0.0072
1.2520053 704.7216 463.3206 11 ConfigurationClassPostProcessor.processConfigBeanDefinitions self 1.6943
1.2552189 715.0531 237.2485 12 ConfigurationClassParser.parse self 0.0228
1.3559845 958.8813 206.4416 12 ConfigurationClassBeanDefinitionReader.loadBeanDefinitions self 0.0595
1.5126049 1289.2196 317.0340 7 ServletWebServerApplicationContext.onRefresh self 0.0035
1.5126051 1289.2232 317.0302 8 ServletWebServerApplicationContext.createWebServer self 0.2040
1.6523808 1607.0679 394.6205 7 AbstractApplicationContext.finishBeanFactoryInitialization self 0.0353
1.6599558 1621.7234 379.9647 8 DefaultListableBeanFactory.preInstantiateSingletons self 0.1534
```

Each line is a call id, its start and duration in ms, its depth, and the method. The calls are in
the order they ran, so the lines read as the phases of startup:

| Phase | Method | ms |
|---|---|---|
| Reading the configuration | `SpringApplication.prepareEnvironment` | 290 |
| Processing the configuration classes | `AbstractApplicationContext.invokeBeanFactoryPostProcessors` | 597 |
| Starting the embedded web server | `ServletWebServerApplicationContext.createWebServer` | 317 |
| Creating the singleton beans | `DefaultListableBeanFactory.preInstantiateSingletons` | 380 |

## Find the slow bean

Spring creates the singleton beans one by one, and each creation also creates the beans it depends
on. List the slowest creations.

```sh
vbtm find rec.vbtm DefaultListableBeanFactory::preInstantiateSingleton --session 1 --limit 3
```

```text
pattern: DefaultListableBeanFactory::preInstantiateSingleton -> org.springframework.beans.factory.support.DefaultListableBeanFactory.preInstantiateSingleton(Ljava/lang/String;Lorg/springframework/beans/factory/support/RootBeanDefinition;)Ljava/util/concurrent/CompletableFuture;  scope: session 1
matches: 149 calls, 361.1682 ms in total, where a call inside another call of the same method is not added again
sorted by dur, showing 3. start is ms from the session start

       id      start       dur    self  depth  method                                              caller                                               flags
1.7000046  1709.1595  153.0142  0.0005      9  DefaultListableBeanFactory.preInstantiateSingleton  DefaultListableBeanFactory.preInstantiateSingletons
1.7681004  1883.2898   64.7623  0.0005      9  DefaultListableBeanFactory.preInstantiateSingleton  DefaultListableBeanFactory.preInstantiateSingletons
1.6802381  1673.0277   22.0640  0.0014      9  DefaultListableBeanFactory.preInstantiateSingleton  DefaultListableBeanFactory.preInstantiateSingletons
# 146 more calls. next: vbtm find rec.vbtm DefaultListableBeanFactory::preInstantiateSingleton --session 1 --limit 9
```

Then print the slowest one and everything under it.

```sh
vbtm tree rec.vbtm --at 1.7000046 --floor 30ms
```

A recording holds no argument values, so bean names do not appear. For a bean defined by a
`@Bean` method, that method appears under `AbstractAutowireCapableBeanFactory.createBeanInstance`.
In this recording, the slowest creation spends 80 ms in
`WebMvcConfigurationSupport.mvcContentNegotiationManager`, setting up the message converters.

## Class loading

Sorted by self time with `vbtm hot rec.vbtm 1`, class loading such as `JarUrlClassLoader.loadClass`
comes first at startup, because most classes load then. It looks longer in a recording than it is
without the agent. See [Limitations](../limitations.md#class-loading-looks-slower-than-it-is).

## Next

- [Finding why a request is slow](./slow-request.md)
- [Agent options](../agent/options.md), including `waitstart=` and `record=startup`
