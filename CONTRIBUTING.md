# Contributing

Issues and pull requests are welcome. Questions about the Pix rules themselves are best answered with a
link to the Banco Central document and section that applies.

## Build

Requirements: JDK 21 or newer. Maven is provided by the wrapper.

```bash
./mvnw verify
```

`verify` compiles with all lint warnings as errors, runs the tests, checks coverage (JaCoCo, at least
85 percent of lines and branches per module) and builds the javadoc with doclint, failing on warnings.

## Guidelines

- Keep the core module free of runtime dependencies.
- Every rule that comes from a specification needs a test, and the test or the code should name the
  document and section.
- New public API needs javadoc and a short mention in the README.
- Source files are ASCII. Write accented test data with Unicode escapes, such as `"S\u00e3o Paulo"`.
- Error messages are in English and say what was expected and what was found.
- One logical change per pull request, with tests.

## Reporting a payload that is rejected

Include the payload (replace the key and names with fictitious ones, then fix the CRC with
`Crc16.hex`), the problems returned by `BrCode.validate`, and which bank or app generated it.
