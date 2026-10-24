package org.selliott.cubearoni;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

// This phase corresponds to the active subsolver for this solver.
enum Phase {
    CROSS, F2L, OLL, PLL;
}

/**
 * Solves a cube using the Cross, F2L, OLL, and PLL phases with a configurable
 * OLL and PLL algorithm database.
 */
public class CFOP extends Composite {
    static final String ALIAS = "cfop";
    static final String DEFAULT_NAME = "cfop-speedsolving";
    private static final String FILE_PREFIX = "cfop-";
    private static final String FILE_SUFFIX = ".txt";
    private static final Set<String> discoveredNames = new HashSet<>();
    private static final String PHASE_OLL = "OLL";
    private static final String PHASE_PLL = "PLL";
    private static final String KEYED_ROW_FORMAT =
            "%-7s %-19s %-31s %-7s %-7s %-7s %s";
    private static final Pattern SIGNATURE_OLL_PATTERN_RE = Pattern
            .compile("^[U*]{3},[U*]{3},[U*]{3},[U*]{3}$");
    private static final Pattern SIGNATURE_PLL_PATTERN_RE = Pattern
            .compile("^[BRFL]{3},[BRFL]{3},[BRFL]{3},[BRFL]{3}$");
    private static final Pattern PROBABILITY_PATTERN_RE = Pattern
            .compile("^(?:2\\*)?\\d+/\\d+$");
    private static final int[][] SIGNATURE_COORDS = new int[][] {
            // B side (left -> right from top view).
            { 0, 2, 0 }, { 1, 2, 0 }, { 2, 2, 0 },
            // R side (back -> front from top view).
            { 2, 2, 0 }, { 2, 2, 1 }, { 2, 2, 2 },
            // F side (right -> left from top view).
            { 2, 2, 2 }, { 1, 2, 2 }, { 0, 2, 2 },
            // L side (front -> back from top view).
            { 0, 2, 2 }, { 0, 2, 1 }, { 0, 2, 0 } };
    private static final int[] SIGNATURE_AXES = new int[] {
            Cube.AXIS_Z, Cube.AXIS_Z, Cube.AXIS_Z, Cube.AXIS_X, Cube.AXIS_X,
            Cube.AXIS_X, Cube.AXIS_Z, Cube.AXIS_Z, Cube.AXIS_Z, Cube.AXIS_X,
            Cube.AXIS_X, Cube.AXIS_X };
    private static final int[][] SIGNATURE_MIRROR_INDEX_MAP = new int[][] {
            { 2, 1, 0 }, { 11, 10, 9 }, { 8, 7, 6 }, { 5, 4, 3 } };
    private static final int[] AUF_MOVES = new int[] {Cube.M_NONE,
            Cube.M_U, Cube.M_UP, Cube.M_U2};
    final Map<Phase, Map<String, int[]>> keyedMovesByPhase = new HashMap<Phase, Map<String, int[]>>();
    private final Map<Phase, Map<String, String>> keyedNamesByPhase =
            new HashMap<Phase, Map<String, String>>();
    private final Map<String, int[]> namedMoves = new HashMap<String, int[]>();
    private final Map<String, NamedSignature> namedSignatures =
            new HashMap<String, NamedSignature>();

    /**
     * Creates a CFOP solver backed by {@code <name>.txt}.
     */
    public CFOP(final String name) {
        super(name);
    }

    /**
     * Discovers and registers CFOP variants from text DB files in a directory.
     */
    public static synchronized void discover(final String dbDir)
            throws CubearoniException {
        for (final String name : discoveredNames) {
            Solver.unregister(name);
        }
        discoveredNames.clear();

        final Path dir = Path.of(dbDir);
        try (final var paths = Files.list(dir)) {
            final List<Path> dbPaths = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isDBFile(path.getFileName().toString()))
                    .sorted()
                    .toList();
            for (final Path dbPath : dbPaths) {
                final String fileName = dbPath.getFileName().toString();
                register(fileName.substring(0,
                        fileName.length() - FILE_SUFFIX.length()), dbDir);
            }
        } catch (final Exception e) {
            throw new CubearoniException("Could not discover CFOP solvers in \""
                    + dbDir + "\": " + e);
        }

