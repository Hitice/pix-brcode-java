package io.github.hitice.pix.brcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Build, write, parse: the parsed code equals the built one, field by field. */
class RoundTripTest {

    private static final long SEED = 20261102L;
    private static final int ITERATIONS = 5_000;

    static Stream<Arguments> combinations() {
        List<PixKey> keys = List.of(
                PixKey.cpf("529.982.247-25"),
                PixKey.cnpj("00.038.166/0001-05"),
                PixKey.cnpj("12.ABC.345/01DE-35"),
                PixKey.email("fulano_da_silva.recebedor@example.com"),
                PixKey.phone("+55 61 91234-5678"),
                PixKey.evp("123e4567-e12b-12d1-a456-426655440000"));
        List<Arguments> out = new ArrayList<>();
        for (PixKey key : keys) {
            for (boolean amount : new boolean[] {false, true}) {
                for (boolean txid : new boolean[] {false, true}) {
                    for (boolean description : new boolean[] {false, true}) {
                        out.add(Arguments.of(key, amount, txid, description));
                    }
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} amount={1} txid={2} description={3}")
    @MethodSource("combinations")
    void everyKeyTypeWithAndWithoutOptionalFields(PixKey key, boolean amount, boolean txid, boolean description) {
        BrCode.StaticBuilder builder = BrCode.staticPayment()
                .key(key)
                .merchantName("Fulano de Tal")
                .merchantCity("Bras\u00edlia");
        if (amount) {
            builder.amount(new BigDecimal("1234.5"));
        }
        if (txid) {
            builder.txid("TX" + key.type());
        }
        if (description) {
            builder.description("Pedido de teste");
        }
        BrCode built = builder.build();
        BrCode parsed = BrCode.parse(built.payload());

        assertSameFields(parsed, built);
        assertThat(parsed.key()).contains(key);
        assertThat(parsed.amount().isPresent()).isEqualTo(amount);
        assertThat(parsed.txid().isPresent()).isEqualTo(txid);
        assertThat(parsed.description().isPresent()).isEqualTo(description);
    }

    @Test
    void randomStaticCodes() {
        Random random = new Random(SEED);
        for (int i = 0; i < ITERATIONS; i++) {
            BrCode built = randomStatic(random);
            BrCode parsed = BrCode.parse(built.payload());
            assertSameFields(parsed, built);
            assertThat(parsed.type()).isEqualTo(BrCode.Type.STATIC);
        }
    }

    @Test
    void randomDynamicCodes() {
        Random random = new Random(SEED + 1);
        for (int i = 0; i < ITERATIONS; i++) {
            BrCode.DynamicBuilder builder = BrCode.dynamicPayment()
                    .location("psp" + random.nextInt(1000) + ".example.com/qr/v2/"
                            + UUID.randomUUID().toString().replace("-", ""))
                    .merchantName(randomText(random, 1, 25))
                    .merchantCity(randomText(random, 1, 15));
            if (random.nextBoolean()) {
                builder.initiationMethod(BrCode.InitiationMethod.REUSABLE);
            }
            if (random.nextBoolean()) {
                builder.recurrenceLocation("psp.example.com/rec/" + random.nextInt(1_000_000));
            }
            BrCode built = builder.build();
            assertSameFields(BrCode.parse(built.payload()), built);
        }
    }

    /** CRC-16 detects every single character change, and the parser must never accept one. */
    @Test
    void everySingleCharacterChangeIsRejected() {
        Random random = new Random(SEED + 2);
        String alphabet = "0123456789ABCDEFabcdef .*-@+";
        for (int i = 0; i < ITERATIONS; i++) {
            String payload = randomStatic(random).payload();
            int at = random.nextInt(payload.length());
            char replacement;
            do {
                replacement = alphabet.charAt(random.nextInt(alphabet.length()));
            } while (replacement == payload.charAt(at));
            String changed = payload.substring(0, at) + replacement + payload.substring(at + 1);

            ValidationResult result = BrCode.validate(changed);
            assertThat(result.isValid()).as("changed index %d of %s", at, payload).isFalse();
            assertThat(result.problems()).isNotEmpty();
        }
    }

    @Test
    void parsedCodesAreReusedAsIs() {
        Random random = new Random(SEED + 3);
        for (int i = 0; i < 100; i++) {
            BrCode built = randomStatic(random);
            BrCode once = BrCode.parse(built.payload());
            BrCode twice = BrCode.parse(once.payload());
            assertThat(twice).isEqualTo(once).isEqualTo(built);
        }
    }

    private static BrCode randomStatic(Random random) {
        PixKey key = randomKey(random);
        BrCode.StaticBuilder builder = BrCode.staticPayment()
                .key(key)
                .merchantName(randomText(random, 1, 25))
                .merchantCity(randomText(random, 1, 15));
        if (random.nextBoolean()) {
            builder.amount(randomAmount(random));
        }
        if (random.nextBoolean()) {
            builder.txid(randomAlphanumeric(random, 1 + random.nextInt(25)));
        }
        int room = 99 - 18 - 4 - key.value().length() - 4;
        if (random.nextBoolean() && room > 0) {
            builder.description(randomText(random, 1, Math.min(room, 72)));
        }
        if (random.nextInt(4) == 0) {
            builder.postalCode(String.format("%08d", random.nextInt(100_000_000)));
        }
        if (random.nextInt(4) == 0) {
            builder.merchantCategoryCode(String.format("%04d", random.nextInt(10_000)));
        }
        if (random.nextInt(4) == 0) {
            builder.initiationMethod(random.nextBoolean()
                    ? BrCode.InitiationMethod.REUSABLE : BrCode.InitiationMethod.SINGLE_USE);
        }
        return builder.build();
    }

    private static PixKey randomKey(Random random) {
        return switch (random.nextInt(6)) {
            case 0 -> PixKey.cpf(withCpfDigits(randomDigits(random, 9)));
            case 1 -> PixKey.cnpj(withCnpjDigits(randomDigits(random, 12)));
            case 2 -> PixKey.cnpj(withCnpjDigits(randomCnpjBase(random)));
            case 3 -> PixKey.email(randomAlphanumeric(random, 1 + random.nextInt(20)).toLowerCase()
                    + "@example" + random.nextInt(100) + ".com.br");
            case 4 -> PixKey.phone("+55" + (1 + random.nextInt(9)) + (1 + random.nextInt(9)) + "9"
                    + randomDigits(random, 8));
            default -> PixKey.evp(new UUID(random.nextLong(), random.nextLong()));
        };
    }

    private static String randomCnpjBase(Random random) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static BigDecimal randomAmount(Random random) {
        long cents = 1 + (long) (random.nextDouble() * (random.nextBoolean() ? 1_000_000L : 999_999_999_999L));
        return BigDecimal.valueOf(cents, 2).stripTrailingZeros();
    }

    /** Letters with and without accents, digits, spaces and punctuation. */
    private static String randomText(Random random, int min, int max) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,-&'/"
                + "\u00e1\u00e0\u00e2\u00e3\u00e9\u00ea\u00ed\u00f3\u00f4\u00f5\u00fa\u00fc\u00e7"
                + "\u00c1\u00c3\u00c9\u00cd\u00d3\u00d5\u00da\u00c7";
        int length = min + random.nextInt(max - min + 1);
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) {
            char c = chars.charAt(random.nextInt(chars.length()));
            boolean edge = sb.isEmpty() || sb.length() == length - 1;
            if (!(edge && c == ' ')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String randomAlphanumeric(Random random, int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String randomDigits(Random random, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) ('0' + random.nextInt(10)));
        }
        return sb.toString();
    }

    /** Independent check digit implementation for test data. */
    private static String withCpfDigits(String nine) {
        String ten = nine + mod11(nine, 10);
        String cpf = ten + mod11(ten, 11);
        return cpf.chars().distinct().count() == 1 ? "52998224725" : cpf;
    }

    private static int mod11(String digits, int firstWeight) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            sum += (digits.charAt(i) - '0') * (firstWeight - i);
        }
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }

