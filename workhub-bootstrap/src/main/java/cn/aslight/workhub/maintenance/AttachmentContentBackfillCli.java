package cn.aslight.workhub.maintenance;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;

/**
 * 将仍存在于应用机器本地目录的历史附件幂等回填到数据库。
 *
 * <p>默认只预览，只有显式传入 {@code --apply} 才写数据库。连接信息通过
 * {@code WORKHUB_ATTACHMENT_BACKFILL_JDBC_URL}、
 * {@code WORKHUB_ATTACHMENT_BACKFILL_DB_USER} 和
 * {@code WORKHUB_ATTACHMENT_BACKFILL_DB_PASSWORD} 环境变量传入。</p>
 */
public final class AttachmentContentBackfillCli {

    private static final String SELECT_SQL = """
            SELECT id, storage_path, file_content IS NOT NULL AS content_stored
            FROM pm_attachment
            ORDER BY id
            """;
    private static final String UPDATE_SQL = """
            UPDATE pm_attachment
            SET file_content = ?,
                file_size = ?,
                file_sha256 = ?
            WHERE id = ?
              AND file_content IS NULL
            """;

    private AttachmentContentBackfillCli() {
    }

    public static void main(String[] args) throws Exception {
        boolean apply = args.length == 1 && "--apply".equals(args[0]);
        if (args.length > 1 || (args.length == 1 && !apply)) {
            throw new IllegalArgumentException("仅支持无参数 dry-run 或 --apply");
        }

        String jdbcUrl = requiredEnvironment("WORKHUB_ATTACHMENT_BACKFILL_JDBC_URL");
        String user = requiredEnvironment("WORKHUB_ATTACHMENT_BACKFILL_DB_USER");
        String password = requiredEnvironment("WORKHUB_ATTACHMENT_BACKFILL_DB_PASSWORD");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            BackfillResult result = run(connection, apply, System.out);
            if (result.failedCount() > 0) {
                throw new IllegalStateException("历史附件回填存在失败记录: " + result.failedCount());
            }
        }
    }

    static BackfillResult run(Connection connection, boolean apply, PrintStream output) throws SQLException {
        int alreadyStoredCount = 0;
        int candidateCount = 0;
        int updatedCount = 0;
        int missingCount = 0;
        int failedCount = 0;
        long candidateBytes = 0;

        connection.setAutoCommit(false);
        try (PreparedStatement selectStatement = connection.prepareStatement(SELECT_SQL);
             PreparedStatement updateStatement = connection.prepareStatement(UPDATE_SQL);
             ResultSet resultSet = selectStatement.executeQuery()) {
            while (resultSet.next()) {
                long attachmentId = resultSet.getLong("id");
                String storagePath = resultSet.getString("storage_path");
                if (resultSet.getBoolean("content_stored")) {
                    alreadyStoredCount++;
                    continue;
                }
                if (storagePath == null || storagePath.isBlank()) {
                    missingCount++;
                    output.printf("MISSING id=%d path=%s%n", attachmentId, storagePath);
                    continue;
                }
                try {
                    Path path = Path.of(storagePath);
                    if (!Files.isRegularFile(path)) {
                        missingCount++;
                        output.printf("MISSING id=%d path=%s%n", attachmentId, storagePath);
                        continue;
                    }
                    long fileSize = Files.size(path);
                    String sha256 = sha256(path);
                    candidateCount++;
                    candidateBytes += fileSize;
                    if (!apply) {
                        output.printf("CANDIDATE id=%d size=%d sha256=%s%n", attachmentId, fileSize, sha256);
                        continue;
                    }

                    try (InputStream inputStream = Files.newInputStream(path)) {
                        updateStatement.setBinaryStream(1, inputStream, fileSize);
                        updateStatement.setLong(2, fileSize);
                        updateStatement.setString(3, sha256);
                        updateStatement.setLong(4, attachmentId);
                        int affectedRows = updateStatement.executeUpdate();
                        if (affectedRows == 1) {
                            connection.commit();
                            updatedCount++;
                            output.printf("UPDATED id=%d size=%d sha256=%s%n", attachmentId, fileSize, sha256);
                        } else {
                            connection.rollback();
                            alreadyStoredCount++;
                            output.printf("SKIPPED id=%d reason=already-stored-or-missing%n", attachmentId);
                        }
                    }
                } catch (Exception ex) {
                    connection.rollback();
                    failedCount++;
                    output.printf("FAILED id=%d reason=%s%n", attachmentId, summarize(ex));
                }
            }
            if (!apply) {
                connection.rollback();
            }
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }

        BackfillResult result = new BackfillResult(
                apply,
                alreadyStoredCount,
                candidateCount,
                candidateBytes,
                updatedCount,
                missingCount,
                failedCount
        );
        output.printf(
                "SUMMARY mode=%s alreadyStored=%d candidates=%d candidateBytes=%d updated=%d missing=%d failed=%d%n",
                apply ? "APPLY" : "DRY_RUN",
                alreadyStoredCount,
                candidateCount,
                candidateBytes,
                updatedCount,
                missingCount,
                failedCount
        );
        return result;
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", ex);
        }
        try (InputStream inputStream = Files.newInputStream(path);
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            digestInputStream.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少环境变量: " + name);
        }
        return value;
    }

    private static String summarize(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    record BackfillResult(boolean apply,
                          int alreadyStoredCount,
                          int candidateCount,
                          long candidateBytes,
                          int updatedCount,
                          int missingCount,
                          int failedCount) {
    }

}
