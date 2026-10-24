package plm

import javax.sql.DataSource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

/**
 * Lightweight, H2-specific, append-only migration runner.
 * Migration files live in grails-app/resources/plm/db/migration.
 * No Grails domain objects or Hibernate queries are used here.
 *
 * IMPORTANT: H2 DDL commits implicitly. Scripts MUST be idempotent.
 * Run only one application instance during migrations.
 */
class PlmDatabaseMigrationService {

    DataSource dataSource

    // Append future migrations here, in increasing version order.
    private static final List<String> MIGRATIONS = [
        'V001__plm_lifecycle_columns.sql'
    ].asImmutable()

    void migrate() {
        Connection connection = dataSource.getConnection()
        try {
            String database = connection.metaData.databaseProductName
            if (!'H2'.equalsIgnoreCase(database)) {
                throw new IllegalStateException("PLM migrations currently support H2 only; detected: ${database}")
            }
            ensureHistoryTable(connection)
            for (String filename : MIGRATIONS) {
                applyMigration(connection, filename)
            }
            log.info('PLM database migrations: completed successfully.')
        } finally {
            connection.close()
        }
    }

    private void ensureHistoryTable(Connection connection) {
        Statement statement = connection.createStatement()
        try {
            statement.execute('''
                CREATE TABLE IF NOT EXISTS PLM_SCHEMA_MIGRATION (
                    VERSION INTEGER PRIMARY KEY,
                    DESCRIPTION VARCHAR(255) NOT NULL,
                    SCRIPT VARCHAR(255) NOT NULL,
                    CHECKSUM VARCHAR(64) NOT NULL,
                    INSTALLED_ON TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
            ''')
        } finally {
            statement.close()
        }
    }

    private void applyMigration(Connection connection, String filename) {
        def matcher = (filename =~ /^V(\d+)__(.+)\.sql$/)
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid migration filename: ${filename}")
        }
        int version = Integer.parseInt(matcher.group(1))
        String description = matcher.group(2).replace('_', ' ')
        String resourcePath = "plm/db/migration/${filename}"
        InputStream input = this.class.classLoader.getResourceAsStream(resourcePath)
        if (input == null) {
            throw new IllegalStateException("Missing PLM migration resource: ${resourcePath}")
        }
        String script
        try {
            script = new String(input.bytes, StandardCharsets.UTF_8)
        } finally {
            input.close()
        }
        String checksum = MessageDigest.getInstance('SHA-256')
            .digest(script.getBytes(StandardCharsets.UTF_8))
            .encodeHex().toString()

        PreparedStatement select = connection.prepareStatement(
            'SELECT CHECKSUM, SCRIPT FROM PLM_SCHEMA_MIGRATION WHERE VERSION = ?')
        try {
            select.setInt(1, version)
            ResultSet rows = select.executeQuery()
            try {
                if (rows.next()) {
                    String previousChecksum = rows.getString('CHECKSUM')
                    if (previousChecksum != checksum) {
                        throw new IllegalStateException(
                            "PLM migration V${version} was modified after being applied. " +
                            "Previously: ${rows.getString('SCRIPT')}. Create a new migration instead."
                        )
                    }
                    log.info("PLM database migration ${filename}: already applied.")
                    return
                }
            } finally {
                rows.close()
            }
        } finally {
            select.close()
        }

        log.info("PLM database migration ${filename}: applying...")
        // Only straightforward SQL is supported: one statement per semicolon;
        // no stored procedures or semicolons inside SQL string literals.
        List<String> statements = script.readLines()
            .findAll { String line -> !line.trim().startsWith('--') }
            .join('\n')
            .split(';')
            .collect { String statement -> statement.trim() }
            .findAll { String statement -> !statement.isEmpty() }

        for (String sql : statements) {
            Statement statement = connection.createStatement()
            try {
                statement.execute(sql)
            } finally {
                statement.close()
            }
        }
        PreparedStatement insert = connection.prepareStatement(
            'INSERT INTO PLM_SCHEMA_MIGRATION (VERSION, DESCRIPTION, SCRIPT, CHECKSUM) VALUES (?, ?, ?, ?)')
        try {
            insert.setInt(1, version)
            insert.setString(2, description)
            insert.setString(3, filename)
            insert.setString(4, checksum)
            insert.executeUpdate()
        } finally {
            insert.close()
        }
        log.info("PLM database migration ${filename}: applied.")
    }
}
