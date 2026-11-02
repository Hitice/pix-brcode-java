package io.github.hitice.pix.brcode.examples;

import io.github.hitice.pix.brcode.BrCode;
import io.github.hitice.pix.brcode.PixKey;
import io.github.hitice.pix.brcode.Problem;
import io.github.hitice.pix.brcode.ValidationResult;
import io.github.hitice.pix.brcode.qrcode.BrCodeQr;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds a static Pix code, prints its payload, parses it back and writes a QR PNG.
 *
 * <p>Run from the repository root: {@code ./mvnw -q install -DskipTests} once, then
 * {@code ./mvnw -q -pl examples exec:java}. The image goes to {@code target/pix.png} under the
 * current directory unless a path is given as the first argument.
 */
public final class Example {

    private Example() {
    }

    /**
     * Entry point.
     *
     * @param args optional output path of the PNG
     * @throws IOException if the image cannot be written
     */
    public static void main(String[] args) throws IOException {
        BrCode code = BrCode.staticPayment()
                .key(PixKey.email("pix@example.com"))
                .merchantName("Padaria Sao Joao")
                .merchantCity("S\u00e3o Paulo")
                .amount(new BigDecimal("12.34"))
                .txid("PEDIDO123")
                .description("Pedido 123")
                .build();

        System.out.println("Static payload:  " + code.payload());
        System.out.println("CRC:             " + code.crc());

        BrCode parsed = BrCode.parse(code.payload());
        System.out.println("Parsed key:      " + parsed.key().orElseThrow().value()
                + " (" + parsed.key().orElseThrow().type() + ")");
        System.out.println("Parsed amount:   " + parsed.amount().orElseThrow());
        System.out.println("Parsed city:     " + parsed.merchantCity());

        BrCode dynamic = BrCode.dynamicPayment()
                .location("pix.example.com/qr/v2/9d36b84fc70b478fb95c12729b90ca25")
                .merchantName("Padaria Sao Joao")
                .merchantCity("Sao Paulo")
                .build();
        System.out.println("Dynamic payload: " + dynamic.payload());

        String tampered = code.payload().replace("12.34", "92.34");
        ValidationResult result = BrCode.validate(tampered);
        System.out.println("Tampered valid:  " + result.isValid());
        for (Problem problem : result.problems()) {
            System.out.println("  " + problem.code() + ": " + problem);
        }

        Path png = Path.of(args.length > 0 ? args[0] : "target/pix.png").toAbsolutePath();
        Files.createDirectories(png.getParent());
        BrCodeQr.of(code).writePng(png, 8);
        System.out.println("QR image:        " + png);
    }
}
