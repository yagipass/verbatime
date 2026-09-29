package io.github.yagipass.verbatime.agent;

public record RootSpec(String className, String methodName, String descriptor) {

    public static RootSpec parse(String s) {
        int sep = s.indexOf("::");
        if (sep <= 0) {
            throw new IllegalArgumentException("expected pkg.Cls::method or pkg.Cls::method(desc), got '" + s + "'");
        }
        String cls = s.substring(0, sep).trim();
        String rest = s.substring(sep + 2).trim();
        int paren = rest.indexOf('(');
        String method = paren < 0 ? rest : rest.substring(0, paren);
        String desc = paren < 0 ? null : rest.substring(paren);
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

    boolean matches(String name, String desc) {
        return methodName.equals(name) && (descriptor == null || descriptor.equals(desc));
    }

    @Override
    public String toString() {
        return className + "::" + methodName + (descriptor == null ? "" : descriptor);
    }
}
