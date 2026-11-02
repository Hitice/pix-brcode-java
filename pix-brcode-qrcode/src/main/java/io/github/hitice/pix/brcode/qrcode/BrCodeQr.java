package io.github.hitice.pix.brcode.qrcode;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import io.github.hitice.pix.brcode.BrCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * QR code image of a {@link BrCode}, ready to be written as PNG or SVG.
 *
 * <p>The payload is encoded in byte mode by ZXing. When it has characters outside ASCII, which a
 * parsed payload may carry in free text fields, the bytes are UTF-8 and the symbol carries the ECI
 * designator 26, as the EMV QRCPS requires. The image includes a quiet zone of
 * {@value #QUIET_ZONE} modules on each side.
 *
 * <pre>{@code
 * BrCodeQr.of(code).writePng(Path.of("pix.png"), 8);
 * }</pre>
 *
 * <p>Instances are immutable.
 */
public final class BrCodeQr {

    /** Width of the white border, in modules, as recommended by ISO/IEC 18004. */
    public static final int QUIET_ZONE = 4;

    /** Error correction level of the QR symbol. */
    public enum ErrorCorrection {
        /** About 7% of the symbol can be restored. */
        L(ErrorCorrectionLevel.L),
        /** About 15% of the symbol can be restored. The default. */
        M(ErrorCorrectionLevel.M),
        /** About 25% of the symbol can be restored. */
        Q(ErrorCorrectionLevel.Q),
        /** About 30% of the symbol can be restored. */
        H(ErrorCorrectionLevel.H);

        private final ErrorCorrectionLevel level;

        ErrorCorrection(ErrorCorrectionLevel level) {
            this.level = level;
        }
    }

    private final String payload;
    private final int size;
    private final boolean[][] dark;

    private BrCodeQr(String payload, ByteMatrix matrix) {
        this.payload = payload;
        int modules = matrix.getWidth();
        this.size = modules + 2 * QUIET_ZONE;
        this.dark = new boolean[size][size];
        for (int y = 0; y < modules; y++) {
            for (int x = 0; x < modules; x++) {
                dark[y + QUIET_ZONE][x + QUIET_ZONE] = matrix.get(x, y) == 1;
            }
        }
    }

    /**
     * Encodes the code with error correction level {@link ErrorCorrection#M}.
     *
     * @param code the code
     * @return the QR image
     */
    public static BrCodeQr of(BrCode code) {
        return of(code, ErrorCorrection.M);
    }

    /**
     * Encodes the code with the given error correction level.
     *
     * @param code the code
     * @param errorCorrection the error correction level
     * @return the QR image
     */
    public static BrCodeQr of(BrCode code, ErrorCorrection errorCorrection) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(errorCorrection, "errorCorrection");
        String payload = code.payload();
        Map<EncodeHintType, Object> hints = isAscii(payload)
                ? Map.of()
                : Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        QRCode qr;
        try {
            qr = Encoder.encode(payload, errorCorrection.level, hints);
        } catch (WriterException e) {
            throw new IllegalStateException("Payload does not fit in a QR code: " + e.getMessage(), e);
        }
        return new BrCodeQr(payload, qr.getMatrix());
    }

    private static boolean isAscii(String s) {
        return s.chars().allMatch(c -> c < 0x80);
    }

    /**
     * Returns the encoded payload.
     *
     * @return the payload of the code
     */
    public String payload() {
        return payload;
    }

    /**
     * Returns the side of the image in modules, quiet zone included.
     *
     * @return the number of modules per side
     */
    public int size() {
        return size;
    }

    /**
     * Whether the module at the given column and row is dark.
     *
     * @param x column, from 0 to {@code size() - 1}
     * @param y row, from 0 to {@code size() - 1}
     * @return true for a dark module
     * @throws IndexOutOfBoundsException if a coordinate is outside the image
     */
    public boolean isDark(int x, int y) {
        Objects.checkIndex(x, size);
        Objects.checkIndex(y, size);
        return dark[y][x];
    }

    /**
     * Renders a black and white PNG (1 bit grayscale).
     *
     * @param moduleSize pixels per module, from 1 to 100
     * @return the PNG file content
     * @throws IllegalArgumentException if the module size is out of range
     */
    public byte[] toPng(int moduleSize) {
        checkModuleSize(moduleSize);
        int pixels = size * moduleSize;
        int rowBytes = (pixels + 7) / 8;
        ByteArrayOutputStream raw = new ByteArrayOutputStream((rowBytes + 1) * pixels);
        byte[] row = new byte[rowBytes];
        for (int y = 0; y < size; y++) {
            Arrays.fill(row, (byte) 0);
            for (int px = 0; px < pixels; px++) {
                // Grayscale 1 bit: 1 is white, 0 is black.
                if (!dark[y][px / moduleSize]) {
                    row[px >> 3] |= (byte) (0x80 >>> (px & 7));
                }
            }
            for (int repeat = 0; repeat < moduleSize; repeat++) {
                raw.write(0); // filter type None
                raw.write(row, 0, rowBytes);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        byte[] header = new byte[13];
        putInt(header, 0, pixels);
        putInt(header, 4, pixels);
        header[8] = 1; // bit depth
        header[9] = 0; // color type grayscale
        chunk(png, "IHDR", header);
        chunk(png, "IDAT", deflate(raw.toByteArray()));
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    /**
     * Writes a PNG file.
     *
     * @param path the file to create or replace
     * @param moduleSize pixels per module, from 1 to 100
     * @throws IOException if the file cannot be written
     */
    public void writePng(Path path, int moduleSize) throws IOException {
        Objects.requireNonNull(path, "path");
        Files.write(path, toPng(moduleSize));
    }

    /**
     * Renders an SVG document. The drawing uses one unit per module, scaled to the given size.
     *
     * @param moduleSize width and height of a module in the SVG {@code width} and {@code height}
     *     attributes, from 1 to 100
     * @return the SVG document
     * @throws IllegalArgumentException if the module size is out of range
     */
    public String toSvg(int moduleSize) {
        checkModuleSize(moduleSize);
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < size; y++) {
            int x = 0;
            while (x < size) {
                if (!dark[y][x]) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < size && dark[y][x]) {
                    x++;
                }
                int run = x - start;
                path.append('M').append(start).append(' ').append(y)
                        .append('h').append(run).append("v1h-").append(run).append('z');
            }
        }
        int pixels = size * moduleSize;
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + pixels + "\" height=\"" + pixels
                + "\" viewBox=\"0 0 " + size + " " + size + "\" shape-rendering=\"crispEdges\">\n"
                + "<rect width=\"" + size + "\" height=\"" + size + "\" fill=\"#ffffff\"/>\n"
                + "<path fill=\"#000000\" d=\"" + path + "\"/>\n"
                + "</svg>\n";
    }

    /**
     * Writes an SVG file in UTF-8.
     *
     * @param path the file to create or replace
     * @param moduleSize size of a module, from 1 to 100
     * @throws IOException if the file cannot be written
     */
    public void writeSvg(Path path, int moduleSize) throws IOException {
        Objects.requireNonNull(path, "path");
        Files.writeString(path, toSvg(moduleSize), StandardCharsets.UTF_8);
    }

    private static void checkModuleSize(int moduleSize) {
        if (moduleSize < 1 || moduleSize > 100) {
            throw new IllegalArgumentException("moduleSize must be from 1 to 100, got " + moduleSize);
        }
    }

    private static void putInt(byte[] b, int offset, int value) {
        b[offset] = (byte) (value >>> 24);
        b[offset + 1] = (byte) (value >>> 16);
        b[offset + 2] = (byte) (value >>> 8);
        b[offset + 3] = (byte) value;
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        byte[] length = new byte[4];
        putInt(length, 0, data.length);
        out.writeBytes(length);
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] crcBytes = new byte[4];
        putInt(crcBytes, 0, (int) crc.getValue());
        out.writeBytes(crcBytes);
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4 + 64);
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
