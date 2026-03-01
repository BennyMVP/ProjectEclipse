package it.unina.dto;
/**
 * DTO di una riga d'ordine.
 *
 * @param idPanino identificativo del panino
 * @param qta quantita richiesta
 */

public record OrderItemDTO(long idPanino, int qta) {}
