package io.github.yagipass.verbatime.fixtures;

import java.util.function.IntUnaryOperator;

public final class Fixture implements FixtureInterface {

    @Marker("root")
    public String root() {
        final StringBuilder sb = new StringBuilder();
        sb.append(stat(1, 2L)).append(',');
        sb.append(inst(3.5, 4.5f)).append(',');
        sb.append(prims()).append(',');
        sb.append(sync(5)).append(',');
        sb.append(rec(3)).append(',');
        sb.append(caught()).append(',');
        sb.append(caughtOther()).append(',');
        sb.append(lambda(6)).append(',');
        sb.append(greet("w")).append(',');
        sb.append(FixtureInterface.istatic(7)).append(',');
        sb.append(new Inner().inner(8)).append(',');
        sb.append(arr(new int[] { 1, 2, 3 }).length).append(',');
        sb.append(annotatedParam("p")).append(',');
        sb.append(vd());
        return sb.toString();
    }

    public void rootThrows() {
        thrower();
    }

    @SuppressWarnings("UnusedVariable")
    private static Object[] live;

    public int rootAllocates() {
        final Object[] keep = new Object[1 << 21];
        for (int i = 0; i < keep.length; i++) {
            keep[i] = new int[2];
        }
        live = keep;
        int n = 0;
        for (int i = 0; i < 32; i++) {
            n += churn(i);
        }
        collect();
        live = null;
        return n;
    }

    int churn(final int i) {
        final byte[][] junk = new byte[16][];
        for (int k = 0; k < junk.length; k++) {
            junk[k] = new byte[64 * 1024];
        }
        return junk.length + i;
    }

    void collect() {
        System.gc();
    }

    public static long stat(final int a, final long b) {
        return a + b;
    }

    double inst(final double a, final float b) {
        return a + b;
    }

    String prims() {
        return "" + bool() + by() + ch() + sh() + in() + lo() + fl() + db();
    }

    boolean bool() {
        return true;
    }

    byte by() {
        return 1;
    }

    char ch() {
        return 'c';
    }

    short sh() {
        return 2;
    }

    int in() {
        return 3;
    }

    long lo() {
        return 4L;
    }

    float fl() {
        return 5f;
    }

    double db() {
        return 6d;
    }

    synchronized int sync(final int x) {
        return x * 2;
    }

    int rec(final int n) {
        return n == 0 ? 0 : 1 + rec(n - 1);
    }

    int caught() {
        try {
            thrower();
            return 0;
        } catch (final IllegalStateException e) {
            return 1;
        }
    }

    void thrower() {
        throw new IllegalStateException("x");
    }

    int caughtOther() {
        try {
            otherThrower();
            return 0;
        } catch (final FixtureException e) {
            return 2;
        }
    }

    void otherThrower() {
        throw new FixtureException("y");
    }

    int lambda(final int x) {
        final IntUnaryOperator op = v -> lambdaBody(v) + 1;
        return op.applyAsInt(x);
    }

    int lambdaBody(final int v) {
        return v * 2;
    }

    int[] arr(final int[] a) {
        return a;
    }

    String annotatedParam(@Marker("param") final String s) {
        return s + s;
    }

    String vd() {
        voidMethod();
        return "v";
    }

    void voidMethod() {
    }

    @Override
    public String abstractMethod() {
        return "impl";
    }

    public static final class Inner {
        int inner(final int x) {
            return x + 100;
        }
    }
}
