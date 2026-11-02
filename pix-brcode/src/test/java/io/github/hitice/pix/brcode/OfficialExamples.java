package io.github.hitice.pix.brcode;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Payloads copied from the Banco Central documents, with the CRC each document prints.
 *
 * <ul>
 *   <li>Manual de Padroes para Iniciacao do Pix, version 2.10.0, section 2.6.3 (pages 13 and 14),
 *       section 2.7.2 (pages 26 and 27) and section 2.8.5 (pages 31 to 37).</li>
 *   <li>Manual do BR Code, version 2.0.1, section 2.2 (pages 12 and 13).</li>
 * </ul>
 */
final class OfficialExamples {

    private OfficialExamples() {
    }

    /** Pix manual 2.6.3: static code with a random key, no amount, no txid. */
    static final String STATIC_1D3D = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-"
            + "4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D";

    /** Pix manual 2.7.2: dynamic code for an immediate charge. */
    static final String DYNAMIC_64E4 = "00020101021226700014br.gov.bcb.pix2548pix.example.com/"
            + "8b3da2f39a4140d1a91abd93113bd4415204000053039865802BR5913Fulano de Tal6008BRASILIA"
            + "62070503***630464E4";

    /** Pix manual 2.8.5.1: composite code with only the recurrence location. */
    static final String RECURRENCE_F2DA = "00020126180014br.gov.bcb.pix5204000053039865802BR5913"
            + "Fulano de Tal6008BRASILIA62070503***80740014br.gov.bcb.pix2552pix.example.com/rec/"
            + "2353c790eefb11eaadc10242ac1200026304F2DA";

    /** Pix manual 2.8.5.2: composite code, static data with amount 100.50 and recurrence. */
    static final String STATIC_RECURRENCE_2875 = "00020126580014br.gov.bcb.pix0136123e4567-e12b-"
            + "12d1-a456-4266554400005204000053039865406100.505802BR5913Fulano de Tal6008BRASILIA"
            + "62070503***80740014br.gov.bcb.pix2552pix.example.com/rec/2353c790eefb11eaadc10242"
            + "ac12000263042875";

    /** Pix manual 2.8.5.3: composite code, dynamic data and recurrence. */
    static final String DYNAMIC_RECURRENCE_FB42 = "00020101021226700014br.gov.bcb.pix2548pix."
            + "example.com/8b3da2f39a4140d1a91abd93113bd4415204000053039865802BR5913Fulano de Tal"
            + "6008BRASILIA62070503***80740014br.gov.bcb.pix2552pix.example.com/rec/2353c790eefb11"
            + "eaadc10242ac1200026304FB42";

    /** BR Code manual 2.2: card scheme (04), Pix (26), another arrangement (27 and 80), CEP. */
    static final String BR_CODE_AD38 = "00020104141234567890123426580014BR.GOV.BCB.PIX0136123e4567"
            + "-e12b-12d1-a456-42665544000027300012BR.COM.OUTRO011001234567895204000053039865406"
            + "123.455802BR5917NOME DO RECEBEDOR6008BRASILIA61087007490062190515RP12345678-2019"
            + "80390012BR.COM.OUTRO01190123.ABCD.3456.WXYZ6304AD38";

    static Stream<Arguments> all() {
        return Stream.of(
                Arguments.of("Pix 2.6.3 static", STATIC_1D3D, "1D3D"),
                Arguments.of("Pix 2.7.2 dynamic", DYNAMIC_64E4, "64E4"),
                Arguments.of("Pix 2.8.5.1 recurrence only", RECURRENCE_F2DA, "F2DA"),
                Arguments.of("Pix 2.8.5.2 static and recurrence", STATIC_RECURRENCE_2875, "2875"),
                Arguments.of("Pix 2.8.5.3 dynamic and recurrence", DYNAMIC_RECURRENCE_FB42, "FB42"),
                Arguments.of("BR Code 2.2 multi arrangement", BR_CODE_AD38, "AD38"));
    }
}
