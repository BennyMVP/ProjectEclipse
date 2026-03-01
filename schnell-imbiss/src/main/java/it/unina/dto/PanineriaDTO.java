package it.unina.dto;

/**
 * DTO della panineria.
 *
 * @param idPanineria identificativo della panineria
 * @param nome        nome della panineria
 */

public record PanineriaDTO(long idPanineria, String nome) {
    @Override public String toString() { return nome + " (#" + idPanineria + ")"; }
}
