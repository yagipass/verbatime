package io.github.yagipass.verbatime.corpusfixtures;

import java.util.List;
import kotlin.collections.CollectionsKt;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.text.StringsKt;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.Dispatchers;

public final class KotlinRoundTrip {

  private KotlinRoundTrip() {}

  public static String run() throws InterruptedException {
    List<Integer> sorted = CollectionsKt.sortedDescending(List.of(3, 1, 2));
    List<List<Integer>> windows = CollectionsKt.windowed(List.of(1, 2, 3, 4), 2, 1, false);
    String reversed = StringsKt.reversed("verbatime").toString();
    Integer answer =
        BuildersKt.runBlocking(
            EmptyCoroutineContext.INSTANCE,
            (scope, cont) -> {
              Deferred<Integer> d =
                  BuildersKt.<Integer>async(
                      scope, Dispatchers.getDefault(), CoroutineStart.DEFAULT, (s, c) -> 6 * 7);
              return d.await(cont);
            });
    return sorted + " " + windows + " " + reversed + " " + answer;
  }
}
