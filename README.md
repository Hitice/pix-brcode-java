# pix-brcode-java

[![CI](https://github.com/Hitice/pix-brcode-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Hitice/pix-brcode-java/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/Hitice/pix-brcode-java.svg)](https://jitpack.io/#Hitice/pix-brcode-java)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Java library to generate, parse and validate Pix payment QR codes (BR Code), following the
documents of the Banco Central do Brasil and the EMV QR code specification they build on.

- Builds static and dynamic codes, including composite codes with a recurrence location
  (Pix Automatico).
- Parses any payload into an immutable object, or lists every problem it has with the position in
  the payload.
- Validates Pix keys: CPF, numeric and alphanumeric CNPJ, e-mail, phone and random key.
- Core module: Java 21, zero runtime dependencies, JPMS module `io.github.hitice.pix.brcode`.
- Optional module renders the QR image as PNG or SVG with ZXing core.

It produces the exact payloads printed in the Pix manual examples, byte for byte, and those
examples are part of the test suite.

## Install

Maven Central publishing is pending. Until then, use JitPack, which builds the library from a Git
tag.

### JitPack

Maven:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.Hitice.pix-brcode-java</groupId>
  <artifactId>pix-brcode</artifactId>
  <version>v0.1.0</version>
</dependency>
<!-- Optional, for QR images -->
<dependency>
  <groupId>com.github.Hitice.pix-brcode-java</groupId>
  <artifactId>pix-brcode-qrcode</artifactId>
  <version>v0.1.0</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.Hitice.pix-brcode-java:pix-brcode:v0.1.0")
    implementation("com.github.Hitice.pix-brcode-java:pix-brcode-qrcode:v0.1.0") // optional
}
```

### Maven Central (after the first release)

Maven:

```xml
<dependency>
  <groupId>io.github.hitice</groupId>
  <artifactId>pix-brcode</artifactId>
  <version>0.1.0</version>
</dependency>
<!-- Optional, for QR images -->
<dependency>
  <groupId>io.github.hitice</groupId>
  <artifactId>pix-brcode-qrcode</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("io.github.hitice:pix-brcode:0.1.0")
    implementation("io.github.hitice:pix-brcode-qrcode:0.1.0") // optional
}
```

With JPMS, add `requires io.github.hitice.pix.brcode;` (or `io.github.hitice.pix.brcode.qrcode`,
which requires the core module transitively).

## Quick start

Static code: the key and the payment data travel inside the payload.

```java
import io.github.hitice.pix.brcode.BrCode;
import io.github.hitice.pix.brcode.PixKey;
import io.github.hitice.pix.brcode.qrcode.BrCodeQr;
import java.math.BigDecimal;
import java.nio.file.Path;

BrCode code = BrCode.staticPayment()
        .key(PixKey.email("pix@example.com"))     // or .key("pix@example.com") to detect the type
        .merchantName("Padaria Sao Joao")
        .merchantCity("S\u00e3o Paulo")            // accents are removed: "Sao Paulo"
        .amount(new BigDecimal("12.34"))           // optional; without it the payer types the amount
        .txid("PEDIDO123")                         // optional; without it the payload has ***
        .description("Pedido 123")                 // optional
        .build();

String copiaECola = code.payload();
// 00020126510014br.gov.bcb.pix0115pix@example.com0210Pedido 123520400005303986540512.345802BR
// 5916Padaria Sao Joao6009Sao Paulo62130509PEDIDO1236304BD94   (one line)

BrCodeQr.of(code).writePng(Path.of("pix.png"), 8);   // 8 pixels per module
String svg = BrCodeQr.of(code).toSvg(8);
```

Dynamic code: the payload carries the location where your PSP serves the charge (created with the
Pix API of your PSP). The location has no `https://` prefix.

```java
BrCode dynamic = BrCode.dynamicPayment()
        .location("pix.example.com/qr/v2/9d36b84fc70b478fb95c12729b90ca25")
        .merchantName("Padaria Sao Joao")
        .merchantCity("Sao Paulo")
        .build();
```

## Parsing and validation

`parse` returns the decoded code or throws `BrCodeException` with the first problem:

```java
BrCode code = BrCode.parse(payload);

code.type();                 // STATIC, DYNAMIC or RECURRENCE_ONLY
code.key();                  // Optional<PixKey>
code.location();             // Optional<String>, dynamic codes
code.amount();               // Optional<BigDecimal>, scale 2
code.txid();                 // Optional<String>, empty for ***
code.merchantName();
code.merchantCity();
code.recurrenceLocation();   // Optional<String>, composite codes
```

