package it.unina.gui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLightLaf;
import it.unina.control.GestioneOrdiniController;
import it.unina.dto.*;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Interfaccia grafica Swing dell'applicazione Schnell Imbiss.
 * Gestisce login, catalogo, carrello, ordini, rider e report.
 */

public class AppGui {

    /** Costruttore privato: classe non istanziabile (solo metodi statici). */
    private AppGui() {}


    // ====== format ======
    private static final Locale LOCALE_IT = Locale.ITALY;
    private static final NumberFormat EUR = NumberFormat.getCurrencyInstance(LOCALE_IT);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ====== ruoli ======
    enum Ruolo { CLIENTE, CASSIERE, RIDER, GESTORE }

    static class Session {
        Ruolo ruolo;
        ClienteDTO cliente; // solo cliente
        GestioneOrdiniController.StaffSession staff; // solo staff

        String displayName() {
            if (ruolo == Ruolo.CLIENTE && cliente != null) return cliente.nome() + " " + cliente.cognome();
            if (staff != null) return staff.username();
            return "(utente)";
        }

        Long idPanineria() { return staff == null ? null : staff.idPanineria(); }
        Long idRider() { return staff == null ? null : staff.idRider(); }
    }

    // ====== carrello ======
    static class CartRow {
        long idPanino;
        String nome;
        BigDecimal prezzo;
        int qta;

        CartRow(long idPanino, String nome, BigDecimal prezzo, int qta) {
            this.idPanino = idPanino;
            this.nome = nome;
            this.prezzo = prezzo;
            this.qta = qta;
        }

        BigDecimal subtotale() {
            return prezzo.multiply(BigDecimal.valueOf(qta));
        }
    }

    static class CartTableModel extends AbstractTableModel {
        private final List<CartRow> rows = new ArrayList<>();
        private final String[] cols = {"ID", "Panino", "Prezzo", "Qta", "Subtotale"};

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int column) { return cols[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CartRow r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.idPanino;
                case 1 -> r.nome;
                case 2 -> r.prezzo;
                case 3 -> r.qta;
                case 4 -> r.subtotale();
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> Long.class;
                case 2, 4 -> BigDecimal.class;
                case 3 -> Integer.class;
                default -> String.class;
            };
        }

        public void addOrIncrement(PaninoDTO p, int qta) {
            for (CartRow r : rows) {
                if (r.idPanino == p.idPanino()) {
                    r.qta += qta;
                    fireTableDataChanged();
                    return;
                }
            }
            rows.add(new CartRow(p.idPanino(), p.nome(), p.prezzo(), qta));
            fireTableDataChanged();
        }

        public void removeAt(int idx) {
            if (idx >= 0 && idx < rows.size()) {
                rows.remove(idx);
                fireTableDataChanged();
            }
        }

        public void clear() {
            rows.clear();
            fireTableDataChanged();
        }

        public BigDecimal totale() {
            BigDecimal t = BigDecimal.ZERO;
            for (CartRow r : rows) t = t.add(r.subtotale());
            return t;
        }

        public boolean isEmpty() { return rows.isEmpty(); }

