package de.dienstplan.database;

import de.dienstplan.model.Abwesenheit;
import de.dienstplan.model.AbwesenheitsArt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object für Abwesenheits-Operationen
 */
public class AbwesenheitDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(AbwesenheitDAO.class);
    
    public AbwesenheitDAO() {
        // Kein DatabaseManager mehr nötig - jede Methode erstellt eigene Connection
    }
    
    /**
     * Speichert eine neue Abwesenheit in der Datenbank
     */
    public Abwesenheit create(Abwesenheit abwesenheit) throws SQLException {
        if (abwesenheit == null) {
            throw new IllegalArgumentException("Abwesenheit darf nicht null sein");
        }
        
        String sql = """
            INSERT INTO abwesenheit (person_id, start_datum, end_datum, art, bemerkung)
            VALUES (?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, abwesenheit.getPersonId());
            stmt.setDate(2, Date.valueOf(abwesenheit.getStartDatum()));
            stmt.setDate(3, Date.valueOf(abwesenheit.getEndDatum()));
            stmt.setString(4, abwesenheit.getArt().name());
            stmt.setString(5, abwesenheit.getBemerkung());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Erstellen der Abwesenheit fehlgeschlagen");
            }
            
            // ID manuell abrufen - SQLite-kompatible Methode
            String getIdSql = "SELECT last_insert_rowid()";
            try (Statement idStmt = conn.createStatement();
                 ResultSet rs = idStmt.executeQuery(getIdSql)) {
                
                if (rs.next()) {
                    abwesenheit.setId(rs.getLong(1));
                } else {
                    throw new SQLException("Erstellen der Abwesenheit fehlgeschlagen, keine ID generiert");
                }
            }
            
            logger.debug("Abwesenheit erstellt: ID {}", abwesenheit.getId());
            return abwesenheit;
        }
    }
    
    /**
     * Findet eine Abwesenheit anhand der ID
     */
    public Optional<Abwesenheit> findById(Long id) throws SQLException {
        if (id == null) {
            return Optional.empty();
        }
        
        String sql = """
            SELECT id, person_id, start_datum, end_datum, art, bemerkung,
                   created_at, updated_at
            FROM abwesenheit 
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAbwesenheit(rs));
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Findet alle Abwesenheiten einer Person
     */
    public List<Abwesenheit> findByPersonId(Long personId) throws SQLException {
        if (personId == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT id, person_id, start_datum, end_datum, art, bemerkung,
                   created_at, updated_at
            FROM abwesenheit 
            WHERE person_id = ?
            ORDER BY start_datum
        """;
        
        List<Abwesenheit> abwesenheiten = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, personId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    abwesenheiten.add(mapResultSetToAbwesenheit(rs));
                }
            }
        }
        
        return abwesenheiten;
    }
    
    /**
     * Findet alle Abwesenheiten in einem Zeitraum
     */
    public List<Abwesenheit> findByDateRange(LocalDate startDatum, LocalDate endDatum) throws SQLException {
        if (startDatum == null || endDatum == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT id, person_id, start_datum, end_datum, art, bemerkung,
                   created_at, updated_at
            FROM abwesenheit 
            WHERE (start_datum <= ? AND end_datum >= ?)
            ORDER BY start_datum, person_id
        """;
        
        List<Abwesenheit> abwesenheiten = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setDate(1, Date.valueOf(endDatum));
            stmt.setDate(2, Date.valueOf(startDatum));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    abwesenheiten.add(mapResultSetToAbwesenheit(rs));
                }
            }
        }
        
        return abwesenheiten;
    }
    
    /**
     * Findet alle Abwesenheiten einer Person in einem Zeitraum
     */
    public List<Abwesenheit> findByPersonAndDateRange(Long personId, LocalDate startDatum, LocalDate endDatum) throws SQLException {
        if (personId == null || startDatum == null || endDatum == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT id, person_id, start_datum, end_datum, art, bemerkung,
                   created_at, updated_at
            FROM abwesenheit 
            WHERE person_id = ? AND (start_datum <= ? AND end_datum >= ?)
            ORDER BY start_datum
        """;
        
        List<Abwesenheit> abwesenheiten = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, personId);
            stmt.setDate(2, Date.valueOf(endDatum));
            stmt.setDate(3, Date.valueOf(startDatum));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    abwesenheiten.add(mapResultSetToAbwesenheit(rs));
                }
            }
        }
        
        return abwesenheiten;
    }
    
    /**
     * Prüft ob eine Person an einem bestimmten Datum abwesend ist
     */
    public boolean isPersonAbwesend(Long personId, LocalDate datum) throws SQLException {
        if (personId == null || datum == null) {
            return false;
        }
        
        String sql = """
            SELECT COUNT(*) 
            FROM abwesenheit 
            WHERE person_id = ? AND ? BETWEEN start_datum AND end_datum
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, personId);
            stmt.setDate(2, Date.valueOf(datum));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Findet alle Abwesenheiten einer bestimmten Art
     */
    public List<Abwesenheit> findByArt(AbwesenheitsArt art) throws SQLException {
        if (art == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT id, person_id, start_datum, end_datum, art, bemerkung,
                   created_at, updated_at
            FROM abwesenheit 
            WHERE art = ?
            ORDER BY start_datum
        """;
        
        List<Abwesenheit> abwesenheiten = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, art.name());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    abwesenheiten.add(mapResultSetToAbwesenheit(rs));
                }
            }
        }
        
        return abwesenheiten;
    }
    
    /**
     * Lädt alle Abwesenheiten aus der Datenbank
     */
    public List<Abwesenheit> findAll() throws SQLException {
        String sql = """
            SELECT id, person_id, start_datum, end_datum, art, bemerkung,
                   created_at, updated_at
            FROM abwesenheit 
            ORDER BY start_datum, person_id
        """;
        
        List<Abwesenheit> abwesenheiten = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                abwesenheiten.add(mapResultSetToAbwesenheit(rs));
            }
        }
        
        logger.debug("Anzahl geladene Abwesenheiten: {}", abwesenheiten.size());
        return abwesenheiten;
    }
    
    /**
     * Aktualisiert eine Abwesenheit in der Datenbank
     */
    public void update(Abwesenheit abwesenheit) throws SQLException {
        if (abwesenheit == null || abwesenheit.getId() == null) {
            throw new IllegalArgumentException("Abwesenheit und ID dürfen nicht null sein");
        }
        
        String sql = """
            UPDATE abwesenheit 
            SET person_id = ?, start_datum = ?, end_datum = ?, art = ?, bemerkung = ?
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, abwesenheit.getPersonId());
            stmt.setDate(2, Date.valueOf(abwesenheit.getStartDatum()));
            stmt.setDate(3, Date.valueOf(abwesenheit.getEndDatum()));
            stmt.setString(4, abwesenheit.getArt().name());
            stmt.setString(5, abwesenheit.getBemerkung());
            stmt.setLong(6, abwesenheit.getId());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Abwesenheit nicht gefunden: " + abwesenheit.getId());
            }
            
            logger.debug("Abwesenheit aktualisiert: ID {}", abwesenheit.getId());
        }
    }
    
    /**
     * Löscht eine Abwesenheit aus der Datenbank
     */
    public boolean delete(Long id) throws SQLException {
        if (id == null) {
            return false;
        }
        
        String sql = "DELETE FROM abwesenheit WHERE id = ?";
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            int affectedRows = stmt.executeUpdate();
            
            boolean deleted = affectedRows > 0;
            if (deleted) {
                logger.debug("Abwesenheit gelöscht: ID {}", id);
            }
            
            return deleted;
        }
    }
    
    /**
     * Löscht alle Abwesenheiten einer Person
     */
    public int deleteByPersonId(Long personId) throws SQLException {
        if (personId == null) {
            return 0;
        }
        
        String sql = "DELETE FROM abwesenheit WHERE person_id = ?";
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, personId);
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                logger.debug("Abwesenheiten gelöscht für Person ID {}: {} Einträge", personId, affectedRows);
            }
            
            return affectedRows;
        }
    }
    
    /**
     * Zählt die Anzahl der Abwesenheiten
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM abwesenheit";
        
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
     * Zählt die Anzahl der Abwesenheiten einer Person
     */
    public int countByPersonId(Long personId) throws SQLException {
        if (personId == null) {
            return 0;
        }
        
        String sql = "SELECT COUNT(*) FROM abwesenheit WHERE person_id = ?";
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, personId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        return 0;
    }
    
    // Private Hilfsmethoden
    
    private Abwesenheit mapResultSetToAbwesenheit(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        Long personId = rs.getLong("person_id");
        LocalDate startDatum = rs.getDate("start_datum").toLocalDate();
        LocalDate endDatum = rs.getDate("end_datum").toLocalDate();
        AbwesenheitsArt art = AbwesenheitsArt.valueOf(rs.getString("art"));
        String bemerkung = rs.getString("bemerkung");
        
        return new Abwesenheit(id, personId, startDatum, endDatum, art, bemerkung);
    }
}