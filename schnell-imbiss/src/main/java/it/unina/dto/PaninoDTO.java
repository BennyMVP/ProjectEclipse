package it.unina.dto;

import java.math.BigDecimal;

/**
 * DTO del panino.
 *
 * @param idPanino     identificativo del panino
 * @param idPanineria  panineria proprietaria
 * @param nome         nome del panino
 * @param descrizione  descrizione del panino
 * @param prezzo       prezzo del panino
 */

public record PaninoDTO(
        long idPanino,
        long idPanineria,
        String nome,
        String descrizione,
        BigDecimal prezzo
) {
    @Override public String toString() { return nome + " - " + prezzo; }
}
