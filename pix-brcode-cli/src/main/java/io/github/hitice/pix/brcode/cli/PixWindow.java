package io.github.hitice.pix.brcode.cli;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Opens the desktop window around {@link PixPanel}, with the FlatLaf look and the app icon. */
final class PixWindow {

    private PixWindow() {
    }

    static void open() {
        SwingUtilities.invokeLater(() -> {
            setUpLook();
            JFrame frame = new JFrame("Pix");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setIconImages(List.of(icon(16), icon(32), icon(64), icon(128)));
            frame.setContentPane(new PixPanel());
            frame.pack();
            frame.setMinimumSize(new Dimension(860, 620));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    static void setUpLook() {
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@accentColor", "#0B7A70",
                "Component.arc", "10",
                "Button.arc", "12",
                "TextComponent.arc", "10",
                "TabbedPane.showTabSeparators", "false",
                "TabbedPane.tabHeight", "36"));
        FlatLightLaf.setup();
    }

    /** A rounded teal square with a small QR-like mark; drawn here, no image files. */
    static BufferedImage icon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(PixPanel.ACCENT);
            int arc = size / 4;
            g.fillRoundRect(0, 0, size, size, arc, arc);
            g.setColor(java.awt.Color.WHITE);
            float unit = size / 8f;
            g.setStroke(new BasicStroke(Math.max(1f, unit / 2)));
            int box = Math.round(unit * 2);
            int[][] corners = {{2, 2}, {4, 2}, {2, 4}};
            for (int[] p : corners) {
                g.drawRect(Math.round(unit * p[0]), Math.round(unit * p[1]), box - 1, box - 1);
            }
            g.fillRect(Math.round(unit * 4.6f), Math.round(unit * 4.6f), Math.round(unit * 1.4f), Math.round(unit * 1.4f));
        } finally {
            g.dispose();
        }
        return img;
    }
}
