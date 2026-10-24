package org.selliott.cubearoni;

// A simple test suite.
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.selliott.cubearoni.Cube.M_B2;
import static org.selliott.cubearoni.Cube.M_D;
import static org.selliott.cubearoni.Cube.M_F2;
import static org.selliott.cubearoni.Cube.M_L;
import static org.selliott.cubearoni.Cube.M_L2;
import static org.selliott.cubearoni.Cube.M_LP;
import static org.selliott.cubearoni.Cube.M_R;
import static org.selliott.cubearoni.Cube.M_R2;
import static org.selliott.cubearoni.Cube.M_RP;
import static org.selliott.cubearoni.Cube.M_U;
import static org.selliott.cubearoni.Cube.M_UP;
import static org.selliott.cubearoni.Cube.VALID_MOVES;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CubearoniTest {
    private static final String SCRAMBLED_1 = "resources/cube-files/scrambled-1.cubearoni";
    private static final Pattern SCRAMBLED_PATTERN = Pattern
            .compile("resources/cube-files/scrambled-(\\d+)(-.*)?\\.cubearoni");
    private static final String CORNER_TWISTED = "resources/cube-files/corner-twisted.cubearoni";
    private static final String EDGE_FLIPPED = "resources/cube-files/edge-flipped.cubearoni";
    private static final String EDGE_SWAPPED = "resources/cube-files/edge-swapped.cubearoni";
    private static final String MOVES_E = "resources/cube-files/E.cubearoni";
    private static final String MOVES_E_WIDE = "resources/cube-files/E-wide.cubearoni";
    private static final String MOVES_F = "resources/cube-files/F.cubearoni";
    private static final String MOVES_R = "resources/cube-files/R.cubearoni";
    private static final String MOVES_RUF = "resources/cube-files/RUF.cubearoni";
    private static final String MOVES_RUF_WIDE = "resources/cube-files/RUF-wide.cubearoni";
    private static final String SOLVED = "resources/cube-files/solved.cubearoni";
    private static final String SOLVED_WIDE = "resources/cube-files/solved-wide.cubearoni";
    private static final String SUPERFLIP = "resources/cube-files/superflip.cubearoni";
    private static final String TURNED = "resources/cube-files/solved-turned.cubearoni";

    // Solvable files.
    private static final String[] cubearoniFilesS = new String[] { MOVES_E, MOVES_E_WIDE,
            MOVES_F, MOVES_R, MOVES_RUF, MOVES_RUF_WIDE, SOLVED, SOLVED_WIDE,
            SUPERFLIP, TURNED };

    private static final String[] cubearoniFilesUS = new String[] { CORNER_TWISTED,
            EDGE_FLIPPED, EDGE_SWAPPED };

    private static String[] cubearoniFiles;

    static final Map<String, String> moveCubeMap = new HashMap<String, String>();
    static {
        moveCubeMap.put("", SOLVED);

        moveCubeMap.put("F", MOVES_F);
        moveCubeMap.put("R", MOVES_R);
        moveCubeMap.put("RUF", MOVES_RUF);

        moveCubeMap.put("UR2FBRB2RU2LB2RU'D'R2FR'LB2U2F2", SUPERFLIP);
        moveCubeMap.put("U R2 F B R B2 R U2 L B2 R U' D' R2 F R' L B2 U2 F2",
                SUPERFLIP);
        moveCubeMap.put(
                "U+ R2 F+ B+ R+ B2 R+ U2 L+ B2 R+ U- D- R2 F+ R- L+ B2 U2 F2",
                SUPERFLIP);
        moveCubeMap.put("", "resources/cube-files/solved.cubearoni");
        moveCubeMap
                .put("L2 R' B F2 L' R D' U L' R F2 L B' L' R2 D2 U' F U' F U' B2 F2 R' U2 F2 R' B F2 U'",
                        SCRAMBLED_1);
    }

    static final Map<String, Boolean> solvabilityMap = new HashMap<String, Boolean>();
    static {
        cubearoniFiles = new String[cubearoniFilesS.length + cubearoniFilesUS.length];

        int idx = 0;
        for (final String cubearoniFile : cubearoniFilesS) {
            solvabilityMap.put(cubearoniFile, true);
            cubearoniFiles[idx++] = cubearoniFile;
        }
        for (final String cubearoniFile : cubearoniFilesUS) {
            solvabilityMap.put(cubearoniFile, false);
            cubearoniFiles[idx++] = cubearoniFile;
        }
    }

    static final Map<String, String> pruneMap = new HashMap<String, String>();
    static {
        pruneMap.put("", "");
        pruneMap.put("R", "R");
        pruneMap.put("RUD", "RUD");
        pruneMap.put("RLUDFBR'L'U'D'F'B'R2L2U2D2F2B2",
                "RLUDFBR'L'U'D'F'B'R2L2U2D2F2B2");
        pruneMap.put("RRRRRRRRRR", "R2");
        pruneMap.put("RUUR", "RU2R");
        pruneMap.put("RURUURUUUR", "RURU2RU'R");
        pruneMap.put("U2U2RUURU2U2", "RU2R");
        pruneMap.put("FFFF", "");
        pruneMap.put("RRRRUUUUDDDD", "");
    }

    @AfterEach
    public void after() throws Exception {
    }

    @Test
    public void badFile() throws Exception {
        assertThrows(CubearoniException.class,
                () -> new Cube("resources/cube-files/does-not-exist.cubearoni").moves(""));
    }

    @Test
    public void badMove() throws Exception {
        assertThrows(CubearoniException.class, () -> new Cube().moves("X"));
    }

    @BeforeEach
    public void before() throws Exception {
    }

    @Test
    public void expectedCubes() throws Exception {
        for (final Iterator<Entry<String, String>> it = moveCubeMap.entrySet()
                .iterator(); it.hasNext();) {
            final Entry<String, String> pair = it.next();
            final String moves = pair.getKey();
            final String cfName = pair.getValue();
            System.out.println("Testing that moves \"" + moves
                    + "\" produces cube \"" + cfName + "\".");
            final Cube cSolved = new Cube();
            cSolved.moves(moves);

            final ByteArrayOutputStream baSolved = new ByteArrayOutputStream();
            final PrintStream psSolved = new PrintStream(baSolved);
            cSolved.print(psSolved);

            final Cube cRead = new Cube(cfName);

            final ByteArrayOutputStream baRead = new ByteArrayOutputStream();
            final PrintStream psRead = new PrintStream(baRead);
            cRead.print(psRead);

            assertTrue(Arrays.equals(baSolved.toByteArray(),
                    baRead.toByteArray()));
        }
    }

    @Test
    public void testCubearoniFileSolvability() throws Exception {
        for (final String cubearoniFile : cubearoniFiles) {
            System.out.println("Testing the solvability of \"" + cubearoniFile
                    + "\".");
            final Cube cube = new Cube(cubearoniFile);
            assertEquals(solvabilityMap.get(cubearoniFile), cube.isSolveable());
        }
    }

    @Test
    public void parseCubearoniFileWithoutSeparators() throws Exception {
        final String[] files = new String[] {
                "resources/cube-files/scrambled-1.cubearoni",
                "resources/cube-files/scrambled-1-back-bottom.cubearoni",
                "resources/cube-files/scrambled-1-back-top.cubearoni",
                "resources/cube-files/scrambled-1-back-right.cubearoni",
                "resources/cube-files/solved-wide.cubearoni"
        };
        for (final String cubearoniFile : files) {
            final String withSeparators = Files.readString(Paths.get(cubearoniFile),
                    StandardCharsets.UTF_8);
            final String withoutSeparators = stripUnwrappedSeparators(withSeparators);
            final Cube cubeWithSeparators = new Cube(null, withSeparators, null);
            final Cube cubeWithoutSeparators = new Cube(null, withoutSeparators, null);
            assertEquals(cubeToString(cubeWithSeparators), cubeToString(cubeWithoutSeparators),
                    "Parsed cube mismatch after stripping separators for " + cubearoniFile);
        }

        // Tabs used for indentation should parse the same as spaces.
        final String backLeftPath = "resources/cube-files/scrambled-1-back-left.cubearoni";
        final String backLeftSpaces = Files.readString(Paths.get(backLeftPath),
                StandardCharsets.UTF_8);
        final String backLeftTabs = backLeftSpaces.replace(' ', '\t');
        final Cube cubeWithSpaces = new Cube(null, backLeftSpaces, null);
        final Cube cubeWithTabs = new Cube(null, backLeftTabs, null);
        assertEquals(cubeToString(cubeWithSpaces), cubeToString(cubeWithTabs),
                "Parsed cube mismatch after replacing indentation spaces with tabs.");
    }

    @Test
    public void parseFaceletsSolvedUrfdlb() throws Exception {
        final String solvedFacelets = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
        final Cube cube = new Cube(null, solvedFacelets, null);
        assertTrue(cube.isSolved());
        assertEquals(solvedFacelets, cubeToString(cube).trim());
    }

    @Test
    public void parseFaceletsDetectsBlockOrderFromCenters() throws Exception {
        final String solvedFacelets = "RRRRRRRRRUUUUUUUUUFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
        final Cube cube = new Cube(null, solvedFacelets, null);
        assertTrue(cube.isSolved());
        assertEquals(solvedFacelets, cubeToString(cube).trim());
    }

    @Test
    public void solvedThroughYIgnoresHigherLayers() throws Exception {
        final Cube upperTurn = new Cube();
        upperTurn.moves("U");
        assertTrue(upperTurn.isSolvedThroughY(1));
        assertFalse(upperTurn.isSolved());

        final Cube rightTurn = new Cube();
        rightTurn.moves("R");
        assertFalse(rightTurn.isSolvedThroughY(1));
    }

    @Test
    public void printFaceletsRespectsConfiguredOrder() throws Exception {
        final Cube cube = new Cube();
        cube.getFormat().setFaceletsOrder("URFDLB");
        assertEquals("UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB",
                cubeToString(cube).trim());

        cube.getFormat().setFaceletsOrder("RUFDLB");
        assertEquals("RRRRRRRRRUUUUUUUUUFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB",
                cubeToString(cube).trim());
    }

    @Test
    public void parseFaceletsRejectsWrongLength() throws Exception {
        final String bad = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBB";
        assertThrows(CubearoniException.class, () -> new Cube(null, bad, null));
    }

    @Test
    public void parseFaceletsRejectsDuplicateCenters() throws Exception {
        final String bad = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBLBBBB";
        assertThrows(CubearoniException.class, () -> new Cube(null, bad, null));
    }

    @Test
    public void parseFaceletsRejectsNonInternalStickers() throws Exception {
        final String bad = "AAAAAAAAABBBBBBBBBCCCCCCCCCDDDDDDDDDEEEEEEEEEFFFFFFFFF";
        assertThrows(CubearoniException.class, () -> new Cube(null, bad, null));
    }

    @Test
    public void parseFaceletsRejectsUnknownSticker() throws Exception {
        final String bad = "XUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
        assertThrows(CubearoniException.class, () -> new Cube(null, bad, null));
    }

    @Test
    public void isDefaultCubeeSolved() throws Exception {
        final Cube cube = new Cube();
        assertTrue(cube.isSolveable());
    }

    @Test
    public void isSolveableSuperflip() throws Exception {
        final Cube cube = new Cube(SUPERFLIP);
        assertTrue(cube.isSolveable());
    }

    @Test
    public void solveLDB() throws CubearoniException {
        final Cube cube = new Cube();
        cube.moves(new int[] { M_L2, M_UP });
        final Solver solver = Solver.getSolver("ldb-corner");
        solver.generateDB();
        final int[] moves = solver.exec(cube);
        assertTrue(Arrays.equals(moves, new int[] { M_R2, M_D }));
    }

    @Test
    public void solveOneSide3() throws CubearoniException {
        final Solver solver = Solver.getSolver("one-side");
        // Be able to solve all cubes that are two moves from solved, but no
        // more.
        solver.setLimit(3);
        solver.generateDB();
        // Make sure all 144 two move possibilities are solvable.
        // TODO: This would be slightly faster if nonsense like R2R2 was
        // excluded.
        for (final int m1 : VALID_MOVES) {
            for (final int m2 : VALID_MOVES) {
                for (final int m3 : VALID_MOVES) {
                    final Cube cube = new Cube().move(m1).move(m2).move(m3);
                    final int[] moves = solver.exec(cube);
                    // If it took two moves to scramble it it must be solvable
                    // in two moves or less.
                    assertTrue(moves.length <= 3);
                }
            }
        }

        // Try a few sequences that are four moves long and make sure they are
        // not solvable.
        final int[][] movess = new int[][] { { M_R2, M_L2, M_F2, M_B2 },
                                             { M_B2, M_L2, M_F2, M_R2 } };
        int exCount = 0;
        int[] solvedMoves = new int[0];
        for (final int[] moves : movess) {
            final Cube cube = new Cube().moves(moves);
            try {
                solvedMoves = solver.exec(cube);
            } catch (CubearoniException e) {
                exCount++;
            }
        }
        assertEquals(movess.length, exCount, "Expected all of the " + movess.length
                + " sequences to be unsolvable, but " + (movess.length - exCount)
                + " were solvable. solvedMoves=" + Cube.movesToStr(solvedMoves));
    }

    @Test
    public void solveCFOP() throws Exception {
        final Solver solver = Solver.getSolver("cfop");
        final CFOP cfopSolver = (CFOP) solver;
        solver.loadDB();

        final List<String> cubearoniFiles = findMatchingCubearoniFiles(
                "resources/cube-files/.*\\.cubearoni");
        int matchedCount = 0;
        for (final String cubearoniFile : cubearoniFiles) {
            final Matcher matcher = SCRAMBLED_PATTERN.matcher(cubearoniFile);
            if (!matcher.matches()) {
                continue;
            }
            matchedCount++;

            final int scrambleNum = Integer.parseInt(matcher.group(1));
            final String movesFile = "resources/cube-files/" + scrambleNum + ".moves";
            assertTrue(Files.exists(Paths.get(movesFile)),
                    "Expected moves file missing: " + movesFile
                            + " (matched " + cubearoniFile + ")");
            final String expMoves = new String(Util.readFileFully(movesFile))
                    .stripTrailing();

            final String suffix = (matcher.group(2) == null) ? ""
                    : matcher.group(2);
            final String solvedFile = "resources/cube-files/solved" + suffix + ".cubearoni";
            assertTrue(Files.exists(Paths.get(solvedFile)),
                    "Expected solved file missing: " + solvedFile);

            // Read as string and pass to Cube() to test that code path too.
            final String cubeAsStr = new String(Util.readFileFully(cubearoniFile));
            final Cube cube = new Cube(null, cubeAsStr, null);
            assertFalse(cube.isSolved());

            final int[] moves = solver.exec(cube);

            // extMovesToStr() is used because OLL and PLL may emit predetermined
            // move sequences that have extended moves.
            final String movesAsStr = Cube.extMovesToStr(moves);

            // The CFOP solver is deterministic, so require exact move list.
            assertEquals(expMoves, movesAsStr, "For scramble #" + scrambleNum);
            assertTrue(cube.isSolved(), "For scramble #" + scrambleNum);

            final Cube cubeMarked = new Cube(null, cubeAsStr, null);
            final String movesMarked = cfopSolver.execMarked(cubeMarked);
            assertTrue(movesMarked.startsWith("|"),
                    "Marked moves should start with a phase marker.");
            assertEquals(4, movesMarked.chars().filter(c -> c == '|').count(),
                    "Expected exactly four phase markers.");
            assertEquals(expMoves, movesMarked.replace("|", ""),
                    "Removing phase markers should match expected moves.");
            assertTrue(cubeMarked.isSolved(), "Marked solve did not solve cube.");

            // Solved output should match the solved file variant for the same suffix.
            final ByteArrayOutputStream baCube = new ByteArrayOutputStream();
            final PrintStream psCube = new PrintStream(baCube);
            cube.print(psCube);
            assertTrue(Arrays.equals(baCube.toByteArray(),
                    Files.readAllBytes(Paths.get(solvedFile))));

            if (SCRAMBLED_1.equals(cubearoniFile)) {
                assertSolvedBackOrientations(cube);
            }
        }
        assertTrue(matchedCount > 0, "No scrambled cubearoni files were tested.");

        final String solvedMarked = cfopSolver.execMarked(new Cube());
        assertEquals("||||", solvedMarked,
                "Solved cube should produce four phase markers with no moves.");
    }

    @Test
    public void solveCFOPUsesNewOllKeyedRow() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        final Phase oldStopAfterPhase = cfopSolver.stopAfterPhase;
        final Path tempFile = Files.createTempFile("cfop-keyed-", ".txt");
        final String cfopText = "OLL ***,***,***,*** RUR' d\n";
        Files.writeString(tempFile, cfopText, StandardCharsets.UTF_8);
        try {
            cfopSolver.dbFile = tempFile.toString();
            cfopSolver.dbURLFile = null;
            cfopSolver.loadDB();
            cfopSolver.setStopAfterPhase("OLL");
            final String moves = Cube.extMovesToStr(cfopSolver.exec(new Cube()));
            assertEquals("RUR'", moves,
                    "Expected keyed OLL row to be used.");
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
            cfopSolver.stopAfterPhase = oldStopAfterPhase;
            Files.deleteIfExists(tempFile);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void solveCFOPLeftHandedMirrorsKeyedRows() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        final Phase oldStopAfterPhase = cfopSolver.stopAfterPhase;
        final boolean oldLeftHanded = cfopSolver.isLeftHanded();
        final Path tempFile = Files.createTempFile("cfop-keyed-left-", ".txt");
        final String cfopText = "OLL ***,***,***,*** RUR' d\n";
        Files.writeString(tempFile, cfopText, StandardCharsets.UTF_8);
        try {
            cfopSolver.dbFile = tempFile.toString();
            cfopSolver.dbURLFile = null;
            cfopSolver.setStopAfterPhase("OLL");
            cfopSolver.loadDB();

            cfopSolver.setLeftHanded(false);
            final String movesRight = Cube.extMovesToStr(cfopSolver.exec(new Cube()));
            assertEquals("RUR'", movesRight);

            cfopSolver.setLeftHanded(true);
            cfopSolver.loadDB();
            final String movesLeft = Cube.extMovesToStr(cfopSolver.exec(new Cube()));
            final String expectedLeft = Cube.extMovesToStr(Cube.mirrorMoves(
                    Util.moveStrToExtendedMoves("RUR'")));
            assertEquals(expectedLeft, movesLeft);
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
            cfopSolver.stopAfterPhase = oldStopAfterPhase;
            cfopSolver.setLeftHanded(oldLeftHanded);
            Files.deleteIfExists(tempFile);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void solveCFOPRejectsDuplicateKeyedSignatures() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        final Path tempFile = Files.createTempFile("cfop-keyed-dup-", ".txt");
        final String cfopText = ""
                + "OLL ***,***,***,*** RUR' d\n"
                + "OLL ***,***,***,*** FRUR'U'F' d\n";
        Files.writeString(tempFile, cfopText, StandardCharsets.UTF_8);
        try {
            cfopSolver.dbFile = tempFile.toString();
            cfopSolver.dbURLFile = null;
            assertThrows(CubearoniException.class, () -> cfopSolver.loadDB());
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
            Files.deleteIfExists(tempFile);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void solveCFOPAppliesKeyedOverrideFromOptions() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        final Phase oldStopAfterPhase = cfopSolver.stopAfterPhase;
        final Map<String, String> oldOptions = new HashMap<String, String>(
                cfopSolver.getOptions());
        try {
            cfopSolver.setStopAfterPhase("OLL");
            cfopSolver.setOptions(Map.of("***,***,***,***", "R"));
            cfopSolver.loadDB();

            final String moves = Cube.extMovesToStr(cfopSolver.exec(new Cube()));
            assertEquals("R", moves,
                    "Expected keyed OLL signature override to replace default row.");
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
            cfopSolver.stopAfterPhase = oldStopAfterPhase;
            cfopSolver.setOptions(oldOptions);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void solveCFOPAppliesKeyedOverrideWithExplicitDefaultFlag() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final Phase oldStopAfterPhase = cfopSolver.stopAfterPhase;
        final Map<String, String> oldOptions = new HashMap<String, String>(
                cfopSolver.getOptions());
        try {
            cfopSolver.setStopAfterPhase("OLL");
            cfopSolver.setOptions(Map.of("***,***,***,***", "R-d"));
            cfopSolver.loadDB();

            final String moves = Cube.extMovesToStr(cfopSolver.exec(new Cube()));
            assertEquals("R", moves,
                    "Expected explicit -d override suffix to be honored.");
        } finally {
            cfopSolver.stopAfterPhase = oldStopAfterPhase;
            cfopSolver.setOptions(oldOptions);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void solveCFOPAppliesKeyedOverrideWithDerivedSignature() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final Map<String, String> oldOptions = new HashMap<String, String>(
                cfopSolver.getOptions());
        try {
            cfopSolver.setOptions(Map.of("45", "UFRUR'U'F'U'",
                    "O", "none"));
            cfopSolver.loadDB();

            assertEquals("UFRUR'U'F'U'", Cube.extMovesToStr(
                    cfopSolver.keyedMovesByPhase.get(Phase.OLL)
                            .get("***,*U*,U*U,*U*")));
            assertEquals("UFRUR'U'F'U'", cfopSolver.getMovesForName("45"));
            final Cube cube = new Cube();
            cube.extMoves("FURU'R'F'", Util.EXT_TO_HTM_MODE_NONE);
            assertEquals("U'UFRUR'U'F'U'U", Cube.extMovesToStr(
                    cfopSolver.exec(cube)));
            assertTrue(cube.isSolved());
            assertEquals("", Cube.extMovesToStr(
                    cfopSolver.keyedMovesByPhase.get(Phase.PLL)
                            .get("111,222,333,444")));
            assertEquals("", cfopSolver.getMovesForName("O"));
        } finally {
            cfopSolver.setOptions(oldOptions);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void solveCFOPRejectsUnknownKeyedOverrideName() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final Map<String, String> oldOptions = new HashMap<String, String>(
                cfopSolver.getOptions());
        try {
            cfopSolver.setOptions(Map.of("NotAName", "FRUR'U'F'"));
            assertThrows(CubearoniException.class, () -> cfopSolver.loadDB());
        } finally {
            cfopSolver.setOptions(oldOptions);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void solveCFOPRejectsMismatchedKeyedOverrideName() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final Map<String, String> oldOptions = new HashMap<String, String>(
                cfopSolver.getOptions());
        try {
            cfopSolver.setOptions(Map.of("Jb", "FRUR'U'F'"));
            assertThrows(CubearoniException.class, () -> cfopSolver.loadDB());
        } finally {
            cfopSolver.setOptions(oldOptions);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void cfopLooksUpMovesByName() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        final Path tempFile = Files.createTempFile("cfop-named-", ".txt");
        final String cfopText = ""
                + "OLL ***,***,***,*** none d 0 1/216\n"
                + "OLL **U,*U*,UU*,U*U FRUR'U'RUR'U'F' m 2 7 2*4/216\n";
        Files.writeString(tempFile, cfopText, StandardCharsets.UTF_8);
        try {
            cfopSolver.dbFile = tempFile.toString();
            cfopSolver.dbURLFile = null;
            cfopSolver.loadDB();

            assertEquals("", cfopSolver.getMovesForName("0"));
            assertEquals("FRUR'U'RUR'U'F'", cfopSolver.getMovesForName("2"));
            assertEquals(Cube.extMovesToStr(Cube.mirrorMoves(
                    Util.moveStrToExtendedMoves("FRUR'U'RUR'U'F'"))),
                    cfopSolver.getMovesForName("7"));
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
            Files.deleteIfExists(tempFile);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void cfopTestRewritesMissingSignatures() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        final Path tempFile = Files.createTempFile("cfop-rewrite-", ".txt");
        final Path outFile = Paths.get(tempFile.toString() + ".new");
        final String cfopText = ""
                + "# Header\n"
                + "OLL none d 0\n"
                + "OLL FRUR'U'F' d 1 # inline\n"
                + "PLL BBB,RRR,FFF,LLL none d O 4/72\n";
        Files.writeString(tempFile, cfopText, StandardCharsets.UTF_8);
        try {
            cfopSolver.dbFile = tempFile.toString();
            cfopSolver.dbURLFile = null;
            cfopSolver.loadDB();
            assertEquals("FRUR'U'F'", cfopSolver.getMovesForName("1"));

            cfopSolver.test();
            assertTrue(Files.exists(outFile), "Expected rewritten CFOP DB file.");
            final List<String> lines = Files.readAllLines(outFile,
                    StandardCharsets.UTF_8);
            assertEquals("# Header", lines.get(0));
            assertEquals("OLL     ***,***,***,***     none                            d       0               1/216",
                    lines.get(1));
            assertEquals("OLL     *U*,***,*U*,U*U     FRUR'U'F'                       d       1               4/216  # inline",
                    lines.get(2));
            assertEquals("PLL     BBB,RRR,FFF,LLL     none                            d       O               1/72",
                    lines.get(3));
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(outFile);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void cfopTestDoesNotWriteCanonicalDB() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        final Path tempFile = Files.createTempFile("cfop-canonical-", ".txt");
        final Path outFile = Paths.get(tempFile.toString() + ".new");
        final String cfopText = ""
                + "# Header\n"
                + "OLL     ***,***,***,***     none                            d       0               1/216\n"
                + "OLL     *U*,***,*U*,U*U     FRUR'U'F'                       d       1               4/216\n";
        Files.writeString(tempFile, cfopText, StandardCharsets.UTF_8);
        try {
            cfopSolver.dbFile = tempFile.toString();
            cfopSolver.dbURLFile = null;
            cfopSolver.loadDB();
            cfopSolver.test();
            assertFalse(Files.exists(outFile),
                    "Canonical CFOP DB should not produce a .new file.");
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(outFile);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void cfopTestRepairsNetCubeRotation() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        final Path tempFile = Files.createTempFile("cfop-rotation-", ".txt");
        final Path outFile = Paths.get(tempFile.toString() + ".new");
        Files.writeString(tempFile, "OLL Y d 0\n", StandardCharsets.UTF_8);
        try {
            cfopSolver.dbFile = tempFile.toString();
            cfopSolver.dbURLFile = null;
            cfopSolver.test();
            final String output = Files.readString(outFile,
                    StandardCharsets.UTF_8);
            assertTrue(output.contains("YY'"),
                    "Expected a trailing Y' rotation: " + output);
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(outFile);
        }
    }

    @Test
    public void cfopTestRejectsInvalidOrDuplicateCases() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        final String[] invalidRows = new String[] {
                "OLL R d bad\n",
                "OLL U d bad\n",
                "PLL FRUR'U'F' d bad\n",
                "OLL FRUR'U'F' d 0\n"
                        + "OLL YFRUR'U'F'Y' d 1\n",
                "OLL UFRUR'U'F'U' d 0\n",
                "OLL FRUR'U'F'U d 0\n",
                "OLL none d 1\n",
                "OLL none d 0\n"
                        + "OLL FRUR'U'F' d 2\n",
                "PLL none d O\n"
                        + "PLL XR'UR'D2RU'R'D2R2X' d Aa\n",
        };
        final String[] expectedMessages = new String[] {
                "does not preserve F2L",
                "is labeled OLL but produces a PLL state",
                "is labeled PLL but produces an OLL state",
                "duplicates an AUF-equivalent OLL signature",
                "OLL algorithm begins with a U move",
                "OLL algorithm ends with a U move",
                "has first OLL name \"1\"; expected \"0\"",
                "OLL algorithm name \"1\" is missing",
                "PLL name \"Aa\", which is not lexically after \"O\"",
        };

        try {
            for (int i = 0; i < invalidRows.length; i++) {
                final Path tempFile = Files.createTempFile("cfop-invalid-", ".txt");
                final Path outFile = Paths.get(tempFile.toString() + ".new");
                try {
                    Files.writeString(tempFile, invalidRows[i],
                            StandardCharsets.UTF_8);
                    cfopSolver.dbFile = tempFile.toString();
                    cfopSolver.dbURLFile = null;
                    final CubearoniException exception = assertThrows(
                            CubearoniException.class, cfopSolver::test);
                    assertTrue(exception.getMessage().contains(expectedMessages[i]),
                            exception.getMessage());
                    assertFalse(Files.exists(outFile),
                            "Invalid CFOP DB should not produce a .new file.");
                } finally {
                    Files.deleteIfExists(tempFile);
                    Files.deleteIfExists(outFile);
                }
            }
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
        }
    }

    @Test
    public void solveCFOPKeyedOllSupportsExtendedMoves() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        final Phase oldStopAfterPhase = cfopSolver.stopAfterPhase;
        final Path tempFile = Files.createTempFile("cfop-keyed-ext-", ".txt");
        final String cfopText = "OLL ***,***,***,*** d2FRUR'U'F'd2 d\n";
        Files.writeString(tempFile, cfopText, StandardCharsets.UTF_8);
        try {
            cfopSolver.dbFile = tempFile.toString();
            cfopSolver.dbURLFile = null;
            cfopSolver.loadDB();
            cfopSolver.setStopAfterPhase("OLL");
            final String moves = Cube.extMovesToStr(cfopSolver.exec(new Cube()));
            final String expected = "d2FRUR'U'F'd2";
            assertEquals(expected, moves,
                    "Expected OLL to translate and execute extended moves.");
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
            cfopSolver.stopAfterPhase = oldStopAfterPhase;
            Files.deleteIfExists(tempFile);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void solveCFOPRejectsDuplicateNormalizedNewPllSignatures() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        final Path tempFile = Files.createTempFile("cfop-keyed-pll-dup-", ".txt");
        final String cfopText = ""
                + "PLL BBB,RRR,FFF,LLL RUR' d\n"
                + "PLL RRR,FFF,LLL,BBB FRUR'U'F' d\n";
        Files.writeString(tempFile, cfopText, StandardCharsets.UTF_8);
        try {
            cfopSolver.dbFile = tempFile.toString();
            cfopSolver.dbURLFile = null;
            assertThrows(CubearoniException.class, () -> cfopSolver.loadDB());
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
            Files.deleteIfExists(tempFile);
            cfopSolver.loadDB();
        }
    }

    @Test
    public void cfopKeyedCoverageCounts() throws Exception {
        final Path cfopPath = Paths.get("db/cfop-badmephisto-mirrored.txt");
        assertTrue(Files.exists(cfopPath), "Missing CFOP config file: " + cfopPath);

        int ollCount = 0;
        int pllCount = 0;
        for (final String rawLine : Files.readAllLines(cfopPath, StandardCharsets.UTF_8)) {
            final String line = rawLine.replaceFirst("#.*$", "").trim();
            if (line.isEmpty()) {
                continue;
            }
            final String[] tokens = line.split("\\s+");
            if (tokens.length < 4) {
                continue;
            }

            final String phase = tokens[0];
            if (!phase.equals("OLL") && !phase.equals("PLL")) {
                continue;
            }
            final String flags = getCFOPFlags(tokens);
            final int weight = flags.contains("m") ? 2 : 1;
            if (phase.equals("OLL")) {
                ollCount += weight;
            } else {
                pllCount += weight;
            }
        }

        assertEquals(58, ollCount, "Expected OLL coverage (including mirrors) to be 58.");
        assertEquals(22, pllCount, "Expected PLL coverage (including mirrors) to be 22.");
    }

    @Test
    public void cfopKeyedSignatureSymmetryStats() throws Exception {
        for (final Path cfopPath : getCFOPDBPaths()) {
            assertTrue(Files.exists(cfopPath), "Missing CFOP config file: " + cfopPath);
            System.out.println("=== " + cfopPath + " ===");
            final int expectedOllTotalUniqueRotations = 216;
            final int expectedPllTotalUniqueRotations = 72;

            int ollRows = 0;
            int ollMirrorable = 0;
            int ollNotMirrorable = 0;
            int ollTotalUniqueRotationsWeighted = 0;
            int pllRows = 0;
            int pllMirrorable = 0;
            int pllNotMirrorable = 0;
            int pllTotalUniqueRotationsWeighted = 0;
            int rowsChecked = 0;
            for (final String rawLine : Files.readAllLines(cfopPath, StandardCharsets.UTF_8)) {
                final String line = rawLine.replaceFirst("#.*$", "").trim();
                if (line.isEmpty()) {
                    continue;
                }
                final String[] tokens = line.split("\\s+");
                if (tokens.length < 4) {
                    continue;
                }

                final Phase phase;
                if (tokens[0].equals("OLL")) {
                    phase = Phase.OLL;
                } else if (tokens[0].equals("PLL")) {
                    phase = Phase.PLL;
                } else {
                    continue;
                }

                if (!CFOP.isSignature(tokens[1])) {
                    continue;
                }
                final String signature = tokens[1];
                final String flags = getCFOPFlags(tokens);
                final Set<String> rotations = getRotationsForSignature(phase, signature);
                final int uniqueRotations = rotations.size();
                assertTrue(uniqueRotations == 1 || uniqueRotations == 2 || uniqueRotations == 4,
                        "Unexpected unique rotation count for " + phase + " signature " + signature
                                + ": " + uniqueRotations);

                final String mirrored = normalizeForPhase(phase,
                        CFOP.mirrorSignature(signature));
                final boolean mirrorable = !rotations.contains(mirrored);
                if (flags.contains("m")) {
                    assertTrue(mirrorable,
                            "Row flagged as mirrored should be mirrorable: " + phase + " "
                                    + signature);
                }
                final int mirroredMultiplier = flags.contains("m") ? 2 : 1;
                final int weightedUniqueRotations = uniqueRotations * mirroredMultiplier;
                final int denominator = (phase == Phase.OLL)
                        ? expectedOllTotalUniqueRotations
                        : expectedPllTotalUniqueRotations;
                final String probability = (mirroredMultiplier == 2)
                        ? "2*" + uniqueRotations + "/" + denominator
                        : uniqueRotations + "/" + denominator;
                final String expectedProbability = extractProbability(rawLine);
                assertEquals(probability, expectedProbability,
                        "Probability suffix mismatch for row: " + rawLine);
                if (phase == Phase.OLL) {
                    ollRows++;
                    if (mirrorable) {
                        ollMirrorable++;
                    } else {
                        ollNotMirrorable++;
                    }
                    ollTotalUniqueRotationsWeighted += weightedUniqueRotations;
                } else {
                    pllRows++;
                    if (mirrorable) {
                        pllMirrorable++;
                    } else {
                        pllNotMirrorable++;
                    }
                    pllTotalUniqueRotationsWeighted += weightedUniqueRotations;
                }

                rowsChecked++;
                System.out.format("%-3s  %-15s  %-17s  %-18s  flags=%s  p=%8s%n",
                        phase, signature, "mirrorable=" + mirrorable,
                        "unique-rotations=" + uniqueRotations, flags, probability);
            }
            assertTrue(rowsChecked > 0, "No OLL/PLL keyed rows were analyzed.");
            assertEquals(expectedOllTotalUniqueRotations, ollTotalUniqueRotationsWeighted,
                    "OLL weighted unique rotation total changed unexpectedly.");
            assertEquals(expectedPllTotalUniqueRotations, pllTotalUniqueRotationsWeighted,
                    "PLL weighted unique rotation total changed unexpectedly.");
            System.out.println();
            System.out.format("OLL: mirrorable=%2d not-mirrorable=%2d total=%2d total-unique-rotations=%3d%n",
                    ollMirrorable, ollNotMirrorable, ollRows, ollTotalUniqueRotationsWeighted);
            System.out.format("PLL: mirrorable=%2d not-mirrorable=%2d total=%2d total-unique-rotations=%3d%n",
                    pllMirrorable, pllNotMirrorable, pllRows, pllTotalUniqueRotationsWeighted);
        }
    }

    @Test
    public void cfopDatabasesPassValidation() throws Exception {
        final CFOP cfopSolver = (CFOP) Solver.getSolver("cfop");
        final String oldDbFile = cfopSolver.dbFile;
        final java.net.URL oldDbURLFile = cfopSolver.dbURLFile;
        try {
            for (final Path cfopPath : getCFOPDBPaths()) {
                final Path outPath = Paths.get(cfopPath + ".new");
                cfopSolver.dbFile = cfopPath.toString();
                cfopSolver.dbURLFile = null;
                cfopSolver.test();
                assertFalse(Files.exists(outPath),
                        "CFOP DB is not canonical: " + cfopPath);
            }
        } finally {
            cfopSolver.dbFile = oldDbFile;
            cfopSolver.dbURLFile = oldDbURLFile;
        }
    }

    private static List<Path> getCFOPDBPaths() throws Exception {
        final List<Path> pathsFound;
        try (final var paths = Files.list(Paths.get("db"))) {
            pathsFound = paths
                    .filter(path -> path.getFileName().toString()
                            .matches("cfop-.*\\.txt"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("cfop-help.txt"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("cfop-custom.txt"))
                    .sorted()
                    .toList();
        }
        assertFalse(pathsFound.isEmpty(), "No CFOP config files found.");
        return pathsFound;
    }

    private static Set<String> getRotationsForSignature(final Phase phase,
            final String signature) throws Exception {
        final Set<String> rotations = new HashSet<String>();
        String cur = signature;
        for (int i = 0; i < 4; i++) {
            rotations.add(normalizeForPhase(phase, cur));
            cur = CFOP.rotateSignatureGroups(cur, Cube.M_U);
        }
        return rotations;
    }

    private static String normalizeForPhase(final Phase phase, final String signature) {
        return (phase == Phase.PLL) ? CFOP.normalizePllSignature(signature)
                : signature;
    }

    private static String extractProbability(final String rawLine)
            throws CubearoniException {
        final Matcher matcher = Pattern
                .compile("(?:p=\\s*)?((?:2\\*)?\\d+/\\d+)\\s*$")
                .matcher(rawLine);
        if (!matcher.find()) {
            throw new CubearoniException("Missing trailing probability in row: " + rawLine);
        }
        return matcher.group(1);
    }

    private static String getCFOPFlags(final String[] tokens)
            throws CubearoniException {
        int index = 1;
        if ((tokens.length > index) && CFOP.isSignature(tokens[index])) {
            index++;
        }
        for (int i = tokens.length - 1; i >= index; i--) {
            if (tokens[i].equals("d") || tokens[i].equals("m")) {
                return tokens[i];
            }
        }
        throw new CubearoniException("Invalid CFOP row flags.");
    }

    private static void assertSolvedBackOrientations(final Cube cube)
            throws Exception {
        final Map<Character, String> expectedByBackPos = new HashMap<Character, String>();
        expectedByBackPos.put(Format.BACK_BOTTOM,
                "resources/cube-files/solved-back-bottom.cubearoni");
        expectedByBackPos.put(Format.BACK_LEFT, "resources/cube-files/solved-back-left.cubearoni");
        expectedByBackPos.put(Format.BACK_RIGHT,
                "resources/cube-files/solved-back-right.cubearoni");
        expectedByBackPos.put(Format.BACK_TOP, "resources/cube-files/solved-back-top.cubearoni");

        for (final Entry<Character, String> entry : expectedByBackPos.entrySet()) {
            cube.getFormat().setBackPos(entry.getKey());
            final ByteArrayOutputStream baCube = new ByteArrayOutputStream();
            final PrintStream psCube = new PrintStream(baCube);
            cube.print(psCube);
            assertTrue(Arrays.equals(baCube.toByteArray(),
                    Files.readAllBytes(Paths.get(entry.getValue()))));
        }
    }

    private static List<String> findMatchingCubearoniFiles(final String regex) {
        try {
            return Files.list(Paths.get("resources/cube-files"))
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".cubearoni"))
                    .map(path -> "resources/cube-files/" + path.getFileName().toString())
                    .filter(path -> path.matches(regex)).sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Unable to list resource files: " + e,
                    e);
        }
    }

    private static String stripUnwrappedSeparators(final String cubeText) {
        final StringBuilder sb = new StringBuilder();
        for (final String line : cubeText.split("\\R")) {
            if (line.trim().equals("...")) {
                continue;
            }
            sb.append(line.replace(".", "")).append('\n');
        }
        return sb.toString();
    }

    private static String cubeToString(final Cube cube) {
        final ByteArrayOutputStream ba = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(ba);
        cube.print(ps);
        return ba.toString();
    }

    @Test
    public void getSetOneSide() throws CubearoniException {
        final Solver solver = Solver.getSolver("one-side");

        // Get a cube and apply some moves to it.
        final Cube cube = new Cube().move(M_R).move(M_U).move(M_L);

        // Get the number and set it again - should be the same cube.
        final int index = solver.getIndex(cube);
        solver.setIndex(cube, index);

        // Apply the reverse moves.
        cube.move(M_LP).move(M_UP).move(M_RP);

        // The result should be solved.
        assertTrue(solver.isSolved(cube));
    }

    @Test
    public void cubearoniFileIOCompare() throws Exception {
        for (final String cubearoniFile : cubearoniFilesS) {
            System.out.println("Comparing input and output for Cubearoni file \""
                    + cubearoniFile + "\".");

            final Cube cWide = new Cube(cubearoniFile);

            final ByteArrayOutputStream baWide = new ByteArrayOutputStream();
            final PrintStream psWide = new PrintStream(baWide);
            cWide.print(psWide);

            assertTrue(Arrays.equals(baWide.toByteArray(),
                    Files.readAllBytes(Paths.get(cubearoniFile))));
        }
    }

    @Test
    public void frostburnParserRawOutput() throws Exception {
        final Process process = new ProcessBuilder("bin/frostburn-oll-pll-parser.py",
                "resources/misc/frostburn/%s.html")
                .redirectErrorStream(true)
                .start();
        final String actual = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), actual);

        final String expected = Files.readString(
                Paths.get("resources/misc/frostburn/raw-out.txt"),
                StandardCharsets.UTF_8);
        assertEquals(expected, actual);
    }

    @Test
    public void prunning() throws Exception {
        for (final Iterator<Entry<String, String>> it = pruneMap.entrySet()
                .iterator(); it.hasNext();) {
            final Entry<String, String> pair = it.next();
            final String inStr = pair.getKey();
            final String outStrExp = pair.getValue();
            final int[] inInts = Util.moveStrToMoves(inStr);
            final int[] outInts = Cube.pruneMoves(inInts);
            final String outStr = Cube.movesToStr(outInts);
            assertEquals(outStrExp, outStr);
        }
    }

    @Test
    public void scoreMoves() throws Exception {
        final int first = VALID_MOVES[0];
        final int second = VALID_MOVES[1];
        assertEquals(10_000, Util.scoreMoves(new int[] { first }));
        assertEquals(20_001, Util.scoreMoves(new int[] { first, second }));
        assertThrows(CubearoniException.class,
                () -> Util.scoreMoves(new int[] { Integer.MAX_VALUE }));
    }

    @Test
    public void translateExtendedMoves() throws Exception {
        final Map<String, String[]> vectorMap = new LinkedHashMap<String, String[]>();
        // Space ok?
        vectorMap.put("R X", new String[]{"R", "X'"});

        // A few moves at once.
        vectorMap.put("RX", new String[]{"R", "X'"});
        vectorMap.put("XR", new String[]{"R", "X'"});
        vectorMap.put("R'X", new String[]{"R'", "X'"});
        vectorMap.put("XR'", new String[]{"R'", "X'"});
        vectorMap.put("RY", new String[]{"R", "Y'"});
        vectorMap.put("YR", new String[]{"B", "Y'"});
        vectorMap.put("Y'R", new String[]{"F", "Y"});
        vectorMap.put("RXY", new String[]{"R", "Y'X'"});
        vectorMap.put("XYR", new String[]{"U", "Y'X'"});
        vectorMap.put("XU", new String[]{"F", "X'"});
        vectorMap.put("X'U", new String[]{"B", "X"});
        vectorMap.put("ZR", new String[]{"U", "Z'"});
        vectorMap.put("Z'R", new String[]{"D", "Z"});

        // Cube rotations that cancel out.
        vectorMap.put(
                "LRDUBF L'R'D'U'B'F' L2R2D2U2B2F2 XYZZ'Y'X'X2Y2Z2 "
                        + "LRDUBF L'R'D'U'B'F' L2R2D2U2B2F2",
                new String[]{
                        "LRDUBFL'R'D'U'B'F'L2R2D2U2B2F2LRDUBFL'R'D'U'B'F'L2R2D2U2B2F2"
                });

        // Many cube rotations at once.
        vectorMap.put("LRDUBF XYZ LRDUBF", new String[]{"LRDUBFBFUDLR", "Y'X2"});
        vectorMap.put("LRDUBF X'Y'Z' LRDUBF", new String[]{"LRDUBFBFDURL", "Y"});
        vectorMap.put("LRDUBF X2Y2Z2 LRDUBF", new String[]{"LRDUBFLRDUBF"});
        vectorMap.put("LRDUBF X2 LRDUBF", new String[]{"LRDUBFLRUDFB", "X2"});
        vectorMap.put("LRDUBF Y2 LRDUBF", new String[]{"LRDUBFRLDUFB", "Y2"});
        vectorMap.put("LRDUBF Z2 LRDUBF", new String[]{"LRDUBFRLUDBF", "Y2X2"});

        // Middle moves.
        vectorMap.put("M", new String[]{"RL'", "X"});
        vectorMap.put("E", new String[]{"UD'", "Y"});
        vectorMap.put("S", new String[]{"F'B", "Z'"});
        vectorMap.put("LRDUBF MES LRDUBF", new String[]{"LRDUBFRL'BF'L'RFBUDRL", "YX2"});

        // Equivalent cube rotations only as above in place of middle moves.
        // resulting in the same six move suffix.
        vectorMap.put("LRDUBF X'Y'Z LRDUBF", new String[]{"LRDUBFFBUDRL", "YX2"});

        // Wide moves.
        vectorMap.put("rRr", new String[]{"LRL", "X2"});
        vectorMap.put("ruf", new String[]{"LBL", "Y'X2"});
        vectorMap.put("r'u'f'", new String[]{"L'F'R'", "Y"});
        vectorMap.put("r2u2f2", new String[]{"L2U2B2"});
        vectorMap.put("r2u2'f2", new String[]{"L2U2'B2"});

        for (final Entry<String, String[]> pair : vectorMap.entrySet()) {
            final String inStr = pair.getKey();
            final String[] outExpValues = pair.getValue();
            final String outExpStrNone = outExpValues[0];
            final String outExpStrPrefix = (outExpValues.length > 1) ? outExpValues[1] : "";
            final String outExpStrPrefixed = outExpStrPrefix + outExpStrNone;

            // NONE mode.
            final int[] outArrayNoneFromStr = Util.extMovesToHTMMoves(
                    inStr, Util.EXT_TO_HTM_MODE_NONE);
            assertEquals(outExpStrNone, Cube.extMovesToStr(outArrayNoneFromStr),
                    "String translation mismatch for NONE mode for \"" + inStr + "\".");

            final int[] inArray = Util.moveStrToExtendedMoves(inStr);
            final int[] outArrayNoneFromArray = Util.extMovesToHTMMoves(
                    inArray, Util.EXT_TO_HTM_MODE_NONE);
            assertArrayEquals(outArrayNoneFromStr, outArrayNoneFromArray,
                    "NONE mode should match for string/array input.");

            // PREFIX mode.
            final int[] outArrayPrefixFromStr = Util.extMovesToHTMMoves(
                    inStr, Util.EXT_TO_HTM_MODE_PREFIX);
            assertEquals(outExpStrPrefixed, Cube.extMovesToStr(outArrayPrefixFromStr),
                    "String translation mismatch for PREFIX mode for \"" + inStr + "\".");

            final int[] outArrayPrefixFromArray = Util.extMovesToHTMMoves(
                    inArray, Util.EXT_TO_HTM_MODE_PREFIX);
            assertArrayEquals(outArrayPrefixFromStr, outArrayPrefixFromArray,
                    "PREFIX mode should match for string/array input.");

            // PREFIX output should contain only cube rotations in the prefix,
            // then only HTM moves.
            boolean inPrefix = true;
            int prefixSize = 0;
            for (final int move : outArrayPrefixFromStr) {
                if (Util.isCubeRotation(move)) {
                    prefixSize++;
                    assertTrue(inPrefix, "There can only be cube rotations in the prefix.");
                } else {
                    inPrefix = false;
                    assertFalse(Util.isExtMove(move),
                            "Expected HTM move, but got extended move "
                                    + Cube.movesToStr(new int[]{move}));
                }
            }
            final int expectedPrefixSize = (outExpValues.length > 1)
                    ? Util.moveStrToExtendedMoves(outExpValues[1]).length : 0;
            assertEquals(expectedPrefixSize, prefixSize,
                    "Prefix size was not as expected for " + inStr);

            // APPLIED mode should return pure HTM moves and match the old
            // two-pass behavior (PREFIX then NONE).
            final int[] outArrayAppliedFromStr = Util.extMovesToHTMMoves(
                    inStr, Util.EXT_TO_HTM_MODE_APPLIED);
            final int[] outArrayAppliedFromArray = Util.extMovesToHTMMoves(
                    inArray, Util.EXT_TO_HTM_MODE_APPLIED);
            assertArrayEquals(outArrayAppliedFromStr, outArrayAppliedFromArray,
                    "APPLIED mode should match for string/array input.");
            for (final int move : outArrayAppliedFromStr) {
                assertFalse(Util.isExtMove(move),
                        "Expected HTM move in APPLIED mode, but got extended move "
                                + Cube.movesToStr(new int[]{move}));
            }
            final int[] outArrayAppliedLegacy = Util.extMovesToHTMMoves(
                    outArrayPrefixFromStr, Util.EXT_TO_HTM_MODE_NONE);
            assertArrayEquals(outArrayAppliedLegacy, outArrayAppliedFromStr,
                    "APPLIED mode should match legacy two-pass behavior for \"" + inStr + "\".");

            // NONE and APPLIED should preserve move-count and offsets.
            assertEquals(outArrayNoneFromStr.length, outArrayAppliedFromStr.length,
                    "NONE/APPLIED length mismatch for \"" + inStr + "\".");
            for (int i = 0; i < outArrayNoneFromStr.length; i++) {
                assertEquals(Util.getOffset(outArrayNoneFromStr[i]),
                        Util.getOffset(outArrayAppliedFromStr[i]),
                        "NONE/APPLIED offset mismatch at index " + i + " for \"" + inStr + "\".");
            }
        }

        assertThrows(CubearoniException.class, () -> Util.moveStrToExtendedMoves("x"));
    }

    @Test
    public void solverNamesInSync() {
        final Map<String, String> nameMap = new LinkedHashMap<String, String>();
        nameMap.put(LDBCorner.NAME, Solver.getNameFromClass(LDBCorner.class));
        nameMap.put(LBSlotSafe.NAME, Solver.getNameFromClass(LBSlotSafe.class));
        nameMap.put(LBSlotUnsafe.NAME, Solver.getNameFromClass(LBSlotUnsafe.class));
        nameMap.put(Cross.NAME, Solver.getNameFromClass(Cross.class));
        nameMap.put(OneSide.NAME, Solver.getNameFromClass(OneSide.class));
        nameMap.put(F2L.NAME, Solver.getNameFromClass(F2L.class));
        nameMap.put(TwoByTwo.NAME, Solver.getNameFromClass(TwoByTwo.class));
        nameMap.put(UpCubiesUp.NAME, Solver.getNameFromClass(UpCubiesUp.class));

        for (final Entry<String, String> pair : nameMap.entrySet()) {
            assertEquals(pair.getKey(), pair.getValue());
        }
    }

    @Test
    public void discoversCFOPSolversFromDBDirectory() throws Exception {
        final Path dbDir = Files.createTempDirectory("cubearoni-cfop-discovery-");
        try {
            Files.createFile(dbDir.resolve("cfop-alpha.txt"));
            Files.createFile(dbDir.resolve("cfop-custom.txt"));
            Files.createFile(dbDir.resolve("cfop-help.txt"));
            Files.createFile(dbDir.resolve("not-cfop.txt"));

            CFOP.discover(dbDir.toString());
            final Set<String> names = new HashSet<>(
                    Arrays.asList(Solver.getSolverNames()));
            assertTrue(names.contains("cfop-alpha"));
            assertTrue(names.contains("cfop-custom"));
            assertFalse(names.contains("cfop-help"));

            final Solver alpha = Solver.getSolver("cfop-alpha");
            assertTrue(alpha instanceof CFOP);
            assertEquals("cfop-alpha", alpha.getName());
            assertEquals(dbDir.resolve("cfop-alpha.txt").toString(),
                    alpha.getDbFile());

            final Solver alias = Solver.getSolver("cfop");
            assertEquals("cfop-speedsolving", alias.getName());
            assertEquals(dbDir.resolve("cfop-speedsolving.txt").toString(),
                    alias.getDbFile());
        } finally {
            CFOP.discover(Solver.DEFAULT_DB_DIR);
            Files.deleteIfExists(dbDir.resolve("cfop-alpha.txt"));
            Files.deleteIfExists(dbDir.resolve("cfop-custom.txt"));
            Files.deleteIfExists(dbDir.resolve("cfop-help.txt"));
            Files.deleteIfExists(dbDir.resolve("not-cfop.txt"));
            Files.deleteIfExists(dbDir);
        }
    }

    @Test
    public void missingFileLoaderCachesPlainAndGzipFiles() throws Exception {
        final byte[] plainBytes = "callback data".getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream gzipBytes = new ByteArrayOutputStream();
        try (final GZIPOutputStream gzip = new GZIPOutputStream(gzipBytes)) {
            gzip.write(plainBytes);
        }
        final int[] loadCount = new int[1];

        Util.clearInMemoryFiles();
        Util.setMissingFileLoader(fileName -> {
            loadCount[0]++;
            if (fileName.equals("plain.txt")) {
                return plainBytes;
            }
            if (fileName.equals("data.gz")) {
                return gzipBytes.toByteArray();
            }
            throw new IllegalArgumentException("Unexpected file " + fileName);
        });
        try {
            assertArrayEquals(plainBytes,
                    Util.readFileFully("/missing/path/plain.txt"));
            assertArrayEquals(plainBytes,
                    Util.readFileFully("/missing/path/plain.txt"));
            assertEquals(1, loadCount[0], "Plain file should be cached.");

            assertArrayEquals(plainBytes,
                    Util.readGZIPFully("/missing/path/data.gz"));
            assertArrayEquals(plainBytes,
                    Util.readGZIPFully("/missing/path/data.gz"));
            assertEquals(2, loadCount[0], "GZIP file should be cached.");

            Util.clearInMemoryFiles();
            assertArrayEquals(plainBytes,
                    Util.readFileFully("/missing/path/plain.txt"));
            assertEquals(3, loadCount[0],
                    "Clearing files should force another callback.");
        } finally {
            Util.setMissingFileLoader(null);
            Util.clearInMemoryFiles();
        }
    }

    @Test
    public void resolvesDynamicCFOPNameWithoutFilesystemDiscovery()
            throws Exception {
        try {
            final Solver solver = Solver.getSolver("cfop-callback-only");
            assertTrue(solver instanceof CFOP);
            assertEquals("cfop-callback-only", solver.getName());
            assertTrue(solver.getDbFile().endsWith(
                    "/cfop-callback-only.txt"));
        } finally {
            CFOP.discover(Solver.DEFAULT_DB_DIR);
        }
    }

    @Test
    public void inverseMovesRoundTrip() throws Exception {
        final int[] moves = Util.moveStrToExtendedMoves("d2FRUR'U'F'd2");
        final int[] inverse = Cube.inverseMoves(moves);
        final Cube cube = new Cube();
        cube.extMoves("d2FRUR'U'F'd2", Util.EXT_TO_HTM_MODE_NONE);
        assertFalse(cube.isSolved());
        cube.extMoves(Cube.extMovesToStr(inverse), Util.EXT_TO_HTM_MODE_NONE);
        assertTrue(cube.isSolved());
        assertArrayEquals(moves, Cube.inverseMoves(inverse));

        assertEquals("F2'U2'R2'",
                Cube.extMovesToStr(Cube.inverseMoves(
                        Util.moveStrToExtendedMoves("R2U2F2"))));
        assertEquals("F2U2R2",
                Cube.extMovesToStr(Cube.inverseMoves(
                        Util.moveStrToExtendedMoves("R2'U2'F2'"))));
    }

    @Test
    public void mirrorMovesHandlesXAndWideMoves() throws Exception {
        final int[] xMoves = Util.moveStrToExtendedMoves("XX'");
        assertEquals("XX'", Cube.extMovesToStr(Cube.mirrorMoves(xMoves)));

        final int[] rotations = Util.moveStrToExtendedMoves("XYZ");
        assertEquals("XY'Z'", Cube.extMovesToStr(Cube.mirrorMoves(rotations)));

        final int[] slices = Util.moveStrToExtendedMoves("MSE");
        assertEquals("MS'E'", Cube.extMovesToStr(Cube.mirrorMoves(slices)));

        final int[] wideMoves = Util.moveStrToExtendedMoves("rr'");
        assertEquals("l'l", Cube.extMovesToStr(Cube.mirrorMoves(wideMoves)));

        final int[] doubleMoves = Util.moveStrToExtendedMoves("R2r2U2u2");
        assertEquals("L2'l2'U2'u2'",
                Cube.extMovesToStr(Cube.mirrorMoves(doubleMoves)));
    }

    @Test
    public void moveConstantsFollowExpectedBitPattern() throws Exception {
        final Pattern moveFieldPattern = Pattern
                .compile("^M_([A-Z]+?)(2P|2|P)?$");
        final class MoveField {
            final String name;
            final String rootName;
            final String suffix;
            final int value;

            MoveField(final String name, final String rootName, final String suffix,
                    final int value) {
                this.name = name;
                this.rootName = rootName;
                this.suffix = suffix;
                this.value = value;
            }
        }

        final List<MoveField> moveFields = new java.util.ArrayList<MoveField>();
        final Map<String, Integer> rootValueMap = new HashMap<String, Integer>();
        final Set<Integer> seenValues = new HashSet<Integer>();

        for (final Field field : Cube.class.getDeclaredFields()) {
            final int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)
                    || field.getType() != int.class) {
                continue;
            }
            final String name = field.getName();
            if (!name.startsWith("M_") || name.equals("M_NONE")
                    || name.startsWith("M_FLAG_")) {
                continue;
            }
            final Matcher matcher = moveFieldPattern.matcher(name);
            assertTrue(matcher.matches(), "Unexpected move constant name: " + name);
            moveFields.add(new MoveField(name, matcher.group(1), matcher.group(2),
                    field.getInt(null)));
        }

        for (final MoveField moveField : moveFields) {
            if (moveField.suffix != null) {
                continue;
            }
            final int value = moveField.value;
            assertEquals(0, value & Cube.M_FLAG_2P,
                    "Root move must not have flags: " + moveField.name);
            assertEquals(0, value % 4,
                    "Root move must be divisible by 4: " + moveField.name);
            if (rootValueMap.containsKey(moveField.rootName)) {
                assertEquals(rootValueMap.get(moveField.rootName).intValue(), value,
                        "Conflicting root values for " + moveField.rootName);
            } else {
                rootValueMap.put(moveField.rootName, value);
            }
            assertTrue(seenValues.add(value),
                    "Duplicate move value: " + value + " (" + moveField.name + ")");
        }

        for (final MoveField moveField : moveFields) {
            if (moveField.suffix == null) {
                continue;
            }
            final int value = moveField.value;

            final int suffixFlags;
            if ("P".equals(moveField.suffix)) {
                suffixFlags = Cube.OFF_P;
            } else if ("2".equals(moveField.suffix)) {
                suffixFlags = Cube.OFF_2;
            } else if ("2P".equals(moveField.suffix)) {
                suffixFlags = Cube.OFF_2P;
            } else {
                throw new CubearoniException(
                        "Unexpected move suffix in " + moveField.name);
            }

            assertTrue(rootValueMap.containsKey(moveField.rootName),
                    "Missing root move for " + moveField.name);
            final int expected = rootValueMap.get(moveField.rootName) | suffixFlags;
            assertEquals(expected, value,
                    "Unexpected flag encoding for move " + moveField.name);
            assertTrue(seenValues.add(value),
                    "Duplicate move value: " + value + " (" + moveField.name + ")");
        }
    }
}