    private static String withCnpjDigits(String twelve) {
        int[] w1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] w2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        String thirteen = twelve + cnpjDigit(twelve, w1);
        String cnpj = thirteen + cnpjDigit(thirteen, w2);
        return cnpj.equals("00000000000000") ? "00038166000105" : cnpj;
    }

    private static int cnpjDigit(String chars, int[] weights) {
        int sum = 0;
        for (int i = 0; i < chars.length(); i++) {
            sum += (chars.charAt(i) - 48) * weights[i];
        }
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }

    private static void assertSameFields(BrCode parsed, BrCode built) {
        assertThat(parsed).isEqualTo(built);
        assertThat(parsed.payload()).isEqualTo(built.payload());
        assertThat(parsed.type()).isEqualTo(built.type());
        assertThat(parsed.key()).isEqualTo(built.key());
        assertThat(parsed.location()).isEqualTo(built.location());
        assertThat(parsed.merchantName()).isEqualTo(built.merchantName());
        assertThat(parsed.merchantCity()).isEqualTo(built.merchantCity());
        assertThat(parsed.amount()).isEqualTo(built.amount());
        assertThat(parsed.txid()).isEqualTo(built.txid());
        assertThat(parsed.description()).isEqualTo(built.description());
        assertThat(parsed.postalCode()).isEqualTo(built.postalCode());
        assertThat(parsed.merchantCategoryCode()).isEqualTo(built.merchantCategoryCode());
        assertThat(parsed.initiationMethod()).isEqualTo(built.initiationMethod());
        assertThat(parsed.withdrawalFacilitator()).isEqualTo(built.withdrawalFacilitator());
        assertThat(parsed.recurrenceLocation()).isEqualTo(built.recurrenceLocation());
        assertThat(parsed.crc()).isEqualTo(built.crc());
        assertThat(parsed.amount().map(BigDecimal::scale)).isIn(Optional.empty(), Optional.of(2));
    }
}
