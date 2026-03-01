package it.unina.model;

/**
 * Stati possibili di un ordine nel ciclo di vita.
 */
public enum StatoOrdine {
    /** Ordine creato/inserito nel sistema. */
    CREATO,
    /** Ordine affidato alla consegna. */
    IN_CONSEGNA,
    /** Ordine consegnato al cliente. */
    CONSEGNATO
}

