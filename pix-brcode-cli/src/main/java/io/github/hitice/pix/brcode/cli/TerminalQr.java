package io.github.hitice.pix.brcode.cli;

import io.github.hitice.pix.brcode.qrcode.BrCodeQr;

/**
 * Draws a QR code in the terminal so a phone can scan it straight from the screen.
 *
 * <p>Each character holds two modules: the upper half block takes the foreground color for the
 * top module and the background color for the bottom one. Colors are explicit (black on bright
 * white) so the code scans on dark and light terminal themes alike.
 */
final class TerminalQr {

    /** The QR specification asks for a four-module light border around the symbol. */
    static final int QUIET_ZONE = 4;

    private static final String UPPER_HALF = "▀";
    private static final String RESET = "\u001b[0m";

    private TerminalQr() {
    }

    static String render(BrCodeQr qr) {
        int size = qr.size();
        int from = -QUIET_ZONE;
        int to = size + QUIET_ZONE;
        StringBuilder text = new StringBuilder();
        for (int y = from; y < to; y += 2) {
            for (int x = from; x < to; x++) {
                boolean top = dark(qr, x, y);
                boolean bottom = dark(qr, x, y + 1);
                text.append("\u001b[").append(top ? "30" : "97").append(';')
                        .append(bottom ? "40" : "107").append('m').append(UPPER_HALF);
            }
            text.append(RESET).append(System.lineSeparator());
        }
        return text.toString();
    }

    private static boolean dark(BrCodeQr qr, int x, int y) {
        int size = qr.size();
        return x >= 0 && y >= 0 && x < size && y < size && qr.isDark(x, y);
    }
}
