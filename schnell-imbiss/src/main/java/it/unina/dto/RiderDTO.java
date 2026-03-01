package it.unina.dto;

/**
 * DTO del rider.
 *
 * @param idRider   identificativo del rider
 * @param nome      nome del rider
 * @param cognome   cognome del rider
 * @param telefono  recapito telefonico
 */

public record RiderDTO(long idRider, String nome, String cognome, String telefono) {
    @Override public String toString() { return nome + " " + cognome + " (#" + idRider + ")"; }
}
