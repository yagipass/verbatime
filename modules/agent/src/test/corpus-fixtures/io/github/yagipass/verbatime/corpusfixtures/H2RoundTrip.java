package io.github.yagipass.verbatime.corpusfixtures;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public final class H2RoundTrip {

    private H2RoundTrip() {
    }

    public static String run() throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (Connection c = new org.h2.Driver().connect("jdbc:h2:mem:", new Properties()); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE t(id INT PRIMARY KEY, name VARCHAR(20))");
            s.execute("INSERT INTO t VALUES (1, 'alpha'), (2, 'beta'), (3, 'gamma')");
            try (ResultSet rs = s.executeQuery("SELECT id, UPPER(name) FROM t WHERE id > 1 ORDER BY id DESC")) {
                while (rs.next()) {
                    sb.append(rs.getInt(1)).append('=').append(rs.getString(2)).append(';');
                }
            }
        }
        return sb.toString();
    }
}
