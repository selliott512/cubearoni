# Cubearoni Contributor Guide

## Project Overview

Cubearoni is a Java 21 Rubik's Cube manipulation and solving library. It provides:

- a command-line interface in `org.selliott.cubearoni.Cubearoni`
- reusable cube and solver classes in `org.selliott.cubearoni`
- a TeaVM bridge for browser and Node.js clients
- a desktop cube viewer based on the historical AnimCube applet

The project is under active development and has not yet had a public release.
Read `README.md` for the user-facing command-line and database documentation.

## Repository Layout

- `src/main/java/org/selliott/cubearoni/`: cube model, move utilities, CLI, and solvers
- `src/main/java/org/selliott/cubearoni/web/`: TeaVM JavaScript bridge
- `src/main/java/org/selliott/animcube/`: desktop AnimCube viewer
- `src/test/java/`: JUnit 5 tests
- `db/`: checked-in CFOP algorithm files and locally generated solver databases
- `resources/cube-files/`: cube, facelet, move, and expected-output test data
- `resources/misc/`: supporting and historical test data
- `bin/`: executable maintenance and conversion scripts
- `examples/`: Node.js and browser TeaVM examples

## Build and Test

Use Maven from the repository root:

```bash
mvn package
```

Useful variants:

```bash
mvn test
mvn -DskipTests package
mvn -Pteavm package
mvn -Pteavm,teavm-debug package
mvn -Panimcube exec:java
```

The normal CLI can be run with:

```bash
mvn exec:java -Dexec.args="-h"
```

## Database Conventions

- Files named `db/*.crdb.gz` are generated artifacts and are ignored by Git.
- Files named `db/cfop-*.txt` are checked-in CFOP algorithm databases. New files
  are discovered dynamically and create matching `cfop-*` solver names.
- `db/cfop-help.txt` documents the CFOP text format; `db/cfop-custom.txt` is a
  template and is not treated as a complete algorithm set by tests.
- Database generation and testing can be expensive. Prefer a small solver such
  as `cross`, `ldb-corner`, or `up-cubies-up` when validating infrastructure.
- When a database is generated with `-N`, use the same `-N` value to solve or
  test it.

## Code Conventions

- Preserve the existing four-space Java indentation and nearby coding style.
- Add concise Javadoc to public or protected classes and methods.
- Use canonical internal face labels `L`, `R`, `D`, `U`, `B`, and `F`; colors
  are an input/output mapping rather than the cube's internal identity.
- Keep move-number flag behavior centralized in `Cube` and move translation in
  `Util`; do not duplicate move encoding logic in individual solvers.
- Prefer the generic indexing and database machinery in `Solver` when adding a
  database-backed solver.
- CFOP is a composite solver whose variants are selected by text database name.
  Avoid adding a Java subclass solely to introduce another `cfop-*.txt` file.
- Solver instances and the TeaVM bridge are shared and mutable. JavaScript
  clients must await one solver operation before starting another.

## Validation

Run `mvn test` after Java or test-data changes. Also run the relevant focused
command when changing a solver, database format, CFOP file, Python utility, or
TeaVM bridge. Do not commit generated files under `target/`, Python
`__pycache__/` directories, `.new` repair files, or generated `.crdb.gz` files.
