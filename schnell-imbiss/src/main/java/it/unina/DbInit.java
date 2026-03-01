package it.unina;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Utility per inizializzare il database H2 (schema + dati demo).
 */

public class DbInit {

    /** Costruttore privato: classe di utilità non istanziabile. */
    private DbInit() {}
    /**
     * Avvia l'inizializzazione del database.
     *
     * @param args argomenti da riga di comando (non utilizzati)
     * @throws Exception in caso di errore durante l'inizializzazione
     */
    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:file:./db/schnell";
        String user = "sa";
        String pass = "";

        String schema = Files.readString(Path.of("src/main/resources/schema.sql"));
        String data   = Files.readString(Path.of("src/main/resources/data.sql"));

        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement st = c.createStatement()) {

            for (String s : schema.split(";")) {
                if (!s.trim().isEmpty()) st.execute(s);
            }
            for (String s : data.split(";")) {
                if (!s.trim().isEmpty()) st.execute(s);
            }

            System.out.println("DB inizializzato con dati demo ✅");
        }
    }
}
