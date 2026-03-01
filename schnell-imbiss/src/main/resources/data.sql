-- Paninerie
INSERT INTO panineria
(nome, citta, via, civico, cap, telefono, email, iban, gestore_nome, gestore_cognome)
VALUES
('Schnell Imbiss', 'Napoli', 'Via Roma', '10', '80100', '0810000001', 'schnell@imbiss.it',
 'IT60X0542811101000000123456', 'Mario', 'Rossi'),
('Panini Express', 'Napoli', 'Via Milano', '22', '80100', '0810000002', 'express@panini.it',
 'IT60X0542811101000000654321', 'Luigi', 'Bianchi');
-- Clienti
INSERT INTO cliente (nome, cognome, indirizzo, cap, telefono, email)
VALUES
('Francesco', 'Beneduce', 'Corso Umberto 1', '80100', '3331112222', 'francesco@mail.it'),
('Anna', 'Verdi', 'Via Toledo 5', '80100', '3339998888', 'anna@mail.it');

-- Carte (token finto)
INSERT INTO carta_credito (id_cliente, numero_carta, scadenza, intestatario)
VALUES
(1, 'tok_1111111111', '12/30', 'Francesco Beneduce'),
(2, 'tok_2222222222', '08/31', 'Anna Verdi');

-- Rider
INSERT INTO rider (nome, cognome, telefono)
VALUES
('Paolo', 'Neri', '3330001111');

-- Panini (per panineria 1 e 2)
INSERT INTO panino (id_panineria, nome, descrizione, prezzo)
VALUES
(1, 'Hamburger Classic', 'Hamburger, insalata, pomodoro', 6.50),
(1, 'Chicken Burger', 'Pollo, salsa yogurt', 7.00),
(2, 'Kebab', 'Kebab, cipolla, salsa piccante', 6.00),
(2, 'Veggie', 'Verdure grigliate', 5.50);