        public List<OrderItemDTO> toOrderItems() {
            List<OrderItemDTO> items = new ArrayList<>();
            for (CartRow r : rows) items.add(new OrderItemDTO(r.idPanino, r.qta));
            return items;
        }
    }

    // ====== panini ======
    static class PaniniTableModel extends AbstractTableModel {
        private final String[] cols = {"ID", "Nome", "Prezzo"};
        private List<PaninoDTO> all = new ArrayList<>();
        private List<PaninoDTO> filtered = new ArrayList<>();

        public void setData(List<PaninoDTO> data) {
            this.all = (data == null) ? new ArrayList<>() : new ArrayList<>(data);
            this.filtered = new ArrayList<>(this.all);
            fireTableDataChanged();
        }

        public void applyFilter(String text) {
            String q = (text == null) ? "" : text.trim().toLowerCase(Locale.ROOT);
            if (q.isEmpty()) {
                filtered = new ArrayList<>(all);
            } else {
                List<PaninoDTO> out = new ArrayList<>();
                for (PaninoDTO p : all) {
                    String name = (p.nome() == null) ? "" : p.nome().toLowerCase(Locale.ROOT);
                    if (name.contains(q) || String.valueOf(p.idPanino()).contains(q)) out.add(p);
                }
                filtered = out;
            }
            fireTableDataChanged();
        }

        public PaninoDTO getAt(int viewRow, JTable table) {
            if (viewRow < 0) return null;
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow < 0 || modelRow >= filtered.size()) return null;
            return filtered.get(modelRow);
        }

        @Override public int getRowCount() { return filtered.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int column) { return cols[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PaninoDTO p = filtered.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> p.idPanino();
                case 1 -> p.nome();
                case 2 -> p.prezzo();
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> Long.class;
                case 2 -> BigDecimal.class;
                default -> String.class;
            };
        }
    }

    // ====== table model generico per Map<String,Object> ======
    static class MapTableModel extends AbstractTableModel {
        private final List<String> cols = new ArrayList<>();
        private final List<String> headers = new ArrayList<>();
        private List<Map<String, Object>> rows = new ArrayList<>();

        public MapTableModel(String[] colKeys, String[] colHeaders) {
            cols.addAll(Arrays.asList(colKeys));
            headers.addAll(Arrays.asList(colHeaders));
        }

        public void setRows(List<Map<String, Object>> rows) {
            this.rows = (rows == null) ? new ArrayList<>() : new ArrayList<>(rows);
            fireTableDataChanged();
        }

        public Map<String, Object> getRow(int viewRow, JTable table) {
            if (viewRow < 0) return null;
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow < 0 || modelRow >= rows.size()) return null;
            return rows.get(modelRow);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.size(); }
        @Override public String getColumnName(int col) { return headers.get(col); }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            return rows.get(rowIndex).get(cols.get(columnIndex));
        }
    }

    // ====== helpers ======
    private static void err(Component parent, Exception e) {
        JOptionPane.showMessageDialog(parent, e.getMessage(), "Errore", JOptionPane.ERROR_MESSAGE);
    }

    private static void info(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void onlyLong(JTextField tf) {
        ((AbstractDocument) tf.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override public void insertString(FilterBypass fb, int off, String str, AttributeSet a) throws BadLocationException {
                if (str != null && str.matches("\\d+")) super.insertString(fb, off, str, a);
            }
            @Override public void replace(FilterBypass fb, int off, int len, String str, AttributeSet a) throws BadLocationException {
                if (str != null && str.matches("\\d*")) super.replace(fb, off, len, str, a);
            }
        });
    }

    private static String moneyLabel(BigDecimal total) {
        return EUR.format(total == null ? BigDecimal.ZERO : total);
    }

    private static JPanel card(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout(12, 12));

        // SOLO proprietà sicure per la tua versione
        p.putClientProperty(FlatClientProperties.STYLE,
                "arc:16; background:#FFFFFF");

        // bordo + padding fatti con Swing (compatibile sempre)
        p.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE6E8EF), 1, true),
                new CompoundBorder(
                        new EmptyBorder(12, 12, 12, 12),
                        new TitledBorder(new EmptyBorder(0, 0, 0, 0), title)
                )
        ));

        p.add(content, BorderLayout.CENTER);
        return p;
    }


    private static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc:14; font:+1; background:#2F6FED; foreground:#FFFFFF; hoverBackground:#295FD0; pressedBackground:#214FAF");
        b.setFocusPainted(false);
        return b;
    }

    private static JButton subtleButton(String text) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc:14; background:#F3F5FA; hoverBackground:#E9EDF7; pressedBackground:#DDE3F4");
        b.setFocusPainted(false);
        return b;
    }

    private static void styleTable(JTable t) {
        t.setRowHeight(34);
        t.setFillsViewportHeight(true);
        t.setAutoCreateRowSorter(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.putClientProperty(FlatClientProperties.STYLE, "height:34; font:+1; background:#F6F7FB;");

        // currency
        t.setDefaultRenderer(BigDecimal.class, new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                setHorizontalAlignment(SwingConstants.RIGHT);
                setText(value instanceof BigDecimal bd ? moneyLabel(bd) : "");
            }
        });

        // timestamp
        t.setDefaultRenderer(Timestamp.class, new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                setHorizontalAlignment(SwingConstants.LEFT);
                if (value instanceof Timestamp ts) {
                    setText(TS_FMT.format(ts.toLocalDateTime()));
                } else setText("");
            }
        });

        // numeric right align
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        t.setDefaultRenderer(Long.class, right);
        t.setDefaultRenderer(Integer.class, right);

        // zebra
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground((row % 2 == 0) ? Color.WHITE : new Color(248, 249, 252));
                }
                return c;
            }
        });
    }

    private static void setColWidth(JTable t, int col, int pref, int min, int max) {
        TableColumn tc = t.getColumnModel().getColumn(col);
        tc.setPreferredWidth(pref);
        tc.setMinWidth(min);
        tc.setMaxWidth(max);
    }


    // ====== main ======
    /**
     * Avvio della GUI.
     *
     * @param args argomenti da riga di comando (non utilizzati)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                FlatLightLaf.setup();
                UIManager.put("Component.arc", 14);
                UIManager.put("Button.arc", 14);
                UIManager.put("TextComponent.arc", 14);
                UIManager.put("ScrollBar.showButtons", true);

                start();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, e.getMessage(), "Errore avvio", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void start() throws Exception {
        String url = "jdbc:h2:file:./db/schnell";
        GestioneOrdiniController controller = new GestioneOrdiniController(url, "sa", "");

        JFrame f = new JFrame("Schnell Imbiss");
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setSize(1280, 780);
        f.setLocationRelativeTo(null);

        // ===== Top bar =====
        JLabel appTitle = new JLabel("Schnell Imbiss");
        appTitle.setFont(appTitle.getFont().deriveFont(Font.BOLD, 18f));

        JLabel appSubtitle = new JLabel("Ordini, carrello e consegne");
        appSubtitle.setForeground(new Color(110, 110, 110));

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);
        titleBox.add(appTitle);
        titleBox.add(appSubtitle);

        JButton btnGlobalRefresh = subtleButton("Ricarica");

        JLabel lblUser = new JLabel("Non autenticato");
        lblUser.setForeground(new Color(50, 50, 50));
        JButton btnLogout = subtleButton("Logout");
        btnLogout.setVisible(false);

        JPanel userChip = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        userChip.setOpaque(true);
        userChip.setBackground(new Color(245, 246, 250));
        userChip.setBorder(new EmptyBorder(2, 10, 2, 10));
        userChip.putClientProperty(FlatClientProperties.STYLE, "arc:14;");
        userChip.add(lblUser);
        userChip.add(btnLogout);

        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightTop.setOpaque(false);
        rightTop.add(btnGlobalRefresh);
        rightTop.add(userChip);

        JPanel topBar = new JPanel(new BorderLayout(10, 10));
        topBar.setBorder(new EmptyBorder(14, 14, 10, 14));
        topBar.add(titleBox, BorderLayout.WEST);
        topBar.add(rightTop, BorderLayout.EAST);

        // ===== Status bar =====
        JLabel status = new JLabel("Pronto.");
        status.setBorder(new EmptyBorder(8, 14, 10, 14));
        status.setForeground(new Color(90, 90, 90));

        // ===== Session =====
        Session[] currentSession = new Session[] { null };

        // ===== Tabs container =====
        JTabbedPane tabs = new JTabbedPane();
        tabs.putClientProperty(FlatClientProperties.STYLE, "tabHeight:36;");

        // ===================== TAB: CATALOGO + CARRELLO =====================
        CartTableModel cartModel = new CartTableModel();
        PaniniTableModel paniniModel = new PaniniTableModel();
        final long[] cartPanineriaId = new long[] { -1L }; // vincolo no-mix

        DefaultListModel<PanineriaDTO> paninerieModel = new DefaultListModel<>();
        JList<PanineriaDTO> listPaninerie = new JList<>(paninerieModel);
        listPaninerie.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listPaninerie.setFixedCellHeight(62);
        listPaninerie.putClientProperty(FlatClientProperties.STYLE, "selectionArc:14; selectionInsets:6,6,6,6;");

        listPaninerie.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PanineriaDTO p) {
                    l.setText("<html><b>" + p.nome() + "</b><br><span style='color:#666'>ID #" + p.idPanineria() + "</span></html>");
                }
                l.setBorder(new EmptyBorder(10, 12, 10, 12));
                l.setOpaque(true);
                if (isSelected) {
                    l.setBackground(new Color(47, 111, 237));
                    l.setForeground(Color.WHITE);
                } else {
                    l.setBackground(new Color(245, 246, 250));
                    l.setForeground(new Color(30, 30, 30));
                }
                l.putClientProperty(FlatClientProperties.STYLE, "arc:14;");
                return l;
            }
        });

        JScrollPane spPaninerie = new JScrollPane(listPaninerie);
        spPaninerie.putClientProperty(FlatClientProperties.STYLE, "arc:14;");

        JTextField txtSearchPanini = new JTextField();
        txtSearchPanini.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Cerca panino per nome o ID...");
        txtSearchPanini.putClientProperty(FlatClientProperties.STYLE, "arc:14;");

        JTable tablePanini = new JTable(paniniModel);
        styleTable(tablePanini);
        setColWidth(tablePanini, 0, 80, 60, 110);
        setColWidth(tablePanini, 2, 140, 120, 180);

        // box ingredienti/descrizione
        JLabel lblIngTitle = new JLabel("Seleziona un panino per vedere ingredienti/descrizione");
        lblIngTitle.setFont(lblIngTitle.getFont().deriveFont(Font.BOLD, 13f));

        JTextArea txtIng = new JTextArea(5, 20);
        txtIng.setEditable(false);
        txtIng.setLineWrap(true);
        txtIng.setWrapStyleWord(true);
        txtIng.setText("Nessun panino selezionato.");
        JScrollPane spIng = new JScrollPane(txtIng);
        spIng.putClientProperty(FlatClientProperties.STYLE, "arc:14;");

        JPanel ingBox = new JPanel(new BorderLayout(8, 8));
        ingBox.setOpaque(false);
        ingBox.add(lblIngTitle, BorderLayout.NORTH);
        ingBox.add(spIng, BorderLayout.CENTER);

        JPanel ingCard = card("Ingredienti / Descrizione", ingBox);
        ingCard.setPreferredSize(new Dimension(200, 170));

        tablePanini.getSelectionModel().addListSelectionListener(ev -> {
            if (ev.getValueIsAdjusting()) return;
            PaninoDTO p = paniniModel.getAt(tablePanini.getSelectedRow(), tablePanini);
            if (p == null) {
                lblIngTitle.setText("Seleziona un panino per vedere ingredienti/descrizione");
                txtIng.setText("Nessun panino selezionato.");
                return;
            }
            lblIngTitle.setText(p.nome() + " (ID #" + p.idPanino() + ")");
            String d = p.descrizione() == null ? "" : p.descrizione().trim();
            if (d.contains(",")) {
                StringBuilder sb = new StringBuilder();
                for (String part : d.split(",")) {
                    String t = part.trim();
                    if (!t.isEmpty()) sb.append("• ").append(t).append("\n");
                }
                txtIng.setText(sb.toString().trim());
            } else {
                txtIng.setText(d.isEmpty() ? "(nessuna descrizione)" : d);
            }
            txtIng.setCaretPosition(0);
        });

        JTable tableCart = new JTable(cartModel);
        styleTable(tableCart);
        setColWidth(tableCart, 0, 70, 60, 100);
        setColWidth(tableCart, 2, 140, 120, 180);
        setColWidth(tableCart, 3, 70, 60, 100);
        setColWidth(tableCart, 4, 160, 130, 220);

        JLabel lblTotale = new JLabel(moneyLabel(cartModel.totale()));
        lblTotale.setFont(lblTotale.getFont().deriveFont(Font.BOLD, 20f));

        JLabel lblTotaleHint = new JLabel("Totale carrello");
        lblTotaleHint.setForeground(new Color(120, 120, 120));

        JPanel totalBox = new JPanel();
        totalBox.setOpaque(false);
        totalBox.setLayout(new BoxLayout(totalBox, BoxLayout.Y_AXIS));
        totalBox.add(lblTotaleHint);
        totalBox.add(lblTotale);

        JSpinner spQta = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
        ((JComponent) spQta.getEditor()).putClientProperty(FlatClientProperties.STYLE, "arc:14;");

        JButton btnAdd = primaryButton("Aggiungi");
        JButton btnRemove = subtleButton("Rimuovi");
        JButton btnClear = subtleButton("Svuota");

        JPanel cartActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        cartActions.setOpaque(false);
        cartActions.add(new JLabel("Quantità"));
        cartActions.add(spQta);
        cartActions.add(btnAdd);
        cartActions.add(Box.createHorizontalStrut(8));
        cartActions.add(btnRemove);
        cartActions.add(btnClear);

        JPanel cartFooter = new JPanel(new BorderLayout());
        cartFooter.setOpaque(false);
        cartFooter.add(totalBox, BorderLayout.WEST);

        JPanel cartBlock = new JPanel(new BorderLayout(10, 10));
        cartBlock.setOpaque(false);
        cartBlock.add(cartActions, BorderLayout.NORTH);
        cartBlock.add(new JScrollPane(tableCart), BorderLayout.CENTER);
        cartBlock.add(cartFooter, BorderLayout.SOUTH);

        JPanel paniniBlock = new JPanel(new BorderLayout(10, 10));
        paniniBlock.setOpaque(false);
        paniniBlock.add(txtSearchPanini, BorderLayout.NORTH);

        JPanel paniniCenter = new JPanel(new BorderLayout(10, 10));
        paniniCenter.setOpaque(false);
        paniniCenter.add(new JScrollPane(tablePanini), BorderLayout.CENTER);
        paniniCenter.add(ingCard, BorderLayout.SOUTH);

        paniniBlock.add(paniniCenter, BorderLayout.CENTER);

        JPanel sidebar = new JPanel(new BorderLayout(10, 10));
        sidebar.setOpaque(false);
        sidebar.add(spPaninerie, BorderLayout.CENTER);

        JSplitPane splitMain = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                card("Paninerie", sidebar),
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                        card("Panini", paniniBlock),
                        card("Carrello", cartBlock)
                )
        );
        splitMain.setBorder(new EmptyBorder(14, 14, 14, 14));
        splitMain.setResizeWeight(0.22);
        ((JSplitPane) splitMain.getRightComponent()).setResizeWeight(0.55);

        JPanel tabCatalogo = new JPanel(new BorderLayout());
        tabCatalogo.add(splitMain, BorderLayout.CENTER);

        // ===================== TAB: ORDINE (CLIENTE) =====================
        DefaultComboBoxModel<ClienteDTO> clientiModel = new DefaultComboBoxModel<>();
        JComboBox<ClienteDTO> comboClienti = new JComboBox<>(clientiModel);
        comboClienti.putClientProperty(FlatClientProperties.STYLE, "arc:14;");

        JLabel lblPanineriaSel = new JLabel("Panineria: (seleziona dal Catalogo)");
        lblPanineriaSel.setForeground(new Color(90, 90, 90));

        JButton btnCreaOrdine = primaryButton("Conferma ordine");

        JTable tableCart2 = new JTable(cartModel);
        styleTable(tableCart2);
        setColWidth(tableCart2, 0, 70, 60, 100);
        setColWidth(tableCart2, 2, 140, 120, 180);
        setColWidth(tableCart2, 3, 70, 60, 100);
        setColWidth(tableCart2, 4, 160, 130, 220);

        JPanel ordineForm = new JPanel(new GridBagLayout());
        ordineForm.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0;
        ordineForm.add(new JLabel("Cliente"), gc);
        gc.gridx = 1; gc.gridy = 0; gc.weightx = 1;
        ordineForm.add(comboClienti, gc);

        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
        ordineForm.add(new JLabel("Panineria"), gc);
        gc.gridx = 1; gc.gridy = 1; gc.weightx = 1;
        ordineForm.add(lblPanineriaSel, gc);

        JLabel lblTotaleOrdine = new JLabel(moneyLabel(cartModel.totale()));
        lblTotaleOrdine.setFont(lblTotaleOrdine.getFont().deriveFont(Font.BOLD, 16f));

        JPanel ordineActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        ordineActions.setOpaque(false);
        ordineActions.add(btnCreaOrdine);
        ordineActions.add(Box.createHorizontalStrut(12));
        ordineActions.add(new JLabel("Totale:"));
        ordineActions.add(lblTotaleOrdine);

        JPanel tabOrdine = new JPanel(new BorderLayout(12, 12));
        tabOrdine.setBorder(new EmptyBorder(14, 14, 14, 14));
        tabOrdine.add(card("Dati ordine", ordineForm), BorderLayout.NORTH);
        tabOrdine.add(card("Carrello", new JScrollPane(tableCart2)), BorderLayout.CENTER);
        tabOrdine.add(card("Azioni", ordineActions), BorderLayout.SOUTH);

        // ===================== TAB: ORDINI CASSIERE =====================
        MapTableModel ordiniModel = new MapTableModel(
                new String[]{"id_ordine","ts_creazione","stato","totale","cliente","indirizzo"},
                new String[]{"ID Ordine","Creato","Stato","Totale","Cliente","Indirizzo"}
        );
        JTable tableOrdini = new JTable(ordiniModel);
        styleTable(tableOrdini);
        setColWidth(tableOrdini, 0, 90, 70, 120);
        setColWidth(tableOrdini, 3, 140, 120, 180);

        DefaultComboBoxModel<RiderDTO> riderModel = new DefaultComboBoxModel<>();
        JComboBox<RiderDTO> comboRider = new JComboBox<>(riderModel);
        comboRider.putClientProperty(FlatClientProperties.STYLE, "arc:14;");

        JButton btnInCucina = subtleButton("Metti in cucina");
        JButton btnAssegnaRider = primaryButton("Assegna rider (invio)");
        JButton btnRefreshOrdini = subtleButton("Ricarica ordini");

        JLabel lblOrdiniHint = new JLabel("Seleziona un ordine dalla tabella.");
        lblOrdiniHint.setForeground(new Color(110,110,110));

        JPanel ordiniActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        ordiniActions.setOpaque(false);
        ordiniActions.add(btnRefreshOrdini);
        ordiniActions.add(btnInCucina);
        ordiniActions.add(new JLabel("Rider:"));
        ordiniActions.add(comboRider);
        ordiniActions.add(btnAssegnaRider);

        JPanel tabOrdiniCassiere = new JPanel(new BorderLayout(10,10));
        tabOrdiniCassiere.setBorder(new EmptyBorder(14,14,14,14));
        tabOrdiniCassiere.add(card("Azioni", ordiniActions), BorderLayout.NORTH);
        tabOrdiniCassiere.add(card("Ordini ricevuti", new JScrollPane(tableOrdini)), BorderLayout.CENTER);
        tabOrdiniCassiere.add(lblOrdiniHint, BorderLayout.SOUTH);

        // ===================== TAB: GESTIONE PANINI (CRUD) - CASSIERE =====================
        PaniniTableModel paniniCrudModel = new PaniniTableModel();
        JTable tablePaniniCrud = new JTable(paniniCrudModel);
        styleTable(tablePaniniCrud);
        setColWidth(tablePaniniCrud, 0, 80, 60, 110);
        setColWidth(tablePaniniCrud, 2, 140, 120, 180);

        JTextField tfPNome = new JTextField();
        JTextArea tfPDescr = new JTextArea(4, 20);
        tfPDescr.setLineWrap(true);
        tfPDescr.setWrapStyleWord(true);
        JScrollPane spPDescr = new JScrollPane(tfPDescr);

        JTextField tfPPrezzo = new JTextField();
        tfPNome.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Nome panino");
        tfPPrezzo.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Prezzo (es. 7.50)");

        JButton btnPanAdd = primaryButton("Aggiungi");
        JButton btnPanUpd = subtleButton("Modifica");
        JButton btnPanDel = subtleButton("Elimina");
        JButton btnPanReload = subtleButton("Ricarica");

        JPanel panForm = new JPanel(new GridBagLayout());
        panForm.setOpaque(false);
        GridBagConstraints pg = new GridBagConstraints();
        pg.insets = new Insets(6,6,6,6);
        pg.fill = GridBagConstraints.HORIZONTAL;
        pg.gridx = 0; pg.gridy = 0; pg.weightx = 0;
        panForm.add(new JLabel("Nome"), pg);
        pg.gridx = 1; pg.gridy = 0; pg.weightx = 1;
        panForm.add(tfPNome, pg);

        pg.gridx = 0; pg.gridy = 1; pg.weightx = 0;
        panForm.add(new JLabel("Descrizione (ingredienti)"), pg);
        pg.gridx = 1; pg.gridy = 1; pg.weightx = 1;
        panForm.add(spPDescr, pg);

        pg.gridx = 0; pg.gridy = 2; pg.weightx = 0;
        panForm.add(new JLabel("Prezzo"), pg);
        pg.gridx = 1; pg.gridy = 2; pg.weightx = 1;
        panForm.add(tfPPrezzo, pg);

        JPanel panActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panActions.setOpaque(false);
        panActions.add(btnPanReload);
        panActions.add(btnPanAdd);
        panActions.add(btnPanUpd);
        panActions.add(btnPanDel);

        JPanel tabGestionePanini = new JPanel(new BorderLayout(10,10));
        tabGestionePanini.setBorder(new EmptyBorder(14,14,14,14));
        tabGestionePanini.add(card("Azioni", panActions), BorderLayout.NORTH);
        tabGestionePanini.add(card("Panini (della tua panineria)", new JScrollPane(tablePaniniCrud)), BorderLayout.CENTER);
        tabGestionePanini.add(card("Dettagli panino", panForm), BorderLayout.SOUTH);

        // click su panino CRUD -> riempi form
        tablePaniniCrud.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            PaninoDTO p = paniniCrudModel.getAt(tablePaniniCrud.getSelectedRow(), tablePaniniCrud);
            if (p == null) return;
            tfPNome.setText(p.nome());
            tfPDescr.setText(p.descrizione());
            tfPPrezzo.setText(p.prezzo() == null ? "" : p.prezzo().toPlainString());
        });

        // ===================== TAB: RIDER (le mie consegne) =====================
        MapTableModel consegneModel = new MapTableModel(
                new String[]{"id_ordine","stato","totale","cliente","indirizzo","ts_invio","ts_consegna"},
                new String[]{"ID Ordine","Stato","Totale","Cliente","Indirizzo","Inviato","Consegnato"}
        );
        JTable tableConsegne = new JTable(consegneModel);
        styleTable(tableConsegne);
        setColWidth(tableConsegne, 0, 90, 70, 120);
        setColWidth(tableConsegne, 2, 140, 120, 180);

        JButton btnRiderRefresh = subtleButton("Ricarica consegne");
        JButton btnRiderConferma = primaryButton("Conferma consegna");
        JPanel riderActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        riderActions.setOpaque(false);
        riderActions.add(btnRiderRefresh);
        riderActions.add(btnRiderConferma);

        JPanel tabRider = new JPanel(new BorderLayout(10,10));
        tabRider.setBorder(new EmptyBorder(14,14,14,14));
        tabRider.add(card("Azioni", riderActions), BorderLayout.NORTH);
        tabRider.add(card("Le mie consegne", new JScrollPane(tableConsegne)), BorderLayout.CENTER);

        // ===================== TAB: GESTORE (rider + report) =====================
        // Gestione rider
        JTextField tfRNome = new JTextField();
        JTextField tfRCognome = new JTextField();
        JTextField tfRTel = new JTextField();
        onlyLong(tfRTel);

        JButton btnRAdd = primaryButton("Aggiungi rider");
        JButton btnRReload = subtleButton("Ricarica rider");
        JButton btnRDel = subtleButton("Elimina rider");
        


        JTable tableRiderAll = new JTable(new DefaultTableModel(new Object[]{"ID","Nome","Cognome","Telefono"}, 0));
        styleTable(tableRiderAll);

        JPanel riderForm = new JPanel(new GridLayout(0,1,8,8));
        riderForm.setOpaque(false);
        riderForm.add(new JLabel("Nome")); riderForm.add(tfRNome);
        riderForm.add(new JLabel("Cognome")); riderForm.add(tfRCognome);
        riderForm.add(new JLabel("Telefono")); riderForm.add(tfRTel);

        JPanel riderFormActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        riderFormActions.setOpaque(false);
        riderFormActions.add(btnRReload);
        riderFormActions.add(btnRAdd);
        riderFormActions.add(btnRDel);

        JPanel tabGestioneRider = new JPanel(new BorderLayout(10,10));
        tabGestioneRider.setBorder(new EmptyBorder(14,14,14,14));
        tabGestioneRider.add(card("Azioni", riderFormActions), BorderLayout.NORTH);
        tabGestioneRider.add(card("Elenco rider", new JScrollPane(tableRiderAll)), BorderLayout.CENTER);
        tabGestioneRider.add(card("Inserimento rider", riderForm), BorderLayout.SOUTH);

        // Report
        int nowY = YearMonth.now().getYear();
        int nowM = YearMonth.now().getMonthValue();

        JComboBox<Integer> cbAnno = new JComboBox<>();
        for (int y = nowY - 3; y <= nowY + 1; y++) cbAnno.addItem(y);
        cbAnno.setSelectedItem(nowY);

        JComboBox<Integer> cbMese = new JComboBox<>();
        for (int m = 1; m <= 12; m++) cbMese.addItem(m);
        cbMese.setSelectedItem(nowM);

        JButton btnBonifici = primaryButton("Report bonifici");
        JButton btnStats = subtleButton("Statistiche mese");

        JTable tableReport = new JTable(new DefaultTableModel(new Object[]{"Campo","Valore"}, 0));
        styleTable(tableReport);

        JTextArea taStats = new JTextArea(6, 20);
        taStats.setEditable(false);
        taStats.setLineWrap(true);
        taStats.setWrapStyleWord(true);

        JPanel repTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        repTop.setOpaque(false);
        repTop.add(new JLabel("Anno"));
        repTop.add(cbAnno);
        repTop.add(new JLabel("Mese"));
        repTop.add(cbMese);
        repTop.add(btnBonifici);
        repTop.add(btnStats);

        JPanel tabReport = new JPanel(new BorderLayout(10,10));
        tabReport.setBorder(new EmptyBorder(14,14,14,14));
        tabReport.add(card("Selezione", repTop), BorderLayout.NORTH);
        tabReport.add(card("Bonifici / Dati", new JScrollPane(tableReport)), BorderLayout.CENTER);
        tabReport.add(card("Statistiche", new JScrollPane(taStats)), BorderLayout.SOUTH);

        // ===== refresh base =====
        Runnable refreshPaninerie = () -> {
            try {
                paninerieModel.clear();

                Session s = currentSession[0];
                if (s != null && s.ruolo == Ruolo.CASSIERE && s.idPanineria() != null) {
                    PanineriaDTO only = controller.panineriaById(s.idPanineria());
                    if (only != null) paninerieModel.addElement(only);
                } else {
                    for (PanineriaDTO p : controller.listaPaninerie())
                        paninerieModel.addElement(p);
                }

                status.setText("Paninerie aggiornate.");
            } catch (Exception e) {
                err(f, e);
                status.setText("Errore aggiornando paninerie.");
            }
        };




        Runnable refreshClienti = () -> {
            try {
                clientiModel.removeAllElements();
                for (ClienteDTO c : controller.listaClienti()) clientiModel.addElement(c);
                status.setText("Clienti aggiornati.");
            } catch (Exception e) {
                err(f, e);
                status.setText("Errore aggiornando clienti.");
            }
        };

        Runnable refreshRiderCombo = () -> {
            try {
                riderModel.removeAllElements();
                for (RiderDTO r : controller.listaRider()) riderModel.addElement(r);
                status.setText("Rider aggiornati.");
            } catch (Exception e) {
                err(f, e);
                status.setText("Errore aggiornando rider.");
            }
        };

        Runnable refreshTotals = () -> {
            String tot = moneyLabel(cartModel.totale());
            lblTotale.setText(tot);
            lblTotaleOrdine.setText(tot);
        };

        // ===== interactions catalogo =====
        listPaninerie.addListSelectionListener(ev -> {
            if (ev.getValueIsAdjusting()) return;

            PanineriaDTO sel = listPaninerie.getSelectedValue();
            if (sel == null) {
                paniniModel.setData(Collections.emptyList());
                lblPanineriaSel.setText("Panineria: (seleziona dal Catalogo)");
                status.setText("Seleziona una panineria.");
                return;
            }

            // vincolo no-mix per cliente (carrello)
            if (!cartModel.isEmpty() && cartPanineriaId[0] != -1L && cartPanineriaId[0] != sel.idPanineria()) {
                int choice = JOptionPane.showConfirmDialog(
                        f,
                        "Hai già un carrello per un'altra panineria.\nVuoi svuotare il carrello e passare a questa panineria?",
                        "Cambio panineria",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (choice == JOptionPane.YES_OPTION) {
                    cartModel.clear();
                    cartPanineriaId[0] = -1L;
                    refreshTotals.run();
                    status.setText("Carrello svuotato. Puoi cambiare panineria.");
                } else {
                    for (int i = 0; i < paninerieModel.size(); i++) {
                        PanineriaDTO p = paninerieModel.get(i);
                        if (p.idPanineria() == cartPanineriaId[0]) {
                            listPaninerie.setSelectedIndex(i);
                            break;
                        }
                    }
                    return;
                }
            }

            lblPanineriaSel.setText("Panineria: " + sel.nome() + "   ·   #" + sel.idPanineria());
            try {
                paniniModel.setData(controller.listaPanini(sel.idPanineria()));
                paniniModel.applyFilter(txtSearchPanini.getText());
                status.setText("Caricati panini di: " + sel.nome());
            } catch (Exception e) {
                err(f, e);
                status.setText("Errore caricando panini.");
            }
        });

        txtSearchPanini.getDocument().addDocumentListener(new DocumentListener() {
            void update() { paniniModel.applyFilter(txtSearchPanini.getText()); }
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        });

        btnAdd.addActionListener(a -> {
            try {
                if (currentSession[0] == null || currentSession[0].ruolo != Ruolo.CLIENTE) {
                    info(f, "Solo il Cliente può aggiungere al carrello.");
                    return;
                }

                PanineriaDTO selPan = listPaninerie.getSelectedValue();
                if (selPan == null) { info(f, "Seleziona una panineria."); return; }

                if (cartPanineriaId[0] == -1L) cartPanineriaId[0] = selPan.idPanineria();
                if (cartPanineriaId[0] != selPan.idPanineria()) {
                    info(f, "Non puoi mischiare panini di paninerie diverse.\nSvuota il carrello per cambiare panineria.");
                    return;
                }

                PaninoDTO p = paniniModel.getAt(tablePanini.getSelectedRow(), tablePanini);
                if (p == null) { info(f, "Seleziona un panino."); return; }

                int qta = (Integer) spQta.getValue();
                cartModel.addOrIncrement(p, qta);
                refreshTotals.run();
                status.setText("Aggiunto: " + p.nome() + " x" + qta);
            } catch (Exception e) {
                err(f, e);
                status.setText("Errore aggiungendo al carrello.");
            }
        });

        btnRemove.addActionListener(a -> {
            if (currentSession[0] == null || currentSession[0].ruolo != Ruolo.CLIENTE) {
                info(f, "Solo il Cliente può modificare il carrello.");
                return;
            }
            int viewRow = tableCart.getSelectedRow();
            if (viewRow < 0) { info(f, "Seleziona una riga del carrello."); return; }
            int modelRow = tableCart.convertRowIndexToModel(viewRow);
            cartModel.removeAt(modelRow);

            if (cartModel.isEmpty()) cartPanineriaId[0] = -1L;

            refreshTotals.run();
            status.setText("Rimosso dal carrello.");
        });

        btnClear.addActionListener(a -> {
            if (currentSession[0] == null || currentSession[0].ruolo != Ruolo.CLIENTE) {
                info(f, "Solo il Cliente può svuotare il carrello.");
                return;
            }
            if (cartModel.isEmpty()) return;
            cartModel.clear();
            cartPanineriaId[0] = -1L;
            refreshTotals.run();
            status.setText("Carrello svuotato.");
        });

        btnCreaOrdine.addActionListener(a -> {
            try {
                if (currentSession[0] == null || currentSession[0].ruolo != Ruolo.CLIENTE) {
                    info(f, "Solo il Cliente può confermare l'ordine.");
                    return;
                }

                ClienteDTO cl = (ClienteDTO) comboClienti.getSelectedItem();
                PanineriaDTO pan = listPaninerie.getSelectedValue();

                if (cl == null) { info(f, "Cliente non valido."); return; }
                if (pan == null) { info(f, "Seleziona una panineria nel Catalogo."); return; }
                if (cartModel.isEmpty()) { info(f, "Carrello vuoto."); return; }

                if (cartPanineriaId[0] != -1L && cartPanineriaId[0] != pan.idPanineria()) {
                    info(f, "Il carrello appartiene a un'altra panineria. Svuota il carrello.");
                    return;
                }

                // 1) creo ordine
                long idOrdine = controller.effettuaOrdine(cl.idCliente(), pan.idPanineria(), cartModel.toOrderItems());

                // 2) chiedo CVV (simulazione pagamento)
                JPasswordField pf = new JPasswordField();
                int ok = JOptionPane.showConfirmDialog(
                        f,
                        new Object[]{ "Inserisci CVV (demo: 123)", pf },
                        "Pagamento con carta",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );

                if (ok != JOptionPane.OK_OPTION) {
                    // se annulla il pagamento, annullo anche l’ordine appena creato (opzionale ma pulito)
                    controller.annullaOrdine(idOrdine);
                    status.setText("Ordine annullato (pagamento non effettuato).");
                    return;
                }

                String cvv = new String(pf.getPassword()).trim();

                // 3) eseguo pagamento (metodo nel controller)
                controller.pagaOrdineCarta(idOrdine, cl.idCliente(), cvv);

                // 4) se arrivo qui, pagamento OK
                JOptionPane.showMessageDialog(
                        f,
                        "Ordine creato e pagato! ID = " + idOrdine,
                        "OK",
                        JOptionPane.INFORMATION_MESSAGE
                );

                cartModel.clear();
                cartPanineriaId[0] = -1L;
                refreshTotals.run();
                status.setText("Ordine pagato: " + idOrdine);

            } catch (Exception e) {
                err(f, e);
                status.setText("Errore creando/pagando ordine.");
            }
        });


        // ===== CASSIERE: ordini =====
        Runnable refreshOrdiniCassiere = () -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.CASSIERE) return;
                Long idPan = s.idPanineria();
                if (idPan == null) { ordiniModel.setRows(Collections.emptyList()); return; }
                ordiniModel.setRows(controller.ordiniPerPanineria(idPan));
                status.setText("Ordini aggiornati.");
            } catch (Exception e) {
                err(f, e);
                status.setText("Errore aggiornando ordini.");
            }
        };

        btnRefreshOrdini.addActionListener(e -> refreshOrdiniCassiere.run());

        btnInCucina.addActionListener(e -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.CASSIERE) return;
                Map<String, Object> row = ordiniModel.getRow(tableOrdini.getSelectedRow(), tableOrdini);
                if (row == null) { info(f, "Seleziona un ordine."); return; }
                long idOrd = ((Number) row.get("id_ordine")).longValue();
                controller.trasmettiInCucina(idOrd);
                status.setText("Ordine " + idOrd + " -> IN_CUCINA");
                refreshOrdiniCassiere.run();
            } catch (Exception ex) {
                err(f, ex);
            }
        });

        btnAssegnaRider.addActionListener(e -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.CASSIERE) return;

                Map<String, Object> row = ordiniModel.getRow(tableOrdini.getSelectedRow(), tableOrdini);
                if (row == null) { info(f, "Seleziona un ordine."); return; }
                long idOrd = ((Number) row.get("id_ordine")).longValue();

                RiderDTO r = (RiderDTO) comboRider.getSelectedItem();
                if (r == null) { info(f, "Seleziona un rider."); return; }

                long idConsegna = controller.assegnaRider(idOrd, r.idRider());
                JOptionPane.showMessageDialog(f, "Rider assegnato. ID Consegna = " + idConsegna, "OK", JOptionPane.INFORMATION_MESSAGE);
                status.setText("Assegnato rider per ordine " + idOrd);
                refreshOrdiniCassiere.run();
            } catch (Exception ex) {
                err(f, ex);
            }
        });

        // ===== CASSIERE: CRUD panini =====
        Runnable refreshPaniniCrud = () -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.CASSIERE) return;
                Long idPan = s.idPanineria();
                if (idPan == null) { paniniCrudModel.setData(Collections.emptyList()); return; }
                paniniCrudModel.setData(controller.listaPanini(idPan));
                status.setText("Panini (CRUD) aggiornati.");
            } catch (Exception ex) {
                err(f, ex);
            }
        };

        btnPanReload.addActionListener(e -> refreshPaniniCrud.run());

        btnPanAdd.addActionListener(e -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.CASSIERE) return;
                Long idPan = s.idPanineria();
                if (idPan == null) { info(f, "Panineria non associata al cassiere."); return; }

                String nome = tfPNome.getText().trim();
                String descr = tfPDescr.getText().trim();
                String prezzoS = tfPPrezzo.getText().trim();

                if (nome.isEmpty() || descr.isEmpty() || prezzoS.isEmpty()) { info(f, "Compila Nome, Descrizione e Prezzo."); return; }
                BigDecimal prezzo = new BigDecimal(prezzoS);

                long idPanino = controller.inserisciPanino(idPan, nome, descr, prezzo);
                status.setText("Panino inserito: ID " + idPanino);
                refreshPaniniCrud.run();
            } catch (Exception ex) {
                err(f, ex);
            }
        });

        btnPanUpd.addActionListener(e -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.CASSIERE) return;

                PaninoDTO sel = paniniCrudModel.getAt(tablePaniniCrud.getSelectedRow(), tablePaniniCrud);
                if (sel == null) { info(f, "Seleziona un panino da modificare."); return; }

                String nome = tfPNome.getText().trim();
                String descr = tfPDescr.getText().trim();
                String prezzoS = tfPPrezzo.getText().trim();

                if (nome.isEmpty() || descr.isEmpty() || prezzoS.isEmpty()) { info(f, "Compila Nome, Descrizione e Prezzo."); return; }
                BigDecimal prezzo = new BigDecimal(prezzoS);

                controller.modificaPanino(sel.idPanino(), nome, descr, prezzo);
                status.setText("Panino modificato: ID " + sel.idPanino());
                refreshPaniniCrud.run();
            } catch (Exception ex) {
                err(f, ex);
            }
        });

        btnPanDel.addActionListener(e -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.CASSIERE) return;

                PaninoDTO sel = paniniCrudModel.getAt(tablePaniniCrud.getSelectedRow(), tablePaniniCrud);
                if (sel == null) { info(f, "Seleziona un panino da eliminare."); return; }

                int ok = JOptionPane.showConfirmDialog(f, "Eliminare panino ID " + sel.idPanino() + "?", "Conferma", JOptionPane.YES_NO_OPTION);
                if (ok != JOptionPane.YES_OPTION) return;

                controller.rimuoviPanino(sel.idPanino());
                status.setText("Panino eliminato: ID " + sel.idPanino());
                refreshPaniniCrud.run();
            } catch (Exception ex) {
                err(f, ex);
            }
        });

        // ===== RIDER: consegne =====
        Runnable refreshConsegneRider = () -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.RIDER) return;
                Long idR = s.idRider();
                if (idR == null) { consegneModel.setRows(Collections.emptyList()); return; }
                consegneModel.setRows(controller.consegnePerRider(idR));
                status.setText("Consegne aggiornate.");
            } catch (Exception ex) {
                err(f, ex);
            }
        };

        btnRiderRefresh.addActionListener(e -> refreshConsegneRider.run());

        btnRiderConferma.addActionListener(e -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.RIDER) return;

                Map<String, Object> row = consegneModel.getRow(tableConsegne.getSelectedRow(), tableConsegne);
                if (row == null) { info(f, "Seleziona una consegna."); return; }

                long idOrd = ((Number) row.get("id_ordine")).longValue();
                controller.confermaConsegna(idOrd);
                status.setText("Consegna confermata per ordine " + idOrd);
                refreshConsegneRider.run();
            } catch (Exception ex) {
                err(f, ex);
            }
        });

        // ===== GESTORE: rider + report =====
        Runnable refreshRiderTable = () -> {
            try {
                DefaultTableModel m = (DefaultTableModel) tableRiderAll.getModel();
                m.setRowCount(0);
                for (RiderDTO r : controller.listaRider()) {
                    m.addRow(new Object[]{r.idRider(), r.nome(), r.cognome(), r.telefono()});
                }
                status.setText("Elenco rider aggiornato.");
            } catch (Exception ex) {
                err(f, ex);
            }
        };

        btnRReload.addActionListener(e -> refreshRiderTable.run());

        btnRAdd.addActionListener(e -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.GESTORE) return;

                String nome = tfRNome.getText().trim();
                String cogn = tfRCognome.getText().trim();
                String tel = tfRTel.getText().trim();
                if (nome.isEmpty() || cogn.isEmpty() || tel.isEmpty()) { info(f, "Compila nome, cognome e telefono."); return; }

                long id = controller.inserisciRider(nome, cogn, tel);
                JOptionPane.showMessageDialog(f,
                    "Rider creato! ID=" + id +
                    "\nCredenziali login:" +
                    "\nusername: rider" + id +
                    "\npassword: rider123",
                    "OK",
                    JOptionPane.INFORMATION_MESSAGE
                );

                status.setText("Rider inserito: ID " + id);
                tfRNome.setText(""); tfRCognome.setText(""); tfRTel.setText("");
                refreshRiderTable.run();
                refreshRiderCombo.run();
            } catch (Exception ex) {
                err(f, ex);
            }
        });

        
        btnRDel.addActionListener(e -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.GESTORE) return;

                int viewRow = tableRiderAll.getSelectedRow();
                if (viewRow < 0) { info(f, "Seleziona un rider dalla tabella."); return; }

                int modelRow = tableRiderAll.convertRowIndexToModel(viewRow);
                long idR = ((Number) tableRiderAll.getModel().getValueAt(modelRow, 0)).longValue();

                int ok = JOptionPane.showConfirmDialog(
                        f,
                        "Eliminare il rider ID " + idR + "?\n(Verrà eliminato anche l'account di login)",
                        "Conferma",
                        JOptionPane.YES_NO_OPTION
                );
                if (ok != JOptionPane.YES_OPTION) return;

                controller.eliminaRider(idR);

                status.setText("Rider eliminato: ID " + idR);
                refreshRiderTable.run();
                refreshRiderCombo.run();

            } catch (Exception ex) {
                err(f, ex);
            }
        });


        btnBonifici.addActionListener(e -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.CASSIERE) return;

                Long idPan = s.idPanineria();
                if (idPan == null) { info(f, "Panineria non associata al cassiere."); return; }

                int anno = (Integer) cbAnno.getSelectedItem();
                int mese = (Integer) cbMese.getSelectedItem();

                List<Map<String,Object>> rows = controller.reportBonificiPanineria(idPan, anno, mese);

                DefaultTableModel m = (DefaultTableModel) tableReport.getModel();
                m.setRowCount(0);
                for (Map<String,Object> r : rows) {
                    m.addRow(new Object[]{
                        "Panineria #" + r.get("id_panineria") + " - " + r.get("panineria"),
                        moneyLabel((BigDecimal) r.get("importo"))
                    });
                }

                taStats.setText("Report bonifici (solo tua panineria) generato.");
                status.setText("Report bonifici pronto.");
            } catch (Exception ex) {
                err(f, ex);
            }
        });


        btnStats.addActionListener(e -> {
            try {
                Session s = currentSession[0];
                if (s == null || s.ruolo != Ruolo.GESTORE) return;

                PanineriaDTO panSel = listPaninerie.getSelectedValue();
                if (panSel == null) {
                    info(f, "Seleziona una panineria dal Catalogo per generare le statistiche.");
                    return;
                }

                int anno = (Integer) cbAnno.getSelectedItem();
                int mese = (Integer) cbMese.getSelectedItem();

                Map<String,Object> st = controller.reportStatistichePanineria(panSel.idPanineria(), anno, mese);

                Object tempo = st.get("tempo_medio_min");
                String tempoStr = (tempo == null) ? "N/D" : String.valueOf(tempo);

                taStats.setText(
                    "Statistiche " + panSel.nome() + " (ID #" + panSel.idPanineria() + ") " + String.format("%02d/%d", mese, anno) + "\n\n" +
                    "Numero ordini consegnati: " + st.get("num_ordini") + "\n" +
                    "Tempo medio consegna (min): " + tempoStr
                );
                status.setText("Statistiche aggiornate.");
            } catch (Exception ex) {
                err(f, ex);
            }
        });




        // ===== refresh button globale: cambierà in base al ruolo =====
        Runnable[] refreshAll = new Runnable[] { () -> {} };
        btnGlobalRefresh.addActionListener(e -> refreshAll[0].run());

        // ====== costruzione APP root ======
        JPanel appRoot = new JPanel(new BorderLayout());
        appRoot.add(topBar, BorderLayout.NORTH);
        appRoot.add(tabs, BorderLayout.CENTER);
        appRoot.add(status, BorderLayout.SOUTH);

        // ====== LOGIN (CardLayout) ======
        CardLayout cardsLayout = new CardLayout();
        JPanel cards = new JPanel(cardsLayout);

        // LOGIN UI
        JTabbedPane loginTabs = new JTabbedPane();

        // --- Cliente tab ---
        JTextField tfNome = new JTextField();
        JTextField tfCognome = new JTextField();
        JTextField tfTel = new JTextField();
        onlyLong(tfTel);

        JButton btnLoginCliente = primaryButton("Accedi");
        JButton btnRegCliente = subtleButton("Registrati");

        JPanel loginCliente = new JPanel(new GridBagLayout());
        loginCliente.setBorder(new EmptyBorder(20,20,20,20));
        GridBagConstraints lc = new GridBagConstraints();
        lc.insets = new Insets(8,8,8,8);
        lc.fill = GridBagConstraints.HORIZONTAL;
        lc.gridx = 0; lc.gridy = 0; lc.gridwidth = 2;
        JLabel tC = new JLabel("Login Cliente");
        tC.setFont(tC.getFont().deriveFont(Font.BOLD, 18f));
        loginCliente.add(tC, lc);

        lc.gridy++; lc.gridwidth = 1;
        loginCliente.add(new JLabel("Nome"), lc);
        lc.gridx = 1;
        loginCliente.add(tfNome, lc);

        lc.gridx = 0; lc.gridy++;
        loginCliente.add(new JLabel("Cognome"), lc);
        lc.gridx = 1;
        loginCliente.add(tfCognome, lc);

        lc.gridx = 0; lc.gridy++;
        loginCliente.add(new JLabel("Telefono"), lc);
        lc.gridx = 1;
        loginCliente.add(tfTel, lc);

        lc.gridx = 0; lc.gridy++; lc.gridwidth = 2;
        JPanel actC = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actC.setOpaque(false);
        actC.add(btnLoginCliente);
        actC.add(btnRegCliente);
        loginCliente.add(actC, lc);

        JLabel hintC = new JLabel("Cliente demo: Benny Rossi - 3330009991");
        hintC.setForeground(new Color(120,120,120));
        lc.gridy++;
        loginCliente.add(hintC, lc);

        // --- Staff tab ---
        JTextField tfUser = new JTextField();
        JPasswordField tfPass = new JPasswordField();
        JButton btnLoginStaff = primaryButton("Accedi Staff");

        JPanel loginStaff = new JPanel(new GridBagLayout());
        loginStaff.setBorder(new EmptyBorder(20,20,20,20));
        GridBagConstraints ls = new GridBagConstraints();
        ls.insets = new Insets(8,8,8,8);
        ls.fill = GridBagConstraints.HORIZONTAL;

        ls.gridx = 0; ls.gridy = 0; ls.gridwidth = 2;
        JLabel tS = new JLabel("Login Staff");
        tS.setFont(tS.getFont().deriveFont(Font.BOLD, 18f));
        loginStaff.add(tS, ls);

        ls.gridy++; ls.gridwidth = 1; ls.gridx = 0;
        loginStaff.add(new JLabel("Username"), ls);
        ls.gridx = 1;
        loginStaff.add(tfUser, ls);

        ls.gridx = 0; ls.gridy++;
        loginStaff.add(new JLabel("Password"), ls);
        ls.gridx = 1;
        loginStaff.add(tfPass, ls);

        ls.gridx = 0; ls.gridy++; ls.gridwidth = 2;
        loginStaff.add(btnLoginStaff, ls);

        JLabel hintS = new JLabel("Demo: gestore/gestore123 · cassiere1/cassiere123");
        hintS.setForeground(new Color(120,120,120));
        ls.gridy++;
        loginStaff.add(hintS, ls);

        // --- Rider tab ---
        JTextField tfUserR = new JTextField();
        JPasswordField tfPassR = new JPasswordField();
        JButton btnLoginRider = primaryButton("Accedi Rider");

        JPanel loginRider = new JPanel(new GridBagLayout());
        loginRider.setBorder(new EmptyBorder(20,20,20,20));
        GridBagConstraints lr = new GridBagConstraints();
        lr.insets = new Insets(8,8,8,8);
        lr.fill = GridBagConstraints.HORIZONTAL;

        lr.gridx = 0; lr.gridy = 0; lr.gridwidth = 2;
        JLabel tR = new JLabel("Login Rider");
        tR.setFont(tR.getFont().deriveFont(Font.BOLD, 18f));
        loginRider.add(tR, lr);

        lr.gridy++; lr.gridwidth = 1; lr.gridx = 0;
        loginRider.add(new JLabel("Username"), lr);
        lr.gridx = 1;
        loginRider.add(tfUserR, lr);

        lr.gridx = 0; lr.gridy++;
        loginRider.add(new JLabel("Password"), lr);
        lr.gridx = 1;
        loginRider.add(tfPassR, lr);

        lr.gridx = 0; lr.gridy++; lr.gridwidth = 2;
        loginRider.add(btnLoginRider, lr);

        JLabel hintR = new JLabel("Demo: rider1/rider123 (o rider<ID>/rider123)");
        hintR.setForeground(new Color(120,120,120));
        lr.gridy++;
        loginRider.add(hintR, lr);


        // --- Registra Panineria tab (UC01) ---
        JTextField pNome = new JTextField();
        JTextField pCitta = new JTextField();
        JTextField pVia = new JTextField();
        JTextField pCivico = new JTextField();
        JTextField pCap = new JTextField();
        JTextField pTel = new JTextField();
        onlyLong(pTel);
        JTextField pEmail = new JTextField();
        JTextField pIban = new JTextField();
        JTextField pGestN = new JTextField();
        JTextField pGestC = new JTextField();
        JTextField pCassUser = new JTextField();
        JPasswordField pCassPass = new JPasswordField();
        JButton btnRegPan = primaryButton("Registra Panineria");

        JPanel regPan = new JPanel(new GridBagLayout());
        regPan.setBorder(new EmptyBorder(20,20,20,20));
        GridBagConstraints rp = new GridBagConstraints();
        rp.insets = new Insets(6,6,6,6);
        rp.fill = GridBagConstraints.HORIZONTAL;

        rp.gridx = 0; rp.gridy = 0; rp.gridwidth = 2;
        JLabel tP = new JLabel("Registrazione Panineria (crea anche un Cassiere)");
        tP.setFont(tP.getFont().deriveFont(Font.BOLD, 16f));
        regPan.add(tP, rp);

        rp.gridwidth = 1;
        String[] labels = {
                "Nome", "Città", "Via", "Civico", "CAP", "Telefono", "Email", "IBAN",
                "Nome Gestore", "Cognome Gestore", "Username Cassiere", "Password Cassiere"
        };
        JComponent[] fields = {
                pNome, pCitta, pVia, pCivico, pCap, pTel, pEmail, pIban, pGestN, pGestC, pCassUser, pCassPass
        };

        for (int i = 0; i < labels.length; i++) {
            rp.gridx = 0; rp.gridy++;
            regPan.add(new JLabel(labels[i]), rp);
            rp.gridx = 1;
            regPan.add(fields[i], rp);
        }

        rp.gridx = 0; rp.gridy++; rp.gridwidth = 2;
        regPan.add(btnRegPan, rp);

        loginTabs.addTab("Cliente", loginCliente);
        loginTabs.addTab("Staff", loginStaff);
        loginTabs.addTab("Rider", loginRider);
        loginTabs.addTab("Registra Panineria", new JScrollPane(regPan));

        JPanel loginRoot = new JPanel(new BorderLayout());
        loginRoot.add(loginTabs, BorderLayout.CENTER);

        // ====== apply session -> tabs per ruolo ======
        Runnable applySession = () -> {
            Session s = currentSession[0];
            if (s == null) return;

            lblUser.setText("👤 " + s.displayName() + " (" + s.ruolo + ")");
            btnLogout.setVisible(true);

            tabs.removeAll();
            tabs.addTab("Catalogo", tabCatalogo);

            if (s.ruolo == Ruolo.CLIENTE) {
                tabs.addTab("Ordine", tabOrdine);
                // blocco combo sul cliente loggato
                comboClienti.setEnabled(false);
                comboClienti.setSelectedItem(s.cliente);
                btnAdd.setEnabled(true);
                btnRemove.setEnabled(true);
                btnClear.setEnabled(true);
                btnCreaOrdine.setEnabled(true);

                refreshAll[0] = () -> { refreshPaninerie.run(); refreshClienti.run(); refreshTotals.run(); refreshRiderCombo.run(); };
                tabs.setSelectedIndex(0);

            } else if (s.ruolo == Ruolo.CASSIERE) {
                tabs.addTab("Ordini", tabOrdiniCassiere);
                tabs.addTab("Gestione Panini", tabGestionePanini);
                tabs.addTab("Report", tabReport);

                btnAdd.setEnabled(false);
                btnRemove.setEnabled(false);
                btnClear.setEnabled(false);
                btnCreaOrdine.setEnabled(false);
                comboClienti.setEnabled(true);

                // CASSIERE: SOLO BONIFICI
                btnBonifici.setVisible(true);
                btnStats.setVisible(false);

                refreshAll[0] = () -> { refreshPaninerie.run(); refreshRiderCombo.run(); refreshOrdiniCassiere.run(); refreshPaniniCrud.run(); };
                tabs.setSelectedIndex(1);
            



            } else if (s.ruolo == Ruolo.RIDER) {
                tabs.addTab("Le mie consegne", tabRider);

                btnAdd.setEnabled(false);
                btnRemove.setEnabled(false);
                btnClear.setEnabled(false);
                btnCreaOrdine.setEnabled(false);

                refreshAll[0] = () -> { refreshConsegneRider.run(); };
                tabs.setSelectedIndex(1);

            } else { // GESTORE
                tabs.addTab("Gestione Rider", tabGestioneRider);
                tabs.addTab("Report", tabReport);

                btnAdd.setEnabled(false);
                btnRemove.setEnabled(false);
                btnClear.setEnabled(false);
                btnCreaOrdine.setEnabled(false);

                // GESTORE: SOLO STATISTICHE
                btnBonifici.setVisible(false);
                btnStats.setVisible(true);

                refreshAll[0] = () -> { refreshRiderTable.run(); refreshRiderCombo.run(); };
                tabs.setSelectedIndex(1);
            }


            status.setText("Login effettuato.");
            refreshAll[0].run();

        };

        // ====== login actions ======
        btnLoginCliente.addActionListener(e -> {
            try {
                String nome = tfNome.getText().trim();
                String cognome = tfCognome.getText().trim();
                String tel = tfTel.getText().trim();
                if (nome.isEmpty() || cognome.isEmpty() || tel.isEmpty()) { info(f, "Inserisci Nome, Cognome e Telefono."); return; }

                ClienteDTO c = controller.trovaClientePerLogin(nome, cognome, tel);
                if (c == null) { info(f, "Cliente non registrato. Premi Registrati."); return; }

                Session s = new Session();
                s.ruolo = Ruolo.CLIENTE;
                s.cliente = c;
                s.staff = null;
                currentSession[0] = s;

                refreshClienti.run();
                applySession.run();
                cardsLayout.show(cards, "APP");
            } catch (Exception ex) {
                err(f, ex);
            }
        });

        btnRegCliente.addActionListener(e -> {
            try {
                String nome = tfNome.getText().trim();
                String cognome = tfCognome.getText().trim();
                String tel = tfTel.getText().trim();
                if (nome.isEmpty() || cognome.isEmpty() || tel.isEmpty()) { info(f, "Inserisci Nome, Cognome e Telefono."); return; }

                JTextField tfInd = new JTextField();
                JTextField tfCap = new JTextField();
                JTextField tfCarta = new JTextField();
                JTextField tfScad = new JTextField();
                JTextField tfEmail = new JTextField();
                tfEmail.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Email (es. nome@mail.it)");

                JPanel form = new JPanel(new GridLayout(0,1,8,8));
                form.add(new JLabel("Indirizzo")); form.add(tfInd);
                form.add(new JLabel("CAP")); form.add(tfCap);
                form.add(new JLabel("Carta (numero)")); form.add(tfCarta);
                form.add(new JLabel("Scadenza (MM/YY)")); form.add(tfScad);

                int ok = JOptionPane.showConfirmDialog(f, form, "Registrazione Cliente", JOptionPane.OK_CANCEL_OPTION);
                if (ok != JOptionPane.OK_OPTION) return;

                ClienteDTO c = controller.registraCliente(
                        nome, cognome,
                        tfInd.getText().trim(),
                        tfCap.getText().trim(),
                        tel,
                        tfEmail.getText().trim(),
                        tfCarta.getText().trim(),
                        tfScad.getText().trim()
                );


                Session s = new Session();
                s.ruolo = Ruolo.CLIENTE;
                s.cliente = c;
                s.staff = null;
                currentSession[0] = s;

                refreshClienti.run();
                applySession.run();
                cardsLayout.show(cards, "APP");

            } catch (Exception ex) {
                err(f, ex);
            }
        });

        btnLoginStaff.addActionListener(e -> {
            try {
                String u = tfUser.getText().trim();
                String p = new String(tfPass.getPassword());
                if (u.isEmpty() || p.isEmpty()) { info(f, "Inserisci username e password."); return; }

                var staff = controller.loginStaff(u, p);
                if (staff == null) { info(f, "Credenziali non valide."); return; }
                if ("RIDER".equalsIgnoreCase(staff.ruolo())) {
                    info(f, "Questo account è un Rider. Usa il tab Rider.");
                    loginTabs.setSelectedIndex(2); // se Rider è il terzo tab
                    return;
                }


                Session s = new Session();
                s.staff = staff;
                s.cliente = null;

                s.ruolo = switch (staff.ruolo()) {
                    case "GESTORE" -> Ruolo.GESTORE;
                    case "CASSIERE" -> Ruolo.CASSIERE;
                    case "RIDER" -> Ruolo.RIDER;
                    default -> Ruolo.CASSIERE;
                };

                currentSession[0] = s;

                // refresh iniziali
                refreshPaninerie.run();
                refreshRiderCombo.run();
                applySession.run();
                cardsLayout.show(cards, "APP");

            } catch (Exception ex) {
                err(f, ex);
            }
        });

        btnRegPan.addActionListener(e -> {
            try {
                long id = controller.registraPanineria(
                        pNome.getText().trim(),
                        pCitta.getText().trim(),
                        pVia.getText().trim(),
                        pCivico.getText().trim(),
                        pCap.getText().trim(),
                        pTel.getText().trim(),
                        pEmail.getText().trim(),
                        pIban.getText().trim(),
                        pGestN.getText().trim(),
                        pGestC.getText().trim(),
                        pCassUser.getText().trim(),
                        new String(pCassPass.getPassword())
                );
                JOptionPane.showMessageDialog(f,
                        "Panineria registrata! ID = " + id + "\nOra fai login come Staff con l'utente cassiere creato.",
                        "OK", JOptionPane.INFORMATION_MESSAGE);
                loginTabs.setSelectedIndex(1);
            } catch (Exception ex) {
                err(f, ex);
            }
        });

        btnLoginRider.addActionListener(e -> {
            try {
                String u = tfUserR.getText().trim();
                String p = new String(tfPassR.getPassword());
                if (u.isEmpty() || p.isEmpty()) { info(f, "Inserisci username e password."); return; }

                var staff = controller.loginStaff(u, p);
                if (staff == null) { info(f, "Credenziali non valide."); return; }

                if (!"RIDER".equalsIgnoreCase(staff.ruolo())) {
                    info(f, "Questo account non è un Rider. Usa il tab Staff.");
                    return;
                }

                Session s = new Session();
                s.staff = staff;
                s.cliente = null;
                s.ruolo = Ruolo.RIDER;
                currentSession[0] = s;

                applySession.run();
                cardsLayout.show(cards, "APP");
                refreshConsegneRider.run();

            } catch (Exception ex) {
                err(f, ex);
            }
        });


        // ===== logout =====
        btnLogout.addActionListener(e -> {
            currentSession[0] = null;

            lblUser.setText("Non autenticato");
            btnLogout.setVisible(false);

            // pulizia carrello
            cartModel.clear();
            cartPanineriaId[0] = -1L;
            refreshTotals.run();

            tabs.removeAll();
            status.setText("Logout effettuato.");
            cardsLayout.show(cards, "LOGIN");
        });

        // ===== totals refresh hook =====
        tabs.addChangeListener(e -> refreshTotals.run());

        // ===== init data (pre-login: giusto paninerie) =====
        refreshPaninerie.run();
        refreshClienti.run();
        refreshRiderCombo.run();
        refreshTotals.run();

        // ===== mount cards =====
        cards.add(loginRoot, "LOGIN");
        cards.add(appRoot, "APP");

        f.setContentPane(cards);
        cardsLayout.show(cards, "LOGIN");
        f.setVisible(true);
    }
}
