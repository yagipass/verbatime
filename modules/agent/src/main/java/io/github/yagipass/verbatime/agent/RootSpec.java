package io.github.yagipass.verbatime.agent;

public record RootSpec(String className, String methodName, String descriptor) {

    public static RootSpec parse(final String s) {
        final int sep = s.indexOf("::");
        if (sep <= 0) {
            throw new IllegalArgumentException("expected pkg.Cls::method or pkg.Cls::method(desc), got '" + s + "'");
        }
        final String cls = s.substring(0, sep).trim();
        final String rest = s.substring(sep + 2).trim();
        final int paren = rest.indexOf('(');
        final String method = paren < 0 ? rest : rest.substring(0, paren);
        final String desc = paren < 0 ? null : rest.substring(paren);
        if (cls.isEmpty() || method.isEmpty()) {
            throw new IllegalArgumentException("expected pkg.Cls::method or pkg.Cls::method(desc), got '" + s + "'");
        }
        if (method.startsWith("<")) {
            throw new IllegalArgumentException("constructors/initializers cannot be roots: " + s);
        }
        return new RootSpec(cls, method, desc);
    }

    public String internalClassName() {
        return className.replace('.', '/');
    }

    boolean matches(final String name, final String desc) {
        return methodName.equals(name) && (descriptor == null || descriptor.equals(desc));
    }

    @Override
    public String toString() {
        return className + "::" + methodName + (descriptor == null ? "" : descriptor);
    }
}