`validate` never throws for a bad payload and lists every problem, each with a category and the
zero based position in the payload:

```java
// The quick start payload with the merchant name edited to 26 characters, CRC left as it was.
String edited = code.payload().replace("5916Padaria Sao Joao", "5926Padaria Sao Joao Ltda XYZ1");

ValidationResult result = BrCode.validate(edited);
if (!result.isValid()) {
    for (Problem p : result.problems()) {
        System.out.println(p.code() + " at " + p.position() + ": " + p.message());
    }
}
// CRC_MISMATCH at 155: CRC mismatch: expected A5F3, found BD94
// INVALID_LENGTH at 93: Merchant name (59) has 26 characters, the maximum is 25
```

Problem categories (`ErrorCode`): `TRUNCATED`, `MALFORMED_TLV`, `INVALID_LENGTH`, `DUPLICATE_ID`,
`UNKNOWN_ID`, `MISPLACED_FIELD`, `MISSING_FIELD`, `INVALID_VALUE`, `INVALID_CHARACTER`,
`CRC_MISMATCH`, `INVALID_KEY`, `INVALID_AMOUNT`, `INVALID_TXID`, `CONFLICTING_FIELDS`, `NOT_PIX`.
Structural problems come first, then the CRC, then field rules.

The parser accepts what the specifications allow and the builder never produces: payment templates
of other arrangements (card schemes, IDs 02 to 51 and 80 to 99), the Pix template in any ID from 26
to 51, an upper case GUI, amounts written as `98` or `98.`, a postal code, tip fields (55 to 57) and
the language template (64).

## Pix keys

`PixKey.of(String)` detects the type. The typed factories accept formatted input and normalize it.
A `PixKey` always holds the canonical value, the one written in the payload.

| Type | Factory | Accepted input | Canonical value |
| --- | --- | --- | --- |
| CPF | `PixKey.cpf` | `123.456.789-09`, `12345678909` | 11 digits, check digits verified, repeated digits rejected |
| CNPJ | `PixKey.cnpj` | `12.ABC.345/01DE-35`, `00038166000105` | 12 characters `[0-9A-Z]` and 2 check digits; numeric and alphanumeric CNPJ |
| E-mail | `PixKey.email` | `Pix@Example.com` | lower case, at most 77 characters, DICT regular expression |
| Phone | `PixKey.phone` | `+55 (61) 91234-5678`, `61912345678` | `+55`, area code, 9 digit mobile number starting with 9 |
| Random key | `PixKey.evp` | UUID in any case, or `java.util.UUID` | lower case UUID with hyphens |

Detection rules of `PixKey.of`: `@` means e-mail, a leading `+` means phone, a UUID means random key,
and after removing `. / -` and spaces, 11 digits mean CPF and 14 characters mean CNPJ. A national
phone number without `+55` reads as a CPF, so use `PixKey.phone` for it.

The alphanumeric CNPJ check digits use the Receita Federal rule: each character is worth its ASCII
code minus 48, then modulo 11 with weights 2 to 9 from the right. For digits this is the same as the
numeric CNPJ rule, so one algorithm validates both.

## Rules implemented

From the BR Code manual, the Pix manual and EMV QRCPS MPM:

- Data objects are ID (2 digits), length (2 digits, 01 to 99) and value. Lengths count characters.
- `00` payload format indicator `01` comes first; `63` CRC comes last.
- `01` point of initiation method is optional: `11` reusable, `12` single use.
- A merchant account template from 26 to 51 holds the GUI `br.gov.bcb.pix` (case insensitive) in
  `00` and either the key in `01` (static) or the location in `25` (dynamic). The template has at
  most 99 characters, so the key (up to 77), the description `02` (up to 72) and the withdrawal
  facilitator `03` (8 digit ISPB, 12 characters with ID and length) share that space.
- `52` merchant category code (4 digits, `0000` by default), `53` currency `986`, `58` country `BR`.
- `54` amount: optional, up to 13 characters, digits and a dot, at most 2 decimals, not zero.
- `59` merchant name up to 25 and `60` merchant city up to 15 characters, printable ASCII (EMV
  "ans" format). The builder removes accents (Unicode NFD, combining marks dropped) and keeps case.
- `61` postal code optional.
- `62` additional data field template with `05` transaction ID. In static codes: up to 25 characters
  `A-Z a-z 0-9`, or `***` when there is none. Dynamic codes always carry `***` from the builder;
  when parsing, their transaction ID must only be printable ASCII, since the payer ignores it.
