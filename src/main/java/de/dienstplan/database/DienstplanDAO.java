package de.dienstplan.database;

import de.dienstplan.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Data Access Object für Dienstplan-Operationen
 */
public class DienstplanDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(DienstplanDAO.class);
    
    public DienstplanDAO() {
        // Kein DatabaseManager mehr nötig - jede Methode erstellt eigene Connection
    }
    
    /**
     * Speichert einen neuen Dienstplan in der Datenbank
     */
    public Dienstplan create(Dienstplan dienstplan) throws SQLException {
        if (dienstplan == null) {
            throw new IllegalArgumentException("Dienstplan darf nicht null sein");
        }
        
        String sql = """
            INSERT INTO dienstplan (name, monat_jahr, status, bemerkung, erstellt_am, letztes_update)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, dienstplan.getName());
            stmt.setString(2, formatYearMonth(dienstplan.getMonat()));
            stmt.setString(3, dienstplan.getStatus().name());
            stmt.setString(4, dienstplan.getBemerkung());
            stmt.setDate(5, Date.valueOf(dienstplan.getErstelltAm()));
            stmt.setDate(6, Date.valueOf(dienstplan.getLetztesUpdate()));
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Erstellen des Dienstplans fehlgeschlagen");
            }
            
            // ID manuell abrufen - SQLite-kompatible Methode
            String getIdSql = "SELECT last_insert_rowid()";
            try (Statement idStmt = conn.createStatement();
                 ResultSet rs = idStmt.executeQuery(getIdSql)) {
                
                if (rs.next()) {
                    dienstplan.setId(rs.getLong(1));
                } else {
                    throw new SQLException("Erstellen des Dienstplans fehlgeschlagen, keine ID generiert");
                }
            }
            
            // Dienste separat speichern
            if (!dienstplan.getDienste().isEmpty()) {
                DienstDAO dienstDAO = new DienstDAO();
                for (Dienst dienst : dienstplan.getDienste()) {
                    dienstDAO.createForDienstplan(dienst, dienstplan.getId());
                }
            }
            
            logger.info("Dienstplan erstellt: {}", dienstplan.getName());
            return dienstplan;
        }
    }
    
    /**
     * Findet einen Dienstplan anhand der ID
     */
    public Optional<Dienstplan> findById(Long id) throws SQLException {
        if (id == null) {
            return Optional.empty();
        }
        
        String sql = """
            SELECT id, name, monat_jahr, status, bemerkung, erstellt_am, letztes_update,
                   created_at, updated_at
            FROM dienstplan 
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Dienstplan dienstplan = mapResultSetToDienstplan(rs);
                    
                    // Dienste laden
                    loadDienste(dienstplan);
                    
                    return Optional.of(dienstplan);
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Findet einen Dienstplan anhand von Name und Monat
     */
    public Optional<Dienstplan> findByNameAndMonat(String name, YearMonth monat) throws SQLException {
        if (name == null || monat == null) {
            return Optional.empty();
        }
        
        String sql = """
            SELECT id, name, monat_jahr, status, bemerkung, erstellt_am, letztes_update,
                   created_at, updated_at
            FROM dienstplan 
            WHERE name = ? AND monat_jahr = ?
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name);
            stmt.setString(2, formatYearMonth(monat));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Dienstplan dienstplan = mapResultSetToDienstplan(rs);
                    loadDienste(dienstplan);
                    return Optional.of(dienstplan);
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Lädt alle Dienstpläne aus der Datenbank
     */
    public List<Dienstplan> findAll() throws SQLException {
        String sql = """
            SELECT id, name, monat_jahr, status, bemerkung, erstellt_am, letztes_update,
                   created_at, updated_at
            FROM dienstplan 
            ORDER BY monat_jahr DESC, name
        """;
        
        List<Dienstplan> dienstplaene = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Dienstplan dienstplan = mapResultSetToDienstplan(rs);
                loadDienste(dienstplan);
                dienstplaene.add(dienstplan);
            }
        }
        
        logger.debug("Anzahl geladene Dienstpläne: {}", dienstplaene.size());
        return dienstplaene;
    }
    
    /**
     * Findet alle Dienstpläne für einen bestimmten Monat
     */
    public List<Dienstplan> findByMonat(YearMonth monat) throws SQLException {
        if (monat == null) {
            return new ArrayList<>();
        }

        String sql = """
            SELECT id, name, monat_jahr, status, bemerkung, erstellt_am, letztes_update,
                   created_at, updated_at
            FROM dienstplan
            WHERE monat_jahr = ?
            ORDER BY name
        """;

        List<Dienstplan> dienstplaene = new ArrayList<>();

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, formatYearMonth(monat));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Dienstplan dienstplan = mapResultSetToDienstplan(rs);
                    loadDienste(dienstplan);
                    dienstplaene.add(dienstplan);
                }
            }
        }

        return dienstplaene;
    }

    /**
     * Findet alle Dienstpläne für einen bestimmten Zeitraum
     */
    public List<Dienstplan> findByDateRange(YearMonth startMonat, YearMonth endMonat) throws SQLException {
        if (startMonat == null || endMonat == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT id, name, monat_jahr, status, bemerkung, erstellt_am, letztes_update,
                   created_at, updated_at
            FROM dienstplan 
            WHERE monat_jahr BETWEEN ? AND ?
            ORDER BY monat_jahr, name
        """;
        
        List<Dienstplan> dienstplaene = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, formatYearMonth(startMonat));
            stmt.setString(2, formatYearMonth(endMonat));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Dienstplan dienstplan = mapResultSetToDienstplan(rs);
                    loadDienste(dienstplan);
                    dienstplaene.add(dienstplan);
                }
            }
        }
        
        return dienstplaene;
    }
    
    /**
     * Findet alle Dienstpläne mit einem bestimmten Status
     */
    public List<Dienstplan> findByStatus(DienstplanStatus status) throws SQLException {
        if (status == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT id, name, monat_jahr, status, bemerkung, erstellt_am, letztes_update,
                   created_at, updated_at
            FROM dienstplan 
            WHERE status = ?
            ORDER BY monat_jahr DESC, name
        """;
        
        List<Dienstplan> dienstplaene = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status.name());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Dienstplan dienstplan = mapResultSetToDienstplan(rs);
                    loadDienste(dienstplan);
                    dienstplaene.add(dienstplan);
                }
            }
        }
        
        return dienstplaene;
    }
    
    /**
     * Aktualisiert einen Dienstplan in der Datenbank
     */
    public void update(Dienstplan dienstplan) throws SQLException {
        if (dienstplan == null || dienstplan.getId() == null) {
            throw new IllegalArgumentException("Dienstplan und ID dürfen nicht null sein");
        }
        
        String sql = """
            UPDATE dienstplan 
            SET name = ?, monat_jahr = ?, status = ?, bemerkung = ?, 
                erstellt_am = ?, letztes_update = ?
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseManager.createConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, dienstplan.getName());
                stmt.setString(2, formatYearMonth(dienstplan.getMonat()));
                stmt.setString(3, dienstplan.getStatus().name());
                stmt.setString(4, dienstplan.getBemerkung());
                stmt.setDate(5, Date.valueOf(dienstplan.getErstelltAm()));
                stmt.setDate(6, Date.valueOf(dienstplan.getLetztesUpdate()));
                stmt.setLong(7, dienstplan.getId());
                
                int affectedRows = stmt.executeUpdate();
                
                if (affectedRows == 0) {
                    throw new SQLException("Dienstplan nicht gefunden: " + dienstplan.getId());
                }
                
                // Dienste aktualisieren (vereinfacht: löschen und neu erstellen)
                updateDienste(conn, dienstplan);
                
                conn.commit();
                logger.info("Dienstplan aktualisiert: {}", dienstplan.getName());
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    
    /**
     * Löscht einen Dienstplan aus der Datenbank
     */
    public boolean delete(Long id) throws SQLException {
        if (id == null) {
            return false;
        }
        
        String sql = "DELETE FROM dienstplan WHERE id = ?";
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            int affectedRows = stmt.executeUpdate();
            
            boolean deleted = affectedRows > 0;
            if (deleted) {
                logger.info("Dienstplan gelöscht: ID {}", id);
            }
            
            return deleted;
        }
    }

    /**
     * Löscht alle Dienstpläne aus der Datenbank
     */
    public int deleteAll() throws SQLException {
        String sql = "DELETE FROM dienstplan";

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int affectedRows = stmt.executeUpdate();
            logger.info("Alle Dienstpläne gelöscht: {} Einträge", affectedRows);
            return affectedRows;
        }
    }

    /**
     * Prüft ob ein Dienstplan für Name/Monat bereits existiert
     */
    public boolean existsByNameAndMonat(String name, YearMonth monat) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dienstplan WHERE name = ? AND monat_jahr = ?";
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name);
            stmt.setString(2, formatYearMonth(monat));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Zählt die Anzahl der Dienstpläne
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM dienstplan";
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        
        return 0;
    }
    
    /**
     * Statistik: Anzahl Dienstpläne pro Status
     */
    public Map<DienstplanStatus, Integer> getStatusStatistik() throws SQLException {
        String sql = """
            SELECT status, COUNT(*) as anzahl
            FROM dienstplan 
            GROUP BY status
        """;
        
        Map<DienstplanStatus, Integer> statistik = new EnumMap<>(DienstplanStatus.class);
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                try {
                    DienstplanStatus status = DienstplanStatus.valueOf(rs.getString("status"));
                    int anzahl = rs.getInt("anzahl");
                    statistik.put(status, anzahl);
                } catch (IllegalArgumentException e) {
                    logger.warn("Unbekannter DienstplanStatus: {}", rs.getString("status"));
                }
            }
        }
        
        return statistik;
    }
    
    // Private Hilfsmethoden
    
    private Dienstplan mapResultSetToDienstplan(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        String name = rs.getString("name");
        YearMonth monat = parseYearMonth(rs.getString("monat_jahr"));
        LocalDate erstelltAm = rs.getDate("erstellt_am").toLocalDate();
        LocalDate letztesUpdate = rs.getDate("letztes_update").toLocalDate();
        DienstplanStatus status = DienstplanStatus.valueOf(rs.getString("status"));
        String bemerkung = rs.getString("bemerkung");
        
        return new Dienstplan(id, name, monat, erstelltAm, letztesUpdate, status, bemerkung);
    }
    
    private void loadDienste(Dienstplan dienstplan) throws SQLException {
        DienstDAO dienstDAO = new DienstDAO();
        List<Dienst> dienste = dienstDAO.findByDienstplanId(dienstplan.getId());
        dienstplan.setDienste(dienste);
    }
    
    private void updateDienste(Connection conn, Dienstplan dienstplan) throws SQLException {
        // Bestehende Dienste löschen
        String deleteSql = "DELETE FROM dienst WHERE dienstplan_id = ?";
        try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
            deleteStmt.setLong(1, dienstplan.getId());
            deleteStmt.executeUpdate();
        }
        
        // Neue Dienste erstellen
        DienstDAO dienstDAO = new DienstDAO();
        for (Dienst dienst : dienstplan.getDienste()) {
            dienstDAO.createForDienstplanWithConnection(conn, dienst, dienstplan.getId());
        }
    }
    
    private String formatYearMonth(YearMonth yearMonth) {
        return yearMonth != null ? yearMonth.toString() : null;
    }
    
    private YearMonth parseYearMonth(String yearMonthStr) {
        return yearMonthStr != null ? YearMonth.parse(yearMonthStr) : null;
    }
}