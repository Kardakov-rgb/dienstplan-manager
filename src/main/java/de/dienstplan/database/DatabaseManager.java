package de.dienstplan.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Utility-Klasse für SQLite-Datenbankoperationen mit Connection Pool
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DATABASE_URL = "jdbc:sqlite:dienstplan.db";

    // HikariCP Connection Pool
    private static HikariDataSource dataSource;
    private static boolean initialized = false;

    // Privater Konstruktor - keine Instanziierung erlaubt
    private DatabaseManager() {
    }

    /**
     * Initialisiert den Connection Pool (einmalig beim Start)
     */
    private static synchronized void initializePool() {
        if (dataSource != null) {
            return;
        }

        logger.info("Initialisiere HikariCP Connection Pool...");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DATABASE_URL);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1800000);
        config.setPoolName("DienstplanPool");

        // SQLite-spezifische Einstellungen
        config.addDataSourceProperty("foreign_keys", "true");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");

        dataSource = new HikariDataSource(config);
        logger.info("Connection Pool erfolgreich initialisiert");
    }

    /**
     * Erstellt eine neue Datenbankverbindung aus dem Pool
     * WICHTIG: Diese Connection muss vom Aufrufer geschlossen werden!
     */
    public static Connection createConnection() throws SQLException {
        if (dataSource == null) {
            initializePool();
        }

        Connection connection = dataSource.getConnection();

        // Foreign Keys für jede Verbindung aktivieren (SQLite-spezifisch)
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }

        logger.debug("Datenbankverbindung aus Pool geholt");
        return connection;
    }

    /**
     * Initialisiert die Datenbank mit allen benötigten Tabellen
     */
    public static void initializeDatabase() throws SQLException {
        if (initialized) {
            logger.debug("Datenbank bereits initialisiert");
            return;
        }

        logger.info("Initialisiere Datenbank...");

        try (Connection conn = createConnection()) {
            conn.setAutoCommit(false);

            try {
                createPersonTable(conn);
                createDienstplanTable(conn);
                createDienstTable(conn);
                createMonatsWunschTable(conn);
                createFairnessHistorieTable(conn);
                createUpdateTriggers(conn);
                createAdditionalIndices(conn);

                conn.commit();
                initialized = true;
                logger.info("Datenbank erfolgreich initialisiert");

            } catch (SQLException e) {
                conn.rollback();
                logger.error("Fehler beim Initialisieren der Datenbank", e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Erstellt die Person-Tabelle
     */
    private static void createPersonTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS person (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                anzahl_dienste INTEGER NOT NULL DEFAULT 0,
                arbeits_tage TEXT NOT NULL DEFAULT '', -- Kommagetrennte Wochentage
                verfuegbare_dienst_arten TEXT NOT NULL DEFAULT '', -- Kommagetrennte DienstArten
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(name)
            )
        """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.debug("Person-Tabelle erstellt/überprüft");
        }
    }

    /**
     * Erstellt die MonatsWunsch-Tabelle (ersetzt Abwesenheit)
     */
    private static void createMonatsWunschTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS monats_wunsch (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                person_id INTEGER NOT NULL,
                person_name TEXT,
                monat_jahr TEXT NOT NULL,
                datum DATE NOT NULL,
                typ TEXT NOT NULL,
                erfuellt BOOLEAN DEFAULT NULL,
                bemerkung TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE
            )
        """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.debug("MonatsWunsch-Tabelle erstellt/überprüft");
        }

        // Indizes für bessere Performance
        String[] indices = {
            "CREATE INDEX IF NOT EXISTS idx_monats_wunsch_person ON monats_wunsch(person_id)",
            "CREATE INDEX IF NOT EXISTS idx_monats_wunsch_monat ON monats_wunsch(monat_jahr)",
            "CREATE INDEX IF NOT EXISTS idx_monats_wunsch_datum ON monats_wunsch(datum)",
            "CREATE INDEX IF NOT EXISTS idx_monats_wunsch_typ ON monats_wunsch(typ)"
        };

        for (String indexSql : indices) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(indexSql);
            }
        }
    }

    /**
     * Erstellt die FairnessHistorie-Tabelle
     */
    private static void createFairnessHistorieTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS fairness_historie (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                person_id INTEGER NOT NULL,
                monat_jahr TEXT NOT NULL,
                freiwuensche_gesamt INTEGER NOT NULL DEFAULT 0,
                freiwuensche_erfuellt INTEGER NOT NULL DEFAULT 0,
                dienstwuensche_gesamt INTEGER NOT NULL DEFAULT 0,
                dienstwuensche_erfuellt INTEGER NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE,
                UNIQUE(person_id, monat_jahr)
            )
        """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.debug("FairnessHistorie-Tabelle erstellt/überprüft");
        }

        // Index für schnelle Abfragen
        String indexSql = "CREATE INDEX IF NOT EXISTS idx_fairness_person_monat ON fairness_historie(person_id, monat_jahr)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(indexSql);
        }
    }

    /**
     * Erstellt die Dienstplan-Tabelle
     */
    private static void createDienstplanTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS dienstplan (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                monat_jahr TEXT NOT NULL, -- Format: YYYY-MM
                status TEXT NOT NULL DEFAULT 'ENTWURF',
                bemerkung TEXT,
                erstellt_am DATE NOT NULL,
                letztes_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(name, monat_jahr)
            )
        """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.debug("Dienstplan-Tabelle erstellt/überprüft");
        }
    }

    /**
     * Erstellt die Dienst-Tabelle
     */
    private static void createDienstTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS dienst (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dienstplan_id INTEGER NOT NULL,
                datum DATE NOT NULL,
                art TEXT NOT NULL,
                person_id INTEGER,
                person_name TEXT, -- Denormalisiert für Performance
                status TEXT NOT NULL DEFAULT 'GEPLANT',
                bemerkung TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (dienstplan_id) REFERENCES dienstplan(id) ON DELETE CASCADE,
                FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE SET NULL
            )
        """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.debug("Dienst-Tabelle erstellt/überprüft");
        }

        // Basis-Indizes für bessere Performance
        String[] indices = {
            "CREATE INDEX IF NOT EXISTS idx_dienst_dienstplan ON dienst(dienstplan_id)",
            "CREATE INDEX IF NOT EXISTS idx_dienst_datum ON dienst(datum)",
            "CREATE INDEX IF NOT EXISTS idx_dienst_person ON dienst(person_id)",
            "CREATE INDEX IF NOT EXISTS idx_dienst_status ON dienst(status)"
        };

        for (String indexSql : indices) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(indexSql);
            }
        }
    }

    /**
     * Erstellt zusätzliche Indizes für optimierte Abfragen
     */
    private static void createAdditionalIndices(Connection conn) throws SQLException {
        String[] indices = {
            // Zusammengesetzter Index für Konfliktprüfung (Person + Datum)
            "CREATE INDEX IF NOT EXISTS idx_dienst_person_datum ON dienst(person_id, datum)",
            // Index für Dienstplan-Monat-Suche
            "CREATE INDEX IF NOT EXISTS idx_dienstplan_monat ON dienstplan(monat_jahr)",
            // Index für Person-Name-Suche
            "CREATE INDEX IF NOT EXISTS idx_person_name ON person(name)"
        };

        for (String indexSql : indices) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(indexSql);
            }
        }

        logger.debug("Zusätzliche Indizes erstellt");
    }

    /**
     * Erstellt Update-Trigger für automatische Zeitstempel
     */
    private static void createUpdateTriggers(Connection conn) throws SQLException {
        // Trigger für automatische updated_at Aktualisierung
        String[] triggers = {
            """
            CREATE TRIGGER IF NOT EXISTS update_person_timestamp
            AFTER UPDATE ON person
            BEGIN
                UPDATE person SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
            END
            """,
            """
            CREATE TRIGGER IF NOT EXISTS update_monats_wunsch_timestamp
            AFTER UPDATE ON monats_wunsch
            BEGIN
                UPDATE monats_wunsch SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
            END
            """,
            """
            CREATE TRIGGER IF NOT EXISTS update_dienstplan_timestamp
            AFTER UPDATE ON dienstplan
            BEGIN
                UPDATE dienstplan SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
            END
            """,
            """
            CREATE TRIGGER IF NOT EXISTS update_dienst_timestamp
            AFTER UPDATE ON dienst
            BEGIN
                UPDATE dienst SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
            END
            """,
            // Trigger für Denormalisierung: Person-Name in Dienst aktualisieren
            """
            CREATE TRIGGER IF NOT EXISTS update_dienst_person_name
            AFTER UPDATE OF name ON person
            BEGIN
                UPDATE dienst SET person_name = NEW.name WHERE person_id = NEW.id;
            END
            """,
            // Trigger für Denormalisierung: Person-Name in MonatsWunsch aktualisieren
            """
            CREATE TRIGGER IF NOT EXISTS update_monats_wunsch_person_name
            AFTER UPDATE OF name ON person
            BEGIN
                UPDATE monats_wunsch SET person_name = NEW.name WHERE person_id = NEW.id;
            END
            """
        };

        for (String trigger : triggers) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(trigger);
            }
        }

        logger.debug("Update-Trigger erstellt");
    }

    /**
     * Prüft die Datenbankverbindung
     */
    public static boolean testConnection() {
        try (Connection conn = createConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {

            return rs.next() && rs.getInt(1) == 1;

        } catch (SQLException e) {
            logger.error("Datenbankverbindungstest fehlgeschlagen", e);
            return false;
        }
    }

    /**
     * Gibt Informationen über die Datenbank zurück
     */
    public static String getDatabaseInfo() throws SQLException {
        StringBuilder info = new StringBuilder();

        try (Connection conn = createConnection();
             Statement stmt = conn.createStatement()) {

            // SQLite Version
            try (ResultSet rs = stmt.executeQuery("SELECT sqlite_version()")) {
                if (rs.next()) {
                    info.append("SQLite Version: ").append(rs.getString(1)).append("\n");
                }
            }

            // Tabellenanzahl
            String tableCountSql = """
                SELECT COUNT(*) as table_count
                FROM sqlite_master
                WHERE type='table' AND name NOT LIKE 'sqlite_%'
            """;

            try (ResultSet rs = stmt.executeQuery(tableCountSql)) {
                if (rs.next()) {
                    info.append("Anzahl Tabellen: ").append(rs.getInt("table_count")).append("\n");
                }
            }

            // Connection Pool Info
            if (dataSource != null) {
                info.append("Connection Pool: Aktiv\n");
                info.append("Pool Size: ").append(dataSource.getHikariPoolMXBean().getTotalConnections()).append("\n");
                info.append("Active Connections: ").append(dataSource.getHikariPoolMXBean().getActiveConnections()).append("\n");
            }
        }

        return info.toString();
    }

    /**
     * Hilfsmethode zur Formatierung von Bytes
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Räumt die Datenbank auf (für Tests oder Entwicklung)
     */
    public static void dropAllTables() throws SQLException {
        logger.warn("Alle Tabellen werden gelöscht!");

        String[] tables = {"dienst", "monats_wunsch", "fairness_historie", "dienstplan", "person"};

        try (Connection conn = createConnection()) {
            conn.setAutoCommit(false);

            try {
                // Foreign Key Constraints temporär deaktivieren
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = OFF");
                }

                // Tabellen löschen
                for (String table : tables) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("DROP TABLE IF EXISTS " + table);
                    }
                }

                // Foreign Key Constraints wieder aktivieren
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                }

                conn.commit();
                initialized = false;
                logger.info("Alle Tabellen erfolgreich gelöscht");

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Schließt den Connection Pool (bei Anwendungsende aufrufen)
     */
    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Schließe Connection Pool...");
            dataSource.close();
            dataSource = null;
            initialized = false;
            logger.info("Connection Pool geschlossen");
        }
    }
}
