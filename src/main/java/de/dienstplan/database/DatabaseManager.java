package de.dienstplan.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Properties;

/**
 * Statische Utility-Klasse für SQLite-Datenbankoperationen
 */
public class DatabaseManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DATABASE_URL = "jdbc:sqlite:dienstplan.db";
    
    // Privater Konstruktor - keine Instanziierung erlaubt
    private DatabaseManager() {
    }
    
    /**
     * Erstellt eine neue Datenbankverbindung
     * WICHTIG: Diese Connection muss vom Aufrufer geschlossen werden!
     */
    public static Connection createConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("foreign_keys", "true"); // Foreign Key Constraints aktivieren
        
        Connection connection = DriverManager.getConnection(DATABASE_URL, properties);
        logger.debug("Neue Datenbankverbindung erstellt");
        return connection;
    }
    
    /**
     * Initialisiert die Datenbank mit allen benötigten Tabellen
     */
    public static void initializeDatabase() throws SQLException {
        logger.info("Initialisiere Datenbank...");
        
        try (Connection conn = createConnection()) {
            conn.setAutoCommit(false);
            
            try {
                createPersonTable(conn);
                createAbwesenheitTable(conn);
                createDienstplanTable(conn);
                createDienstTable(conn);
                createUpdateTriggers(conn);
                
                conn.commit();
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
     * Erstellt die Abwesenheit-Tabelle
     */
    private static void createAbwesenheitTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS abwesenheit (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                person_id INTEGER NOT NULL,
                start_datum DATE NOT NULL,
                end_datum DATE NOT NULL,
                art TEXT NOT NULL,
                bemerkung TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE,
                CHECK (start_datum <= end_datum)
            )
        """;
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.debug("Abwesenheit-Tabelle erstellt/überprüft");
        }
        
        // Index für bessere Performance
        String indexSql = """
            CREATE INDEX IF NOT EXISTS idx_abwesenheit_person_datum 
            ON abwesenheit(person_id, start_datum, end_datum)
        """;
        
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
        
        // Indizes für bessere Performance
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
            CREATE TRIGGER IF NOT EXISTS update_abwesenheit_timestamp 
            AFTER UPDATE ON abwesenheit
            BEGIN
                UPDATE abwesenheit SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
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
            
            // Datenbankgröße
            try (ResultSet rs = stmt.executeQuery("PRAGMA page_count; PRAGMA page_size")) {
                if (rs.next()) {
                    int pageCount = rs.getInt(1);
                    if (rs.next()) {
                        int pageSize = rs.getInt(1);
                        long dbSize = (long) pageCount * pageSize;
                        info.append("Datenbankgröße: ").append(formatBytes(dbSize)).append("\n");
                    }
                }
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
        
        String[] tables = {"dienst", "abwesenheit", "dienstplan", "person"};
        
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
                logger.info("Alle Tabellen erfolgreich gelöscht");
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}