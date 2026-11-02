package io.github.hitice.pix.brcode.qrcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.decoder.Mode;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.github.hitice.pix.brcode.BrCode;
import io.github.hitice.pix.brcode.Crc16;
import io.github.hitice.pix.brcode.PixKey;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class BrCodeQrTest {

    /** Static example of the Pix manual, section 2.6.3. */
    private static final String MANUAL_STATIC = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-"
            + "a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D";

    private static BrCode sample() {
        return BrCode.staticPayment()
                .key(PixKey.email("pix@example.com"))
                .merchantName("Padaria Sao Joao")
                .merchantCity("S\u00e3o Paulo")
                .amount(new BigDecimal("12.34"))
                .txid("PEDIDO123")
                .description("Pedido 123")
                .build();
    }

    private static Result decode(BufferedImage image) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE));
    }

    private static BufferedImage readPng(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).as("ImageIO reads the PNG").isNotNull();
        return image;
    }

    @ParameterizedTest
    @EnumSource(BrCodeQr.ErrorCorrection.class)
    void pngDecodesBackToThePayload(BrCodeQr.ErrorCorrection level) throws Exception {
        BrCode code = sample();
        byte[] png = BrCodeQr.of(code, level).toPng(4);

        Result result = decode(readPng(png));
        assertThat(result.getText()).isEqualTo(code.payload());
        assertThat(BrCode.parse(result.getText())).isEqualTo(code);
    }

    @Test
    void manualExampleDecodesBack() throws Exception {
        BrCode code = BrCode.parse(MANUAL_STATIC);
        assertThat(decode(readPng(BrCodeQr.of(code).toPng(3))).getText()).isEqualTo(MANUAL_STATIC);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 8, 10})
    void pngSizeFollowsTheModuleSize(int moduleSize) throws Exception {
        BrCodeQr qr = BrCodeQr.of(sample());
        BufferedImage image = readPng(qr.toPng(moduleSize));

        assertThat(image.getWidth()).isEqualTo(qr.size() * moduleSize);
        assertThat(image.getHeight()).isEqualTo(qr.size() * moduleSize);
        for (int y = 0; y < qr.size(); y++) {
            for (int x = 0; x < qr.size(); x++) {
                int rgb = image.getRGB(x * moduleSize + moduleSize / 2, y * moduleSize + moduleSize / 2) & 0xFFFFFF;
                assertThat(rgb).isEqualTo(qr.isDark(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
    }

    @Test
    void quietZoneIsWhite() {
        BrCodeQr qr = BrCodeQr.of(sample());
        for (int i = 0; i < qr.size(); i++) {
            for (int border = 0; border < BrCodeQr.QUIET_ZONE; border++) {
                assertThat(qr.isDark(i, border)).isFalse();
                assertThat(qr.isDark(border, i)).isFalse();
                assertThat(qr.isDark(i, qr.size() - 1 - border)).isFalse();
                assertThat(qr.isDark(qr.size() - 1 - border, i)).isFalse();
            }
        }
        // Top left finder pattern starts right after the quiet zone.
        assertThat(qr.isDark(BrCodeQr.QUIET_ZONE, BrCodeQr.QUIET_ZONE)).isTrue();
    }

    @Test
    void builtPayloadsUseByteMode() throws Exception {
        assertThat(Encoder.encode(sample().payload(), ErrorCorrectionLevel.M).getMode()).isEqualTo(Mode.BYTE);
    }

    @Test
    void nonAsciiPayloadIsEncodedAsUtf8() throws Exception {
        // A parsed payload may carry UTF-8 in the description (String format in EMV).
        String account = "0014br.gov.bcb.pix" + "0114+5561912345678" + "0204P\u00e3o!";
        String body = "000201" + "26" + account.length() + account
                + "5204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***6304";
        String payload = body + Crc16.hex(body);
        BrCode code = BrCode.parse(payload);

        Result result = decode(readPng(BrCodeQr.of(code).toPng(4)));
        assertThat(result.getText()).isEqualTo(payload);
        assertThat(code.description()).contains("P\u00e3o!");
        assertThat(payload.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(payload.length());
    }

    @Test
    void svgHasOneSquarePerDarkModule() {
        BrCodeQr qr = BrCodeQr.of(sample());
        String svg = qr.toSvg(5);
        int pixels = qr.size() * 5;

        assertThat(svg).startsWith("<?xml").contains("<svg xmlns=\"http://www.w3.org/2000/svg\"")
                .contains("width=\"" + pixels + "\"").contains("viewBox=\"0 0 " + qr.size() + " " + qr.size() + "\"")
                .endsWith("</svg>\n");
        int dark = 0;
        for (int y = 0; y < qr.size(); y++) {
            for (int x = 0; x < qr.size(); x++) {
                dark += qr.isDark(x, y) ? 1 : 0;
            }
        }
        int covered = 0;
        String d = svg.substring(svg.indexOf(" d=\"") + 4, svg.lastIndexOf("\"/>"));
        for (String run : d.split("z")) {
            covered += Integer.parseInt(run.substring(run.indexOf('h') + 1, run.indexOf('v')));
        }
        assertThat(covered).isEqualTo(dark);
    }

    @Test
    void writesFiles(@TempDir Path dir) throws Exception {
        BrCodeQr qr = BrCodeQr.of(sample());
        Path png = dir.resolve("pix.png");
        Path svg = dir.resolve("pix.svg");
        qr.writePng(png, 6);
        qr.writeSvg(svg, 6);

        assertThat(decode(readPng(Files.readAllBytes(png))).getText()).isEqualTo(sample().payload());
        assertThat(Files.readString(svg)).isEqualTo(qr.toSvg(6));
        assertThat(qr.payload()).isEqualTo(sample().payload());
    }

    @Test
    void rejectsInvalidArguments() {
        BrCodeQr qr = BrCodeQr.of(sample());
        assertThatIllegalArgumentException().isThrownBy(() -> qr.toPng(0));
        assertThatIllegalArgumentException().isThrownBy(() -> qr.toPng(101));
        assertThatIllegalArgumentException().isThrownBy(() -> qr.toSvg(0));
        assertThatThrownBy(() -> qr.isDark(qr.size(), 0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> qr.isDark(0, -1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatNullPointerException().isThrownBy(() -> BrCodeQr.of(null));
        assertThatNullPointerException().isThrownBy(() -> BrCodeQr.of(sample(), null));
        assertThatNullPointerException().isThrownBy(() -> qr.writePng(null, 1));
        assertThatNullPointerException().isThrownBy(() -> qr.writeSvg(null, 1));
    }
}
