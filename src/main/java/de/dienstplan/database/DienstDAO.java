package de.dienstplan.database;

import de.dienstplan.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object für Dienst-Operationen
 */
public class DienstDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(DienstDAO.class);
    
    public DienstDAO() {
        // Kein DatabaseManager mehr nötig - jede Methode erstellt eigene Connection
    }
    
    /**
     * Speichert einen neuen Dienst in der Datenbank
     */
    public Dienst create(Dienst dienst, Long dienstplanId) throws SQLException {
        try (Connection conn = DatabaseManager.createConnection()) {
            return createForDienstplanWithConnection(conn, dienst, dienstplanId);
        }
    }
    
    /**
     * Speichert einen neuen Dienst für einen Dienstplan (vereinfachte Methode)
     */
    public Dienst createForDienstplan(Dienst dienst, Long dienstplanId) throws SQLException {
        return create(dienst, dienstplanId);
    }
    
    /**
     * Speichert einen neuen Dienst mit vorhandener Connection (für Transaktionen)
     */
    public Dienst createForDienstplanWithConnection(Connection conn, Dienst dienst, Long dienstplanId) throws SQLException {
        if (dienst == null || dienstplanId == null) {
            throw new IllegalArgumentException("Dienst und DienstplanID dürfen nicht null sein");
        }
        
        String sql = """
            INSERT INTO dienst (dienstplan_id, datum, art, person_id, person_name, status, bemerkung)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, dienstplanId);
            stmt.setDate(2, Date.valueOf(dienst.getDatum()));
            stmt.setString(3, dienst.getArt().name());
            
            if (dienst.getPersonId() != null) {
                stmt.setLong(4, dienst.getPersonId());
                stmt.setString(5, dienst.getPersonName());
            } else {
                stmt.setNull(4, Types.BIGINT);
                stmt.setNull(5, Types.VARCHAR);
            }
            
            stmt.setString(6, dienst.getStatus().name());
            stmt.setString(7, dienst.getBemerkung());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Erstellen des Dienstes fehlgeschlagen");
            }
            
            // ID manuell abrufen - SQLite-kompatible Methode
            String getIdSql = "SELECT last_insert_rowid()";
            try (Statement idStmt = conn.createStatement();
                 ResultSet rs = idStmt.executeQuery(getIdSql)) {
                
                if (rs.next()) {
                    dienst.setId(rs.getLong(1));
                } else {
                    throw new SQLException("Erstellen des Dienstes fehlgeschlagen, keine ID generiert");
                }
            }
            
            logger.debug("Dienst erstellt: {} {} für Dienstplan {}", 
                        dienst.getDatum(), dienst.getArt().getKurzName(), dienstplanId);
            return dienst;
        }
    }
    
    /**
     * Findet einen Dienst anhand der ID
     */
    public Optional<Dienst> findById(Long id) throws SQLException {
        if (id == null) {
            return Optional.empty();
        }
        
        String sql = """
            SELECT id, dienstplan_id, datum, art, person_id, person_name, status, bemerkung,
                   created_at, updated_at
            FROM dienst 
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Dienst dienst = mapResultSetToDienst(rs);
                    return Optional.ofNullable(dienst);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Findet alle Dienste eines Dienstplans
     */
    public List<Dienst> findByDienstplanId(Long dienstplanId) throws SQLException {
        if (dienstplanId == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT id, dienstplan_id, datum, art, person_id, person_name, status, bemerkung,
                   created_at, updated_at
            FROM dienst 
            WHERE dienstplan_id = ?
            ORDER BY datum, art
        """;
        
        List<Dienst> dienste = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, dienstplanId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Dienst dienst = mapResultSetToDienst(rs);
                    if (dienst != null) {
                        dienste.add(dienst);
                    }
                }
            }
        }

        return dienste;
    }

    /**
     * Findet alle Dienste einer Person
     */
    public List<Dienst> findByPersonId(Long personId) throws SQLException {
        if (personId == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT id, dienstplan_id, datum, art, person_id, person_name, status, bemerkung,
                   created_at, updated_at
            FROM dienst 
            WHERE person_id = ?
            ORDER BY datum
        """;
        
        List<Dienst> dienste = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, personId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Dienst dienst = mapResultSetToDienst(rs);
                    if (dienst != null) {
                        dienste.add(dienst);
                    }
                }
            }
        }

        return dienste;
    }

    /**
     * Findet alle Dienste in einem Datumsbereich
     */
    public List<Dienst> findByDateRange(LocalDate startDatum, LocalDate endDatum) throws SQLException {
        if (startDatum == null || endDatum == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT id, dienstplan_id, datum, art, person_id, person_name, status, bemerkung,
                   created_at, updated_at
            FROM dienst 
            WHERE datum BETWEEN ? AND ?
            ORDER BY datum, art
        """;
        
        List<Dienst> dienste = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setDate(1, Date.valueOf(startDatum));
            stmt.setDate(2, Date.valueOf(endDatum));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Dienst dienst = mapResultSetToDienst(rs);
                    if (dienst != null) {
                        dienste.add(dienst);
                    }
                }
            }
        }

        return dienste;
    }

    /**
     * Findet alle Dienste eines bestimmten Datums
     */
    public List<Dienst> findByDate(LocalDate datum) throws SQLException {
        if (datum == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT id, dienstplan_id, datum, art, person_id, person_name, status, bemerkung,
                   created_at, updated_at
            FROM dienst 
            WHERE datum = ?
            ORDER BY art
        """;
        
        List<Dienst> dienste = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setDate(1, Date.valueOf(datum));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Dienst dienst = mapResultSetToDienst(rs);
                    if (dienst != null) {
                        dienste.add(dienst);
                    }
                }
            }
        }

        return dienste;
    }

    /**
     * Findet alle offenen (nicht zugewiesenen) Dienste
     */
    public List<Dienst> findOffeneDienste() throws SQLException {
        String sql = """
            SELECT id, dienstplan_id, datum, art, person_id, person_name, status, bemerkung,
                   created_at, updated_at
            FROM dienst 
            WHERE person_id IS NULL
            ORDER BY datum, art
        """;
        
        List<Dienst> dienste = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Dienst dienst = mapResultSetToDienst(rs);
                if (dienst != null) {
                    dienste.add(dienst);
                }
            }
        }

        return dienste;
    }

    /**
     * Findet alle Dienste mit einem bestimmten Status
     */
    public List<Dienst> findByStatus(DienstStatus status) throws SQLException {
        if (status == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT id, dienstplan_id, datum, art, person_id, person_name, status, bemerkung,
                   created_at, updated_at
            FROM dienst 
            WHERE status = ?
            ORDER BY datum, art
        """;
        
        List<Dienst> dienste = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status.name());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Dienst dienst = mapResultSetToDienst(rs);
                    if (dienst != null) {
                        dienste.add(dienst);
                    }
                }
            }
        }

        return dienste;
    }

    /**
     * Aktualisiert einen Dienst in der Datenbank
     */
    public void update(Dienst dienst) throws SQLException {
        if (dienst == null || dienst.getId() == null) {
            throw new IllegalArgumentException("Dienst und ID dürfen nicht null sein");
        }
        
        String sql = """
            UPDATE dienst 
            SET datum = ?, art = ?, person_id = ?, person_name = ?, status = ?, bemerkung = ?
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setDate(1, Date.valueOf(dienst.getDatum()));
            stmt.setString(2, dienst.getArt().name());
            
            if (dienst.getPersonId() != null) {
                stmt.setLong(3, dienst.getPersonId());
                stmt.setString(4, dienst.getPersonName());
            } else {
                stmt.setNull(3, Types.BIGINT);
                stmt.setNull(4, Types.VARCHAR);
            }
            
            stmt.setString(5, dienst.getStatus().name());
            stmt.setString(6, dienst.getBemerkung());
            stmt.setLong(7, dienst.getId());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Dienst nicht gefunden: " + dienst.getId());
            }
            
            logger.debug("Dienst aktualisiert: ID {}", dienst.getId());
        }
    }
    
    /**
     * Weist einen Dienst einer Person zu
     */
    public void assignPerson(Long dienstId, Person person) throws SQLException {
        if (dienstId == null || person == null) {
            throw new IllegalArgumentException("DienstID und Person dürfen nicht null sein");
        }
        
        String sql = """
            UPDATE dienst 
            SET person_id = ?, person_name = ?, status = ?
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, person.getId());
            stmt.setString(2, person.getName());
            stmt.setString(3, DienstStatus.GEPLANT.name());
            stmt.setLong(4, dienstId);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Dienst nicht gefunden: " + dienstId);
            }
            
            logger.info("Dienst {} wurde {} zugewiesen", dienstId, person.getName());
        }
    }
    
    /**
     * Entfernt die Zuweisung eines Dienstes
     */
    public void unassignPerson(Long dienstId) throws SQLException {
        if (dienstId == null) {
            throw new IllegalArgumentException("DienstID darf nicht null sein");
        }
        
        String sql = """
            UPDATE dienst 
            SET person_id = NULL, person_name = NULL, status = ?
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, DienstStatus.GEPLANT.name());
            stmt.setLong(2, dienstId);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Dienst nicht gefunden: " + dienstId);
            }
            
            logger.info("Zuweisung für Dienst {} entfernt", dienstId);
        }
    }
    
    /**
     * Löscht einen Dienst aus der Datenbank
     */
    public boolean delete(Long id) throws SQLException {
        if (id == null) {
            return false;
        }
        
        String sql = "DELETE FROM dienst WHERE id = ?";
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            int affectedRows = stmt.executeUpdate();
            
            boolean deleted = affectedRows > 0;
            if (deleted) {
                logger.debug("Dienst gelöscht: ID {}", id);
            }
            
            return deleted;
        }
    }
    
    /**
     * Löscht alle Dienste eines Dienstplans
     */
    public int deleteByDienstplanId(Long dienstplanId) throws SQLException {
        if (dienstplanId == null) {
            return 0;
        }
        
        String sql = "DELETE FROM dienst WHERE dienstplan_id = ?";
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, dienstplanId);
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                logger.debug("Dienste gelöscht für Dienstplan ID {}: {} Einträge", dienstplanId, affectedRows);
            }
            
            return affectedRows;
        }
    }
    
    /**
     * Zählt die Anzahl der Dienste
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM dienst";
        
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
     * Zählt die Anzahl der Dienste pro Person
     */
    public Map<Long, Integer> countByPerson() throws SQLException {
        String sql = """
            SELECT person_id, COUNT(*) as anzahl
            FROM dienst 
            WHERE person_id IS NOT NULL
            GROUP BY person_id
        """;
        
        Map<Long, Integer> statistik = new HashMap<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Long personId = rs.getLong("person_id");
                int anzahl = rs.getInt("anzahl");
                statistik.put(personId, anzahl);
            }
        }
        
        return statistik;
    }
    
    /**
     * Statistik: Anzahl Dienste pro Dienstart
     */
    public Map<DienstArt, Integer> countByDienstArt() throws SQLException {
        String sql = """
            SELECT art, COUNT(*) as anzahl
            FROM dienst 
            GROUP BY art
        """;
        
        Map<DienstArt, Integer> statistik = new EnumMap<>(DienstArt.class);
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                try {
                    DienstArt art = DienstArt.valueOf(rs.getString("art"));
                    int anzahl = rs.getInt("anzahl");
                    statistik.put(art, anzahl);
                } catch (IllegalArgumentException e) {
                    logger.warn("Unbekannte DienstArt: {}", rs.getString("art"));
                }
            }
        }
        
        return statistik;
    }
    
    /**
     * Statistik: Anzahl Dienste pro Status
     */
    public Map<DienstStatus, Integer> countByStatus() throws SQLException {
        String sql = """
            SELECT status, COUNT(*) as anzahl
            FROM dienst 
            GROUP BY status
        """;
        
        Map<DienstStatus, Integer> statistik = new EnumMap<>(DienstStatus.class);
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                try {
                    DienstStatus status = DienstStatus.valueOf(rs.getString("status"));
                    int anzahl = rs.getInt("anzahl");
                    statistik.put(status, anzahl);
                } catch (IllegalArgumentException e) {
                    logger.warn("Unbekannter DienstStatus: {}", rs.getString("status"));
                }
            }
        }
        
        return statistik;
    }
    
    /**
     * Berechnet Zuweisungsgrad (Prozent der zugewiesenen Dienste)
     */
    public double calculateZuweisungsgrad() throws SQLException {
        String sql = """
            SELECT 
                COUNT(*) as gesamt,
                COUNT(person_id) as zugewiesen
            FROM dienst
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int gesamt = rs.getInt("gesamt");
                int zugewiesen = rs.getInt("zugewiesen");
                
                if (gesamt == 0) return 0.0;
                return (double) zugewiesen / gesamt * 100.0;
            }
        }
        
        return 0.0;
    }
    
    // Private Hilfsmethoden
    
    private Dienst mapResultSetToDienst(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        LocalDate datum = rs.getDate("datum").toLocalDate();
        String artString = rs.getString("art");
        DienstArt art = DienstArt.safeValueOf(artString);

        if (art == null) {
            logger.warn("Überspringe Dienst mit unbekannter DienstArt: {}", artString);
            return null;
        }

        Long personId = rs.getLong("person_id");
        if (rs.wasNull()) {
            personId = null;
        }

        String personName = rs.getString("person_name");
        DienstStatus status = DienstStatus.valueOf(rs.getString("status"));
        String bemerkung = rs.getString("bemerkung");

        return new Dienst(id, datum, art, personId, personName, status, bemerkung);
    }
}
