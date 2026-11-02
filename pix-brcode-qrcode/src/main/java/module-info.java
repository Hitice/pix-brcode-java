/**
 * Renders a Pix BR Code as a QR code image (PNG or SVG) with ZXing core.
 *
 * <p>Entry point: {@link io.github.hitice.pix.brcode.qrcode.BrCodeQr}. PNG output is written
 * without {@code java.desktop}.
 */
module io.github.hitice.pix.brcode.qrcode {
    requires transitive io.github.hitice.pix.brcode;
    requires com.google.zxing;

    exports io.github.hitice.pix.brcode.qrcode;
}
