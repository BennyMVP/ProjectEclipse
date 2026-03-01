-- =========================================================
-- Schnell Imbiss - Schema DB (H2)
-- =========================================================


DROP TABLE IF EXISTS pagamento;     
DROP TABLE IF EXISTS utente_staff;
DROP TABLE IF EXISTS consegna;
DROP TABLE IF EXISTS riga_ordine;
DROP TABLE IF EXISTS ordine;
DROP TABLE IF EXISTS panino;
DROP TABLE IF EXISTS carta_credito;
DROP TABLE IF EXISTS rider;
DROP TABLE IF EXISTS panineria;
DROP TABLE IF EXISTS cliente;


CREATE TABLE IF NOT EXISTS cliente (
  id_cliente BIGINT AUTO_INCREMENT PRIMARY KEY,
  nome VARCHAR(60) NOT NULL,
  cognome VARCHAR(60) NOT NULL,
  indirizzo VARCHAR(200) NOT NULL,
  cap VARCHAR(10) NOT NULL,
  telefono VARCHAR(20) NOT NULL,
  email VARCHAR(120) NOT NULL
);


CREATE TABLE IF NOT EXISTS carta_credito (
  id_carta BIGINT AUTO_INCREMENT PRIMARY KEY,
  id_cliente BIGINT NOT NULL UNIQUE,
  numero_carta VARCHAR(19) NOT NULL,
  scadenza VARCHAR(5) NOT NULL,
  intestatario VARCHAR(100) NOT NULL,
  CONSTRAINT fk_cc_cliente FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)
);


CREATE TABLE IF NOT EXISTS panineria (
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
);

CREATE TABLE IF NOT EXISTS panino (
  id_panino BIGINT AUTO_INCREMENT PRIMARY KEY,
  id_panineria BIGINT NOT NULL,
  nome VARCHAR(100) NOT NULL,
  descrizione VARCHAR(500) NOT NULL,
  prezzo DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_panino_panineria FOREIGN KEY (id_panineria) REFERENCES panineria(id_panineria)
);


CREATE TABLE IF NOT EXISTS rider (
  id_rider BIGINT AUTO_INCREMENT PRIMARY KEY,
  nome VARCHAR(60) NOT NULL,
  cognome VARCHAR(60) NOT NULL,
  telefono VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS ordine (
  id_ordine BIGINT AUTO_INCREMENT PRIMARY KEY,
  id_cliente BIGINT NOT NULL,
  id_panineria BIGINT NOT NULL,
  ts_creazione TIMESTAMP NOT NULL,
  stato VARCHAR(30) NOT NULL,
  totale DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_ordine_cliente FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente),
  CONSTRAINT fk_ordine_panineria FOREIGN KEY (id_panineria) REFERENCES panineria(id_panineria)
);

CREATE TABLE IF NOT EXISTS riga_ordine (
  id_ordine BIGINT NOT NULL,
  id_panino BIGINT NOT NULL,
  qta INT NOT NULL,
  prezzo_unit DECIMAL(10,2) NOT NULL,
  PRIMARY KEY(id_ordine, id_panino),
  CONSTRAINT fk_riga_ordine_ordine FOREIGN KEY (id_ordine) REFERENCES ordine(id_ordine),
  CONSTRAINT fk_riga_ordine_panino FOREIGN KEY (id_panino) REFERENCES panino(id_panino)
);

CREATE TABLE IF NOT EXISTS consegna (
  id_consegna BIGINT AUTO_INCREMENT PRIMARY KEY,
  id_ordine BIGINT NOT NULL,
  id_rider BIGINT NOT NULL,
  ts_invio TIMESTAMP NOT NULL,
  ts_consegna TIMESTAMP,
  CONSTRAINT fk_consegna_ordine FOREIGN KEY (id_ordine) REFERENCES ordine(id_ordine),
  CONSTRAINT fk_consegna_rider FOREIGN KEY (id_rider) REFERENCES rider(id_rider)
);

CREATE TABLE IF NOT EXISTS utente_staff (
  username VARCHAR(60) PRIMARY KEY,
  password VARCHAR(60) NOT NULL,
  ruolo VARCHAR(20) NOT NULL,
  id_panineria BIGINT,
  id_rider BIGINT
);

-- 0..1 consegna per ordine (una sola consegna per id_ordine)
ALTER TABLE consegna
ADD CONSTRAINT uq_consegna_ordine UNIQUE (id_ordine);

-- collegamenti dello staff a panineria e rider
ALTER TABLE utente_staff
ADD CONSTRAINT fk_staff_panineria
FOREIGN KEY (id_panineria) REFERENCES panineria(id_panineria);

ALTER TABLE utente_staff
ADD CONSTRAINT fk_staff_rider
FOREIGN KEY (id_rider) REFERENCES rider(id_rider);

