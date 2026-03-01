package it.unina.dto;
/**
 * DTO del cliente utilizzato per trasferire i dati tra GUI e logica applicativa.
 *
 * @param idCliente identificativo univoco del cliente
 * @param nome nome del cliente
 * @param cognome cognome del cliente
 * @param indirizzo indirizzo di consegna
 * @param cap CAP dell'indirizzo
 * @param telefono numero di telefono
 * @param email email del cliente
 */
public record ClienteDTO(
        long idCliente,
        String nome,
        String cognome,
        String indirizzo,
        String cap,
        String telefono,
        String email
) {
    @Override
    public String toString() {
        return idCliente + " - " + nome + " " + cognome + " (" + telefono + ")";
    }
}
