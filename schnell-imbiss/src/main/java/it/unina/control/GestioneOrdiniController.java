package it.unina.control;

import it.unina.dto.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Controller applicativo: gestisce le operazioni principali (clienti, paninerie, panini, ordini, consegne).
 */

public class GestioneOrdiniController {


    /** Stato ordine: nuovo ordine. */
    public static final String ST_NUOVO = "NUOVO";

    /** Stato ordine: consegnato. */
    public static final String ST_CONSEGNATO = "CONSEGNATO";

    /** Stato ordine: in consegna. */
    public static final String ST_IN_CONSEGNA = "IN_CONSEGNA";

    /** Stato ordine: in cucina. */
    public static final String ST_IN_CUCINA = "IN_CUCINA";

    private final String url;
    private final String user;
    private final String pass;

        /**
     * Sessione staff dopo login (ruolo e eventuale riferimento a panineria/rider).
     *
     * @param username     username dello staff
     * @param ruolo        ruolo (es. GESTORE, CASSIERE, RIDER)
     * @param idPanineria  id panineria associata (se applicabile)
     * @param idRider      id rider associato (se applicabile)
     */
    public record StaffSession(String username, String ruolo, Long idPanineria, Long idRider) {}

    /**
     * Inizializza il controller configurando la connessione al DB.
     *
     * @param url URL JDBC di H2
     * @param user username DB
     * @param pass password DB
     * @throws Exception in caso di errore di inizializzazione/connessione
     */

    public GestioneOrdiniController(String url, String user, String pass) throws Exception {
        this.url = url;
        this.user = user;
        this.pass = pass;
        initSchema();
        seedIfEmpty();
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }


    private void initSchema() throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.execute("""
            CREATE TABLE IF NOT EXISTS panineria(
              id_panineria BIGINT AUTO_INCREMENT PRIMARY KEY,
              nome VARCHAR(100) NOT NULL,
              citta VARCHAR(60) NOT NULL,
              via VARCHAR(80) NOT NULL,
              civico VARCHAR(10) NOT NULL,
              cap VARCHAR(10) NOT NULL,
              telefono VARCHAR(20) NOT NULL,
              email VARCHAR(120) NOT NULL,
              iban VARCHAR(40) NOT NULL,
              gestore_nome VARCHAR(60) NOT NULL,
              gestore_cognome VARCHAR(60) NOT NULL
            )
            """);

            st.execute("""
            CREATE TABLE IF NOT EXISTS panino(
              id_panino BIGINT AUTO_INCREMENT PRIMARY KEY,
              id_panineria BIGINT NOT NULL,
              nome VARCHAR(100) NOT NULL,
              descrizione VARCHAR(500) NOT NULL,
              prezzo DECIMAL(10,2) NOT NULL,
              CONSTRAINT fk_panino_panineria FOREIGN KEY (id_panineria) REFERENCES panineria(id_panineria)
            )
            """);

            st.execute("""
            CREATE TABLE IF NOT EXISTS cliente(
            id_cliente BIGINT AUTO_INCREMENT PRIMARY KEY,
            nome VARCHAR(60) NOT NULL,
            cognome VARCHAR(60) NOT NULL,
            indirizzo VARCHAR(200) NOT NULL,
            cap VARCHAR(10) NOT NULL,
            telefono VARCHAR(20) NOT NULL,
            email VARCHAR(120) NOT NULL
            )
            """);



            st.execute("""
            CREATE TABLE IF NOT EXISTS rider(
              id_rider BIGINT AUTO_INCREMENT PRIMARY KEY,
              nome VARCHAR(60) NOT NULL,
              cognome VARCHAR(60) NOT NULL,
              telefono VARCHAR(20) NOT NULL
            )
            """);

            st.execute("""
            CREATE TABLE IF NOT EXISTS ordine(
              id_ordine BIGINT AUTO_INCREMENT PRIMARY KEY,
              id_cliente BIGINT NOT NULL,
              id_panineria BIGINT NOT NULL,
              ts_creazione TIMESTAMP NOT NULL,
              stato VARCHAR(30) NOT NULL,
              totale DECIMAL(10,2) NOT NULL,
              CONSTRAINT fk_ordine_cliente FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente),
              CONSTRAINT fk_ordine_panineria FOREIGN KEY (id_panineria) REFERENCES panineria(id_panineria)
            )
            """);

            st.execute("""
            CREATE TABLE IF NOT EXISTS riga_ordine(
              id_ordine BIGINT NOT NULL,
              id_panino BIGINT NOT NULL,
              qta INT NOT NULL,
              prezzo_unit DECIMAL(10,2) NOT NULL,
              PRIMARY KEY(id_ordine, id_panino),
              CONSTRAINT fk_riga_ordine_ordine FOREIGN KEY (id_ordine) REFERENCES ordine(id_ordine),
              CONSTRAINT fk_riga_ordine_panino FOREIGN KEY (id_panino) REFERENCES panino(id_panino)
            )
            """);

            st.execute("""
            CREATE TABLE IF NOT EXISTS pagamento(
            id_pagamento BIGINT AUTO_INCREMENT PRIMARY KEY,
            id_ordine BIGINT NOT NULL UNIQUE,
            metodo VARCHAR(20) NOT NULL,          -- CARD / CASH
            esito VARCHAR(20) NOT NULL,           -- OK / KO / PENDING
            importo DECIMAL(10,2) NOT NULL,
            ts TIMESTAMP NOT NULL,
            auth_code VARCHAR(40),
            messaggio VARCHAR(200),
            CONSTRAINT fk_pagamento_ordine FOREIGN KEY (id_ordine) REFERENCES ordine(id_ordine)
            )
            """);


