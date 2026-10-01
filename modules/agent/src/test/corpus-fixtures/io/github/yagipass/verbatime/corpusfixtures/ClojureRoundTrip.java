package io.github.yagipass.verbatime.corpusfixtures;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

public final class ClojureRoundTrip {

    private ClojureRoundTrip() {
    }

    public static String run() {
        IFn plus = Clojure.var("clojure.core", "+");
        IFn readString = Clojure.var("clojure.core", "read-string");
        IFn eval = Clojure.var("clojure.core", "eval");
        Object sum = eval.invoke(readString.invoke("(reduce + (map inc (range 10)))"));
        return plus.invoke(1, 2) + " " + sum;
    }
}
