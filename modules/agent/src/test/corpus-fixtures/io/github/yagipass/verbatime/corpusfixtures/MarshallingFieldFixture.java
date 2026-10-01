package io.github.yagipass.verbatime.corpusfixtures;

import org.jboss.marshalling.FieldSetter;

public final class MarshallingFieldFixture {

    private int value;

    private MarshallingFieldFixture() {
    }

    public static String fromMethod() {
        try {
            FieldSetter setter = FieldSetter.get(MarshallingFieldFixture.class, "value");
            MarshallingFieldFixture f = new MarshallingFieldFixture();
            setter.setInt(f, 42);
            return String.valueOf(f.value);
        } catch (RuntimeException e) {
            return e.toString();
        }
    }

    public static String fromStaticInitializer() {
        try {
            return Holder.read();
        } catch (ExceptionInInitializerError e) {
            return String.valueOf(e.getCause());
        }
    }

    static final class Holder {

        private static final FieldSetter SETTER = FieldSetter.get(Holder.class, "count");

        private int count;

        private Holder() {
        }

        static String read() {
            Holder h = new Holder();
            SETTER.setInt(h, 7);
            return String.valueOf(h.count);
        }
    }
}
