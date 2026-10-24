// Minimal browser example that loads Cubearoni (transpiled by TeaVM), supplies DB
// files on demand, and solves the cube text entered in the page.
const cubearoni = window.cubearoni || window;
const statusEl = document.getElementById("status");
const resultEl = document.getElementById("result");
const cubeTextEl = document.getElementById("cubeText");
const movesEl = document.getElementById("moves");
const solverOptionsEl = document.getElementById("solverOptions");
const solverNameEl = document.getElementById("solverName");
const userFaceEl = document.getElementById("userFace");
const stopAfterPhaseEl = document.getElementById("stopAfterPhase");
const limitEl = document.getElementById("limit");
const leftHandedEl = document.getElementById("leftHanded");
const solveButtonEl = document.getElementById("solveButton");
const extMovesMode = cubearoni.getExtMovesModeApplied();

function setStatus(msg) {
  // Update status text shown near the Solve button.
  statusEl.textContent = msg;
}

function parseSolverOptions(text) {
  // Convert multiline key=value text into a string array for solveJSON().
  const lines = text.split(/\r?\n/).map((line) => line.trim())
    .filter((line) => line.length > 0);
  return lines.length > 0 ? lines : null;
}

if (typeof cubearoni.solveJSON !== "function"
    || typeof cubearoni.setDBFileLoader !== "function") {
  throw new Error("Required Cubearoni TeaVM functions are unavailable.");
}

cubearoni.clearDBFiles();
cubearoni.setDBFileLoader(async (fileName) => {
  // Files are cached by Cubearoni after the first successful callback.
  setStatus("Loading " + fileName + "...");
  const response = await fetch("../db/" + encodeURIComponent(fileName));
  if (!response.ok) {
    throw new Error("Failed to fetch " + fileName + ": " + response.status);
  }
  return new Uint8Array(await response.arrayBuffer());
});

async function loadDefaultCube() {
  // Preload a sample scramble so the page is usable immediately.
  try {
    const response = await fetch("../resources/cube-files/scrambled-1.cubearoni");
    if (!response.ok) {
      return;
    }
    cubeTextEl.value = await response.text();
  } catch (e) {
    // Ignore default load failures.
  }
}

async function solve() {
  // Invoke Cubearoni solveJSON() and display either moves or the returned error.
  try {
    solveButtonEl.disabled = true;
    resultEl.textContent = "";
    setStatus("Solving...");

    const solverName = solverNameEl.value;
    const userFace = userFaceEl.value;
    const stopAfterPhase = stopAfterPhaseEl.value;
    const limit = Number(limitEl.value) || 0;
    const cubeText = cubeTextEl.value;
    const moves = movesEl.value;
    const solverOptions = parseSolverOptions(solverOptionsEl.value);
    const leftHanded = leftHandedEl.checked;

    const beforeMillis = performance.now();
    const result = await cubearoni.solveJSON(
      cubeText, solverName, userFace, stopAfterPhase, limit, extMovesMode, false,
      moves, leftHanded, solverOptions
    );
    const elapsedMillis = performance.now() - beforeMillis;
    if (!result.ok) {
      resultEl.textContent = result.error + (result.stack ? "\n\n" + result.stack : "");
      setStatus("Solve failed (" + elapsedMillis.toFixed(3) + " millis)");
      return;
    }

    resultEl.textContent = result.result + "\n\nsolveJSON elapsed: "
      + elapsedMillis.toFixed(3) + " millis";
    setStatus("Solved (" + elapsedMillis.toFixed(3) + " millis)");
  } catch (e) {
    resultEl.textContent = String(e);
    setStatus("Error");
  } finally {
    solveButtonEl.disabled = false;
  }
}

solveButtonEl.addEventListener("click", solve);
loadDefaultCube();
