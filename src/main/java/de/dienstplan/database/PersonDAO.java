package de.dienstplan.database;

import de.dienstplan.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Data Access Object für Person-Operationen
 */
public class PersonDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(PersonDAO.class);
    
    /**
     * Speichert eine neue Person in der Datenbank
     */
    public Person create(Person person) throws SQLException {
        if (person == null) {
            throw new IllegalArgumentException("Person darf nicht null sein");
        }
        
        String sql = """
            INSERT INTO person (name, anzahl_dienste, arbeits_tage, verfuegbare_dienst_arten)
            VALUES (?, ?, ?, ?)
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, person.getName());
            stmt.setInt(2, person.getAnzahlDienste());
            stmt.setString(3, encodeWochentage(person.getArbeitsTage()));
            stmt.setString(4, encodeDienstArten(person.getVerfuegbareDienstArten()));
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Erstellen der Person fehlgeschlagen");
            }
            
            // ID manuell abrufen - SQLite-kompatible Methode
            String getIdSql = "SELECT last_insert_rowid()";
            try (Statement idStmt = conn.createStatement();
                 ResultSet rs = idStmt.executeQuery(getIdSql)) {
                
                if (rs.next()) {
                    person.setId(rs.getLong(1));
                } else {
                    throw new SQLException("Erstellen der Person fehlgeschlagen, keine ID generiert");
                }
            }
            
            // Abwesenheiten separat speichern
            if (!person.getAbwesenheiten().isEmpty()) {
                AbwesenheitDAO abwesenheitDAO = new AbwesenheitDAO();
                for (Abwesenheit abwesenheit : person.getAbwesenheiten()) {
                    abwesenheit.setPersonId(person.getId());
                    abwesenheitDAO.create(abwesenheit);
                }
            }
            
            logger.info("Person erstellt: {}", person.getName());
            return person;
        }
    }
    
/**
 * Findet eine Person anhand der ID
 */
public Optional<Person> findById(Long id) throws SQLException {
    if (id == null) {
        return Optional.empty();
    }
   
    String sql = """
        SELECT id, name, anzahl_dienste, arbeits_tage, verfuegbare_dienst_arten,
               created_at, updated_at
        FROM person
        WHERE id = ?
    """;
    
    try (Connection conn = DatabaseManager.createConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
        
        stmt.setLong(1, id);
        
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                Person person = mapResultSetToPerson(rs);
                
                // Abwesenheiten laden
                loadAbwesenheiten(person);
                
                return Optional.of(person);
            }
        }
    }
    
    return Optional.empty();
}
    /**
     * Findet eine Person anhand des Namens
     */
    public Optional<Person> findByName(String name) throws SQLException {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        
        String sql = """
            SELECT id, name, anzahl_dienste, arbeits_tage, verfuegbare_dienst_arten,
                   created_at, updated_at
            FROM person 
            WHERE name = ?
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name.trim());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Person person = mapResultSetToPerson(rs);
                    loadAbwesenheiten(person);
                    return Optional.of(person);
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Lädt alle Personen aus der Datenbank.
     * Optimiert mit Batch-Loading für Abwesenheiten (vermeidet N+1 Problem).
     */
    public List<Person> findAll() throws SQLException {
        String sql = """
            SELECT id, name, anzahl_dienste, arbeits_tage, verfuegbare_dienst_arten,
                   created_at, updated_at
            FROM person
            ORDER BY name
        """;

        List<Person> personen = new ArrayList<>();

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Person person = mapResultSetToPerson(rs);
                personen.add(person);
            }
        }

        // Batch-Load: Alle Abwesenheiten in einer einzigen Query laden
        if (!personen.isEmpty()) {
            AbwesenheitDAO abwesenheitDAO = new AbwesenheitDAO();
            List<Long> personIds = personen.stream()
                .map(Person::getId)
                .toList();

            Map<Long, List<Abwesenheit>> abwesenheitenMap = abwesenheitDAO.findByPersonIds(personIds);

            // Abwesenheiten den Personen zuordnen
            for (Person person : personen) {
                List<Abwesenheit> abwesenheiten = abwesenheitenMap.getOrDefault(person.getId(), new ArrayList<>());
                person.setAbwesenheiten(abwesenheiten);
            }
        }

        logger.debug("Anzahl geladene Personen: {} (mit Batch-Loading)", personen.size());
        return personen;
    }
    
    /**
     * Aktualisiert eine Person in der Datenbank
     */
    public void update(Person person) throws SQLException {
        if (person == null || person.getId() == null) {
            throw new IllegalArgumentException("Person und ID dürfen nicht null sein");
        }
        
        String sql = """
            UPDATE person 
            SET name = ?, anzahl_dienste = ?, arbeits_tage = ?, verfuegbare_dienst_arten = ?
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, person.getName());
            stmt.setInt(2, person.getAnzahlDienste());
            stmt.setString(3, encodeWochentage(person.getArbeitsTage()));
            stmt.setString(4, encodeDienstArten(person.getVerfuegbareDienstArten()));
            stmt.setLong(5, person.getId());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Person nicht gefunden: " + person.getId());
            }
            
            // Abwesenheiten aktualisieren (vereinfacht: löschen und neu erstellen)
            updateAbwesenheiten(person);
            
            logger.info("Person aktualisiert: {}", person.getName());
        }
    }
    
    /**
     * Löscht eine Person aus der Datenbank
     */
    public boolean delete(Long id) throws SQLException {
        if (id == null) {
            return false;
        }
        
        String sql = "DELETE FROM person WHERE id = ?";
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            int affectedRows = stmt.executeUpdate();
            
            boolean deleted = affectedRows > 0;
            if (deleted) {
                logger.info("Person gelöscht: ID {}", id);
            }
            
            return deleted;
        }
    }
    
    /**
     * Sucht Personen anhand verschiedener Kriterien
     */
    public List<Person> search(String namePattern, DienstArt dienstArt, Wochentag wochentag) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT id, name, anzahl_dienste, arbeits_tage, verfuegbare_dienst_arten,
                   created_at, updated_at
            FROM person 
            WHERE 1=1
        """);
        
        List<Object> parameters = new ArrayList<>();
        
        // Name-Suche (LIKE)
        if (namePattern != null && !namePattern.trim().isEmpty()) {
            sql.append(" AND name LIKE ?");
            parameters.add("%" + namePattern.trim() + "%");
        }
        
        // DienstArt-Filter
        if (dienstArt != null) {
            sql.append(" AND verfuegbare_dienst_arten LIKE ?");
            parameters.add("%" + dienstArt.name() + "%");
        }
        
        // Wochentag-Filter
        if (wochentag != null) {
            sql.append(" AND arbeits_tage LIKE ?");
            parameters.add("%" + wochentag.name() + "%");
        }
        
        sql.append(" ORDER BY name");
        
        List<Person> personen = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            // Parameter setzen
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Person person = mapResultSetToPerson(rs);
                    loadAbwesenheiten(person);
                    personen.add(person);
                }
            }
        }
        
        return personen;
    }
    
    /**
     * Zählt die Anzahl der Personen
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM person";
        
        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        
        return 0;
    }
    
    // Private Hilfsmethoden
    
    private Person mapResultSetToPerson(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        String name = rs.getString("name");
        int anzahlDienste = rs.getInt("anzahl_dienste");
        EnumSet<Wochentag> arbeitsTage = decodeWochentage(rs.getString("arbeits_tage"));
        EnumSet<DienstArt> verfuegbareDienstArten = decodeDienstArten(rs.getString("verfuegbare_dienst_arten"));
        
        return new Person(id, name, anzahlDienste, arbeitsTage, verfuegbareDienstArten);
    }
    
    private void loadAbwesenheiten(Person person) throws SQLException {
        AbwesenheitDAO abwesenheitDAO = new AbwesenheitDAO();
        List<Abwesenheit> abwesenheiten = abwesenheitDAO.findByPersonId(person.getId());
        person.setAbwesenheiten(abwesenheiten);
    }
    
    private void updateAbwesenheiten(Person person) throws SQLException {
        // Bestehende Abwesenheiten löschen
        AbwesenheitDAO abwesenheitDAO = new AbwesenheitDAO();
        abwesenheitDAO.deleteByPersonId(person.getId());
        
        // Neue Abwesenheiten erstellen
        for (Abwesenheit abwesenheit : person.getAbwesenheiten()) {
            abwesenheit.setPersonId(person.getId());
            abwesenheit.setId(null); // Sicherstellen, dass eine neue ID generiert wird
            abwesenheitDAO.create(abwesenheit);
        }
    }
    
    private String encodeWochentage(EnumSet<Wochentag> wochentage) {
        if (wochentage == null || wochentage.isEmpty()) {
            return "";
        }
        return wochentage.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }
    
    private EnumSet<Wochentag> decodeWochentage(String encoded) {
        EnumSet<Wochentag> result = EnumSet.noneOf(Wochentag.class);
        
        if (encoded != null && !encoded.trim().isEmpty()) {
            String[] parts = encoded.split(",");
            for (String part : parts) {
                try {
                    result.add(Wochentag.valueOf(part.trim()));
                } catch (IllegalArgumentException e) {
                    logger.warn("Unbekannter Wochentag: {}", part);
                }
            }
        }
        
        return result;
    }
    
    private String encodeDienstArten(EnumSet<DienstArt> dienstArten) {
        if (dienstArten == null || dienstArten.isEmpty()) {
            return "";
        }
        return dienstArten.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }
    
    private EnumSet<DienstArt> decodeDienstArten(String encoded) {
        EnumSet<DienstArt> result = EnumSet.noneOf(DienstArt.class);
        
        if (encoded != null && !encoded.trim().isEmpty()) {
            String[] parts = encoded.split(",");
            for (String part : parts) {
                try {
                    result.add(DienstArt.valueOf(part.trim()));
                } catch (IllegalArgumentException e) {
                    logger.warn("Unbekannte DienstArt: {}", part);
                }
            }
        }
        
        return result;
    }
}