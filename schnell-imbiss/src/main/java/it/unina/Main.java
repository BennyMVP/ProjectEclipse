package it.unina;

import it.unina.control.GestioneOrdiniController;
import it.unina.dto.OrderItemDTO;

import java.util.List;

/** Costruttore privato: entry point non istanziabile. */
public class Main {
    
    /** Costruttore privato: entry point non istanziabile. */
    private Main() {}

    /**
     * Entry point dell'applicazione.
     *
     * @param args argomenti da riga di comando (non utilizzati)
     * @throws Exception in caso di errore durante l'avvio
     */

    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:file:./db/schnell";
        var controller = new GestioneOrdiniController(url, "sa", "");

        long idOrdine = controller.effettuaOrdine(
                1, 1,
                List.of(new OrderItemDTO(1, 2), new OrderItemDTO(2, 1))
        );
        System.out.println("Ordine creato ✅ idOrdine=" + idOrdine);

        long idConsegna = controller.assegnaRider(idOrdine, 1);
        System.out.println("Rider assegnato ✅ idConsegna=" + idConsegna);

        controller.confermaConsegna(idOrdine);
        System.out.println("Consegna confermata ✅ ordine=" + idOrdine);
    }
}