        final Solver alias = Solver.findRegistered(ALIAS);
        alias.setDBDir(dbDir);
    }

    /**
     * Registers a named CFOP variant without requiring directory listing.
     */
    public static synchronized void register(final String name)
            throws CubearoniException {
        register(name, Solver.DEFAULT_DB_DIR);
    }

    static synchronized void registerIfValid(final String name)
            throws CubearoniException {
        if (!isName(name)) {
            return;
        }
        register(name, Solver.DEFAULT_DB_DIR);
    }

    static boolean isName(final String name) {
        return (name != null) && name.startsWith(FILE_PREFIX);
    }

    static boolean isDBFile(final String fileName) {
        return fileName.startsWith(FILE_PREFIX)
                && fileName.endsWith(FILE_SUFFIX)
                && !fileName.equals("cfop-help.txt");
    }

    private static void register(final String name, final String dbDir)
            throws CubearoniException {
        if (!isName(name)) {
            throw new CubearoniException("Invalid CFOP solver name \"" + name + "\".");
        }
        final CFOP solver = new CFOP(name);
        solver.setDBDir(dbDir);
        Solver.register(name, solver);
        discoveredNames.add(name);
    }

    // Public methods.

    /**
     * Executes all CFOP phases and returns the resulting move list.
     *
     * @param cube The cube to solve.
     * @return The generated extended moves.
     * @throws CubearoniException If any phase fails.
     */
    @Override
    public int[] exec(final Cube cube) throws CubearoniException {
        return execInternal(cube, false).moves();
    }

    /**
     * Executes all CFOP phases and returns moves with phase-start markers.
     *
     * @param cube The cube to solve.
     * @return A move string with {@code |} at the start of each phase.
     * @throws CubearoniException If any phase fails.
     */
    public String execMarked(final Cube cube) throws CubearoniException {
        return execInternal(cube, true).markedMoves();
    }

    /**
     * Returns configured OLL/PLL moves by algorithm name.
     *
     * @param name The OLL/PLL name.
     * @return The configured moves.
     * @throws CubearoniException If the name is unknown.
     */
    public String getMovesForName(final String name) throws CubearoniException {
        final int[] moves = namedMoves.get(name);
        if (moves == null) {
            throw new CubearoniException("Unknown CFOP algorithm name \"" + name + "\".");
        }
        return Cube.extMovesToStr(moves);
    }

    /**
     * Loads keyed OLL/PLL algorithms from the configured CFOP file.
     *
     * @throws CubearoniException If the file cannot be read or contains invalid rows.
     */
    @Override
    public void loadDB() throws CubearoniException {
        super.loadDB();
        keyedMovesByPhase.clear();
        keyedNamesByPhase.clear();
        namedMoves.clear();
        namedSignatures.clear();
        final byte[] dbBytes = (dbURLFile != null)
                ? Util.readFileFully(dbURLFile)
                : Util.readFileFully(dbFile);
        try (final BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(dbBytes),
                        StandardCharsets.UTF_8))) {
            int lineNum = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.replaceAll("#.*$", "").trim();
                if (line.equals("")) {
                    continue;
                }
                lineNum++;
                final String[] tokens = line.split("\\s+");
                final String phaseToken = tokens[0];
                if (!phaseToken.equals(PHASE_OLL) && !phaseToken.equals(PHASE_PLL)) {
                    // Only keyed OLL/PLL rows are used by CFOP now.
                    continue;
                }
                final Phase phase;
                if (phaseToken.equals(PHASE_OLL)) {
                    phase = Phase.OLL;
                } else {
                    phase = Phase.PLL;
                }
                final ParsedKeyedRow row = parseKeyedRow(lineNum, tokens);
                final String signature = (row.signature != null)
                        ? row.signature
                        : deriveSignature(phase, row.moves);
                addKeyedMoves(phase, lineNum, signature, row.moves, row.flags,
                        row.name, row.mirroredName);
            }
            applyKeyedOverrides();
        } catch (final Exception e) {
            throw new CubearoniException("Could not open \"" + dbFile
                    + "\" for read: " + e);
        }
    }

    /**
     * Checks and rewrites the CFOP text database to canonical row formatting.
     *
     * @throws CubearoniException If the DB cannot be read or rewritten.
     */
    @Override
    public void test() throws CubearoniException {
        final Path path = Paths.get(dbFile);
        final Path outPath = Paths.get(dbFile + ".new");
        final List<String> outputLines = new ArrayList<String>();
        final RewriteValidation validation = new RewriteValidation();
        int changedLines = 0;
        try {
            Files.deleteIfExists(outPath);
            final List<String> inputLines = Files.readAllLines(path,
                    StandardCharsets.UTF_8);
            for (int i = 0; i < inputLines.size(); i++) {
                final String rawLine = inputLines.get(i);
                final String outputLine = rewriteCFOPDBLine(rawLine, i + 1,
                        validation);
                outputLines.add(outputLine);
                if (!rawLine.equals(outputLine)) {
                    changedLines++;
                }
            }
            validation.finish();
            if (changedLines > 0) {
                Files.write(outPath, outputLines, StandardCharsets.UTF_8);
                System.out.println("Wrote " + changedLines
                        + " changed CFOP DB line(s) to " + outPath + ".");
            } else {
                System.out.println("CFOP DB \"" + dbFile
                        + "\" is already canonical.");
            }
        } catch (final Exception e) {
            throw new CubearoniException("Could not test CFOP DB \"" + dbFile
                    + "\": " + e);
        }
    }

    // Protected methods

    /**
     * Returns the child solver names used by this composite solver.
     *
     * @return The sub-solver names, in execution order.
     */
    @Override
    protected List<String> getSubSolverNames() {
        return List.of(Cross.NAME, F2L.NAME);
    }

    // Private methods

    /**
     * Parses a CFOP keyed row while tolerating metadata columns after flags.
     *
     * @param lineNum The source line number.
     * @param tokens The whitespace-split row tokens.
     * @return Parsed row fields used by the solver.
     * @throws CubearoniException If the row is malformed.
     */
    private static ParsedKeyedRow parseKeyedRow(final int lineNum,
            final String[] tokens) throws CubearoniException {
        int index = 1;
        final String signature = (tokens.length > index && isSignature(tokens[index]))
                ? tokens[index++]
                : null;
        final int flagsIndex = findFlagsIndex(tokens, index);
        if (flagsIndex <= index) {
            throw new CubearoniException("line " + lineNum
                    + " has invalid CFOP keyed row token count.");
        }
        final String moves = String.join("", Arrays.copyOfRange(tokens, index,
                flagsIndex));
        final String flags = tokens[flagsIndex];
        final int metadataStart = flagsIndex + 1;
        int metadataEnd = tokens.length;
        String probability = null;
        if ((metadataEnd > metadataStart)
                && PROBABILITY_PATTERN_RE.matcher(tokens[metadataEnd - 1]).matches()) {
            probability = tokens[--metadataEnd];
        }
        final int expectedNames = flags.contains("m") ? 2 : 1;
        if ((metadataEnd - metadataStart) > expectedNames) {
            // Preserve a malformed trailing probability so -t can repair it.
            probability = tokens[--metadataEnd];
        }
        final String name = (metadataStart < metadataEnd)
                ? tokens[metadataStart]
                : null;
        final String mirroredName = ((metadataStart + 1) < metadataEnd)
                ? tokens[metadataStart + 1]
                : null;
        if ((metadataStart + 2) < metadataEnd) {
            throw new CubearoniException("line " + lineNum
                    + " has too many CFOP metadata tokens.");
        }
        return new ParsedKeyedRow(signature, moves, flags, name, mirroredName,
                probability);
    }

    private static String rewriteCFOPDBLine(final String rawLine,
            final int lineNum, final RewriteValidation validation)
            throws CubearoniException {
        final int commentIndex = rawLine.indexOf('#');
        final String dataPart = (commentIndex == -1)
                ? rawLine
                : rawLine.substring(0, commentIndex);
        final String commentPart = (commentIndex == -1)
                ? null
                : rawLine.substring(commentIndex);
        final String trimmed = dataPart.trim();
        if (trimmed.isEmpty()) {
            return rawLine;
        }

        final String[] tokens = trimmed.split("\\s+");
        final Phase phase;
        if (tokens[0].equals(PHASE_OLL)) {
            phase = Phase.OLL;
        } else if (tokens[0].equals(PHASE_PLL)) {
            phase = Phase.PLL;
        } else {
            return rawLine;
        }

        final ParsedKeyedRow row = parseKeyedRow(lineNum, tokens);
        final String moves = repairNetCubeRotation(row.moves, lineNum);
        final Cube cube = deriveSignatureCube(moves);
        validateDerivedCube(phase, cube, lineNum);
        validateOllAuf(phase, moves, lineNum);
        final String signature = getSignature(cube, phase);
        validation.add(phase, signature, lineNum);
        if (row.flags.contains("m")) {
            validation.add(phase, mirrorSignature(signature), lineNum);
        }
        validation.addNames(phase, row.name, row.mirroredName, lineNum);
        final String probability = deriveProbability(phase, signature, row.flags);
        String out = formatKeyedRow(phase, signature, moves, row.flags,
                row.name, row.mirroredName, probability);
        if (commentPart != null) {
            out += "  " + commentPart.trim();
        }
        return out;
    }

    /**
     * Rejects initial or final AUF moves embedded in OLL algorithms.
     */
    private static void validateOllAuf(final Phase phase, final String moves,
            final int lineNum) throws CubearoniException {
        if ((phase != Phase.OLL) || moves.equals("none")) {
            return;
        }
        final int[] parsedMoves = Util.moveStrToExtendedMoves(moves);
        if ((parsedMoves.length > 0)
                && (Util.getRootMove(parsedMoves[0]) == Cube.M_U)) {
            throw new CubearoniException("line " + lineNum
                    + " OLL algorithm begins with a U move.");
        }
        if ((parsedMoves.length > 0)
                && (Util.getRootMove(parsedMoves[parsedMoves.length - 1])
                        == Cube.M_U)) {
            throw new CubearoniException("line " + lineNum
                    + " OLL algorithm ends with a U move.");
        }
    }

    /**
     * Appends rotations needed to restore the original cube coordinate system.
     */
    private static String repairNetCubeRotation(final String moves,
            final int lineNum) throws CubearoniException {
        final int[] translated = Util.extMovesToHTMMoves(moves,
                Util.EXT_TO_HTM_MODE_NONE);
        final int[] translatedWithCorrection = Util.extMovesToHTMMoves(moves,
                Util.EXT_TO_HTM_MODE_PREFIX);
        if (Arrays.equals(translated, translatedWithCorrection)) {
            return moves;
        }

        final int correctionCount =
                translatedWithCorrection.length - translated.length;
        if (correctionCount <= 0) {
            throw new CubearoniException("line " + lineNum
                    + " algorithm has an invalid cube rotation correction.");
        }
        final String correction = Cube.extMovesToStr(Arrays.copyOf(
                translatedWithCorrection, correctionCount));
        final String repairedMoves = moves + correction;
        final int[] repaired = Util.extMovesToHTMMoves(repairedMoves,
                Util.EXT_TO_HTM_MODE_NONE);
        final int[] repairedWithCorrection = Util.extMovesToHTMMoves(
                repairedMoves, Util.EXT_TO_HTM_MODE_PREFIX);
        if (!Arrays.equals(repaired, repairedWithCorrection)) {
            throw new CubearoniException("line " + lineNum
                    + " algorithm could not be repaired by appending \""
                    + correction + "\".");
        }
        return repairedMoves;
    }

    /**
     * Validates that an inverse algorithm produces the expected CFOP phase
     * while preserving the first two layers.
     */
    private static void validateDerivedCube(final Phase phase, final Cube cube,
            final int lineNum) throws CubearoniException {
        if (!cube.isSolvedThroughY(1)) {
            throw new CubearoniException("line " + lineNum
                    + " algorithm does not preserve F2L.");
        }

        final boolean hasUnorientedUpperSticker =
                getSignature(cube, Phase.PLL).contains("U");
        if ((phase == Phase.OLL)
                && !hasUnorientedUpperSticker && !cube.isSolved()) {
            throw new CubearoniException("line " + lineNum
                    + " is labeled OLL but produces a PLL state.");
        }
        if ((phase == Phase.PLL) && hasUnorientedUpperSticker) {
            throw new CubearoniException("line " + lineNum
                    + " is labeled PLL but produces an OLL state.");
        }
    }

    /**
     * Returns a signature key shared by all four AUF rotations.
     */
    private static String canonicalAufSignature(final Phase phase,
            final String signature) throws CubearoniException {
        String current = signature;
        String canonical = null;
        for (int i = 0; i < 4; i++) {
            final String candidate = (phase == Phase.PLL)
                    ? normalizePllSignature(current) : current;
            if ((canonical == null) || (candidate.compareTo(canonical) < 0)) {
                canonical = candidate;
            }
            current = rotateSignatureGroups(current, Cube.M_U);
        }
        return canonical;
    }

    private static String formatKeyedRow(final Phase phase, final String signature,
            final String moves, final String flags, final String name,
            final String mirroredName, final String probability) {
        return String.format(KEYED_ROW_FORMAT, phase, signature, moves, flags,
                (name == null) ? "" : name,
                (mirroredName == null) ? "" : mirroredName,
                (probability == null) ? "" : probability);
    }

    /**
     * Derives an algorithm row's probability from its rotational symmetry.
     */
    private static String deriveProbability(final Phase phase,
            final String signature, final String flags) throws CubearoniException {
        final Set<String> rotations = new HashSet<>();
        String current = signature;
        for (int i = 0; i < 4; i++) {
            rotations.add((phase == Phase.PLL)
                    ? normalizePllSignature(current) : current);
            current = rotateSignatureGroups(current, Cube.M_U);
        }
        final int uniqueRotations = rotations.size();
        final int denominator = (phase == Phase.OLL) ? 216 : 72;
        final String multiplier = flags.contains("m") ? "2*" : "";
        return multiplier + uniqueRotations + "/" + denominator;
    }

    private static String deriveSignature(final Phase phase, final String moves)
            throws CubearoniException {
        final Cube cube = deriveSignatureCube(moves);
        return getSignature(cube, phase);
    }

    private static DerivedSignature deriveSignature(final String moves)
            throws CubearoniException {
        final Cube cube = deriveSignatureCube(moves);
        final String pllSignature = getSignature(cube, Phase.PLL);
        if (!pllSignature.contains("U")) {
            return new DerivedSignature(Phase.PLL, pllSignature);
        }

        final StringBuilder ollSignature = new StringBuilder();
        for (int i = 0; i < pllSignature.length(); i++) {
            final char c = pllSignature.charAt(i);
            ollSignature.append((c == ',') ? ',' : ((c == 'U') ? 'U' : '*'));
        }
        final String signature = ollSignature.toString();
        return new DerivedSignature(getPhaseFromSignature(signature), signature);
    }

    private static Cube deriveSignatureCube(final String moves)
            throws CubearoniException {
        final Cube cube = new Cube();
        if (!moves.equals("none")) {
            final int[] movesExt = Util.moveStrToExtendedMoves(moves);
            final String inverseMoves = Cube.extMovesToStr(
                    Cube.inverseMoves(movesExt));
            cube.extMoves(inverseMoves, Util.EXT_TO_HTM_MODE_NONE);
        }
        return cube;
    }

    /**
     * Returns true if a token has the simple signature shape: 12 stickers and
     * three commas.
     *
     * @param token The token to check.
     * @return true if the token looks like a signature.
     */
    static boolean isSignature(final String token) {
        if ((token == null) || (token.length() != 15)) {
            return false;
        }
        int commaCount = 0;
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) == ',') {
                commaCount++;
            }
        }
        return commaCount == 3;
    }

    private static Phase getPhaseFromSignature(final String signature) {
        return signature.contains("*") ? Phase.OLL : Phase.PLL;
    }

    private static int findFlagsIndex(final String[] tokens, final int fromIndex)
            throws CubearoniException {
        for (int i = tokens.length - 1; i >= fromIndex; i--) {
            if (tokens[i].equals("d") || tokens[i].equals("m")) {
                return i;
            }
        }
        throw new CubearoniException("CFOP keyed row is missing flags.");
    }

    /**
     * Adds a keyed algorithm row for OLL or PLL, including optional mirror.
     *
     * @param phase The phase for the row.
     * @param lineNum The source line number in the config.
     * @param signatureIn The raw signature.
     * @param moves The configured algorithm string.
     * @param flags Row flags (for example {@code m} for mirrored variant).
     * @throws CubearoniException If the row is invalid or duplicates an existing key.
     */
    private void addKeyedMoves(final Phase phase, final int lineNum,
            final String signatureIn, final String moves, final String flags)
            throws CubearoniException {
        addKeyedMoves(phase, lineNum, signatureIn, moves, flags, false, null,
                null);
    }

    private void addKeyedMoves(final Phase phase, final int lineNum,
            final String signatureIn, final String moves, final String flags,
            final String name, final String mirroredName)
            throws CubearoniException {
        addKeyedMoves(phase, lineNum, signatureIn, moves, flags, false, name,
                mirroredName);
    }

    private void addKeyedMoves(final Phase phase, final int lineNum,
            final String signatureIn, final String moves, final String flags,
            final boolean allowDuplicates)
            throws CubearoniException {
        addKeyedMoves(phase, lineNum, signatureIn, moves, flags, allowDuplicates,
                null, null);
    }

    private void addKeyedMoves(final Phase phase, final int lineNum,
            final String signatureIn, final String moves, final String flags,
            final boolean allowDuplicates, final String name,
            final String mirroredName) throws CubearoniException {
        String signatureRaw = signatureIn;
        int[] displayMoves = Util.moveStrToExtendedMoves(moves);
        if (isLeftHanded()) {
            if (signatureRaw != null) {
                signatureRaw = mirrorSignature(signatureRaw);
            }
            displayMoves = Cube.mirrorMoves(displayMoves);
        }
        if (signatureRaw != null) {
            final String signature = validateAndNormalizeSignature(phase,
                    lineNum, signatureRaw);
            final String effectiveName = allowDuplicates
                    ? removeRotatedOverrideTarget(phase, lineNum, signatureRaw,
                            signature, name)
                    : name;
            addKeyedMovesToMap(phase, signature, displayMoves, lineNum,
                    allowDuplicates, effectiveName);
        }
        if (flags.contains("m")) {
            final int[] displayMovesMirrored = Cube.mirrorMoves(displayMoves);
            if (signatureRaw != null) {
                final String signatureMirroredRaw = mirrorSignature(signatureRaw);
                final String signatureMirrored = (phase == Phase.PLL)
                        ? normalizePllSignature(signatureMirroredRaw)
                        : signatureMirroredRaw;
                final String effectiveMirroredName = allowDuplicates
                        ? removeRotatedOverrideTarget(phase, lineNum,
                                signatureMirroredRaw, signatureMirrored,
                                mirroredName)
                        : mirroredName;
                addKeyedMovesToMap(phase, signatureMirrored, displayMovesMirrored,
                        lineNum, allowDuplicates, effectiveMirroredName);
            }
        }
    }

    private String removeRotatedOverrideTarget(final Phase phase, final int lineNum,
            final String signatureRaw, final String signature,
            final String name) throws CubearoniException {
        final String existingRaw = getExistingOverrideSignature(phase, signatureRaw);
        final String existingSignature = validateAndNormalizeSignature(phase,
                lineNum, existingRaw);
        final Map<String, int[]> keyedMoves = keyedMovesByPhase.get(phase);
        final Map<String, String> keyedNames = keyedNamesByPhase.get(phase);
        final String existingName = (keyedNames == null)
                ? null
                : keyedNames.get(existingSignature);
        if (!existingSignature.equals(signature) && (keyedMoves != null)) {
            keyedMoves.remove(existingSignature);
            if (keyedNames != null) {
                keyedNames.remove(existingSignature);
            }
        }
        return ((name != null) && !name.isEmpty()) ? name : existingName;
    }

    private void addNamedMoves(final String name, final int[] moves,
            final int lineNum, final boolean allowDuplicates) throws CubearoniException {
        if ((name == null) || name.isEmpty()) {
            return;
        }
        if (!allowDuplicates && (namedMoves.put(name, moves) != null)) {
            throw new CubearoniException("line " + lineNum
                    + " duplicates CFOP algorithm name \"" + name + "\".");
        }
        namedMoves.put(name, moves);
    }

    private void addNamedSignature(final String name, final Phase phase,
            final String signature) {
        if ((name == null) || name.isEmpty()) {
            return;
        }
        namedSignatures.put(name, new NamedSignature(phase, signature));
    }

    /**
     * Stores a keyed move row for a phase, creating the map if needed.
     *
     * @param phase The phase map to update.
     * @param signature The normalized lookup signature.
     * @param moves The moves associated with the signature.
     * @param lineNum The source line number used for error reporting.
     * @throws CubearoniException If the signature already exists.
     */
    private void addKeyedMovesToMap(final Phase phase, final String signature,
            final int[] moves, final int lineNum, final boolean allowDuplicates,
            final String name) throws CubearoniException {
        Map<String, int[]> keyedMoves = keyedMovesByPhase.get(phase);
        if (keyedMoves == null) {
            keyedMoves = new HashMap<String, int[]>();
            keyedMovesByPhase.put(phase, keyedMoves);
        }
        if (!allowDuplicates && (keyedMoves.put(signature, moves) != null)) {
            throw new CubearoniException("line " + lineNum
                    + " duplicates keyed signature \"" + signature
                    + "\" for phase " + phase + ".");
        }
        keyedMoves.put(signature, moves);

        Map<String, String> keyedNames = keyedNamesByPhase.get(phase);
        if (keyedNames == null) {
            keyedNames = new HashMap<String, String>();
            keyedNamesByPhase.put(phase, keyedNames);
        }
        String effectiveName = name;
        if (((effectiveName == null) || effectiveName.isEmpty())
                && allowDuplicates) {
            effectiveName = keyedNames.get(signature);
        }
        if (allowDuplicates && (keyedNames.get(signature) != null)) {
            addNamedMoves(keyedNames.get(signature), moves, lineNum, true);
        }
        if ((effectiveName != null) && !effectiveName.isEmpty()) {
            keyedNames.put(signature, effectiveName);
            addNamedMoves(effectiveName, moves, lineNum, allowDuplicates);
            addNamedSignature(effectiveName, phase, signature);
        }
    }

    /**
     * Applies caller-provided keyed OLL/PLL overrides.
     *
     * Each option key is either a signature or an existing OLL/PLL algorithm
     * name. When a name is used, the signature is derived from the algorithm
     * moves and must match that existing named algorithm.
     */
    private void applyKeyedOverrides() throws CubearoniException {
        for (final Map.Entry<String, String> entry : getOptions().entrySet()) {
            final String key = entry.getKey();
            String signature = key;
            final String value = entry.getValue();
            if ((key == null) || key.isEmpty()
                    || (value == null) || value.isEmpty()) {
                continue;
            }

            final Phase phase;
            final String name;
            final ParsedOverride parsed = parseOverrideValue(value);
            if (SIGNATURE_OLL_PATTERN_RE.matcher(signature).matches()) {
                phase = Phase.OLL;
                name = null;
            } else if (SIGNATURE_PLL_PATTERN_RE.matcher(signature).matches()) {
                phase = Phase.PLL;
                name = null;
            } else {
                final NamedSignature target = namedSignatures.get(key);
                if (target == null) {
                    throw new CubearoniException("Unknown CFOP algorithm name \""
                            + key + "\".");
                }
                final DerivedSignature derived = deriveSignature(parsed.moves);
                phase = derived.phase();
                signature = derived.signature();
                final String existingSignature = getExistingOverrideSignature(phase,
                        signature);
                final String normalizedSignature = validateAndNormalizeSignature(
                        phase, 0, existingSignature);
                if ((phase != target.phase())
                        || !normalizedSignature.equals(target.signature())) {
                    throw new CubearoniException("Override for CFOP algorithm name \""
                            + key + "\" derives signature \"" + normalizedSignature
                            + "\" for phase " + phase + ", but expected signature \""
                            + target.signature() + "\" for phase " + target.phase()
                            + ".");
                }
                name = key;
            }
            // Parse as standard keyed row, but allow duplicate keys to override.
            addKeyedMoves(phase, 0, signature, parsed.moves, parsed.flags, true,
                    name, null);
        }
    }

    private String getExistingOverrideSignature(final Phase phase,
            final String signature) throws CubearoniException {
        final Map<String, int[]> keyedMoves = keyedMovesByPhase.get(phase);
        if (keyedMoves == null) {
            return signature;
        }
        String cur = signature;
        for (int i = 0; i < AUF_MOVES.length; i++) {
            final String candidate = (phase == Phase.PLL)
                    ? normalizePllSignature(cur)
                    : cur;
            if (keyedMoves.containsKey(candidate)) {
                return cur;
            }
            cur = rotateSignatureGroups(cur, Cube.M_U);
        }
        return signature;
    }

    private static ParsedOverride parseOverrideValue(final String value)
            throws CubearoniException {
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new CubearoniException("Override value must not be empty.");
        }

        final int dash = trimmed.lastIndexOf('-');
        if ((dash > 0) && (dash == trimmed.length() - 2)) {
            final char flag = trimmed.charAt(trimmed.length() - 1);
            if ((flag == 'd') || (flag == 'm')) {
                final String moves = trimmed.substring(0, dash);
                if (moves.isEmpty()) {
                    throw new CubearoniException("Override moves must not be empty.");
                }
                return new ParsedOverride(moves, String.valueOf(flag));
            }
        }

        // Default if flags are not provided.
        return new ParsedOverride(trimmed, "d");
    }

    private static final class ParsedOverride {
        final String moves;
        final String flags;

        ParsedOverride(final String moves, final String flags) {
            this.moves = moves;
            this.flags = flags;
        }
    }

    private static final class ParsedKeyedRow {
        final String signature;
        final String moves;
        final String flags;
        final String name;
        final String mirroredName;
        final String probability;

        ParsedKeyedRow(final String signature, final String moves,
                final String flags, final String name,
                final String mirroredName, final String probability) {
            this.signature = signature;
            this.moves = moves;
            this.flags = flags;
            this.name = name;
            this.mirroredName = mirroredName;
            this.probability = probability;
        }
    }

    private record DerivedSignature(Phase phase, String signature) { }

    private record NamedSignature(Phase phase, String signature) { }

    /**
     * Builds the marked move string by aligning per-phase moves to final output.
     *
     * @param movesFinal The complete final move list.
     * @param phaseMovesAll The per-phase move lists in phase order.
     * @return The marked move string.
     */
    private static String buildMarkedMoves(final int[] movesFinal,
                                           final List<int[]> phaseMovesAll) {
        final String finalStr = Cube.extMovesToStr(movesFinal);
        final StringBuilder marked = new StringBuilder();
        int pos = 0;
        for (final int[] phaseMoves : phaseMovesAll) {
            marked.append('|');
            final String phaseStr = Cube.extMovesToStr(phaseMoves);
            final int max = Math.min(phaseStr.length(), finalStr.length() - pos);
            int matched = 0;
            while ((matched < max)
                    && (phaseStr.charAt(matched) == finalStr.charAt(pos + matched))) {
                matched++;
            }
            if (matched > 0) {
                marked.append(finalStr, pos, pos + matched);
                pos += matched;
            }
        }
        if (pos < finalStr.length()) {
            marked.append(finalStr.substring(pos));
        }
        return marked.toString();
    }

    /**
     * Prints the verbose timing table with moves in the unbounded final column.
     */
    private static void printSummary(final List<SummaryRow> rows) {
        int nameWidth = "Name".length();
        for (final SummaryRow row : rows) {
            nameWidth = Math.max(nameWidth, row.name().length());
        }
        final String format = "%5s  %7s  %-" + nameWidth + "s  %s%n";
        System.out.format(format, "Phase", "Micros", "Name", "Moves");
        System.out.format(format, "-----", "------", "----", "-----");
        for (final SummaryRow row : rows) {
            System.out.format(format, row.phase(), row.micros(), row.name(),
                    row.moves());
        }
    }

    /**
     * Shared execution path for regular and marked outputs.
     *
     * @param cube The cube to solve.
     * @param markPhaseStarts Whether to include phase-start markers.
     * @return Execution result containing both raw and marked forms.
     * @throws CubearoniException If solving fails.
     */
    private ExecResult execInternal(final Cube cube,
                                    final boolean markPhaseStarts) throws CubearoniException {
        final long beforeCFOPNanos = System.nanoTime();

        char intFace = 0;
        if (getUserFace() != 0) {
            // Match Solver.exec() behavior: rotate so requested face is up.
            intFace = cube.getFormat().toInt(getUserFace());
            cube.rotateKeepColor(null, intFace, false, null);
        }

        int[] moves = new int[] {};
        final List<int[]> phaseMovesAll = markPhaseStarts ? new ArrayList<int[]>()
                : null;
        final List<SummaryRow> summaryRows = verbose ? new ArrayList<>() : null;
        for (final Phase phase : Phase.values()) {
            final long beforePhaseNanos = System.nanoTime();
            final PhaseResult phaseResult = solve(phase, cube);
            final int[] phaseMoves = phaseResult.moves();
            if (verbose) {
                summaryRows.add(new SummaryRow(phase.toString(),
                        (System.nanoTime() - beforePhaseNanos) / 1000,
                        Cube.extMovesToStr(phaseMoves), phaseResult.name()));
            }
            if (markPhaseStarts) {
                phaseMovesAll.add(phaseMoves);
            }
            moves = Util.concat(moves, phaseMoves);
            if (phase == stopAfterPhase) {
                break;
            }
        }

        final int[] movesFinal = moves;
        if (getUserFace() != 0) {
            // Undo pre-solve rotation and rewrite moves to original orientation.
            intFace = cube.getFormat().toInt(getUserFace());
            cube.rotateKeepColor(null, intFace, true, movesFinal);
            if (markPhaseStarts) {
                for (final int[] phaseMoves : phaseMovesAll) {
                    cube.rotateKeepColor(null, intFace, true, phaseMoves);
                }
            }
        }

        if (verbose) {
            summaryRows.add(new SummaryRow("CFOP",
                    (System.nanoTime() - beforeCFOPNanos) / 1000,
                    Cube.extMovesToStr(movesFinal), ""));
        }

        boolean solved = true;
        if ((stopAfterPhase == null) || (stopAfterPhase == Phase.PLL)) {
            final long beforeSolvedCheckNanos = System.nanoTime();
            solved = cube.isSolved();
            if (verbose) {
                summaryRows.add(new SummaryRow("CHECK",
                        (System.nanoTime() - beforeSolvedCheckNanos) / 1000,
                        "", solved ? "pass" : "fail"));
            }
        }
        if (verbose) {
            printSummary(summaryRows);
        }
        if (!solved) {
            throw new CubearoniException("CFOP solver produced a cube that is not solved");
        }

        if (markPhaseStarts) {

            return new ExecResult(movesFinal,
                    buildMarkedMoves(movesFinal, phaseMovesAll));
        }

        return new ExecResult(movesFinal, null);
    }

    /**
     * Appends or merges PLL final AUF to keep emitted move text natural.
     *
     * @param cube The cube after keyed PLL moves have been applied.
     * @param moves The keyed PLL moves.
     * @return Adjusted PLL moves including final AUF handling.
     * @throws CubearoniException If AUF resolution fails.
     */
    private int [] getAdjustedMovesForPLL(final Cube cube, final int[] moves) throws CubearoniException {
        final int aufMove = getPLLFinalAUFMove(cube);
        if (aufMove == Cube.M_NONE) {
            // Nothing to adjust.
            return moves;
        }

        // Adjustments after this point will make no net difference to the cube,
        // so just apply aufMove now.
        cube.move(aufMove);

        // For the most part we don't normalize OLL and PLL moves
        // so that those moves are emitted exactly as expected.
        // However, there is one exception - some PLLs end with Ux
        // moves. We'll combine that with the AUF move since othewise
        // it seems very odd to end with two seemingly redundant U moves.
        if (moves.length > 0) {
            final int lastMove = moves[moves.length - 1];
            final int lastRoot = Util.getRootMove(lastMove);
            if (lastRoot == Cube.M_U) {
                final int lastOffsetAsRot = Util.getOffsetAsRotation(lastMove);
                final int aufOffsetAsRot = Util.getOffsetAsRotation(aufMove);
                // The following produces an offset in range [-1, 2].
                final int combinedOffsetAsRot =
                        (lastOffsetAsRot + aufOffsetAsRot + 5) % 4 - 1;
                if (combinedOffsetAsRot == 0) {
                    // We can remove the last U move entirely.
                    return Arrays.copyOf(moves, moves.length - 1);
                } else {
                    // Change the last U move to the combined move.
                    moves[moves.length - 1] = lastRoot +
                            Util.getOffsetFromRotation(combinedOffsetAsRot);
                }
            } else {
                // PLL did not end with a Ux move. Just use the AUF
                // move.
                return Util.concat(moves, new int[]{aufMove});
            }
        } else {
            // In this case there is a AUF move, but the PLL moves
            // is empty. A PLL skip? Just use the AUF move.
            return Util.concat(moves, new int[]{aufMove});
        }
        return moves;
    }

    /**
     * Computes the final PLL AUF move from the current top-front sticker.
     *
     * @param cube The cube after PLL algorithm application.
     * @return The AUF move needed to align the final layer.
     * @throws CubearoniException If an unexpected sticker is observed.
     */
    private static int getPLLFinalAUFMove(final Cube cube) throws CubearoniException {
        final char fColor = cube.getStickerColor(1, 2, 2, Cube.AXIS_Z);
        switch (fColor) {
            case 'F':
                return Cube.M_NONE;
            case 'L':
                return Cube.M_U;
            case 'R':
                return Cube.M_UP;
            case 'B':
                return Cube.M_U2;
            default:
                throw new CubearoniException("Unexpected front color for PLL AUF: "
                        + fColor);
        }
    }

    /**
     * Extracts the 12-sticker signature for OLL or PLL lookup.
     *
     * @param cube The cube to inspect.
     * @param phase The target phase.
     * @return The generated signature string.
     */
    private static String getSignature(final Cube cube, final Phase phase) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SIGNATURE_COORDS.length; i++) {
            if ((i > 0) && ((i % 3) == 0)) {
                sb.append(',');
            }
            final int[] coord = SIGNATURE_COORDS[i];
            final char sticker = cube.getStickerColor(coord[0], coord[1], coord[2],
                    SIGNATURE_AXES[i]);
            if (phase == Phase.OLL) {
                sb.append((sticker == 'U') ? 'U' : '*');
            } else {
                sb.append(sticker);
            }
        }
        return sb.toString();
    }

    /**
     * Mirrors a signature across the YZ plane.
     *
     * @param signature Input signature.
     * @return Mirrored signature.
     */
    static String mirrorSignature(final String signature) {
        final String compact = signature.replace(",", "");
        final StringBuilder sb = new StringBuilder();
        for (int side = 0; side < SIGNATURE_MIRROR_INDEX_MAP.length; side++) {
            if (side > 0) {
                sb.append(',');
            }
            for (int idx = 0; idx < 3; idx++) {
                sb.append(compact.charAt(SIGNATURE_MIRROR_INDEX_MAP[side][idx]));
            }
        }
        return sb.toString();
    }

    /**
     * Normalizes PLL signature colors to first-seen digit labels.
     *
     * @param signature Raw PLL signature.
     * @return Normalized PLL signature.
     */
    static String normalizePllSignature(final String signature) {
        final String compact = signature.replace(",", "");
        final int[] map = new int['Z' + 1];
        int next = 1;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < compact.length(); i++) {
            if ((i > 0) && ((i % 3) == 0)) {
                sb.append(',');
            }
            final char c = compact.charAt(i);
            if (map[c] == 0) {
                map[c] = next++;
            }
            sb.append((char) ('0' + map[c]));
        }
        return sb.toString();
    }

    /**
     * Applies AUF group rotation to a comma-delimited 4x3 signature.
     *
     * @param signature Input signature.
     * @param aufMove AUF move to apply as signature rotation.
     * @return Rotated signature.
     * @throws CubearoniException If {@code aufMove} is not a U-layer AUF move.
     */
    static String rotateSignatureGroups(final String signature,
            final int aufMove) throws CubearoniException {
        switch (aufMove) {
        case Cube.M_NONE:
            return signature;
        case Cube.M_U:
            return signature.substring(12) + "," + signature.substring(0, 11);
        case Cube.M_UP:
            return signature.substring(4) + "," + signature.substring(0, 3);
        case Cube.M_U2:
            return signature.substring(8) + "," + signature.substring(0, 7);
        default:
            throw new CubearoniException("Invalid AUF move " + aufMove);
        }
    }

    /**
     * Executes a single CFOP phase.
     *
     * @param phase The phase to execute.
     * @param cube The cube to solve.
     * @return Moves and optional algorithm name produced for the phase.
     * @throws CubearoniException If phase execution fails.
     */
    private PhaseResult solve(final Phase phase, final Cube cube) throws CubearoniException {
        // Cross and F2L have their own solvers, so we defer to them. For OLL
        // and PLL, they are both driven by the same configuration file using a
        // similar lookup table approach.
        switch (phase) {
            case CROSS:
                return new PhaseResult(cross.exec(cube), "");
            case F2L:
                return new PhaseResult(f2l.exec(cube), "");
            case OLL:
                return ollExec(cube);
            case PLL:
                return pllExec(cube);
            default:
                // This should never happen since we control the enum values,
                // but just in case...
                throw new CubearoniException("Invalid phase " + phase + ". cube=\n" + cube);
        }
    }

    /**
     * For a given phase find configured keyed moves based on the signature, and
     * apply them to the cube.
     *
     * @param phase The phase.
     * @param cube The cube.
     * @return The moves and algorithm name found. The moves are never null,
     *         but can be empty when the phase is already solved.
     * @throws CubearoniException If the moves could not be found.
     */
    private PhaseResult solveWithKeyedMoves(final Phase phase, final Cube cube)
            throws CubearoniException {
        final Map<String, int[]> keyedMoves = keyedMovesByPhase.get(phase);
        if (keyedMoves == null) {
            throw new CubearoniException("No keyed moves loaded for phase " + phase + ".");
        }
        final String signature = getSignature(cube, phase);
        for (final int aufMove : AUF_MOVES) {
            final String adjustedSignatureRaw = rotateSignatureGroups(signature,
                    aufMove);
            final String adjustedSignature = (phase == Phase.PLL)
                    ? normalizePllSignature(adjustedSignatureRaw)
                    : adjustedSignatureRaw;
            final int[] phaseMoves = keyedMoves.get(adjustedSignature);
            if (phaseMoves != null) {
                final int[] moves;
                if (aufMove == Cube.M_NONE) {
                    moves = phaseMoves;
                } else {
                    final int[] aufMoveArray = new int[]{aufMove};
                    moves = Util.concat(aufMoveArray, phaseMoves);
                }
                cube.extMoves(Cube.extMovesToStr(moves),
                        Util.EXT_TO_HTM_MODE_APPLIED);
                final Map<String, String> keyedNames = keyedNamesByPhase.get(phase);
                final String name = (keyedNames == null)
                        ? "" : keyedNames.getOrDefault(adjustedSignature, "");
                return new PhaseResult(moves, name);
            }
        }
        throw new CubearoniException("Could not find valid initial AUF for phase "
                + phase + ".");
    }

    /**
     * The main exec method for the OLL phase.
     *
     * @param cube The cube to solve OLL on.
     * @return The moves and OLL algorithm name.
     * @throws CubearoniException If an OLL could not be found.
     */
    private PhaseResult ollExec(final Cube cube) throws CubearoniException {
        return solveWithKeyedMoves(Phase.OLL, cube);
    }

    /**
     * The main exec method for the PLL phase. This includes the final AUF move,
     * and adjusting it (consolidating it with any preceding Ux moves at the end
     * of the PLL).
     *
     * @param cube The cube to solve PLL on.
     * @return The moves and PLL algorithm name.
     * @throws CubearoniException If an PLL could not be found.
     */
    private PhaseResult pllExec(final Cube cube) throws CubearoniException {
        final PhaseResult keyedResult = solveWithKeyedMoves(Phase.PLL, cube);
        return new PhaseResult(getAdjustedMovesForPLL(cube, keyedResult.moves()),
                keyedResult.name());
    }

    /**
     * Validates a signature and applies PLL normalization when needed.
     *
     * @param phase The phase for which the signature is defined.
     * @param lineNum Source line number for error reporting.
     * @param signature The raw signature.
     * @return A validated (and possibly normalized) signature.
     * @throws CubearoniException If the signature format is invalid.
     */
    private static String validateAndNormalizeSignature(
            final Phase phase, final int lineNum, final String signature)
            throws CubearoniException {
        // TODO: Faster validation without regexs?
        if ((phase == Phase.OLL)
                && !SIGNATURE_OLL_PATTERN_RE.matcher(signature).matches()) {
            throw new CubearoniException("line " + lineNum
                    + " has invalid signature \"" + signature + "\".");
        }
        if ((phase == Phase.PLL)
                && !SIGNATURE_PLL_PATTERN_RE.matcher(signature).matches()) {
            throw new CubearoniException("line " + lineNum
                    + " has invalid signature \"" + signature + "\".");
        }
        return (phase == Phase.PLL) ? normalizePllSignature(signature)
                : signature;
    }

    // Private classes

    /** Holds raw and optionally phase-marked solve output from one CFOP execution. */
    private record ExecResult(int[] moves, String markedMoves) { }

    private record PhaseResult(int[] moves, String name) { }

    private record SummaryRow(String phase, long micros, String moves, String name) { }

    /** Tracks AUF-equivalent signatures while rewriting a CFOP DB. */
    private static final class RewriteValidation {
        private final Map<Phase, Map<String, Integer>> linesBySignature =
                new EnumMap<>(Phase.class);
        private final Set<String> names = new HashSet<>();
        private int previousOllName = -1;
        private int maximumOllName = -1;
        private String previousPllName;

        void add(final Phase phase, final String signature, final int lineNum)
                throws CubearoniException {
            final String canonical = canonicalAufSignature(phase, signature);
            final Map<String, Integer> lines = linesBySignature.computeIfAbsent(
                    phase, ignored -> new HashMap<>());
            final Integer previousLine = lines.putIfAbsent(canonical, lineNum);
            if (previousLine != null) {
                throw new CubearoniException("line " + lineNum
                        + " duplicates an AUF-equivalent " + phase
                        + " signature from line " + previousLine + ".");
            }
        }

        void addNames(final Phase phase, final String name,
                final String mirroredName, final int lineNum)
                throws CubearoniException {
            if ((name == null) || name.isEmpty()) {
                throw new CubearoniException("line " + lineNum
                        + " is missing an algorithm name.");
            }
            int numericName = -1;
            if (phase == Phase.OLL) {
                try {
                    numericName = Integer.parseInt(name);
                } catch (final NumberFormatException e) {
                    throw new CubearoniException("line " + lineNum
                            + " has non-numeric OLL name \"" + name + "\".");
                }
                if ((previousOllName == -1) && (numericName != 0)) {
                    throw new CubearoniException("line " + lineNum
                            + " has first OLL name \"" + name
                            + "\"; expected \"0\".");
                }
                if ((previousOllName != -1) && (numericName <= previousOllName)) {
                    throw new CubearoniException("line " + lineNum
                            + " has OLL name \"" + name
                            + "\", which is not numerically after \""
                            + previousOllName + "\".");
                }
            } else if ((previousPllName != null)
                    && (name.compareTo(previousPllName) <= 0)) {
                throw new CubearoniException("line " + lineNum + " has PLL name \""
                        + name + "\", which is not lexically after \""
                        + previousPllName + "\".");
            }

            addName(name, lineNum);
            if ((mirroredName != null) && !mirroredName.isEmpty()) {
                addName(mirroredName, lineNum);
            }
            if (phase == Phase.OLL) {
                previousOllName = numericName;
                maximumOllName = Math.max(maximumOllName, numericName);
                if ((mirroredName != null) && !mirroredName.isEmpty()) {
                    try {
                        maximumOllName = Math.max(maximumOllName,
                                Integer.parseInt(mirroredName));
                    } catch (final NumberFormatException e) {
                        throw new CubearoniException("line " + lineNum
                                + " has non-numeric mirrored OLL name \""
                                + mirroredName + "\".");
                    }
                }
            } else {
                previousPllName = name;
            }
        }

        void finish() throws CubearoniException {
            for (int name = 0; name <= maximumOllName; name++) {
                if (!names.contains(Integer.toString(name))) {
                    throw new CubearoniException("OLL algorithm name \"" + name
                            + "\" is missing.");
                }
            }
        }

        private void addName(final String name, final int lineNum)
                throws CubearoniException {
            if (!names.add(name)) {
                throw new CubearoniException("line " + lineNum
                        + " duplicates algorithm name \"" + name + "\".");
            }
        }
    }
}
