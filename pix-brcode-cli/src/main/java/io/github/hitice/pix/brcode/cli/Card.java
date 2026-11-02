package io.github.hitice.pix.brcode.cli;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** A white panel with rounded corners and a hairline border, used as a card in the window. */
final class Card extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int ARC = 18;
    private static final Color BORDER = new Color(0xE3E7EC);

    Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
