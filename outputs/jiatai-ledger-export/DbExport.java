import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Locale;

public class DbExport {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: DbExport <sql-file> <csv-output>");
        }
        String host = getenv("DB_HOST", "172.20.6.33");
        String port = getenv("DB_PORT", "3306");
        String user = getenv("DB_USER", "root");
        String pass = System.getenv("DB_PASS");
        if (pass == null || pass.isBlank()) {
            throw new IllegalArgumentException("DB_PASS is required");
        }

        String sql = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8).trim();
        String normalized = sql.toLowerCase(Locale.ROOT).stripLeading();
        if (!(normalized.startsWith("select") || normalized.startsWith("show") || normalized.startsWith("with"))) {
            throw new IllegalArgumentException("Only read-only SQL is allowed");
        }

        String url = "jdbc:mysql://" + host + ":" + port + "/?useUnicode=true&characterEncoding=utf8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement setup = conn.createStatement()) {
            conn.setReadOnly(true);
            setup.execute("SET SESSION TRANSACTION READ ONLY");
            setup.execute("SET SESSION MAX_EXECUTION_TIME=600000");
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery();
                 BufferedWriter out = Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.UTF_8)) {
                writeCsv(rs, out);
            }
        }
    }

    private static String getenv(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void writeCsv(ResultSet rs, BufferedWriter out) throws SQLException, IOException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        for (int i = 1; i <= cols; i++) {
            if (i > 1) out.write(',');
            out.write(csv(meta.getColumnLabel(i)));
        }
        out.write('\n');
        long rows = 0;
        while (rs.next()) {
            for (int i = 1; i <= cols; i++) {
                if (i > 1) out.write(',');
                Object value = rs.getObject(i);
                out.write(csv(format(value)));
            }
            out.write('\n');
            rows++;
        }
        System.err.println("rows=" + rows + " at " + LocalDateTime.now());
    }

    private static String format(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros().toPlainString();
        return String.valueOf(value);
    }

    private static String csv(String value) {
        if (value == null) return "";
        boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }
}
