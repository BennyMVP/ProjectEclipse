package it.unina;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;

/** Costruttore privato: classe di demo non istanziabile. */
public class OrderDemo {

    /** Costruttore privato: classe di demo non istanziabile. */
    private OrderDemo() {}
    
    /**
     * Esegue uno scenario dimostrativo.
     *
     * @param args argomenti da riga di comando (non utilizzati)
     * @throws Exception in caso di errore durante l'esecuzione della demo
     */
    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:file:./db/schnell";
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            c.setAutoCommit(false);

            long idCliente = 1;
            long idPanineria = 1;

            // 1) crea ordine
            long idOrdine;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ordine (id_cliente, id_panineria, data_ora_creazione, stato_ordine, totale) " +
                    "VALUES (?, ?, ?, ?, 0.00)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, idCliente);
                ps.setLong(2, idPanineria);
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(4, "CREATO");
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    idOrdine = keys.getLong(1);
                }
            }

            // 2) aggiungi 2 righe: panino 1 x2, panino 2 x1 (esempio)
            BigDecimal totale = BigDecimal.ZERO;
            totale = totale.add(insertRiga(c, idOrdine, 1, 2));
            totale = totale.add(insertRiga(c, idOrdine, 2, 1));

            // 3) aggiorna totale ordine
            try (PreparedStatement ps = c.prepareStatement("UPDATE ordine SET totale=? WHERE id_ordine=?")) {
                ps.setBigDecimal(1, totale);
                ps.setLong(2, idOrdine);
                ps.executeUpdate();
            }

            c.commit();
            System.out.println("Ordine creato ✅ idOrdine=" + idOrdine + " totale=" + totale);
        }
    }

    private static BigDecimal insertRiga(Connection c, long idOrdine, long idPanino, int qta) throws Exception {
        BigDecimal prezzo;
        try (PreparedStatement ps = c.prepareStatement("SELECT prezzo FROM panino WHERE id_panino=?")) {
            ps.setLong(1, idPanino);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Panino inesistente: " + idPanino);
                prezzo = rs.getBigDecimal(1);
            }
        }

        BigDecimal subtotale = prezzo.multiply(BigDecimal.valueOf(qta));

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO riga_ordine (id_ordine, id_panino, quantita, prezzo_unitario, subtotale) " +
                "VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, idOrdine);
            ps.setLong(2, idPanino);
            ps.setInt(3, qta);
            ps.setBigDecimal(4, prezzo);
            ps.setBigDecimal(5, subtotale);
            ps.executeUpdate();
        }
        return subtotale;
    }
}
