package io.github.yagipass.verbatime.corpusfixtures;

import java.util.List;
import java.util.Locale;
import scala.jdk.javaapi.CollectionConverters;

public final class ScalaRoundTrip {

  private ScalaRoundTrip() {}

  public static String run() {
    scala.collection.immutable.List<String> xs =
        CollectionConverters.asScala(List.of("beta", "alpha", "gamma")).toList();
    scala.collection.immutable.List<String> upper = xs.map(s -> s.toUpperCase(Locale.ROOT));
    scala.collection.immutable.Vector<String> v = upper.reverse().$colon$colon("DELTA").toVector();
    return v.mkString("[", ",", "]") + " size=" + v.size() + " head=" + xs.head();
  }
}
