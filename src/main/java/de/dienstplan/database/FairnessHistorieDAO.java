package de.dienstplan.database;

import de.dienstplan.model.FairnessScore;
import de.dienstplan.model.WunschStatistik;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Data Access Object für die Fairness-Historie.
 * Speichert und lädt historische Wunscherfüllungsdaten für das Fairness-Tracking.
 */
public class FairnessHistorieDAO {

    private static final Logger logger = LoggerFactory.getLogger(FairnessHistorieDAO.class);
    private static final DateTimeFormatter MONAT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * Speichert oder aktualisiert die Fairness-Historie für eine Person in einem Monat.
     */
    public void saveOrUpdate(Long personId, YearMonth monat, WunschStatistik statistik) throws SQLException {
        if (personId == null || monat == null || statistik == null) {
            throw new IllegalArgumentException("Parameter dürfen nicht null sein");
        }

        // Prüfen ob bereits ein Eintrag existiert
        String checkSql = "SELECT id FROM fairness_historie WHERE person_id = ? AND monat_jahr = ?";

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setLong(1, personId);
            checkStmt.setString(2, formatMonat(monat));

            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    // Update existierenden Eintrag
                    updateInternal(conn, rs.getLong("id"), statistik);
                } else {
                    // Neuen Eintrag erstellen
                    createInternal(conn, personId, monat, statistik);
                }
            }
        }
    }

    /**
     * Speichert Fairness-Historie für mehrere Personen in einer Transaktion.
     */
    public void saveOrUpdateBatch(YearMonth monat, Map<Long, WunschStatistik> statistiken) throws SQLException {
        if (monat == null || statistiken == null || statistiken.isEmpty()) {
            return;
        }

        try (Connection conn = DatabaseManager.createConnection()) {
            conn.setAutoCommit(false);

            try {
                for (Map.Entry<Long, WunschStatistik> entry : statistiken.entrySet()) {
                    Long personId = entry.getKey();
                    WunschStatistik statistik = entry.getValue();

                    // Prüfen ob Eintrag existiert
                    String checkSql = "SELECT id FROM fairness_historie WHERE person_id = ? AND monat_jahr = ?";
                    try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                        checkStmt.setLong(1, personId);
                        checkStmt.setString(2, formatMonat(monat));

                        try (ResultSet rs = checkStmt.executeQuery()) {
                            if (rs.next()) {
                                updateInternal(conn, rs.getLong("id"), statistik);
                            } else {
                                createInternal(conn, personId, monat, statistik);
                            }
                        }
                    }
                }

                conn.commit();
                logger.info("{} Fairness-Historien für {} gespeichert", statistiken.size(), monat);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Lädt die Fairness-Historie für eine Person für einen bestimmten Monat.
     */
    public Optional<WunschStatistik> findByPersonAndMonat(Long personId, YearMonth monat) throws SQLException {
        if (personId == null || monat == null) {
            return Optional.empty();
        }

        String sql = """
            SELECT person_id, monat_jahr, freiwuensche_gesamt, freiwuensche_erfuellt,
                   dienstwuensche_gesamt, dienstwuensche_erfuellt
            FROM fairness_historie
            WHERE person_id = ? AND monat_jahr = ?
        """;

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, personId);
            stmt.setString(2, formatMonat(monat));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToStatistik(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Lädt die gesamte Fairness-Historie für eine Person.
     */
    public List<WunschStatistik> findByPersonId(Long personId) throws SQLException {
        if (personId == null) {
            return new ArrayList<>();
        }

        String sql = """
            SELECT person_id, monat_jahr, freiwuensche_gesamt, freiwuensche_erfuellt,
                   dienstwuensche_gesamt, dienstwuensche_erfuellt
            FROM fairness_historie
            WHERE person_id = ?
            ORDER BY monat_jahr DESC
        """;

        List<WunschStatistik> historie = new ArrayList<>();

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, personId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    historie.add(mapResultSetToStatistik(rs));
                }
            }
        }

        return historie;
    }

    /**
     * Lädt die Fairness-Historie für alle Personen für einen bestimmten Monat.
     */
    public Map<Long, WunschStatistik> findByMonat(YearMonth monat) throws SQLException {
        if (monat == null) {
            return new HashMap<>();
        }

        String sql = """
            SELECT person_id, monat_jahr, freiwuensche_gesamt, freiwuensche_erfuellt,
                   dienstwuensche_gesamt, dienstwuensche_erfuellt
            FROM fairness_historie
            WHERE monat_jahr = ?
        """;

        Map<Long, WunschStatistik> result = new HashMap<>();

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, formatMonat(monat));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    WunschStatistik statistik = mapResultSetToStatistik(rs);
                    result.put(statistik.getPersonId(), statistik);
                }
            }
        }

        return result;
    }

    /**
     * Berechnet den FairnessScore für eine Person basierend auf der gesamten Historie.
     */
    public FairnessScore calculateFairnessScore(Long personId, String personName) throws SQLException {
        if (personId == null) {
            return null;
        }

        FairnessScore score = new FairnessScore(personId, personName);

        List<WunschStatistik> historie = findByPersonId(personId);

        for (WunschStatistik statistik : historie) {
            int wuensche = statistik.getAnzahlWeicheWuensche();
            int erfuellt = statistik.getErfuellteWeicheWuensche();
            score.addMonatsDaten(wuensche, erfuellt);
        }

        return score;
    }

    /**
     * Berechnet die FairnessScores für alle Personen und sortiert sie nach Priorität.
     * Personen mit niedrigerer Erfüllungsquote erhalten höhere Priorität.
     */
    public List<FairnessScore> calculateAllFairnessScores() throws SQLException {
        String sql = """
            SELECT fh.person_id, p.name as person_name,
                   COUNT(DISTINCT fh.monat_jahr) as anzahl_monate,
                   SUM(fh.freiwuensche_gesamt + fh.dienstwuensche_gesamt) as gesamt_wuensche,
                   SUM(fh.freiwuensche_erfuellt + fh.dienstwuensche_erfuellt) as erfuellte_wuensche
            FROM fairness_historie fh
            LEFT JOIN person p ON fh.person_id = p.id
            GROUP BY fh.person_id, p.name
            ORDER BY
                CASE
                    WHEN SUM(fh.freiwuensche_gesamt + fh.dienstwuensche_gesamt) = 0 THEN 0.5
                    ELSE CAST(SUM(fh.freiwuensche_erfuellt + fh.dienstwuensche_erfuellt) AS REAL) /
                         SUM(fh.freiwuensche_gesamt + fh.dienstwuensche_gesamt)
                END ASC
        """;

        List<FairnessScore> scores = new ArrayList<>();

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            int prioritaet = 1;
            while (rs.next()) {
                FairnessScore score = new FairnessScore();
                score.setPersonId(rs.getLong("person_id"));
                score.setPersonName(rs.getString("person_name"));
                score.setAnzahlMonate(rs.getInt("anzahl_monate"));
                score.setGesamtWuensche(rs.getInt("gesamt_wuensche"));
                score.setErfuellteWuensche(rs.getInt("erfuellte_wuensche"));
                score.berechneErfuellung();
                score.setPrioritaet(prioritaet++);

                scores.add(score);
            }
        }

        logger.debug("{} FairnessScores berechnet", scores.size());
        return scores;
    }

    /**
     * Berechnet FairnessScores nur für die letzten N Monate.
     */
    public List<FairnessScore> calculateFairnessScoresForLastMonths(int anzahlMonate) throws SQLException {
        if (anzahlMonate <= 0) {
            return calculateAllFairnessScores();
        }

        // Berechne das Startdatum
        YearMonth startMonat = YearMonth.now().minusMonths(anzahlMonate - 1);

        String sql = """
            SELECT fh.person_id, p.name as person_name,
                   COUNT(DISTINCT fh.monat_jahr) as anzahl_monate,
                   SUM(fh.freiwuensche_gesamt + fh.dienstwuensche_gesamt) as gesamt_wuensche,
                   SUM(fh.freiwuensche_erfuellt + fh.dienstwuensche_erfuellt) as erfuellte_wuensche
            FROM fairness_historie fh
            LEFT JOIN person p ON fh.person_id = p.id
            WHERE fh.monat_jahr >= ?
            GROUP BY fh.person_id, p.name
            ORDER BY
                CASE
                    WHEN SUM(fh.freiwuensche_gesamt + fh.dienstwuensche_gesamt) = 0 THEN 0.5
                    ELSE CAST(SUM(fh.freiwuensche_erfuellt + fh.dienstwuensche_erfuellt) AS REAL) /
                         SUM(fh.freiwuensche_gesamt + fh.dienstwuensche_gesamt)
                END ASC
        """;

        List<FairnessScore> scores = new ArrayList<>();

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, formatMonat(startMonat));

            try (ResultSet rs = stmt.executeQuery()) {
                int prioritaet = 1;
                while (rs.next()) {
                    FairnessScore score = new FairnessScore();
                    score.setPersonId(rs.getLong("person_id"));
                    score.setPersonName(rs.getString("person_name"));
                    score.setAnzahlMonate(rs.getInt("anzahl_monate"));
                    score.setGesamtWuensche(rs.getInt("gesamt_wuensche"));
                    score.setErfuellteWuensche(rs.getInt("erfuellte_wuensche"));
                    score.berechneErfuellung();
                    score.setPrioritaet(prioritaet++);

                    scores.add(score);
                }
            }
        }

        return scores;
    }

    /**
     * Gibt alle Monate zurück, für die Fairness-Historie existiert.
     */
    public List<YearMonth> findAlleMonate() throws SQLException {
        String sql = """
            SELECT DISTINCT monat_jahr
            FROM fairness_historie
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

    /**
     * Löscht die Fairness-Historie für einen bestimmten Monat.
     */
    public int deleteByMonat(YearMonth monat) throws SQLException {
        if (monat == null) {
            return 0;
        }

        String sql = "DELETE FROM fairness_historie WHERE monat_jahr = ?";

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, formatMonat(monat));
            int deleted = stmt.executeUpdate();

            logger.info("{} Fairness-Historie-Einträge für {} gelöscht", deleted, monat);
            return deleted;
        }
    }

    /**
     * Löscht die Fairness-Historie für eine Person.
     */
    public int deleteByPersonId(Long personId) throws SQLException {
        if (personId == null) {
            return 0;
        }

        String sql = "DELETE FROM fairness_historie WHERE person_id = ?";

        try (Connection conn = DatabaseManager.createConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, personId);
            int deleted = stmt.executeUpdate();

            logger.debug("{} Fairness-Historie-Einträge für Person {} gelöscht", deleted, personId);
            return deleted;
        }
    }

    // Private Hilfsmethoden

    private void createInternal(Connection conn, Long personId, YearMonth monat, WunschStatistik statistik)
            throws SQLException {
        String sql = """
            INSERT INTO fairness_historie (person_id, monat_jahr, freiwuensche_gesamt, freiwuensche_erfuellt,
                                           dienstwuensche_gesamt, dienstwuensche_erfuellt)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, personId);
            stmt.setString(2, formatMonat(monat));
            stmt.setInt(3, statistik.getAnzahlFreiwuensche());
            stmt.setInt(4, statistik.getErfuellteFreiwuensche());
            stmt.setInt(5, statistik.getAnzahlDienstwuensche());
            stmt.setInt(6, statistik.getErfuellteDienstwuensche());

            stmt.executeUpdate();
            logger.debug("Fairness-Historie erstellt für Person {} im Monat {}", personId, monat);
        }
    }

    private void updateInternal(Connection conn, Long id, WunschStatistik statistik) throws SQLException {
        String sql = """
            UPDATE fairness_historie
            SET freiwuensche_gesamt = ?, freiwuensche_erfuellt = ?,
                dienstwuensche_gesamt = ?, dienstwuensche_erfuellt = ?
            WHERE id = ?
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, statistik.getAnzahlFreiwuensche());
            stmt.setInt(2, statistik.getErfuellteFreiwuensche());
            stmt.setInt(3, statistik.getAnzahlDienstwuensche());
            stmt.setInt(4, statistik.getErfuellteDienstwuensche());
            stmt.setLong(5, id);

            stmt.executeUpdate();
            logger.debug("Fairness-Historie aktualisiert: ID {}", id);
        }
    }

    private WunschStatistik mapResultSetToStatistik(ResultSet rs) throws SQLException {
        WunschStatistik statistik = new WunschStatistik();
        statistik.setPersonId(rs.getLong("person_id"));
        statistik.setMonat(parseMonat(rs.getString("monat_jahr")));
        statistik.setAnzahlFreiwuensche(rs.getInt("freiwuensche_gesamt"));
        statistik.setErfuellteFreiwuensche(rs.getInt("freiwuensche_erfuellt"));
        statistik.setAnzahlDienstwuensche(rs.getInt("dienstwuensche_gesamt"));
        statistik.setErfuellteDienstwuensche(rs.getInt("dienstwuensche_erfuellt"));
        return statistik;
    }

    private String formatMonat(YearMonth monat) {
        return monat != null ? monat.format(MONAT_FORMAT) : null;
    }

    private YearMonth parseMonat(String monatStr) {
        return monatStr != null ? YearMonth.parse(monatStr, MONAT_FORMAT) : null;
    }
}
