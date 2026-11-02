package io.github.hitice.pix.brcode.cli;

import io.github.hitice.pix.brcode.qrcode.BrCodeQr;

/**
 * Draws a QR code in the terminal so a phone can scan it straight from the screen.
 *
 * <p>Each module is two spaces painted with a background color. A background fills the whole
 * character cell, including the extra line spacing many terminals add, so the symbol has no gaps;
 * glyphs such as half blocks leave light stripes between rows on those terminals. Two columns per
 * row keep the modules close to square. Colors are explicit (black on bright white) so the code
 * scans on dark and light themes alike.
 */
final class TerminalQr {

    /** Light border around the symbol, in modules; two scan reliably from a screen. */
    static final int QUIET_ZONE = 2;

    static final String DARK = "\u001b[40m  ";
    static final String LIGHT = "\u001b[107m  ";
    static final String RESET = "\u001b[0m";

    private TerminalQr() {
    }

    static String render(BrCodeQr qr) {
        int size = qr.size();
        StringBuilder text = new StringBuilder();
        for (int y = -QUIET_ZONE; y < size + QUIET_ZONE; y++) {
            for (int x = -QUIET_ZONE; x < size + QUIET_ZONE; x++) {
                text.append(dark(qr, x, y) ? DARK : LIGHT);
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
