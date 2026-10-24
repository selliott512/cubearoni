// Minimal Node example that loads Cubearoni (transpiled by TeaVM), supplies DB
// files on demand, solves cubes, and prints the resulting move sequences.
const fs = require("fs");
const path = require("path");
const { performance } = require("perf_hooks");

const root = path.resolve(__dirname, "..");
const rc = require(path.join(root, "target/teavm/cubearoni.js"));
const dbDir = path.join(root, "db");
const args = process.argv.slice(2);
const extMovesMode = rc.getExtMovesModeApplied();

function usage() {
  console.log("Usage:");
  console.log("  node examples/node-teavm-ex.js <cube-file> [<cube-file> ...]");
  console.log("  node examples/node-teavm-ex.js -m <moves>");
  console.log("Examples:");
  console.log("  node examples/node-teavm-ex.js resources/cube-files/scrambled-1.cubearoni");
  console.log("  node examples/node-teavm-ex.js -m \"R U R' U'\"");
}

if ((args.length === 0) || args.includes("-h") || args.includes("--help")) {
  usage();
  process.exit(0);
}

rc.clearDBFiles();
rc.setDBFileLoader(async (fileName) => {
  // Cubearoni caches these bytes after the first successful callback.
  const dbPath = path.join(dbDir, fileName);
  if (!fs.existsSync(dbPath)) {
    throw new Error("DB file is missing: " + dbPath);
  }
  return new Uint8Array(await fs.promises.readFile(dbPath));
});

async function main() {
if (args[0] === "-m") {
  const moves = args.slice(1).join(" ").trim();
  if (moves.length === 0) {
    console.error("No moves provided after -m.");
    process.exit(1);
  }

  const beforeMillis = performance.now();
  const result = await rc.solveJSON(null, "cfop", "U", "", 0,
    extMovesMode, false, moves, false, null);
  const elapsedMillis = performance.now() - beforeMillis;
  if (!result.ok) {
    console.error("TeaVM solve error for moves:", result.error);
    if (result.stack) {
      console.error(result.stack);
    }
    process.exit(1);
  }

  console.log();
  console.log("-m", moves);
  console.log(result.result);
  console.log(`solveJSON elapsed: ${elapsedMillis.toFixed(3)} millis`);
} else {
  for (const cubePathArg of args) {
    const cubePath = path.resolve(cubePathArg);
    if (!fs.existsSync(cubePath)) {
      console.error("Cube file does not exist:", cubePathArg);
      process.exit(1);
    }
    const cube = fs.readFileSync(cubePath, "utf8");

    const beforeMillis = performance.now();
    const result = await rc.solveJSON(cube, "cfop", "U", "", 0,
      extMovesMode, false, null, false, null);
    const elapsedMillis = performance.now() - beforeMillis;
    if (!result.ok) {
      console.error("TeaVM solve error for", cubePathArg + ":", result.error);
      if (result.stack) {
        console.error(result.stack);
      }
      process.exit(1);
    }
    console.log();
    console.log(cubePathArg);
    console.log(result.result);
    console.log(`solveJSON elapsed: ${elapsedMillis.toFixed(3)} millis`);
  }
}
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
