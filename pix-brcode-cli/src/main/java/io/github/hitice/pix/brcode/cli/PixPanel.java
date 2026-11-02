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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
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
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The window's content: a "Gerar" tab that builds a code and shows its QR, and a "Conferir" tab
 * that checks a pasted code. Kept as a panel, without the frame, so tests can drive it headless.
 */
final class PixPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int QR_FRAME = 320;
    /** BrCodeQr draws the four-module quiet zone the QR specification asks for. */
    private static final int QR_QUIET_ZONE = 4;
    private static final Color ERROR = new Color(0xB3261E);
    private static final Color OK = new Color(0x1B6E3C);

    final JTextField key = new JTextField(28);
    final JTextField amount = new JTextField(10);
    final JTextField name = new JTextField(28);
    final JTextField city = new JTextField(15);
    final JTextField txid = new JTextField(25);
    final JTextField description = new JTextField(28);
    final JLabel keyStatus = new JLabel(" ");
    final JLabel message = new JLabel(" ");
    final JLabel qr = new JLabel("O QR Code aparece aqui", SwingConstants.CENTER);
    final JTextArea payload = new JTextArea(4, 40);
    final JButton generate = new JButton("Gerar");
    final JButton copy = new JButton("Copiar copia e cola");
    final JButton save = new JButton("Salvar imagem");
    final JTextArea pasted = new JTextArea(5, 50);
    final JTextArea checkResult = new JTextArea(9, 50);
    final JButton check = new JButton("Conferir");

    private transient BrCode current;

    PixPanel() {
        super(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Gerar", generateTab());
        tabs.addTab("Conferir c\u00f3digo", checkTab());
        add(tabs, BorderLayout.CENTER);
    }

    private JComponent generateTab() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        row = field(form, c, row, "Chave Pix", key, "CPF, CNPJ, e-mail, telefone +55 ou chave aleat\u00f3ria");
        c.gridx = 1;
        c.gridy = row++;
        form.add(keyStatus, c);
        row = field(form, c, row, "Valor (R$)", amount, "Vazio deixa quem paga digitar");
        row = field(form, c, row, "Nome no QR", name, "Opcional. Vazio usa \"" + PixForm.DEFAULT_NAME + "\"");
        row = field(form, c, row, "Cidade no QR", city, "Opcional. Vazio usa \"" + PixForm.DEFAULT_CITY + "\"");
        row = field(form, c, row, "Identificador", txid, "Opcional. Letras e n\u00fameros, at\u00e9 25");
        row = field(form, c, row, "Descri\u00e7\u00e3o", description, "Opcional");
        c.gridx = 1;
        c.gridy = row++;
        form.add(generate, c);
        c.gridy = row;
        form.add(new JLabel("<html><small>" + PixForm.PAYER_NOTE + "</small></html>"), c);

        JPanel result = new JPanel(new BorderLayout(8, 8));
        result.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 16));
        qr.setPreferredSize(new Dimension(QR_FRAME + 8, QR_FRAME + 8));
        qr.setBorder(BorderFactory.createLineBorder(new Color(0xDDDDDD)));
        result.add(qr, BorderLayout.NORTH);
        payload.setEditable(false);
        payload.setLineWrap(true);
        payload.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        result.add(new JScrollPane(payload), BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        buttons.add(copy);
        buttons.add(save);
        result.add(buttons, BorderLayout.SOUTH);
        copy.setEnabled(false);
        save.setEnabled(false);

        JPanel tab = new JPanel(new BorderLayout());
        tab.add(form, BorderLayout.WEST);
        tab.add(result, BorderLayout.CENTER);
        tab.add(message, BorderLayout.SOUTH);
        message.setBorder(BorderFactory.createEmptyBorder(0, 20, 12, 16));

        key.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateKeyStatus();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateKeyStatus();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateKeyStatus();
            }
        });
        generate.addActionListener(e -> generate());
        copy.addActionListener(e -> copyPayload());
        save.addActionListener(e -> chooseAndSave());
        return tab;
    }

    private static int field(JPanel form, GridBagConstraints c, int row, String label, JTextField input,
            String hint) {
        c.gridx = 0;
        c.gridy = row;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        form.add(input, c);
        input.setToolTipText(hint);
        c.gridy = row + 1;
        JLabel small = new JLabel("<html><small>" + hint + "</small></html>");
        small.setForeground(Color.GRAY);
        form.add(small, c);
        return row + 2;
    }

    private JComponent checkTab() {
        JPanel tab = new JPanel(new BorderLayout(8, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        tab.add(new JLabel("Cole um Pix copia e cola para ver os campos e conferir se est\u00e1 \u00edntegro:"),
                BorderLayout.NORTH);
        pasted.setLineWrap(true);
        pasted.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        checkResult.setEditable(false);
        checkResult.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel input = new JPanel(new BorderLayout(8, 8));
        input.add(new JScrollPane(pasted), BorderLayout.CENTER);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonRow.add(check);
        input.add(buttonRow, BorderLayout.SOUTH);
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(input, BorderLayout.NORTH);
        center.add(new JScrollPane(checkResult), BorderLayout.CENTER);
        tab.add(center, BorderLayout.CENTER);
        check.addActionListener(e -> check());
        return tab;
    }

    void updateKeyStatus() {
        String status = PixForm.keyStatus(key.getText());
        keyStatus.setText(status.isEmpty() ? " " : status);
        keyStatus.setForeground(status.startsWith("Chave inv\u00e1lida") ? ERROR : OK);
    }

    void generate() {
        try {
            current = PixForm.build(key.getText(), amount.getText(), name.getText(), city.getText(),
                    txid.getText(), description.getText());
        } catch (IllegalArgumentException | IllegalStateException | BrCodeException e) {
            current = null;
            showMessage("Erro: " + e.getMessage(), true);
            qr.setIcon(null);
            qr.setText("O QR Code aparece aqui");
            payload.setText("");
            copy.setEnabled(false);
            save.setEnabled(false);
            return;
        }
        qr.setText(null);
        qr.setIcon(new ImageIcon(qrImage(current)));
        payload.setText(current.payload());
        copy.setEnabled(true);
        save.setEnabled(true);
        showMessage("Pronto: " + current.merchantName() + ", "
                + current.amount().map(PixForm::money).orElse("valor aberto"), false);
    }

    void check() {
        String text = pasted.getText();
        if (text.isBlank()) {
            checkResult.setText("Cole um c\u00f3digo para conferir.");
            return;
        }
        List<String> problems = PixForm.problems(text);
        if (!problems.isEmpty()) {
            checkResult.setText("C\u00f3digo Pix inv\u00e1lido:\n  " + String.join("\n  ", problems));
            return;
        }
        BrCode code = BrCode.parse(text.strip());
        checkResult.setText("C\u00f3digo Pix v\u00e1lido (" + PixForm.label(code.type()) + ")\n"
                + String.join("\n", PixForm.describe(code)));
    }

    /** Writes the current QR as PNG; false when there is nothing to save. */
    boolean save(Path png) throws IOException {
        if (current == null) {
            return false;
        }
        BrCodeQr.of(current).writePng(png, 10);
        showMessage("Imagem salva em " + png, false);
        return true;
    }

    BrCode current() {
        return current;
    }

    private void copyPayload() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(payload.getText()), null);
        showMessage("Copia e cola copiado. Cole no app do banco ou mande para quem vai pagar.", false);
    }

    private void chooseAndSave() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("pix.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            save(chooser.getSelectedFile().toPath());
        } catch (IOException e) {
            showMessage("Erro ao salvar: " + e.getMessage(), true);
        }
    }

    private void showMessage(String text, boolean error) {
        message.setText(text);
        message.setForeground(error ? ERROR : OK);
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
}
