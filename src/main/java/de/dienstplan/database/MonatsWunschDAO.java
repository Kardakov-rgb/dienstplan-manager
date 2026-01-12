package de.dienstplan.database;

import de.dienstplan.model.MonatsWunsch;
import de.dienstplan.model.WunschTyp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Data Access Object für MonatsWunsch-Operationen.
 * Verwaltet Urlaube, Freiwünsche und Dienstwünsche.
 */
public class MonatsWunschDAO {

    private static final Logger logger = LoggerFactory.getLogger(MonatsWunschDAO.class);
    private static final DateTimeFormatter MONAT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * Speichert einen neuen MonatsWunsch in der Datenbank.
     */
    public MonatsWunsch create(MonatsWunsch wunsch) throws SQLException {
        if (wunsch == null) {
            throw new IllegalArgumentException("MonatsWunsch darf nicht null sein");
        }

        String sql = """
            INSERT INTO monats_wunsch (person_id, person_name, monat_jahr, datum, typ, erfuellt, bemerkung)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, wunsch.getPersonId());
            stmt.setString(2, wunsch.getPersonName());
            stmt.setString(3, formatMonat(wunsch.getMonat()));
            stmt.setDate(4, java.sql.Date.valueOf(wunsch.getDatum()));
            stmt.setString(5, wunsch.getTyp().name());

            if (wunsch.getErfuellt() != null) {
                stmt.setBoolean(6, wunsch.getErfuellt());
            } else {
                stmt.setNull(6, Types.BOOLEAN);
            }
            stmt.setString(7, wunsch.getBemerkung());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Erstellen des MonatsWunsch fehlgeschlagen");
            }

            // ID abrufen
            try (Statement idStmt = conn.createStatement();
                 ResultSet rs = idStmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    wunsch.setId(rs.getLong(1));
                }
            }

            logger.debug("MonatsWunsch erstellt: {} für Person {}", wunsch.getTyp(), wunsch.getPersonId());
            return wunsch;
        }
    }

    /**
     * Speichert mehrere MonatsWünsche in einer Transaktion (Batch-Insert).
     */
    public List<MonatsWunsch> createBatch(List<MonatsWunsch> wuensche) throws SQLException {
        if (wuensche == null || wuensche.isEmpty()) {
            return new ArrayList<>();
        }

        String sql = """
            INSERT INTO monats_wunsch (person_id, person_name, monat_jahr, datum, typ, erfuellt, bemerkung)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseManager.createConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (MonatsWunsch wunsch : wuensche) {
                    stmt.setLong(1, wunsch.getPersonId());
                    stmt.setString(2, wunsch.getPersonName());
                    stmt.setString(3, formatMonat(wunsch.getMonat()));
                    stmt.setDate(4, java.sql.Date.valueOf(wunsch.getDatum()));
                    stmt.setString(5, wunsch.getTyp().name());

                    if (wunsch.getErfuellt() != null) {
                        stmt.setBoolean(6, wunsch.getErfuellt());
                    } else {
                        stmt.setNull(6, Types.BOOLEAN);
                    }
                    stmt.setString(7, wunsch.getBemerkung());

                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();

                logger.info("{} MonatsWünsche per Batch erstellt", wuensche.size());
                return wuensche;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Findet einen MonatsWunsch anhand der ID.
     */
    public Optional<MonatsWunsch> findById(Long id) throws SQLException {
        if (id == null) {
            return Optional.empty();
        }

        String sql = """
            SELECT id, person_id, person_name, monat_jahr, datum, typ, erfuellt, bemerkung
            FROM monats_wunsch
            WHERE id = ?
        """;

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToMonatsWunsch(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Findet alle MonatsWünsche für eine Person.
     */
    public List<MonatsWunsch> findByPersonId(Long personId) throws SQLException {
        if (personId == null) {
            return new ArrayList<>();
        }

        String sql = """
            SELECT id, person_id, person_name, monat_jahr, datum, typ, erfuellt, bemerkung
            FROM monats_wunsch
            WHERE person_id = ?
            ORDER BY datum
        """;

        List<MonatsWunsch> wuensche = new ArrayList<>();

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, personId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    wuensche.add(mapResultSetToMonatsWunsch(rs));
                }
            }
        }

        return wuensche;
    }

    /**
     * Findet alle MonatsWünsche für einen bestimmten Monat.
     */
    public List<MonatsWunsch> findByMonat(YearMonth monat) throws SQLException {
        if (monat == null) {
            return new ArrayList<>();
        }

        String sql = """
            SELECT id, person_id, person_name, monat_jahr, datum, typ, erfuellt, bemerkung
            FROM monats_wunsch
            WHERE monat_jahr = ?
            ORDER BY person_name, datum
        """;

        List<MonatsWunsch> wuensche = new ArrayList<>();

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, formatMonat(monat));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    wuensche.add(mapResultSetToMonatsWunsch(rs));
                }
            }
        }

        logger.debug("{} MonatsWünsche für {} geladen", wuensche.size(), monat);
        return wuensche;
    }

    /**
     * Findet alle MonatsWünsche für eine Person in einem bestimmten Monat.
     */
    public List<MonatsWunsch> findByPersonAndMonat(Long personId, YearMonth monat) throws SQLException {
        if (personId == null || monat == null) {
            return new ArrayList<>();
        }

        String sql = """
            SELECT id, person_id, person_name, monat_jahr, datum, typ, erfuellt, bemerkung
            FROM monats_wunsch
            WHERE person_id = ? AND monat_jahr = ?
            ORDER BY datum
        """;

        List<MonatsWunsch> wuensche = new ArrayList<>();

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, personId);
            stmt.setString(2, formatMonat(monat));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    wuensche.add(mapResultSetToMonatsWunsch(rs));
                }
            }
        }

        return wuensche;
    }

    /**
     * Findet alle MonatsWünsche eines bestimmten Typs für einen Monat.
     */
    public List<MonatsWunsch> findByMonatAndTyp(YearMonth monat, WunschTyp typ) throws SQLException {
        if (monat == null || typ == null) {
            return new ArrayList<>();
        }

        String sql = """
            SELECT id, person_id, person_name, monat_jahr, datum, typ, erfuellt, bemerkung
            FROM monats_wunsch
            WHERE monat_jahr = ? AND typ = ?
            ORDER BY person_name, datum
        """;

        List<MonatsWunsch> wuensche = new ArrayList<>();

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, formatMonat(monat));
            stmt.setString(2, typ.name());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    wuensche.add(mapResultSetToMonatsWunsch(rs));
                }
            }
        }

        return wuensche;
    }

    /**
     * Findet alle MonatsWünsche gruppiert nach Person-ID für einen Monat.
     * Nützlich für den Algorithmus.
     */
    public Map<Long, List<MonatsWunsch>> findByMonatGroupedByPerson(YearMonth monat) throws SQLException {
        List<MonatsWunsch> alleWuensche = findByMonat(monat);

        Map<Long, List<MonatsWunsch>> grouped = new HashMap<>();
        for (MonatsWunsch wunsch : alleWuensche) {
            grouped.computeIfAbsent(wunsch.getPersonId(), k -> new ArrayList<>())
                   .add(wunsch);
        }

        return grouped;
    }

    /**
     * Findet den MonatsWunsch für eine Person an einem bestimmten Datum.
     */
    public Optional<MonatsWunsch> findByPersonAndDatum(Long personId, LocalDate datum) throws SQLException {
        if (personId == null || datum == null) {
            return Optional.empty();
        }

        String sql = """
            SELECT id, person_id, person_name, monat_jahr, datum, typ, erfuellt, bemerkung
            FROM monats_wunsch
            WHERE person_id = ? AND datum = ?
        """;

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, personId);
            stmt.setDate(2, java.sql.Date.valueOf(datum));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToMonatsWunsch(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Aktualisiert einen MonatsWunsch.
     */
    public void update(MonatsWunsch wunsch) throws SQLException {
        if (wunsch == null || wunsch.getId() == null) {
            throw new IllegalArgumentException("MonatsWunsch und ID dürfen nicht null sein");
        }

        String sql = """
            UPDATE monats_wunsch
            SET person_id = ?, person_name = ?, monat_jahr = ?, datum = ?, typ = ?, erfuellt = ?, bemerkung = ?
            WHERE id = ?
        """;

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, wunsch.getPersonId());
            stmt.setString(2, wunsch.getPersonName());
            stmt.setString(3, formatMonat(wunsch.getMonat()));
            stmt.setDate(4, java.sql.Date.valueOf(wunsch.getDatum()));
            stmt.setString(5, wunsch.getTyp().name());

            if (wunsch.getErfuellt() != null) {
                stmt.setBoolean(6, wunsch.getErfuellt());
            } else {
                stmt.setNull(6, Types.BOOLEAN);
            }
            stmt.setString(7, wunsch.getBemerkung());
            stmt.setLong(8, wunsch.getId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("MonatsWunsch nicht gefunden: " + wunsch.getId());
            }

            logger.debug("MonatsWunsch aktualisiert: {}", wunsch.getId());
        }
    }

    /**
     * Aktualisiert den Erfüllungsstatus eines MonatsWunschs.
     */
    public void updateErfuellung(Long id, boolean erfuellt) throws SQLException {
        if (id == null) {
            throw new IllegalArgumentException("ID darf nicht null sein");
        }

        String sql = "UPDATE monats_wunsch SET erfuellt = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, erfuellt);
            stmt.setLong(2, id);

            stmt.executeUpdate();
            logger.debug("Erfüllung aktualisiert für MonatsWunsch {}: {}", id, erfuellt);
        }
    }

    /**
     * Aktualisiert den Erfüllungsstatus mehrerer MonatsWünsche (Batch).
     */
    public void updateErfuellungBatch(Map<Long, Boolean> erfuellungen) throws SQLException {
        if (erfuellungen == null || erfuellungen.isEmpty()) {
            return;
        }

        String sql = "UPDATE monats_wunsch SET erfuellt = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.createConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Map.Entry<Long, Boolean> entry : erfuellungen.entrySet()) {
                    stmt.setBoolean(1, entry.getValue());
                    stmt.setLong(2, entry.getKey());
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();

                logger.info("{} MonatsWunsch-Erfüllungen aktualisiert", erfuellungen.size());

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Löscht einen MonatsWunsch.
     */
    public boolean delete(Long id) throws SQLException {
        if (id == null) {
            return false;
        }

        String sql = "DELETE FROM monats_wunsch WHERE id = ?";

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            int affectedRows = stmt.executeUpdate();

            boolean deleted = affectedRows > 0;
            if (deleted) {
                logger.debug("MonatsWunsch gelöscht: ID {}", id);
            }

            return deleted;
        }
    }

    /**
     * Löscht alle MonatsWünsche für einen bestimmten Monat.
     */
    public int deleteByMonat(YearMonth monat) throws SQLException {
        if (monat == null) {
            return 0;
        }

        String sql = "DELETE FROM monats_wunsch WHERE monat_jahr = ?";

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, formatMonat(monat));
            int deleted = stmt.executeUpdate();

            logger.info("{} MonatsWünsche für {} gelöscht", deleted, monat);
            return deleted;
        }
    }

    /**
     * Löscht alle MonatsWünsche für eine Person.
     */
    public int deleteByPersonId(Long personId) throws SQLException {
        if (personId == null) {
            return 0;
        }

        String sql = "DELETE FROM monats_wunsch WHERE person_id = ?";

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, personId);
            int deleted = stmt.executeUpdate();

            logger.debug("{} MonatsWünsche für Person {} gelöscht", deleted, personId);
            return deleted;
        }
    }

    /**
     * Löscht alle MonatsWünsche für eine Person in einem bestimmten Monat.
     */
    public int deleteByPersonAndMonat(Long personId, YearMonth monat) throws SQLException {
        if (personId == null || monat == null) {
            return 0;
        }

        String sql = "DELETE FROM monats_wunsch WHERE person_id = ? AND monat_jahr = ?";

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, personId);
            stmt.setString(2, formatMonat(monat));
            int deleted = stmt.executeUpdate();

            logger.debug("{} MonatsWünsche für Person {} im Monat {} gelöscht", deleted, personId, monat);
            return deleted;
        }
    }

    /**
     * Prüft ob eine Person an einem bestimmten Datum Urlaub hat.
     */
    public boolean hatUrlaubAm(Long personId, LocalDate datum) throws SQLException {
        if (personId == null || datum == null) {
            return false;
        }

        String sql = """
            SELECT COUNT(*) FROM monats_wunsch
            WHERE person_id = ? AND datum = ? AND typ = 'URLAUB'
        """;

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, personId);
            stmt.setDate(2, java.sql.Date.valueOf(datum));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }

        return false;
    }

    /**
     * Findet alle Urlaubstage einer Person in einem Monat.
     */
    public List<LocalDate> findUrlaubsTage(Long personId, YearMonth monat) throws SQLException {
        List<MonatsWunsch> urlaubeWuensche = findByPersonAndMonat(personId, monat).stream()
            .filter(MonatsWunsch::isUrlaub)
            .toList();

        return urlaubeWuensche.stream()
            .map(MonatsWunsch::getDatum)
            .toList();
    }

    /**
     * Zählt die MonatsWünsche pro Typ für einen Monat.
     */
    public Map<WunschTyp, Integer> countByMonatAndTyp(YearMonth monat) throws SQLException {
        if (monat == null) {
            return new EnumMap<>(WunschTyp.class);
        }

        String sql = """
            SELECT typ, COUNT(*) as anzahl
            FROM monats_wunsch
            WHERE monat_jahr = ?
            GROUP BY typ
        """;

        Map<WunschTyp, Integer> counts = new EnumMap<>(WunschTyp.class);
        // Initialisiere alle Typen mit 0
        for (WunschTyp typ : WunschTyp.values()) {
            counts.put(typ, 0);
        }

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, formatMonat(monat));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        WunschTyp typ = WunschTyp.valueOf(rs.getString("typ"));
                        counts.put(typ, rs.getInt("anzahl"));
                    } catch (IllegalArgumentException e) {
                        logger.warn("Unbekannter WunschTyp: {}", rs.getString("typ"));
                    }
                }
            }
        }

        return counts;
    }

    /**
     * Gibt alle Monate zurück, für die MonatsWünsche existieren.
     */
    public List<YearMonth> findAlleMonate() throws SQLException {
        String sql = """
            SELECT DISTINCT monat_jahr
            FROM monats_wunsch
            ORDER BY monat_jahr DESC
        """;

        List<YearMonth> monate = new ArrayList<>();

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                monate.add(parseMonat(rs.getString("monat_jahr")));
            }
        }

        return monate;
    }

    // Private Hilfsmethoden

    private MonatsWunsch mapResultSetToMonatsWunsch(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        Long personId = rs.getLong("person_id");
        String personName = rs.getString("person_name");
        YearMonth monat = parseMonat(rs.getString("monat_jahr"));
        LocalDate datum = rs.getDate("datum").toLocalDate();

        WunschTyp typ;
        try {
            typ = WunschTyp.valueOf(rs.getString("typ"));
        } catch (IllegalArgumentException e) {
            logger.warn("Unbekannter WunschTyp: {}, verwende FREIWUNSCH als Fallback", rs.getString("typ"));
            typ = WunschTyp.FREIWUNSCH;
        }

        Boolean erfuellt = rs.getObject("erfuellt") != null ? rs.getBoolean("erfuellt") : null;
        String bemerkung = rs.getString("bemerkung");

        return new MonatsWunsch(id, personId, personName, monat, datum, typ, erfuellt, bemerkung);
    }

    private String formatMonat(YearMonth monat) {
        return monat != null ? monat.format(MONAT_FORMAT) : null;
    }

    private YearMonth parseMonat(String monatStr) {
        return monatStr != null ? YearMonth.parse(monatStr, MONAT_FORMAT) : null;
    }
}
