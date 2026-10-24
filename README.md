# Cubearoni 0.9.0

<p align="center">
  <img src="images/cubearoni-icon.jpg"
       alt="A bowl filled with colorful twisty cubes"
       width="314">
</p>

by Steven L. Elliott

## Overview

Cubearoni provides both a command-line interface and a simple API for manipulating Rubik's cubes.
Its primary role is as a solver/manipulation backend, including for separate Rubik's cube
frontends. It is used by [QWERTYcube](https://github.com/selliott512/qwertycube), a web-based
Rubik's cube simulator and solver.

A desktop GUI is also provided through a modified version of AnimCube. See
[AnimCube History](#animcube-history) for details.

This documentation is available in `Cubearoni.java`, this README, and:
- http://selliott.org/utilities/Cubearoni

## Building

This is a Maven project. To compile, run tests, and build the JAR:

```bash
mvn package
```

This produces `target/cubearoni-*.jar`.

### TeaVM (JavaScript build)

An experimental TeaVM build is available as an opt-in Maven profile:

```bash
mvn -Pteavm package
```

This generates `target/teavm/cubearoni.js` as a UMD module (usable from Node and browsers).

For a debug-friendly TeaVM build (source maps, non-minified symbols):

```bash
mvn -Pteavm,teavm-debug package
```

The generated module exports:
- `clearDBFiles()`
- `setDBFileLoader(loader)` where `loader(fileName)` returns a
  `Promise<Uint8Array>`
- `solveJSON(cubeText, solverName, userFace, stopAfterPhase, limit, extMovesMode, markPhaseStarts, moves, leftHanded, options)`
  returns a promise and `options` is either `null` or an array like
  `["key=value", ...]`
- `getCFOPMovesForNameJSON(solverName, name, leftHanded)` also returns a promise
- `movesToFaceletsJSON(moves, faceletsOrder)` remains synchronous

Promises from DB-dependent methods suffixed with `JSON` resolve to a JSON object:
- success: `{"ok":true,"result":"..."}`
- failure: `{"ok":false,"error":"...","stack":"..."}`

Example from Node:

```js
const fs = require("fs");
const cubearoni = require("./target/teavm/cubearoni.js");
(async () => {
  cubearoni.clearDBFiles();
  cubearoni.setDBFileLoader(async (fileName) =>
    new Uint8Array(await fs.promises.readFile("./db/" + fileName)));
  const mode = cubearoni.getExtMovesModeApplied();
  const result = await cubearoni.solveJSON(cubeText, "cfop", "U", "", 0,
    mode, false, null, false, null);
  if (!result.ok) {
    console.error(result.error);
  } else {
    console.log(result.result);
  }
})();
```

Current limitations:
- this is a first-pass bridge focused on solver logic and move output.
- callers must configure a DB loader before invoking a solver operation.
- callers must await one solver operation before starting another because
  solver instances are shared and mutable.
- loaded files are cached; call `clearDBFiles()` to force them to be loaded
  again.

## Running

The same solve can be run in multiple ways. Example arguments used below:

```text
-n cross -i resources/scrambled-1.cubearoni -ln cfop -MC
```

1. Run via Maven `exec:java`:

```bash
mvn exec:java -Dexec.args="-n cross -i resources/scrambled-1.cubearoni -ln cfop -MC"
```

2. Run the packaged JAR:

```bash
java -cp target/cubearoni-*.jar org.selliott.cubearoni.Cubearoni -n cross -i resources/scrambled-1.cubearoni -ln cfop -MC
```

3. Run compiled class files directly:

```bash
java -cp target/classes org.selliott.cubearoni.Cubearoni -n cross -i resources/scrambled-1.cubearoni -ln cfop -MC
```

Run the AnimCube desktop viewer through Maven:

```bash
mvn -Panimcube exec:java
```

## Databases

For some subset solvers, Cubearoni can generate and use full state databases. Generate a database with `-g`.

Supported database-backed solvers:

- `ldb-corner`: solve the Left Down Back corner
- `cfop`: solves with CFOP using `cfop-speedsolving.txt`
- `cfop-*`: a CFOP variant discovered from a matching `db/cfop-*.txt` file
- `cross`: solves the cross ("C" in CFOP)
- `one-side`: solves one side
- `up-cubies-up`: moves the upper-layer cubies into the upper layer without
  caring about their order or orientation

Database files are named `<solver-name>.crdb.gz`.
For example, `cross` uses `cross.crdb.gz`.

CFOP solvers use text algorithm files rather than `.crdb.gz` databases. Adding
`db/cfop-example.txt` makes `cfop-example` available without a Java code change.
With `-d`, CFOP variants are discovered from the selected database directory.

## Database Encoding (Compact v1)

Cubearoni databases embed a small header and then a binary payload. We plan to move to a compact encoding with `version=1` and drop support for the older sparse format.

Header example:

```text
Cubearoni

name=Cross
version=1
```

Compact v1 conventions (planned):

- **Coordinate order:** when a piece is encoded by `(x, y, z, flip)`, the natural numeric order is `x` most-significant, then `y`, then `z`, then `flip` as the least-significant bit.
- **Piece order:** a fixed piece order is used and is derived from the solved position. In the solved position the natural numeric order (`x`, `y`, then `z`) is used.
- **Solved index:** the solved position maps to index `0` whenever possible.
- **Tight packing:** compact ranking removes gaps caused by already-occupied slots.
- **Spatial vs compact index:**
  - *Spatial index* is the index into the fixed list of allowed positions (e.g., 12 edge slots).
  - *Compact index* removes earlier occupied spatial slots (a mixed-radix permutation rank).
- **Nibble packing:** when the maximum distance is ≤ 15, two entries are stored per byte: even index in the low nibble, odd index in the high nibble.

## Command-Line Syntax

When invoked from the command line, Cubearoni can read cube files, apply moves, solve subsets of the cube using a database, test solvability, and more.

`.cubearoni` files use canonical face labels: `L`, `R`, `U`, `D`, `F`, `B`.

```text
Usage: Cubearoni [options]
  -b back-pos    back text position (bottom|right|top|left or b|r|t|l) or facelets order (for example URFDLB)
  -c             use color for output
  -C             include the final cube in the output
  -d dir         database directory (default: db)
  -e             list the solvers
  -f up-face     face to treat as up for the overall solve (default: U)
  -F             mirror the -m move list across the YZ plane
  -g             generate the database
  -h             this help message
  -i input-cube  input cube (default: solved)
  -j facelets    input cube as 54-char facelets string
  -I             apply the inverse of moves passed with -m
  -k key=value   solver option override (repeatable)
  -L             left-handed solver mode
  -l             solve with the solver specified
  -m input-list  input list of moves
  -M             include the moves applied in the output
  -n solver-name name of the solver to use
  -N limit       the maximum number of moves the mover will generate
  -o output-file output cube or moves file
  -p last-phase  stop after the phase specified
  -P             include phase start markers in the output moves (cfop only)
  -r rnd-seed    seed to random generator (deterministic scrambles)
  -s num-moves   scramble with a given number of HTM moves
  -S             output the cube pre-solve
  -t             test the database
  -v             verbose
```

Additional option notes:

- `-b` controls output format. Pass a back position (`bottom`, `right`, `top`, `left`, or `b|r|t|l`) for text-net output, or pass a six-letter facelets order (for example `URFDLB`) for one-line 54-char facelets output.
- If input is a 54-char facelets line, output defaults to facelets in the same detected order unless `-b` overrides it.
- `-f` sets the up face for the entire cube. Top-oriented phases (for example OLL/PLL) operate on that face; bottom-oriented phases (for example cross/F2L) operate on the opposite face.
- `-F` mirrors the move list from `-m` in the same way CFOP `m` rows are mirrored.
- `-i` and `-j` are mutually exclusive (`-i` reads a file, `-j` reads a 54-char facelets string).
- `-I` inverts the move list from `-m` before applying it.
- `-k` passes solver options as string key/value pairs and may be used multiple times; for CFOP, keys can be OLL/PLL names such as `1` or `Jb`, or direct signatures.
- `-L` enables left-handed solver behavior (currently affects CFOP keyed algorithms).
- `-p` is most useful with `-n cfop`; valid phases are `CROSS`, `F2L`, `OLL`, and `PLL`.
- `-P` is only supported with `-n cfop`; it prefixes each phase with `|` in the emitted move string.
- `-r` is used with `-s` to make scramble generation repeatable.
- if you use `-N` for database generation (`-g`), use the same `-N` for solving (`-l`) and testing (`-t`), or omit `-N` for all three.
- Cubearoni uses databases based on Dijkstra's algorithm. For example the `Cross` solver uses the `cross.crdb.gz` database.

## Example

Load Cubearoni file `resources/scrambled-1.cubearoni` and use the `one-side` database to solve for the up face. Output the moves (`-M`) and the final cube state (`-C`):

```text
> Cubearoni -i resources/scrambled-1.cubearoni -ln one-side -f U -MC
...

    UUU
    ...
LLL.FFF.RRR
    ...
    DDD
    ...
    BBB
```

## Misc

Although the cross, OLL and PLL are optimal in the sense that the solved in the minimum number of moves there is no lookahead to setup the next phase.

## AnimCube History

This project includes a modified version of AnimCube, originally written by
Josef Jelinek circa 2001-2004 as a Java applet. The original AnimCube page
described the applet as freeware and allowed modification, while requesting
that users contact the author about use or changes:
- https://animcubejs.cubing.net/old/sources/AnimCube/

The version included here has been updated for Cubearoni integration and modern
Java usage. Notable changes include removal of applet-only assumptions,
desktop launch support, Cubearoni solver integration, standard move notation
handling, configurable colors/solve face, and UI sizing fixes.

AnimCube is retained here for historical/desktop visualization purposes. Cubearoni's
primary role is as a solver backend; modern web usage is generally better
served by AnimCubeJS or another JavaScript cube viewer.

## License

Cubearoni's original code is licensed under the [MIT License](LICENSE).
The bundled historical AnimCube code retains its upstream terms; see
[Third-party notices](THIRD-PARTY-NOTICES.md) and [AnimCube History](#animcube-history).
The MIT license does not relicense that third-party code.

The license and third-party notices are included under `META-INF/` in the JAR.

The latest version of this code may be found at:
- https://github.com/selliott512/cubearoni
