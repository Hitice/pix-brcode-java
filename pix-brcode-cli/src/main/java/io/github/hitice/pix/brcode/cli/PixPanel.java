package io.github.hitice.pix.brcode.cli;

import io.github.hitice.pix.brcode.BrCode;
import io.github.hitice.pix.brcode.BrCodeException;
import io.github.hitice.pix.brcode.qrcode.BrCodeQr;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The window's content. "Receber" builds the code as the person types and shows its QR;
 * "Conferir" checks a pasted code. Kept as a panel, without the frame, so tests can drive it
 * headless. Client properties named "JTextField.*" and "FlatLaf.style" style it under FlatLaf
 * and are ignored by other looks.
 */
final class PixPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    static final Color ACCENT = new Color(0x0B7A70);
    static final Color ERROR = new Color(0xB3261E);
    static final Color MUTED = new Color(0x5F6B7A);
    static final Color PAGE = new Color(0xF4F6F8);
    private static final int QR_FRAME = 340;
    /** BrCodeQr draws the four-module quiet zone the QR specification asks for. */
    private static final int QR_QUIET_ZONE = 4;
    private static final int TYPING_DELAY_MS = 250;
    private static final String EMPTY_QR = "Digite uma chave Pix para ver o QR Code aqui.";
    private static final String PRIMARY_STYLE =
            "background:#0B7A70; foreground:#FFFFFF; borderWidth:0; focusWidth:0; font:bold; arc:12";

    final JTextField key = field("CPF, CNPJ, e-mail, celular ou chave aleatória");
    final JTextField amount = field("0,00");
    final JTextField name = field("Ex.: Padaria São João");
    final JTextField city = field(PixForm.DEFAULT_CITY);
    final JTextField txid = field("Ex.: PEDIDO123");
    final JTextField description = field("Ex.: Pedido 123");
    final JLabel keyStatus = small(" ", MUTED);
    final JLabel message = new JLabel(" ", SwingConstants.CENTER);
    final JLabel qr = new JLabel(EMPTY_QR, SwingConstants.CENTER);
    final JTextArea payload = new JTextArea(3, 30);
    final JToggleButton moreOptions = new JToggleButton("Mais opções  ▸");
    final JPanel options = new JPanel(new GridBagLayout());
    final JButton copy = new JButton("Copiar código");
    final JButton save = new JButton("Salvar imagem");
    final JTextArea pasted = new JTextArea(4, 50);
    final JLabel checkBadge = new JLabel(" ");
    final JTextArea checkResult = new JTextArea(8, 50);
    final JButton check = new JButton("Conferir");

    private final Timer typing = new Timer(TYPING_DELAY_MS, e -> generate());
    private final Timer copiedReset = new Timer(2000, e -> copy.setText("Copiar código"));
    private transient BrCode current;

    PixPanel() {
        super(new BorderLayout());
        setBackground(PAGE);
        typing.setRepeats(false);
        copiedReset.setRepeats(false);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Receber", receiveTab());
        tabs.addTab("Conferir código", checkTab());
        add(tabs, BorderLayout.CENTER);
    }

    // ---- Receber -------------------------------------------------------------------------

    private JComponent receiveTab() {
        JPanel page = page();
        page.add(header("Receber por Pix",
                "Digite a sua chave e o valor. O QR Code e o código copia e cola aparecem na hora."),
                BorderLayout.NORTH);
        JPanel cards = new JPanel(new GridBagLayout());
        cards.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.insets = new Insets(0, 0, 0, 16);
        c.weightx = 0.45;
        cards.add(dataCard(), c);
        c.insets = new Insets(0, 0, 0, 0);
        c.gridx = 1;
        c.weightx = 0.55;
        cards.add(qrCard(), c);
        page.add(cards, BorderLayout.CENTER);
        return page;
    }

    private JComponent dataCard() {
        Card card = new Card(new GridBagLayout());
        GridBagConstraints c = column();
        card.add(title("Dados do Pix"), c);
        c.insets = new Insets(14, 0, 4, 0);
        card.add(label("Chave Pix"), c);
        c.insets = new Insets(0, 0, 4, 0);
        card.add(key, c);
        card.add(keyStatus, c);
        c.insets = new Insets(12, 0, 4, 0);
        card.add(label("Valor"), c);
        c.insets = new Insets(0, 0, 4, 0);
        JLabel currency = small("R$", MUTED);
        currency.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 2));
        amount.putClientProperty("JTextField.leadingComponent", currency);
        card.add(amount, c);
        card.add(small("Deixe vazio para quem paga digitar o valor.", MUTED), c);
        c.insets = new Insets(12, 0, 4, 0);
        card.add(label("Seu nome ou da loja"), c);
        c.insets = new Insets(0, 0, 4, 0);
        card.add(name, c);
        card.add(small("Recomendado: é o nome que quem paga vê ao ler o QR Code.", MUTED), c);

        c.insets = new Insets(14, 0, 4, 0);
        moreOptions.putClientProperty("JButton.buttonType", "borderless");
        moreOptions.setForeground(ACCENT);
        moreOptions.setHorizontalAlignment(SwingConstants.LEFT);
        card.add(moreOptions, c);
        c.insets = new Insets(0, 0, 0, 0);
        buildOptions();
        card.add(options, c);

        c.weighty = 1;
        c.anchor = GridBagConstraints.SOUTHWEST;
        c.insets = new Insets(16, 0, 0, 0);
        card.add(small("<html>Na hora de pagar, o banco de quem paga mostra o nome cadastrado na sua chave.</html>",
                MUTED), c);

        moreOptions.addActionListener(e -> showOptions(moreOptions.isSelected()));
        for (JTextField input : List.of(key, amount, name, city, txid, description)) {
            input.getDocument().addDocumentListener(new Changed(this::typed));
        }
        return card;
    }

    private void buildOptions() {
        options.setOpaque(false);
        GridBagConstraints c = column();
        c.insets = new Insets(4, 0, 4, 0);
        options.add(label("Cidade"), c);
        options.add(city, c);
        options.add(label("Identificador do pedido"), c);
        options.add(txid, c);
        options.add(label("Mensagem para quem paga"), c);
        options.add(description, c);
        options.setVisible(false);
    }

    void showOptions(boolean visible) {
        moreOptions.setSelected(visible);
        moreOptions.setText(visible ? "Menos opções  ▾" : "Mais opções  ▸");
        options.setVisible(visible);
        revalidate();
    }

    private JComponent qrCard() {
        Card card = new Card(new BorderLayout(0, 12));
        card.add(title("Seu QR Code"), BorderLayout.NORTH);

        qr.setPreferredSize(new Dimension(QR_FRAME, QR_FRAME));
        qr.setForeground(MUTED);
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(qr, BorderLayout.CENTER);
        message.setFont(message.getFont().deriveFont(Font.BOLD, 15f));
        center.add(message, BorderLayout.SOUTH);
        card.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 10));
        bottom.setOpaque(false);
        payload.setEditable(false);
        payload.setLineWrap(true);
        payload.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        payload.setForeground(MUTED);
        payload.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        payload.setBackground(PAGE);
        bottom.add(payload, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        copy.putClientProperty("FlatLaf.style", PRIMARY_STYLE);
        save.putClientProperty("FlatLaf.style", "arc:12");
        buttons.add(copy);
        buttons.add(save);
        bottom.add(buttons, BorderLayout.SOUTH);
        card.add(bottom, BorderLayout.SOUTH);

        copy.addActionListener(e -> copyPayload());
        save.addActionListener(e -> chooseAndSave());
        clear(EMPTY_QR, null);
        return card;
    }

    private void typed() {
        updateKeyStatus();
        typing.restart();
    }

    void updateKeyStatus() {
        String status = PixForm.keyStatus(key.getText());
        if (status.isEmpty()) {
            keyStatus.setText(" ");
        } else if (status.startsWith("Chave inválida")) {
            keyStatus.setText("✕  " + status.substring("Chave inválida: ".length()));
            keyStatus.setForeground(ERROR);
        } else {
            keyStatus.setText("✓  " + status.replace("Reconhecida como ", "Chave de "));
            keyStatus.setForeground(ACCENT);
        }
    }

    /** Builds the code from the fields; an incomplete key just keeps the empty state. */
    void generate() {
        typing.stop();
        String keyText = key.getText();
        if (keyText.isBlank()) {
            clear(EMPTY_QR, null);
            return;
        }
        if (PixForm.keyStatus(keyText).startsWith("Chave inválida")) {
            clear("Confira a chave Pix para gerar o QR Code.", null);
            return;
        }
        try {
            current = PixForm.build(keyText, amount.getText(), name.getText(), city.getText(),
                    txid.getText(), description.getText());
        } catch (IllegalArgumentException | IllegalStateException | BrCodeException e) {
            clear(EMPTY_QR, "Não deu para gerar: " + e.getMessage());
            return;
        }
        qr.setText(null);
        qr.setIcon(new ImageIcon(qrImage(current)));
        payload.setText(current.payload());
        copy.setEnabled(true);
        save.setEnabled(true);
        message.setForeground(ACCENT);
        message.setText(current.amount()
                .map(a -> "Pronto para receber " + PixForm.money(a))
                .orElse("Pronto. Quem paga digita o valor."));
        if (name.getText().isBlank()) {
            message.setText("<html><div style='text-align:center'>" + message.getText()
                    + "<br><span style='font-weight:normal;font-size:smaller'>Dica: preencha o seu nome para quem paga reconhecer você.</span></div></html>");
        }
    }

    private void clear(String qrText, String error) {
        current = null;
        qr.setIcon(null);
        qr.setText("<html><div style='text-align:center;width:220px'>" + qrText + "</div></html>");
        payload.setText("");
        copy.setEnabled(false);
        save.setEnabled(false);
        message.setForeground(ERROR);
        message.setText(error == null ? " " : error);
    }

    BrCode current() {
        return current;
    }

    /** Writes the current QR as PNG; false when there is nothing to save. */
    boolean save(Path png) throws IOException {
        if (current == null) {
            return false;
        }
        BrCodeQr.of(current).writePng(png, 10);
        message.setForeground(ACCENT);
        message.setText("Imagem salva em " + png.getFileName());
        return true;
    }

    private void copyPayload() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(payload.getText()), null);
        copy.setText("Copiado!");
        copiedReset.restart();
    }

    private void chooseAndSave() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("pix.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            save(chooser.getSelectedFile().toPath());
        } catch (IOException e) {
            message.setForeground(ERROR);
            message.setText("Não deu para salvar: " + e.getMessage());
        }
    }

    /** The QR at the largest whole module size whose image, quiet zone included, fits the frame. */
    private static BufferedImage qrImage(BrCode code) {
        BrCodeQr symbol = BrCodeQr.of(code);
        int moduleSize = Math.max(2, QR_FRAME / (symbol.size() + 2 * QR_QUIET_ZONE));
        try {
            return ImageIO.read(new ByteArrayInputStream(symbol.toPng(moduleSize)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- Conferir ------------------------------------------------------------------------

    private JComponent checkTab() {
        JPanel page = page();
        page.add(header("Conferir um código Pix",
                "Cole um copia e cola para ver para quem vai o dinheiro e se o código está íntegro."),
                BorderLayout.NORTH);
        Card card = new Card(new BorderLayout(0, 12));
        pasted.setLineWrap(true);
        pasted.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        pasted.putClientProperty("JTextArea.placeholderText", "Cole aqui o código que começa com 000201...");
        JPanel input = new JPanel(new BorderLayout(0, 10));
        input.setOpaque(false);
        input.add(new JScrollPane(pasted), BorderLayout.CENTER);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        check.putClientProperty("FlatLaf.style", PRIMARY_STYLE);
        row.add(check);
        input.add(row, BorderLayout.SOUTH);
        card.add(input, BorderLayout.NORTH);

        JPanel result = new JPanel(new BorderLayout(0, 8));
        result.setOpaque(false);
        checkBadge.setFont(checkBadge.getFont().deriveFont(Font.BOLD, 15f));
        result.add(checkBadge, BorderLayout.NORTH);
        checkResult.setEditable(false);
        checkResult.setOpaque(false);
        checkResult.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        result.add(checkResult, BorderLayout.CENTER);
        card.add(result, BorderLayout.CENTER);
        page.add(card, BorderLayout.CENTER);
        check.addActionListener(e -> check());
        return page;
    }

    void check() {
        String text = pasted.getText();
        if (text.isBlank()) {
            checkBadge.setText(" ");
            checkResult.setText("Cole um código para conferir.");
            return;
        }
        List<String> problems = PixForm.problems(text);
        if (!problems.isEmpty()) {
            checkBadge.setForeground(ERROR);
            checkBadge.setText("✕  Código com problema");
            checkResult.setText("Código Pix inválido:\n  " + String.join("\n  ", problems));
            return;
        }
        BrCode code = BrCode.parse(text.strip());
        checkBadge.setForeground(ACCENT);
        checkBadge.setText("✓  Código válido");
        checkResult.setText("Código Pix válido (" + PixForm.label(code.type()) + ")\n"
                + String.join("\n", PixForm.describe(code)));
    }

    // ---- Small builders ------------------------------------------------------------------

    private static JPanel page() {
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setBackground(PAGE);
        page.setBorder(BorderFactory.createEmptyBorder(20, 24, 24, 24));
        return page;
    }

    private static JComponent header(String title, String subtitle) {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 22f));
        header.add(t);
        header.add(Box.createVerticalStrut(4));
        header.add(small(subtitle, MUTED));
        return header;
    }

    private static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 16f));
        return l;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private static JLabel small(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 1f));
        return l;
    }

    private static JTextField field(String placeholder) {
        JTextField f = new JTextField(24);
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.putClientProperty("JTextField.showClearButton", true);
        f.putClientProperty("FlatLaf.style", "arc:10; margin:6,8,6,8");
        return f;
    }

    private static GridBagConstraints column() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = GridBagConstraints.RELATIVE;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        return c;
    }

    /** Runs an action on every change of a text field. */
    private record Changed(Runnable action) implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            action.run();
        }
    }
}
