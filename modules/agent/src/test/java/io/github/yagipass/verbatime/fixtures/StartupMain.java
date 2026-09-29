package io.github.yagipass.verbatime.fixtures;

import java.util.List;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

public final class StartupMain {

    private StartupMain() {
    }

    public static void main(String[] args) {
        List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
        if (!servers.isEmpty()) {
            System.err.println("[e2e-startup]   FAIL record=startup created " + servers.size() + " MBeanServer(s)");
            System.exit(1);
        }
        Fixture fx = new Fixture();
        System.out.println("root() = " + fx.root());
        try {
            fx.rootThrows();
        } catch (IllegalStateException expected) {
            System.out.println("rootThrows() threw as expected");
        }
    }
}