            st.execute("""
            CREATE TABLE IF NOT EXISTS consegna(
              id_consegna BIGINT AUTO_INCREMENT PRIMARY KEY,
              id_ordine BIGINT NOT NULL,
              id_rider BIGINT NOT NULL,
              ts_invio TIMESTAMP NOT NULL,
              ts_consegna TIMESTAMP,
              CONSTRAINT fk_consegna_ordine FOREIGN KEY (id_ordine) REFERENCES ordine(id_ordine),
              CONSTRAINT fk_consegna_rider FOREIGN KEY (id_rider) REFERENCES rider(id_rider)
            )
            """);

            st.execute("""
            CREATE TABLE IF NOT EXISTS carta_credito(
            id_carta BIGINT AUTO_INCREMENT PRIMARY KEY,
            id_cliente BIGINT NOT NULL UNIQUE,
            numero_carta VARCHAR(19) NOT NULL,
            scadenza VARCHAR(5) NOT NULL,
            intestatario VARCHAR(100) NOT NULL,
            CONSTRAINT fk_cc_cliente FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)
            )
            """);


            st.execute("""
            CREATE TABLE IF NOT EXISTS utente_staff(
              username VARCHAR(60) PRIMARY KEY,
              password VARCHAR(60) NOT NULL,
              ruolo VARCHAR(20) NOT NULL,
              id_panineria BIGINT,
              id_rider BIGINT
            )
            """);
        }
    }
    
    private boolean tableEmpty(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1) == 0;
        }
    }

    private void seedIfEmpty() throws SQLException {
        try (Connection c = conn()) {
            if (tableEmpty(c, "panineria")) {
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO panineria(nome,citta,via,civico,cap,telefono,email,iban,gestore_nome,gestore_cognome)
                    VALUES(?,?,?,?,?,?,?,?,?,?)
                """)) {
                    ps.setString(1, "Schnell Imbiss Centro");
                    ps.setString(2, "Napoli");
                    ps.setString(3, "Via Roma");
                    ps.setString(4, "10");
                    ps.setString(5, "80100");
                    ps.setString(6, "0810000000");
                    ps.setString(7, "centro@schnell.it");
                    ps.setString(8, "IT60X0542811101000000123456");
                    ps.setString(9, "Mario");
                    ps.setString(10, "Rossi");
                    ps.executeUpdate();
                }
            }

            if (tableEmpty(c, "panino")) {
                long idPan = firstId(c, "panineria", "id_panineria");
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO panino(id_panineria,nome,descrizione,prezzo) VALUES(?,?,?,?)
                """)) {
                    insertPanino(ps, idPan, "Classico", "Pane, hamburger, cheddar, lattuga, pomodoro, salsa", new BigDecimal("6.50"));
                    insertPanino(ps, idPan, "Pollo BBQ", "Pane, pollo, bacon, salsa BBQ, cipolla croccante", new BigDecimal("7.90"));
                    insertPanino(ps, idPan, "Veggie", "Pane, burger vegetale, insalata, pomodoro, maionese veg", new BigDecimal("7.20"));
                }
            }

            if (tableEmpty(c, "rider")) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO rider(nome,cognome,telefono) VALUES(?,?,?)")) {
                    ps.setString(1, "Luca"); ps.setString(2, "Verdi"); ps.setString(3, "3331112223"); ps.executeUpdate();
                    ps.setString(1, "Anna"); ps.setString(2, "Bianchi"); ps.setString(3, "3334445556"); ps.executeUpdate();
                }
            }

            if (tableEmpty(c, "cliente")) {

                long idCliente;

                // 1) inserisco CLIENTE (senza carta)
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO cliente(nome,cognome,indirizzo,cap,telefono,email)
                    VALUES(?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, "Benny");
                    ps.setString(2, "Rossi");
                    ps.setString(3, "Centro Direzionale, Isola A");
                    ps.setString(4, "80143");
                    ps.setString(5, "3330009991");
                    ps.setString(6, "benny@mail.it");
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        idCliente = keys.getLong(1);
                    }
                }

                // 2) inserisco CARTA_CREDITO (nomi colonne come nel tuo SQL)
                try (PreparedStatement ps2 = c.prepareStatement("""
                    INSERT INTO carta_credito(id_cliente, numero_carta, scadenza, intestatario)
                    VALUES(?,?,?,?)
                """)) {
                    ps2.setLong(1, idCliente);
                    ps2.setString(2, "4111111111111111");
                    ps2.setString(3, "12/30");
                    ps2.setString(4, "Benny Rossi");
                    ps2.executeUpdate();
                }
            }


            if (tableEmpty(c, "utente_staff")) {
                // Gestore
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO utente_staff(username,password,ruolo,id_panineria,id_rider) VALUES(?,?,?,?,?)
                """)) {
                    ps.setString(1, "gestore");
                    ps.setString(2, "gestore123");
                    ps.setString(3, "GESTORE");
                    ps.setObject(4, null);
                    ps.setObject(5, null);
                    ps.executeUpdate();

                    // Cassiere della prima panineria
                    long idPan = firstId(c, "panineria", "id_panineria");
                    ps.setString(1, "cassiere1");
                    ps.setString(2, "cassiere123");
                    ps.setString(3, "CASSIERE");
                    ps.setLong(4, idPan);
                    ps.setObject(5, null);
                    ps.executeUpdate();

                    // Rider1
                    long idR = firstId(c, "rider", "id_rider");
                    ps.setString(1, "rider1");
                    ps.setString(2, "rider123");
                    ps.setString(3, "RIDER");
                    ps.setObject(4, null);
                    ps.setLong(5, idR);
                    ps.executeUpdate();
                }
            }
        }
    }

    private long firstId(Connection c, String table, String col) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + col + " FROM " + table + " ORDER BY " + col + " LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void insertPanino(PreparedStatement ps, long idPanineria, String nome, String descr, BigDecimal prezzo) throws SQLException {
        ps.setLong(1, idPanineria);
        ps.setString(2, nome);
        ps.setString(3, descr);
        ps.setBigDecimal(4, prezzo);
        ps.executeUpdate();
    }


    // ===================== PAGAMENTI =====================


    private boolean scadenzaValida(String mmYY) {
        if (mmYY == null) return false;
        String s = mmYY.trim();
        if (!s.matches("\\d{2}/\\d{2}")) return false;

        int mm = Integer.parseInt(s.substring(0,2));
        int yy = Integer.parseInt(s.substring(3,5)) + 2000;
        if (mm < 1 || mm > 12) return false;

        YearMonth exp = YearMonth.of(yy, mm);
        return exp.isAfter(YearMonth.now()) || exp.equals(YearMonth.now());
    }

    /**
     * Esegue il pagamento dell'ordine tramite carta di credito, validando il CVV.
     *
     * @param idOrdine id ordine
     * @param idCliente id cliente
     * @param cvv codice CVV
     * @throws Exception in caso di CVV non valido o errore DB
     */
    public void pagaOrdineCarta(long idOrdine, long idCliente, String cvv) throws Exception {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                // 1) totale ordine + check proprietario
                BigDecimal totale;
                try (PreparedStatement ps = c.prepareStatement("""
                    SELECT totale FROM ordine WHERE id_ordine=? AND id_cliente=?
                """)) {
                    ps.setLong(1, idOrdine);
                    ps.setLong(2, idCliente);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Ordine non trovato o non tuo.");
                        totale = rs.getBigDecimal(1);
                    }
                }

                // 2) prendo carta del cliente
                String numero, scad, intest;
                try (PreparedStatement ps = c.prepareStatement("""
                    SELECT numero_carta, scadenza, intestatario
                    FROM carta_credito
                    WHERE id_cliente=?
                """)) {
                    ps.setLong(1, idCliente);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Carta non trovata per il cliente.");
                        numero = rs.getString(1);
                        scad   = rs.getString(2);
                        intest = rs.getString(3);
                    }
                }

                if (!scadenzaValida(scad)) throw new SQLException("Carta scaduta.");
                // CVV: nel progetto puoi simulare (es: accetta solo 123)
                if (cvv == null || !cvv.trim().matches("\\d{3}")) throw new SQLException("CVV non valido.");
                if (!cvv.trim().equals("123")) throw new SQLException("Pagamento rifiutato (CVV errato).");

                // 3) inserisco pagamento
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO pagamento(id_ordine, metodo, esito, importo, ts, auth_code, messaggio)
                    VALUES(?, 'CARD', 'OK', ?, ?, ?, ?)
                """)) {
                    ps.setLong(1, idOrdine);
                    ps.setBigDecimal(2, totale);
                    ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                    ps.setString(4, "AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                    ps.setString(5, "Pagamento carta OK (" + intest + ")");
                    ps.executeUpdate();
                }

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }



    // ===================== LOGIN / REGISTRAZIONI =====================
    /**
     * Esegue il login di un membro dello staff e restituisce i dati di sessione.
     *
     * @param username username dello staff
     * @param password password dello staff
     * @return sessione staff con ruolo e riferimenti associati
     * @throws Exception in caso di credenziali non valide o errore DB
     */

    public StaffSession loginStaff(String username, String password) throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT username, ruolo, id_panineria, id_rider
                 FROM utente_staff
                 WHERE username=? AND password=?
             """)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new StaffSession(
                        rs.getString("username"),
                        rs.getString("ruolo"),
                        (Long) rs.getObject("id_panineria"),
                        (Long) rs.getObject("id_rider")
                );
            }
        }
    }

    /**
     * Ricerca un cliente registrato tramite dati di login (nome, cognome, telefono).
     *
     * @param nome nome del cliente
     * @param cognome cognome del cliente
     * @param telefono telefono del cliente
     * @return DTO del cliente se trovato
     * @throws Exception in caso di cliente non trovato o errore DB
     */
    public ClienteDTO trovaClientePerLogin(String nome, String cognome, String telefono) throws Exception {
        try (Connection c = conn();
            PreparedStatement ps = c.prepareStatement("""
                SELECT id_cliente,nome,cognome,indirizzo,cap,telefono,email
                FROM cliente
                WHERE nome=? AND cognome=? AND telefono=?
            """)) {
            ps.setString(1, nome);
            ps.setString(2, cognome);
            ps.setString(3, telefono);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new ClienteDTO(
                        rs.getLong("id_cliente"),
                        rs.getString("nome"),
                        rs.getString("cognome"),
                        rs.getString("indirizzo"),
                        rs.getString("cap"),
                        rs.getString("telefono"),
                        rs.getString("email")
                );
            }
        }
    }

    /**
     * Registra un nuovo cliente e, se previsto, memorizza i dati della carta associata.
     *
     * @param nome nome del cliente
     * @param cognome cognome del cliente
     * @param indirizzo indirizzo di consegna
     * @param cap CAP dell'indirizzo
     * @param telefono telefono del cliente
     * @param email email del cliente
     * @param cartaNumero numero carta (se previsto)
     * @param cartaScadenza scadenza carta (MM/YY o formato previsto)
     * @return DTO del cliente registrato
     * @throws Exception in caso di errore DB o validazione
     */

    public ClienteDTO registraCliente(String nome, String cognome, String indirizzo, String cap,
                                  String telefono, String email,
                                  String cartaNumero, String cartaScadenza) throws Exception {

        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                long idCliente;

                // 1) inserisco CLIENTE (con email)
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO cliente(nome,cognome,indirizzo,cap,telefono,email)
                        VALUES (?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, nome);
                    ps.setString(2, cognome);
                    ps.setString(3, indirizzo);
                    ps.setString(4, cap);
                    ps.setString(5, telefono);
                    ps.setString(6, email);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("ID cliente non generato");
                        idCliente = keys.getLong(1);
                    }
                }

                // 2) inserisco CARTA_CREDITO (nomi colonne come nel tuo SQL!)
                try (PreparedStatement ps2 = c.prepareStatement("""
                        INSERT INTO carta_credito(id_cliente, numero_carta, scadenza, intestatario)
                        VALUES (?,?,?,?)
                        """)) {

                    ps2.setLong(1, idCliente);
                    ps2.setString(2, cartaNumero);
                    ps2.setString(3, cartaScadenza);
                    ps2.setString(4, nome + " " + cognome); // intestatario obbligatorio
                    ps2.executeUpdate();
                }

                c.commit();
                return new ClienteDTO(idCliente, nome, cognome, indirizzo, cap, telefono, email);

            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Crea le credenziali di accesso per un rider esistente.
     *
     * @param idRider id rider
     * @param username username
     * @param password password
     * @throws Exception in caso di errore DB
     */

    public void creaAccountRider(long idRider, String username, String password) throws Exception {
        try (Connection c = conn();
            PreparedStatement ps = c.prepareStatement("""
                INSERT INTO utente_staff(username,password,ruolo,id_panineria,id_rider)
                VALUES(?,?,'RIDER',NULL,?)
            """)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setLong(3, idRider);
            ps.executeUpdate();
        }
    }

    /**
     * Inserisce un rider e crea anche un account di accesso.
     *
     * @param nome nome del rider
     * @param cognome cognome del rider
     * @param telefono telefono del rider
     * @param username username per login
     * @param password password per login
     * @return id del rider creato
     * @throws Exception in caso di errore DB
     */

    public long inserisciRiderConAccount(String nome, String cognome, String telefono,
                                    String username, String password) throws Exception {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                long idRider;

                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO rider(nome,cognome,telefono) VALUES(?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, nome);
                    ps.setString(2, cognome);
                    ps.setString(3, telefono);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); idRider = k.getLong(1); }
                }

                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO utente_staff(username,password,ruolo,id_panineria,id_rider)
                    VALUES(?,?,'RIDER',NULL,?)
                """)) {
                    ps.setString(1, username);
                    ps.setString(2, password);
                    ps.setLong(3, idRider);
                    ps.executeUpdate();
                }

                c.commit();
                return idRider;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }



    /**
     * Registra una nuova panineria e crea le credenziali per il personale associato (gestore/cassiere).
     *
     * @param nome nome della panineria
     * @param citta citta
     * @param via via
     * @param civico numero civico
     * @param cap CAP
     * @param telefono telefono panineria
     * @param email email panineria
     * @param iban IBAN per bonifici
     * @param gestoreNome nome del gestore
     * @param gestoreCognome cognome del gestore
     * @param cassiereUsername username del cassiere
     * @param cassierePassword password del cassiere
     * @return id della panineria creata
     * @throws Exception in caso di errore DB o validazione
     */

    public long registraPanineria(String nome, String citta, String via, String civico, String cap,
                                 String telefono, String email, String iban, String gestoreNome, String gestoreCognome,
                                 String cassiereUsername, String cassierePassword) throws Exception {
        try (Connection c = conn()) {
            long idPanineria;
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO panineria(nome,citta,via,civico,cap,telefono,email,iban,gestore_nome,gestore_cognome)
                VALUES(?,?,?,?,?,?,?,?,?,?)
            """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, nome);
                ps.setString(2, citta);
                ps.setString(3, via);
                ps.setString(4, civico);
                ps.setString(5, cap);
                ps.setString(6, telefono);
                ps.setString(7, email);
                ps.setString(8, iban);
                ps.setString(9, gestoreNome);
                ps.setString(10, gestoreCognome);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    idPanineria = keys.getLong(1);
                }
            }

            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO utente_staff(username,password,ruolo,id_panineria,id_rider)
                VALUES(?,?,?,?,NULL)
            """)) {
                ps.setString(1, cassiereUsername);
                ps.setString(2, cassierePassword);
                ps.setString(3, "CASSIERE");
                ps.setLong(4, idPanineria);
                ps.executeUpdate();
            }

            return idPanineria;
        }
    }

    /**
     * Recupera una panineria dato il suo identificativo.
     *
     * @param idPanineria id panineria
     * @return DTO panineria
     * @throws Exception in caso di errore DB o panineria non trovata
     */
    public PanineriaDTO panineriaById(long idPanineria) throws Exception {
        try (Connection c = conn();
            PreparedStatement ps = c.prepareStatement(
                "SELECT id_panineria, nome FROM panineria WHERE id_panineria=?"
            )) {
            ps.setLong(1, idPanineria);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new PanineriaDTO(rs.getLong(1), rs.getString(2));
            }
        }
    }


    // ===================== LISTE BASE =====================
    /**
     * Restituisce i panini disponibili per una specifica panineria.
     *
     * @return lista di panini
     * @throws Exception in caso di errore DB
     */

    public List<PanineriaDTO> listaPaninerie() throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT id_panineria, nome
                 FROM panineria ORDER BY id_panineria
             """);
             ResultSet rs = ps.executeQuery()) {

            List<PanineriaDTO> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new PanineriaDTO(rs.getLong(1), rs.getString(2)));
            }
            return out;
        }
    }

    /**
     * Restituisce i panini disponibili per una specifica panineria.
     *
     * @param idPanineria id della panineria
     * @return lista di panini
     * @throws Exception in caso di errore DB
     */

    public List<PaninoDTO> listaPanini(long idPanineria) throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT id_panino, id_panineria, nome, descrizione, prezzo
                 FROM panino
                 WHERE id_panineria=?
                 ORDER BY id_panino
             """)) {
            ps.setLong(1, idPanineria);
            try (ResultSet rs = ps.executeQuery()) {
                List<PaninoDTO> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new PaninoDTO(
                            rs.getLong("id_panino"),
                            rs.getLong("id_panineria"),
                            rs.getString("nome"),
                            rs.getString("descrizione"),
                            rs.getBigDecimal("prezzo")
                    ));
                }
                return out;
            }
        }
    }

    /**
     * Restituisce la lista dei clienti registrati.
     *
     * @return lista di clienti
     * @throws Exception in caso di errore DB
     */
    public List<ClienteDTO> listaClienti() throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id_cliente, nome, cognome, indirizzo, cap, telefono, email FROM cliente ORDER BY id_cliente"
            );

             ResultSet rs = ps.executeQuery()) {

            List<ClienteDTO> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new ClienteDTO(
                        rs.getLong("id_cliente"),
                        rs.getString("nome"),
                        rs.getString("cognome"),
                        rs.getString("indirizzo"),
                        rs.getString("cap"),
                        rs.getString("telefono"),
                        rs.getString("email")
                ));
            }
            return out;
        }
    }

    /**
     * Restituisce la lista dei rider registrati.
     *
     * @return lista rider
     * @throws Exception in caso di errore DB
     */

    public List<RiderDTO> listaRider() throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT id_rider, nome, cognome, telefono FROM rider ORDER BY id_rider");
             ResultSet rs = ps.executeQuery()) {

            List<RiderDTO> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new RiderDTO(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
            return out;
        }
    }

    // ===================== UC02: CRUD PANINI =====================
    /**
     * Inserisce un nuovo panino per la panineria indicata.
     *
     * @param idPanineria id panineria
     * @param nome nome del panino
     * @param descrizione descrizione del panino
     * @param prezzo prezzo del panino
     * @return id del panino creato
     * @throws Exception in caso di errore DB o validazione
     */

    public long inserisciPanino(long idPanineria, String nome, String descrizione, BigDecimal prezzo) throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO panino(id_panineria,nome,descrizione,prezzo) VALUES(?,?,?,?)
             """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, idPanineria);
            ps.setString(2, nome);
            ps.setString(3, descrizione);
            ps.setBigDecimal(4, prezzo);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { keys.next(); return keys.getLong(1); }
        }
    }

    /**
     * Modifica i dati di un panino esistente.
     *
     * @param idPanino id del panino
     * @param nome nuovo nome
     * @param descrizione nuova descrizione
     * @param prezzo nuovo prezzo
     * @throws Exception in caso di errore DB o panino non trovato
     */
    public void modificaPanino(long idPanino, String nome, String descrizione, BigDecimal prezzo) throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("""
                 UPDATE panino SET nome=?, descrizione=?, prezzo=? WHERE id_panino=?
             """)) {
            ps.setString(1, nome);
            ps.setString(2, descrizione);
            ps.setBigDecimal(3, prezzo);
            ps.setLong(4, idPanino);
            ps.executeUpdate();
        }
    }

    /**
     * Rimuove un panino dal catalogo.
     *
     * @param idPanino id del panino
     * @throws Exception in caso di errore DB
     */

    public void rimuoviPanino(long idPanino) throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("DELETE FROM panino WHERE id_panino=?")) {
            ps.setLong(1, idPanino);
            ps.executeUpdate();
        }
    }

    // ===================== UC05: EFFETTUARE ORDINE =====================
    /**
     * Effettua un ordine associandolo a cliente e panineria e inserendo le righe d'ordine.
     *
     * @param idCliente id del cliente
     * @param idPanineria id della panineria
     * @param items lista di righe d'ordine (panino, quantita)
     * @return id dell'ordine creato
     * @throws Exception in caso di errore DB o validazione
     */

    public long effettuaOrdine(long idCliente, long idPanineria, List<OrderItemDTO> items) throws Exception {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Carrello vuoto.");

        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                // calcolo totale dal DB (prezzi reali)
                BigDecimal tot = BigDecimal.ZERO;
                Map<Long, BigDecimal> prezzi = new HashMap<>();

                try (PreparedStatement ps = c.prepareStatement("SELECT id_panino, prezzo FROM panino WHERE id_panineria=?")) {
                    ps.setLong(1, idPanineria);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) prezzi.put(rs.getLong(1), rs.getBigDecimal(2));
                    }
                }

                for (OrderItemDTO it : items) {
                    BigDecimal pr = prezzi.get(it.idPanino());
                    if (pr == null) throw new IllegalArgumentException("Panino non valido nel carrello: " + it.idPanino());
                    tot = tot.add(pr.multiply(BigDecimal.valueOf(it.qta())));
                }

                long idOrdine;
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO ordine(id_cliente,id_panineria,ts_creazione,stato,totale)
                    VALUES(?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, idCliente);
                    ps.setLong(2, idPanineria);
                    ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                    ps.setString(4, ST_NUOVO);
                    ps.setBigDecimal(5, tot);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) { keys.next(); idOrdine = keys.getLong(1); }
                }

                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO riga_ordine(id_ordine,id_panino,qta,prezzo_unit) VALUES(?,?,?,?)
                """)) {
                    for (OrderItemDTO it : items) {
                        BigDecimal pr = prezzi.get(it.idPanino());
                        ps.setLong(1, idOrdine);
                        ps.setLong(2, it.idPanino());
                        ps.setInt(3, it.qta());
                        ps.setBigDecimal(4, pr);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Traccia: ignora incasso carta (ma l’ordine viene registrato e "notifica" = appare alla panineria)
                c.commit();
                return idOrdine;

            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    
    // ===================== CASSIERE VEDE SOLO PROPRIO NEGOZIO =====================
    /**
     * Recupera i dettagli di una panineria.
     *
     * @param idPanineria id panineria
     * @return DTO panineria
     * @throws Exception in caso di errore DB o panineria non trovata
     */

    public PanineriaDTO getPanineria(long idPanineria) throws Exception {
        try (Connection c = conn();
            PreparedStatement ps = c.prepareStatement("""
                SELECT id_panineria, nome
                FROM panineria
                WHERE id_panineria=?
            """)) {
            ps.setLong(1, idPanineria);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new PanineriaDTO(rs.getLong(1), rs.getString(2));
            }
        }
    }


    // ===================== UC06: GESTIRE ORDINE (CASSIERE) =====================

    /**
     * Aggiorna lo stato dell'ordine impostandolo come "in cucina".
     *
     * @param idOrdine id ordine
     * @throws Exception in caso di errore DB o transizione non valida
     */

    public void trasmettiInCucina(long idOrdine) throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("UPDATE ordine SET stato=? WHERE id_ordine=?")) {
            ps.setString(1, ST_IN_CUCINA);
            ps.setLong(2, idOrdine);
            ps.executeUpdate();
        }
    }

    /**
     * Annulla un ordine esistente aggiornandone lo stato nel database.
     *
     * @param idOrdine id dell'ordine da annullare
     * @throws Exception in caso di errore DB o stato non coerente
     */
    public void annullaOrdine(long idOrdine) throws Exception {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                // se non hai ancora la tabella pagamento, puoi commentare questa riga
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM pagamento WHERE id_ordine=?")) {
                    ps.setLong(1, idOrdine);
                    ps.executeUpdate();
                } catch (SQLException ignore) {}

                try (PreparedStatement ps = c.prepareStatement("DELETE FROM consegna WHERE id_ordine=?")) {
                    ps.setLong(1, idOrdine);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement("DELETE FROM riga_ordine WHERE id_ordine=?")) {
                    ps.setLong(1, idOrdine);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement("DELETE FROM ordine WHERE id_ordine=?")) {
                    ps.setLong(1, idOrdine);
                    ps.executeUpdate();
                }

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Restituisce gli ordini associati a una panineria.
     *
     * @param idPanineria id panineria
     * @return lista di ordini (come mappe chiave/valore)
     * @throws Exception in caso di errore DB
     */

    public List<Map<String, Object>> ordiniPerPanineria(long idPanineria) throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT o.id_ordine, o.ts_creazione, o.stato, o.totale,
                        cl.nome as cl_nome, cl.cognome as cl_cognome, cl.indirizzo as cl_indirizzo
                 FROM ordine o
                 JOIN cliente cl ON cl.id_cliente = o.id_cliente
                 WHERE o.id_panineria=?
                 ORDER BY o.id_ordine DESC
             """)) {
            ps.setLong(1, idPanineria);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id_ordine", rs.getLong("id_ordine"));
                    m.put("ts_creazione", rs.getTimestamp("ts_creazione"));
                    m.put("stato", rs.getString("stato"));
                    m.put("totale", rs.getBigDecimal("totale"));
                    m.put("cliente", rs.getString("cl_nome") + " " + rs.getString("cl_cognome"));
                    m.put("indirizzo", rs.getString("cl_indirizzo"));
                    out.add(m);
                }
                return out;
            }
        }
    }

    // ===================== UC19: NOTIFICA RIDER (assegnazione + ora invio) =====================

    /**
     * Assegna un rider a un ordine e aggiorna lo stato di consegna.
     *
     * @param idOrdine id ordine
     * @param idRider id rider
     * @return id della consegna creata/aggiornata
     * @throws Exception in caso di errore DB o dati non validi
     */

    public long assegnaRider(long idOrdine, long idRider) throws Exception {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                long idConsegna;
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO consegna(id_ordine,id_rider,ts_invio,ts_consegna) VALUES(?,?,?,NULL)
                """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, idOrdine);
                    ps.setLong(2, idRider);
                    ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) { keys.next(); idConsegna = keys.getLong(1); }
                }

                try (PreparedStatement ps = c.prepareStatement("UPDATE ordine SET stato=? WHERE id_ordine=?")) {
                    ps.setString(1, ST_IN_CONSEGNA);
                    ps.setLong(2, idOrdine);
                    ps.executeUpdate();
                }

                c.commit();
                return idConsegna;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ===================== UC07: CONFERMA CONSEGNA (ora consegna) =====================

    /**
     * Conferma la consegna di un ordine aggiornando gli stati a "consegnato".
     *
     * @param idOrdine id ordine
     * @throws Exception in caso di errore DB
     */

    public void confermaConsegna(long idOrdine) throws Exception {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE consegna SET ts_consegna=? WHERE id_ordine=? AND ts_consegna IS NULL
                """)) {
                    ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                    ps.setLong(2, idOrdine);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement("UPDATE ordine SET stato=? WHERE id_ordine=?")) {
                    ps.setString(1, ST_CONSEGNATO);
                    ps.setLong(2, idOrdine);
                    ps.executeUpdate();
                }

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Restituisce lo stato corrente di un ordine.
     *
     * @param idOrdine id ordine
     * @return stato dell'ordine (stringa)
     * @throws Exception in caso di errore DB
     */

    public String statoOrdine(long idOrdine) throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT stato, totale FROM ordine WHERE id_ordine=?")) {
            ps.setLong(1, idOrdine);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "Ordine non trovato";
                return rs.getString("stato") + " · Totale=" + rs.getBigDecimal("totale");
            }
        }
    }

    // ===================== RIDER: consegne assegnate =====================

    /**
     * Restituisce l'elenco delle consegne assegnate a un rider.
     *
     * @param idRider id rider
     * @return lista consegne (come mappe chiave/valore)
     * @throws Exception in caso di errore DB
     */

    public List<Map<String, Object>> consegnePerRider(long idRider) throws Exception {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT o.id_ordine, o.stato, o.totale,
                        cl.nome as cl_nome, cl.cognome as cl_cognome, cl.indirizzo as cl_indirizzo,
                        co.ts_invio, co.ts_consegna
                 FROM consegna co
                 JOIN ordine o ON o.id_ordine = co.id_ordine
                 JOIN cliente cl ON cl.id_cliente = o.id_cliente
                 WHERE co.id_rider=?
                 ORDER BY co.ts_invio DESC
             """)) {
            ps.setLong(1, idRider);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id_ordine", rs.getLong("id_ordine"));
                    m.put("stato", rs.getString("stato"));
                    m.put("totale", rs.getBigDecimal("totale"));
                    m.put("cliente", rs.getString("cl_nome") + " " + rs.getString("cl_cognome"));
                    m.put("indirizzo", rs.getString("cl_indirizzo"));
                    m.put("ts_invio", rs.getTimestamp("ts_invio"));
                    m.put("ts_consegna", rs.getTimestamp("ts_consegna"));
                    out.add(m);
                }
                return out;
            }
        }
    }

    // ===================== UC03: inserire rider (Gestore società) =====================

    /**
     * Inserisce un nuovo rider nel sistema.
     *
     * @param nome nome del rider
     * @param cognome cognome del rider
     * @param telefono telefono del rider
     * @return id del rider creato
     * @throws Exception in caso di errore DB
     */

    public long inserisciRider(String nome, String cognome, String telefono) throws Exception {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                long idRider;

                // 1) inserisco il rider
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO rider(nome,cognome,telefono) VALUES(?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, nome);
                    ps.setString(2, cognome);
                    ps.setString(3, telefono);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("ID rider non generato");
                        idRider = keys.getLong(1);
                    }
                }

                // 2) creo account login automatico in utente_staff
                String username = "rider" + idRider;
                String password = "rider123"; // password demo

                try (PreparedStatement ps2 = c.prepareStatement("""
                    INSERT INTO utente_staff(username,password,ruolo,id_panineria,id_rider)
                    VALUES(?,?,'RIDER',NULL,?)
                """)) {
                    ps2.setString(1, username);
                    ps2.setString(2, password);
                    ps2.setLong(3, idRider);
                    ps2.executeUpdate();
                }

                c.commit();
                return idRider;

            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }


    // ===================== Elimina rider (Gestore società) =====================

    /**
     * Elimina un rider dal sistema.
     *
     * @param idRider id rider
     * @throws Exception in caso di errore DB
     */

    public void eliminaRider(long idRider) throws Exception {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                // 1) controllo FK: esistono consegne per questo rider?
                try (PreparedStatement chk = c.prepareStatement(
                        "SELECT COUNT(*) FROM consegna WHERE id_rider=?")) {
                    chk.setLong(1, idRider);
                    try (ResultSet rs = chk.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) {
                            throw new IllegalStateException(
                                    "Impossibile eliminare: il rider ha consegne associate. " +
                                    "Prima rimuovi/chiudi le consegne o usa 'disattiva rider'."
                            );
                        }
                    }
                }

                // 2) cancello account login (se esiste)
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM utente_staff WHERE ruolo='RIDER' AND id_rider=?")) {
                    ps.setLong(1, idRider);
                    ps.executeUpdate();
                }

                // 3) cancello rider
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM rider WHERE id_rider=?")) {
                    ps.setLong(1, idRider);
                    ps.executeUpdate();
                }

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }


    // ===================== REPORT (calcolo “on demand”) =====================

    /**
     * Genera un report dei bonifici per mese/anno.
     *
     * @param anno anno di riferimento
     * @param mese mese di riferimento
     * @return lista di record di report (come mappe)
     * @throws Exception in caso di errore DB
     */

    public List<Map<String, Object>> reportBonifici(int anno, int mese) throws Exception {
        LocalDate from = YearMonth.of(anno, mese).atDay(1);
        LocalDate to = YearMonth.of(anno, mese).atEndOfMonth().plusDays(1);

        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT p.id_panineria, p.nome,
                        COALESCE(SUM(o.totale),0) AS importo
                 FROM panineria p
                 LEFT JOIN ordine o ON o.id_panineria = p.id_panineria
                     AND o.stato = ?
                     AND o.ts_creazione >= ? AND o.ts_creazione < ?
                 GROUP BY p.id_panineria, p.nome
                 ORDER BY p.id_panineria
             """)) {
            ps.setString(1, ST_CONSEGNATO);
            ps.setTimestamp(2, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(3, Timestamp.valueOf(to.atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id_panineria", rs.getLong("id_panineria"));
                    m.put("panineria", rs.getString("nome"));
                    m.put("importo", rs.getBigDecimal("importo"));
                    out.add(m);
                }
                return out;
            }
        }
    }

    /**
     * Restituisce la lista dei bonifici (o movimenti) di una panineria in un mese.
     *
     * @param idPanineria id della panineria
     * @param anno anno di riferimento
     * @param mese mese di riferimento (1-12)
     * @return lista di record (mappa chiave/valore) relativi ai bonifici del periodo
     * @throws Exception in caso di errore DB o parametri non validi
     */

    public List<Map<String, Object>> reportBonificiPanineria(long idPanineria, int anno, int mese) throws Exception {
        LocalDate from = YearMonth.of(anno, mese).atDay(1);
        LocalDate to = YearMonth.of(anno, mese).atEndOfMonth().plusDays(1);

        try (Connection c = conn();
            PreparedStatement ps = c.prepareStatement("""
                SELECT p.id_panineria, p.nome,
                        COALESCE(SUM(o.totale),0) AS importo
                FROM panineria p
                LEFT JOIN ordine o ON o.id_panineria = p.id_panineria
                    AND o.stato = ?
                    AND o.ts_creazione >= ? AND o.ts_creazione < ?
                WHERE p.id_panineria = ?
                GROUP BY p.id_panineria, p.nome
            """)) {

            ps.setString(1, ST_CONSEGNATO);
            ps.setTimestamp(2, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(3, Timestamp.valueOf(to.atStartOfDay()));
            ps.setLong(4, idPanineria);

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id_panineria", rs.getLong("id_panineria"));
                    m.put("panineria", rs.getString("nome"));
                    m.put("importo", rs.getBigDecimal("importo"));
                    out.add(m);
                }
                return out;
            }
        }
    }


    /**
     * Genera il report statistico mensile per una panineria.
     *
     * @param idPanineria id della panineria di cui calcolare le statistiche
     * @param anno anno di riferimento (es. 2026)
     * @param mese mese di riferimento (1-12)
     * @return mappa con i valori statistici calcolati
     * @throws Exception in caso di errore DB o parametri non validi
     */

    public Map<String, Object> reportStatistichePanineria(long idPanineria, int anno, int mese) throws Exception {
        LocalDate from = YearMonth.of(anno, mese).atDay(1);
        LocalDate to = YearMonth.of(anno, mese).atEndOfMonth().plusDays(1);

        try (Connection c = conn();
            PreparedStatement ps = c.prepareStatement("""
                SELECT
                COUNT(DISTINCT o.id_ordine) AS num_ordini,
                AVG(DATEDIFF('MINUTE', co.ts_invio, co.ts_consegna)) AS tempo_medio_min
                FROM ordine o
                LEFT JOIN consegna co ON co.id_ordine = o.id_ordine
                WHERE o.stato = ?
                AND o.id_panineria = ?
                AND o.ts_creazione >= ? AND o.ts_creazione < ?
            """)) {

            ps.setString(1, ST_CONSEGNATO);
            ps.setLong(2, idPanineria);
            ps.setTimestamp(3, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(4, Timestamp.valueOf(to.atStartOfDay()));

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("num_ordini", rs.getLong("num_ordini"));
                m.put("tempo_medio_min", rs.getObject("tempo_medio_min"));
                return m;
            }
        }
    }


}
