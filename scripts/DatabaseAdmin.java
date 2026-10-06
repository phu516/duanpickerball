import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;

/** Local database maintenance command. Never exposes passwords or password hashes. */
public class DatabaseAdmin {
    static final List<String> TABLES = List.of("users", "categories", "products", "product_variants",
            "product_fulfillment_options", "product_images", "courts", "court_price_rules", "court_blocks",
            "bookings", "booking_cancellation_requests", "carts", "cart_items", "orders", "order_items",
            "inventory_reservations", "inventory_movements", "payments", "payment_events", "refunds",
            "guest_access_tokens", "access_challenges", "password_reset_tokens", "settings", "audit_logs",
            "idempotency_records", "notification_outbox");

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[1]).toAbsolutePath();
        if (args[0].equals("inspect-h2")) {
            for (String relative : List.of("data/picklezone-dev", "backend/data/picklezone-dev")) {
                Path file = root.resolve(relative);
                if (!Files.exists(Path.of(file + ".mv.db"))) continue;
                String url = "jdbc:h2:file:" + file.toString().replace('\\', '/')
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;IFEXISTS=TRUE;ACCESS_MODE_DATA=r";
                try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                    inspect(connection, relative);
                }
            }
        } else if (args[0].equals("inspect-mysql")) {
            try (Connection connection = mysql(root)) { inspect(connection, "MySQL picklezone"); }
        } else if (args[0].equals("inspect-docker")) {
            try (Connection connection = docker(root)) { inspect(connection, "MySQL docker source"); }
        } else if (args[0].equals("discover-mysql")) {
            try (Connection connection = mysql(root, true);
                 ResultSet databases = connection.getMetaData().getCatalogs()) {
                while (databases.next()) System.out.println("SCHEMA " + databases.getString(1));
            }
        } else if (args[0].equals("dry-run") || args[0].equals("transfer")) {
            transfer(root, args[0].equals("dry-run"));
        } else if (args[0].equals("reset-admin")) {
            resetAdmin(root);
        } else if (args[0].equals("verify")) {
            try (Connection connection = mysql(root)) { verify(connection); }
        } else {
            throw new IllegalArgumentException("Unknown database command");
        }
    }

    static Connection mysql(Path root) throws Exception {
        return mysql(root, false);
    }

    @SuppressWarnings("unchecked")
    static Connection docker(Path root) throws Exception {
        Map<String, Object> compose;
        try (var reader = Files.newBufferedReader(root.resolve("docker-compose.yml"))) {
            compose = new org.yaml.snakeyaml.Yaml().load(reader);
        }
        Map<String, Object> service = (Map<String, Object>) ((Map<?, ?>) compose.get("services")).get("mysql");
        Map<String, Object> environment = (Map<String, Object>) service.get("environment");
        String port = ((List<?>) service.get("ports")).getFirst().toString().split(":")[0];
        String url = "jdbc:mysql://localhost:" + port + "/" + environment.get("MYSQL_DATABASE")
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        return DriverManager.getConnection(url, environment.get("MYSQL_USER").toString(), environment.get("MYSQL_PASSWORD").toString());
    }

    static Connection mysql(Path root, boolean serverOnly) throws Exception {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("backend/application-local.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String url = properties.getProperty("spring.datasource.url");
        String password = properties.getProperty("spring.datasource.password");
        if (url == null || !url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException("Expected MySQL URL");
        if (serverOnly) url = url.replaceFirst("(jdbc:mysql://[^/]+/)[^?]*", "$1");
        if (password == null || password.equals("PASTE_MYSQL_ROOT_PASSWORD_HERE"))
            throw new IllegalArgumentException("Enter the MySQL root password in backend/application-local.properties first");
        Connection connection = DriverManager.getConnection(url, properties.getProperty("spring.datasource.username"), password);
        connection.createStatement().execute("SET time_zone = '+00:00'");
        return connection;
    }

    record Source(String name, Connection connection) implements AutoCloseable {
        public void close() throws SQLException { connection.close(); }
    }

    static void transfer(Path root, boolean dryRun) throws Exception {
        Path backup = root.resolve("backend/data-transfer/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(backup);
        List<Source> sources = new ArrayList<>();
        try {
            Connection legacy = docker(root);
            legacy.setReadOnly(true);
            legacy.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            legacy.setAutoCommit(false);
            sources.add(new Source("mysql-3307", legacy));
            dumpMysql(legacy, backup.resolve("mysql-3307-before.sql"));
            for (String relative : List.of("backend/data/picklezone-dev", "data/picklezone-dev")) {
                String name = relative.startsWith("backend/") ? "h2-backend" : "h2-root";
                Path original = root.resolve(relative + ".mv.db");
                Path copy = backup.resolve(name + ".mv.db");
                Files.copy(original, copy);
                Connection connection = DriverManager.getConnection("jdbc:h2:file:" + backup.resolve(name).toString().replace('\\', '/')
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;IFEXISTS=TRUE;ACCESS_MODE_DATA=r", "sa", "");
                sources.add(new Source(name, connection));
            }
            try (Connection server = mysql(root, true); Statement statement = server.createStatement()) {
                statement.execute("CREATE DATABASE IF NOT EXISTS picklezone CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            Properties local = new Properties();
            try (var reader = Files.newBufferedReader(root.resolve("backend/application-local.properties"), StandardCharsets.UTF_8)) { local.load(reader); }
            org.flywaydb.core.Flyway.configure()
                    .dataSource(local.getProperty("spring.datasource.url"), local.getProperty("spring.datasource.username"), local.getProperty("spring.datasource.password"))
                    .locations("filesystem:" + root.resolve("backend/src/main/resources/db/migration").toString().replace('\\', '/'))
                    .load().migrate();
            try (Connection target = mysql(root)) {
                if (!"picklezone".equals(target.getCatalog())) throw new IllegalStateException("Unexpected target schema");
                for (String table : TABLES) {
                    if (count(target, table) != 0) throw new IllegalStateException("Target must be empty before transfer: " + table);
                }
                target.setAutoCommit(false);
                StringBuilder report = new StringBuilder("Target: localhost:3306/picklezone\nMode: " + (dryRun ? "DRY RUN" : "APPLY") + "\n");
                StringBuilder ids = new StringBuilder("source\ttable\tsource_id\ttarget_id\n");
                try {
                    for (Source source : sources) merge(source, target, report, ids);
                    report.append(verify(target));
                    Files.writeString(backup.resolve("id-mapping.tsv"), ids, StandardCharsets.UTF_8);
                    if (dryRun) target.rollback(); else target.commit();
                    report.append(dryRun ? "ROLLED BACK: validation only\n" : "COMMITTED\n");
                    Files.writeString(backup.resolve("transfer-report.txt"), report, StandardCharsets.UTF_8);
                    System.out.print(report);
                    System.out.println("Backup: " + backup);
                } catch (Exception error) {
                    target.rollback();
                    Files.writeString(backup.resolve("transfer-report.txt"), report + "ROLLED BACK: " + error.getMessage(), StandardCharsets.UTF_8);
                    throw error;
                }
            }
        } finally {
            for (Source source : sources) source.close();
        }
    }

    static void merge(Source source, Connection target, StringBuilder report, StringBuilder idReport) throws Exception {
        Map<String, Map<Long, Long>> ids = new HashMap<>();
        for (String table : TABLES) {
            Map<Long, Long> tableIds = new HashMap<>();
            ids.put(table, tableIds);
            Map<String, String> foreignKeys = new HashMap<>();
            try (ResultSet keys = source.connection().getMetaData().getImportedKeys(source.connection().getCatalog(), null, table)) {
                while (keys.next()) foreignKeys.put(keys.getString("FKCOLUMN_NAME").toLowerCase(), keys.getString("PKTABLE_NAME").toLowerCase());
            }
            List<List<String>> uniqueKeys = uniqueKeys(target, table);
            int added = 0, reused = 0;
            for (Map<String, Object> original : rows(source.connection(), "SELECT * FROM " + quote(table))) {
                Map<String, Object> row = new LinkedHashMap<>(original);
                Long oldId = row.get("id") == null ? null : ((Number) row.remove("id")).longValue();
                for (var key : foreignKeys.entrySet()) remap(row, key.getKey(), key.getValue(), ids);
                if (table.equals("payment_events") && "SANDBOX".equals(row.get("provider"))
                        && ("sandbox-" + original.get("payment_id")).equals(row.get("event_id"))) {
                    row.put("event_id", "sandbox-" + row.get("payment_id"));
                }
                if (List.of("guest_access_tokens", "access_challenges", "audit_logs").contains(table)) {
                    remapResource(row, "resource_type", "resource_id", ids);
                } else if (table.equals("idempotency_records")) {
                    remapResource(row, "result_type", "result_id", ids);
                }
                if (table.equals("settings")) {
                    var existing = matching(target, table, row, List.of("setting_key"));
                    if (existing == null) { insert(target, table, row); added++; } else reused++;
                    continue;
                }
                if (table.equals("product_variants") && matching(target, table, row, List.of("sku")) != null) {
                    String previous = row.get("sku").toString();
                    String suffix = "-" + source.name() + "-" + oldId;
                    String renamed = previous.substring(0, Math.min(previous.length(), 80 - suffix.length())) + suffix;
                    row.put("sku", renamed);
                    if (matching(target, table, row, List.of("sku")) != null)
                        throw new IllegalStateException("Imported SKU also conflicts: " + renamed);
                    report.append("SKU_RENAMED ").append(source.name()).append(' ').append(previous).append(" -> ").append(renamed).append('\n');
                }
                Map<String, Object> existing = null;
                for (List<String> key : uniqueKeys) {
                    Map<String, Object> candidate = matching(target, table, row, key);
                    if (candidate != null) {
                        if (existing != null && !existing.get("id").equals(candidate.get("id")))
                            throw new IllegalStateException("Conflicting unique keys in " + source.name() + "/" + table + "/" + oldId);
                        existing = candidate;
                    }
                }
                if (table.equals("court_price_rules")) existing = mergePrice(target, row);
                long newId;
                if (existing == null) {
                    newId = insert(target, table, row);
                    if (table.equals("payments") && "SANDBOX".equals(row.get("provider"))
                            && ("sbx-" + oldId).equals(row.get("provider_reference"))) {
                        execute(target, "UPDATE payments SET provider_reference=? WHERE id=?", "sbx-" + newId, newId);
                    }
                    added++;
                } else {
                    newId = ((Number) existing.get("id")).longValue();
                    if (table.equals("users") && !Objects.equals(row.get("role"), existing.get("role")))
                        throw new IllegalStateException("User role conflict: " + row.get("username"));
                    if (!List.of("users", "categories", "courts", "court_price_rules").contains(table)) {
                        for (var field : row.entrySet()) {
                            if (!equivalent(field.getValue(), existing.get(field.getKey())))
                                throw new IllegalStateException("Different rows share a unique key: " + source.name() + "/" + table + "/" + oldId + " field=" + field.getKey());
                        }
                    }
                    if (table.equals("courts") && Boolean.TRUE.equals(row.get("locked"))) {
                        execute(target, "UPDATE courts SET locked = true WHERE id = ?", newId);
                    }
                    reused++;
                }
                tableIds.put(oldId, newId);
                idReport.append(source.name()).append('\t').append(table).append('\t').append(oldId).append('\t').append(newId).append('\n');
            }
            report.append(source.name()).append(' ').append(table).append(" inserted=").append(added).append(" merged=").append(reused).append('\n');
        }
    }

    static void remap(Map<String, Object> row, String column, String table, Map<String, Map<Long, Long>> ids) {
        Object value = row.get(column);
        if (value == null) return;
        Long mapped = ids.getOrDefault(table, Map.of()).get(((Number) value).longValue());
        if (mapped == null) throw new IllegalStateException("Missing parent mapping: " + column + " -> " + table + "/" + value);
        row.put(column, mapped);
    }

    static void remapResource(Map<String, Object> row, String typeColumn, String idColumn, Map<String, Map<Long, Long>> ids) {
        if (row.get(idColumn) == null) return;
        String table = switch (Objects.toString(row.get(typeColumn), "")) {
            case "BOOKING" -> "bookings";
            case "ORDER" -> "orders";
            case "PAYMENT" -> "payments";
            case "REFUND" -> "refunds";
            case "COURT" -> "courts";
            case "USER" -> "users";
            default -> throw new IllegalStateException("Unsupported resource type: " + row.get(typeColumn));
        };
        remap(row, idColumn, table, ids);
    }

    static boolean equivalent(Object first, Object second) {
        if (first == null || second == null) return first == second;
        if (first instanceof Number && second instanceof Number)
            return new BigDecimal(first.toString()).compareTo(new BigDecimal(second.toString())) == 0;
        return first.toString().equals(second.toString());
    }

    static Map<String, Object> mergePrice(Connection target, Map<String, Object> incoming) throws Exception {
        var sameSlot = rows(target, "SELECT * FROM court_price_rules WHERE court_id = ? AND day_of_week = ? AND ((start_time = ? AND end_time = ?) OR (start_time < ? AND end_time > ?))",
                incoming.get("court_id"), incoming.get("day_of_week"), incoming.get("start_time"), incoming.get("end_time"), incoming.get("end_time"), incoming.get("start_time"));
        for (var existing : sameSlot) {
            LocalDate start = LocalDate.parse(incoming.get("valid_from").toString());
            LocalDate oldStart = LocalDate.parse(existing.get("valid_from").toString());
            LocalDate end = incoming.get("valid_to") == null ? LocalDate.MAX : LocalDate.parse(incoming.get("valid_to").toString());
            LocalDate oldEnd = existing.get("valid_to") == null ? LocalDate.MAX : LocalDate.parse(existing.get("valid_to").toString());
            if (start.isAfter(oldEnd) || oldStart.isAfter(end)) continue;
            if (!equivalent(incoming.get("hourly_price"), existing.get("hourly_price"))
                    || !equivalent(incoming.get("start_time"), existing.get("start_time"))
                    || !equivalent(incoming.get("end_time"), existing.get("end_time")))
                throw new IllegalStateException("Overlapping prices differ for court " + incoming.get("court_id"));
            LocalDate earliest = start.isBefore(oldStart) ? start : oldStart;
            LocalDate latest = end.isAfter(oldEnd) ? end : oldEnd;
            execute(target, "UPDATE court_price_rules SET valid_from = ?, valid_to = ? WHERE id = ?",
                    earliest, latest.equals(LocalDate.MAX) ? null : latest, existing.get("id"));
            return existing;
        }
        return null;
    }

    static List<List<String>> uniqueKeys(Connection connection, String table) throws Exception {
        Map<String, SortedMap<Short, String>> indexes = new LinkedHashMap<>();
        try (ResultSet keys = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, true, false)) {
            while (keys.next()) {
                String name = keys.getString("INDEX_NAME"), column = keys.getString("COLUMN_NAME");
                if (name == null || column == null) continue;
                indexes.computeIfAbsent(name, ignored -> new TreeMap<>()).put(keys.getShort("ORDINAL_POSITION"), column.toLowerCase());
            }
        }
        return indexes.values().stream().map(index -> List.copyOf(index.values())).filter(key -> !key.equals(List.of("id"))).toList();
    }

    static Map<String, Object> matching(Connection target, String table, Map<String, Object> row, List<String> columns) throws Exception {
        if (columns.stream().anyMatch(column -> row.get(column) == null)) return null;
        String condition = String.join(" AND ", columns.stream().map(column -> quote(column) + " = ?").toList());
        List<Map<String, Object>> matches = rows(target, "SELECT * FROM " + quote(table) + " WHERE " + condition, columns.stream().map(row::get).toArray());
        if (matches.size() > 1) throw new IllegalStateException("Ambiguous match in " + table);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    static long insert(Connection connection, String table, Map<String, Object> row) throws Exception {
        String columns = String.join(",", row.keySet().stream().map(DatabaseAdmin::quote).toList());
        String placeholders = String.join(",", Collections.nCopies(row.size(), "?"));
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + quote(table) + " (" + columns + ") VALUES (" + placeholders + ")", Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            for (Object value : row.values()) statement.setObject(index++, value);
            statement.executeUpdate();
            try (ResultSet result = statement.getGeneratedKeys()) { return result.next() ? result.getLong(1) : 0; }
        }
    }

    static List<Map<String, Object>> rows(Connection connection, String sql, Object... parameters) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            try (ResultSet data = statement.executeQuery()) {
                ResultSetMetaData metadata = data.getMetaData();
                while (data.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        Object value = switch (metadata.getColumnType(index)) {
                            case Types.DATE -> data.getObject(index, java.time.LocalDate.class);
                            case Types.TIME -> data.getObject(index, java.time.LocalTime.class);
                            case Types.TIMESTAMP -> data.getObject(index, java.time.LocalDateTime.class);
                            default -> data.getObject(index);
                        };
                        if (value instanceof Clob clob) value = clob.getSubString(1, Math.toIntExact(clob.length()));
                        if (value instanceof Blob blob) value = blob.getBytes(1, Math.toIntExact(blob.length()));
                        row.put(metadata.getColumnLabel(index).toLowerCase(), value);
                    }
                    result.add(row);
                }
            }
        }
        return result;
    }

    static void execute(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            statement.executeUpdate();
        }
    }

    static long count(Connection connection, String table) throws Exception {
        return ((Number) rows(connection, "SELECT COUNT(*) AS total FROM " + quote(table)).getFirst().get("total")).longValue();
    }

    static String quote(String name) {
        if (!name.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Invalid SQL identifier");
        return "`" + name + "`";
    }

    static void dumpMysql(Connection source, Path path) throws Exception {
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("-- Source: local Docker picklezone database. Contains sensitive application data.\nSET FOREIGN_KEY_CHECKS=0;\n");
            List<String> tables = new ArrayList<>(TABLES);
            tables.add("flyway_schema_history");
            for (String table : tables) {
                try (Statement statement = source.createStatement(); ResultSet ddl = statement.executeQuery("SHOW CREATE TABLE " + quote(table))) {
                    ddl.next(); writer.write(ddl.getString(2) + ";\n");
                }
                for (var row : rows(source, "SELECT * FROM " + quote(table))) {
                    writer.write("INSERT INTO " + quote(table) + " (" + String.join(",", row.keySet().stream().map(DatabaseAdmin::quote).toList())
                            + ") VALUES (" + String.join(",", row.values().stream().map(DatabaseAdmin::literal).toList()) + ");\n");
                }
            }
            writer.write("SET FOREIGN_KEY_CHECKS=1;\n");
        }
    }

    static String literal(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Boolean bool) return bool ? "1" : "0";
        if (value instanceof Number) return value.toString();
        if (value instanceof byte[] bytes) return "X'" + HexFormat.of().formatHex(bytes) + "'";
        return "CONVERT(X'" + HexFormat.of().formatHex(value.toString().getBytes(StandardCharsets.UTF_8)) + "' USING utf8mb4)";
    }

    static String verify(Connection connection) throws Exception {
        StringBuilder report = new StringBuilder();
        for (String table : TABLES) report.append("TOTAL ").append(table).append('=').append(count(connection, table)).append('\n');
        String orphanTokens = "SELECT COUNT(*) AS total FROM guest_access_tokens g LEFT JOIN bookings b ON g.resource_type='BOOKING' AND b.id=g.resource_id LEFT JOIN orders o ON g.resource_type='ORDER' AND o.id=g.resource_id WHERE (g.resource_type='BOOKING' AND b.id IS NULL) OR (g.resource_type='ORDER' AND o.id IS NULL)";
        if (((Number) rows(connection, orphanTokens).getFirst().get("total")).longValue() != 0)
            throw new IllegalStateException("Guest token references are invalid");
        for (var record : rows(connection, "SELECT result_type,result_id FROM idempotency_records")) {
            String table = switch (record.get("result_type").toString()) {
                case "BOOKING" -> "bookings";
                case "ORDER" -> "orders";
                default -> throw new IllegalStateException("Unknown idempotency result type");
            };
            if (rows(connection, "SELECT id FROM " + quote(table) + " WHERE id=?", record.get("result_id")).size() != 1)
                throw new IllegalStateException("Invalid idempotency reference");
        }
        String overlapSql = "SELECT a.booking_code AS first_code,b.booking_code AS second_code FROM bookings a JOIN bookings b ON a.court_id=b.court_id AND a.id<b.id AND a.start_at<b.end_at AND a.end_at>b.start_at WHERE a.end_at>UTC_TIMESTAMP() AND b.end_at>UTC_TIMESTAMP() AND (a.status IN ('CONFIRMED','CHECKED_IN') OR (a.status='HOLD' AND a.hold_expires_at>UTC_TIMESTAMP())) AND (b.status IN ('CONFIRMED','CHECKED_IN') OR (b.status='HOLD' AND b.hold_expires_at>UTC_TIMESTAMP()))";
        for (var overlap : rows(connection, overlapSql)) report.append("BOOKING_OVERLAP ").append(overlap.get("first_code")).append(" / ").append(overlap.get("second_code")).append('\n');
        report.append("REFERENCE_CHECK_OK\n");
        System.out.print(report);
        return report.toString();
    }

    static void resetAdmin(Path root) throws Exception {
        byte[] random = new byte[10];
        new SecureRandom().nextBytes(random);
        String password = "Pz!" + HexFormat.of().formatHex(random) + "A9";
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        try (Connection connection = mysql(root)) {
            var accounts = rows(connection, "SELECT id,role FROM users WHERE username='admin'");
            if (accounts.size()!=1 || !"ADMIN".equals(accounts.getFirst().get("role")))
                throw new IllegalStateException("Expected an existing admin account with ADMIN role");
            long id = ((Number) accounts.getFirst().get("id")).longValue();
            connection.setAutoCommit(false);
            try {
                String hash = encoder.encode(password);
                execute(connection, "UPDATE users SET password_hash=?, enabled=true WHERE id=?", hash, id);
                execute(connection, "DELETE FROM password_reset_tokens WHERE user_id=?", id);
                execute(connection, "INSERT INTO audit_logs(actor_id,action,resource_type,resource_id,reason,created_at) VALUES(NULL,'ADMIN_PASSWORD_RESET','USER',?,'Local owner recovery',UTC_TIMESTAMP(6))", id);
                if (!encoder.matches(password, hash)) throw new IllegalStateException("Password verification failed");
                Path access = root.resolve("backend/data-transfer/admin-access.txt");
                Files.createDirectories(access.getParent());
                Files.writeString(access, "Database: localhost:3306/picklezone\nUsername: admin\nPassword: " + password + "\n", StandardCharsets.UTF_8);
                connection.commit();
                System.out.println("Admin password reset. Credentials saved in backend/data-transfer/admin-access.txt");
            } catch (Exception error) { connection.rollback(); throw error; }
        }
    }

    static void inspect(Connection connection, String label) throws Exception {
        System.out.println("DATABASE " + label);
        List<String> tables = new ArrayList<>();
        try (ResultSet result = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (result.next()) {
                String schema = result.getString("TABLE_SCHEM");
                if (schema != null && schema.equalsIgnoreCase("information_schema")) continue;
                tables.add(result.getString("TABLE_NAME"));
            }
        }
        Collections.sort(tables);
        for (String table : tables) {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                rows.next();
                System.out.println(table + "=" + rows.getLong(1));
            }
        }
        if (tables.contains("users")) {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT id, username, role, enabled FROM users ORDER BY id")) {
                while (rows.next()) System.out.printf("USER %s %s %s enabled=%s%n",
                        rows.getLong(1), rows.getString(2), rows.getString(3), rows.getBoolean(4));
            }
        }
        if (tables.contains("bookings")) {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT status, COUNT(*) FROM bookings GROUP BY status")) {
                while (rows.next()) System.out.printf("BOOKINGS %s=%s%n", rows.getString(1), rows.getLong(2));
            }
        }
        if (tables.contains("court_price_rules")) {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT hourly_price, valid_from, valid_to, COUNT(*) FROM court_price_rules GROUP BY hourly_price, valid_from, valid_to")) {
                while (rows.next()) System.out.printf("PRICES %s from=%s to=%s count=%s%n", rows.getObject(1), rows.getObject(2), rows.getObject(3), rows.getLong(4));
            }
        }
    }
}