- `80` to `99` with the Pix GUI and `25`: recurrence location of composite codes.
- `63` CRC-16/CCITT-FALSE (polynomial `0x1021`, initial value `0xFFFF`) over the whole payload up to
  and including `6304`, written as 4 upper case hexadecimal digits.
- Locations have no scheme, at most 77 characters.

## Specification references

| Document | Version used | Used for |
| --- | --- | --- |
| [Manual de Padroes para Iniciacao do Pix](https://www.bcb.gov.br/content/estabilidadefinanceira/pix/Regulamento_Pix/II_ManualdePadroesparaIniciacaodoPix.pdf), Banco Central do Brasil | 2.10.0 | Pix template, key formats in the payload, txid rules, examples in sections 2.6.3, 2.7.2 and 2.8.5 |
| [Manual do BR Code](https://www.bcb.gov.br/content/estabilidadefinanceira/spb_docs/ManualBRCode.pdf), Banco Central do Brasil | 2.0.1 | Common BR Code fields, mandatory 62-05, example in section 2.2 |
| [EMV QR Code Specification for Payment Systems, Merchant-Presented Mode](https://www.emvco.com/emv-technologies/qr-codes/), EMVCo | 1.1 | TLV rules, field order, formats N, ans and S, amount format, CRC, byte mode and UTF-8 ECI |
| [Manual Operacional do DICT](https://www.bcb.gov.br/content/estabilidadefinanceira/pix/Regulamento_Pix/X_ManualOperacionaldoDICT.pdf), Banco Central do Brasil | 8.5 | Key types and formats, alphanumeric CNPJ keys |
| [DICT API specification](https://github.com/bacen/pix-dict-api) (`openapi.yaml`), Banco Central do Brasil | 1.8.0 | E-mail and random key regular expressions, 77 character limit |
| [CNPJ alfanumerico, perguntas e respostas](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/perguntas-e-respostas/cnpj/cnpj-alfanumerico.pdf), Receita Federal (IN RFB 2.229/2024) | April 2026 | Alphanumeric CNPJ format and check digit calculation |
| [Validation routines](https://www.serpro.gov.br/menu/noticias/videos/cnpj-alfanumerico.zip), Serpro | 2022 | Check digit reference implementation and test vectors |

## Design notes

- Zero dependencies in the core. The QR module depends only on ZXing core and writes PNG itself, so
  it does not need `java.desktop`.
- Immutable values. `BrCode`, `PixKey` (a record), `Problem` (a record) and `ValidationResult` are
  immutable and thread safe. Builders are not thread safe.
- Always valid. A `BrCode` exists only after every rule passed; a `PixKey` only holds a canonical,
  validated key.
- No nulls. Optional fields return `Optional`; arguments are checked with `Objects.requireNonNull`.
- Money as `BigDecimal` with scale 2. Amounts with more than 2 decimal places are rejected instead
  of rounded.
- Errors: invalid builder arguments throw `IllegalArgumentException`, a missing mandatory field
  throws `IllegalStateException` on `build()`, and invalid payloads throw `BrCodeException` (or are
  reported by `validate`).
- Equality: two codes are equal when their payloads are equal. A parsed code keeps the exact text it
  was read from.

## Limitations

- It does not talk to banks, to the DICT or to the Pix API. A valid payload can still carry a key
  that is not registered.
- It does not create the server side of dynamic codes: the charge, its location URL and the JSON
  payload served there come from your PSP.
- No PSP integration, payment confirmation or webhook handling.
- No builder for composite codes that carry only a recurrence location; they are parsed.
- The parser checks the Pix rules for fields that payer apps ignore (such as the merchant name
  format) as strictly as the specifications state. Use `validate` to see every finding.
- A parsed payload made only of characters of the QR alphanumeric set (digits, upper case letters
  and ` $%*+-./:`) is encoded by ZXing in alphanumeric mode instead of byte mode. Payloads built by
  this library always contain the lower case GUI and use byte mode.

## Building

```bash
./mvnw verify                               # tests, coverage gate (85%), javadoc
./mvnw -q install -DskipTests && ./mvnw -q -pl examples exec:java   # runs the example
```

`./mvnw javadoc:javadoc` needs the core module built in the same command or installed first
(`./mvnw compile javadoc:javadoc`).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Release steps are in [RELEASING.md](RELEASING.md).

## License

[MIT](LICENSE), Copyright (c) 2026 Pedro Henrique Fernandes.
