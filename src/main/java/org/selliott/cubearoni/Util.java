package org.selliott.cubearoni;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Miscellaneous utilities
 */
public class Util {
    public static final int EXT_TO_HTM_MODE_NONE = 0;
    public static final int EXT_TO_HTM_MODE_PREFIX = 1;
    public static final int EXT_TO_HTM_MODE_APPLIED = 2;
    private static final int BUFF_SIZE = 16384;
    private static final int MOVE_SCORE_SCALE = 10_000;
    private static final Map<Integer, Integer> validMoveIndexMap =
            new HashMap<>();
    private static final Map<String, byte[]> inMemoryFileMap =
            new HashMap<>();
    private static MissingFileLoader missingFileLoader;
    static {
        for (int i = 0; i < Cube.VALID_MOVES.length; i++) {
            validMoveIndexMap.put(Cube.VALID_MOVES[i], i);
        }
    }

    // Public methods

    /**
     * Loads bytes for a file that is not already cached in memory.
     */
    @FunctionalInterface
    public interface MissingFileLoader {
        /** Loads the named file and returns its bytes. */
        byte[] load(String baseName) throws Exception;
    }

    /**
     * Sets the optional loader used before ordinary file or URL I/O.
     */
    public static void setMissingFileLoader(final MissingFileLoader loader) {
        missingFileLoader = loader;
    }

    /**
     * Convert an array list of Integer to a primitive array.
     *
     * @param arrayList
     *            array to convert
     * @return array of int[]
     */
    public static int[] arrayListToPrimitive(List<Integer> arrayList) {
        // TODO: Find more elegant way of of this such as Apache's
        // ArrayUtils.toPrimitive(). Maybe Java 8 will help.
        final int[] ints = new int[arrayList.size()];
        final Integer[] integers = arrayList.toArray(new Integer[0]);
        for (int i = 0; i < arrayList.size(); i++) {
            ints[i] = integers[i];
        }
        return ints;
    }

    /**
     * Sorts a list of ints in place
     *
     * @param list
     *            the list of ints
     */
    static int bubbleSort(final int[] list) {
        int i;
        int swaps = 0;
        int swapsOld = 0;
        while (true) {
            for (i = 0; i <= list.length - 2; i++) {
                if (list[i] > list[i + 1]) {
                    // swap
                    int buff = list[i];
                    list[i] = list[i + 1];
                    list[i + 1] = buff;
                    swaps++;
                }
            }
            if (swaps == swapsOld) {
                // done sorting
                break;
            }
            swapsOld = swaps;
        }
        return swaps;
    }

    /**
     * Concatenate two int arrays.
     *
     * @param a
     *            first array, may be null
     * @param b
     *            second array, may be null
     * @return concatenated array, or the non-null input when one is null
     */
    public static int[] concat(final int[] a, final int[] b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        final int aLen = a.length;
        final int bLen = b.length;
        final int[] ret = new int[aLen + bLen];
        System.arraycopy(a, 0, ret, 0, aLen);
        System.arraycopy(b, 0, ret, aLen, bLen);
        return ret;
    }

    /**
     * Translate moves that may include X/Y/Z, M/E/S, and wide moves into base
     * R/L/U/D/F/B moves.
     *
     * @param extMoves
     *            moves to translate
     * @param mode
     *            one of EXT_TO_HTM_MODE_*
     * @return translated base moves
     * @throws CubearoniException
     */
    public static int[] extMovesToHTMMoves(
            final int[] extMoves, final int mode) throws CubearoniException {
        // Fast path avoid NPE.
        if (extMoves == null) {
            return new int[0];
        }
        if ((mode != EXT_TO_HTM_MODE_NONE)
                && (mode != EXT_TO_HTM_MODE_PREFIX)
                && (mode != EXT_TO_HTM_MODE_APPLIED)) {
            throw new CubearoniException("Unsupported ext->htm mode: " + mode + ".");
        }

        // Fast path avoid needless translation. Note that this means that the
        // returned value may be what was passed in.
        boolean isExt = false;
        for (final int extMove : extMoves) {
            if (isExtMove(extMove)) {
                isExt = true;
                break;
            }
        }
        if (!isExt) {
            return extMoves;
        }

        // Initially the move map is no-op by mapping each move to itself.
        // It will be updated with each cube rotation.
        final int[] moveMap = moveMapCreate();

        final ArrayList<Integer> htmMoves = new ArrayList<>();
        for (final int extMove : extMoves) {
            // It's an ext move, or it's a HTM move that may need mapping. Get
            // the root so we can find it in a table, and then add the equivalent
            // HTM moves, and update our mapping based on the cube rotation.
            final int extRootMove = getRootMove(extMove);
            final int extOffset   = extMove - extRootMove;

            // Convert the ext move to a sequence of HTM moves ending with a cube
            // rotation.
            int[] htmMovesCubeRotation = Cube.rootExtMoveToHTMMoveMap.get(extRootMove);
            if (htmMovesCubeRotation == null) {
                // The extended move must be a cube rotation, so just use it directly.
                htmMovesCubeRotation = new int[]{extRootMove};
            }
            for (final int move  : htmMovesCubeRotation) {
                // Here we need to combine the offset of the move with the
                // original offset of the ext move.
                final int rootMove = getRootMove(move);
                final int moveOffset = move - rootMove;
                final int combinedOffset = combineOffsets(extOffset, moveOffset);
                final int adjustedMove = rootMove + combinedOffset;
                if (isCubeRotation(adjustedMove)) {
                    moveMapUpdate(moveMap, adjustedMove);
                } else {
                    final int mappedMove = moveMapApply(moveMap, rootMove);
                    htmMoves.add(mappedMove + combinedOffset);
                }
            }
        }

        // In some cases we want to add additional moves to the moves that
        // will be returned so that they do not introduce a net cube rotation
        // (so that the cube is in the same orientation as before the moves
        // are applied) as doing so assures that moves, such as "R", has
        // the expected meaning to the user. Typically prior to the moves
        // being applied the cube was solved. The moves are prepended.
        final List<Integer> correctiveRotations =  new ArrayList<>();
        if (mode != EXT_TO_HTM_MODE_NONE) {
            // First determine rotations needed to R back to the original R
            // location. The first of X, Y and then Z is preferred. X-axis rotations
            // won't help in this case, so Y-axis rotations ares preferred.
            final int mappedR = moveMapApplyInverse(moveMap, Cube.M_R);
            final int correctR;
            switch (mappedR) {
                case Cube.M_R:
                    // No rotation needed.
                    correctR = Cube.M_NONE;
                    break;
                case Cube.M_L:
                    correctR = Cube.M_Y2;
                    break;
                case Cube.M_U:
                    correctR =  Cube.M_Z;
                    break;
                case Cube.M_D:
                    correctR = Cube.M_ZP;
                    break;
                case Cube.M_F:
                    correctR = Cube.M_YP;
                    break;
                case Cube.M_B:
                    correctR = Cube.M_Y;
                    break;
                default:
                    // This shouldn't happen.
                    throw new CubearoniException("Invalid cube rotation mapping: R " +
                            "is mapped to " + mappedR + ".");
            }
            if (correctR != Cube.M_NONE) {
                moveMapUpdate(moveMap, correctR);
                correctiveRotations.add(correctR);
            }

            // Next determine rotations needed to U back to the original U
            // location. Since we know that R is in the correct place it should
            // be possible to do this only with X-axis rotations. U must be on
            // one of the four faces adjacent to R.
            final int mappedU = moveMapApplyInverse(moveMap, Cube.M_U);
            final int correctU;
            switch (mappedU) {
                case Cube.M_U:
                    // No rotation needed.
                    correctU = Cube.M_NONE;
                    break;
                case Cube.M_D:
                    correctU = Cube.M_X2;
                    break;
                case Cube.M_F:
                    correctU = Cube.M_X;
                    break;
                case Cube.M_B:
                    correctU = Cube.M_XP;
                    break;
                default:
                    // This shouldn't happen.
                    throw new CubearoniException("Invalid cube rotation mapping: U " +
                            "is mapped to " + mappedU + ".");
            }
            if (correctU != Cube.M_NONE) {
                moveMapUpdate(moveMap, correctU);
                correctiveRotations.add(correctU);
            }

            if (!correctiveRotations.isEmpty()) {
                // moveMap should now be a no-op. Compare to moveMapCreate().
                final int[] newMap = moveMapCreate();
                if (!Arrays.equals(moveMap, newMap)) {
                    throw new CubearoniException("Invalid move map after corrective " +
                            "rotations: " + Arrays.toString(moveMap));
                }
            }

            if (mode == EXT_TO_HTM_MODE_PREFIX) {
                // Prepend htmMoves with correctiveRotations.
                if (!correctiveRotations.isEmpty()) {
                    htmMoves.addAll(0, correctiveRotations);
                }
            } else {
                // Apply corrective rotations to the HTM moves in one pass so
                // that only HTM moves are returned.
                if (!correctiveRotations.isEmpty()) {
                    final int[] correctionMap = moveMapCreate();
                    for (final int rotation : correctiveRotations) {
                        moveMapUpdate(correctionMap, rotation);
                    }
                    for (int i = 0; i < htmMoves.size(); i++) {
                        final int move = htmMoves.get(i);
                        final int rootMove = getRootMove(move);
                        final int moveOffset = move - rootMove;
                        final int mappedMove = moveMapApply(correctionMap, rootMove);
                        htmMoves.set(i, mappedMove + moveOffset);
                    }
                }
            }
        }

        return arrayListToPrimitive(htmMoves);
    }

    /**
     * Parse and translate a human readable move string that may include
     * extended notation into base R/L/U/D/F/B moves.
     *
     * @param ms
     *            human readable move string
     * @param mode
     *            one of EXT_TO_HTM_MODE_*
     * @return translated base moves
     * @throws CubearoniException
     */
    public static int[] extMovesToHTMMoves(final String ms,
                                           final int mode)
            throws CubearoniException {
        return extMovesToHTMMoves(moveStrToExtendedMoves(ms), mode);
    }

    /**
     * Return true if the move is one of the supported cube rotations.
     *
     * @param extMove
     *            move to check
     * @return true if the move is X/Y/Z with optional suffix
     */
    public static boolean isCubeRotation(final int extMove) {
        return extMove >= Cube.M_X && extMove <= Cube.M_Z2P;
    }

    /**
     * Determine if a move is an extended move, which is a non-HTM move.
     *
     * @param move The move to consider.
     * @return True if the move is extended. False if the move is a HTM move.
     */
    public static boolean isExtMove(final int move) {
        return move >= Cube.M_M;
    }

    /**
     * Convert a human readable move string to an array of possibly extended
     * moves.
     *
     * @param ms
     *            human readable moves
     * @return array of moves that may include X/Y/Z, M/E/S, and wide moves
     * @throws CubearoniException
     */
    public static int[] moveStrToExtendedMoves(final String ms)
            throws CubearoniException {
        return moveStrToMoves(ms, Cube.extStrToMoveMap);
    }

    /**
     * Convert a human readable move string to an array of base moves.
     *
     * @param ms
     *            human readable moves
     * @return array of moves
     * @throws CubearoniException
     */
    public static int[] moveStrToMoves(final String ms) throws CubearoniException {
        return moveStrToMoves(ms, Cube.strToMoveMap);
    }

    /**
     * Score a move sequence with length as the dominant term and preferred move
     * order as a tie-breaker.
     *
     * @param moves
     *            move sequence to score
     * @return score where lower is better
     * @throws CubearoniException
     *             if a move is not in Cube.VALID_MOVES
     */
    public static int scoreMoves(final int[] moves) throws CubearoniException {
        if (moves == null) {
            return 0;
        }
        int score = MOVE_SCORE_SCALE * moves.length;
        for (final int mv : moves) {
            final Integer idx = validMoveIndexMap.get(mv);
            if (idx == null) {
                throw new CubearoniException("Move " + mv
                        + " is not in Cube.VALID_MOVES.");
            }
            score += idx.intValue();
        }
        return score;
    }

    /**
     * Register an in-memory file by basename for environments where Java file
     * and URL I/O is unavailable (for example TeaVM in JS runtimes).
     *
     * @param baseName
     *            file basename, e.g. "cross.crdb.gz"
     * @param bytes
     *            file contents
     */
    public static void setInMemoryFile(final String baseName, final byte[] bytes) {
        if ((baseName == null) || (baseName.isEmpty()) || (bytes == null)) {
            return;
        }
        inMemoryFileMap.put(baseName, bytes.clone());
    }

    /**
     * Remove all in-memory files.
     */
    public static void clearInMemoryFiles() {
        inMemoryFileMap.clear();
    }

    /**
     * Return the contents of of an ordinary file.
     *
     * @param fName
     *            The file name.
     * @return
     * @throws CubearoniException
     */
    public static byte[] readFileFully(final String fName) throws CubearoniException {
        final byte[] inMemoryBytes = getOrLoadInMemoryFile(fName);
        if (inMemoryBytes != null) {
            return inMemoryBytes;
        }
        try (final FileInputStream fIn = new FileInputStream(fName)) {
            // Generally only small files will be read this way, so a small buffer.
            final byte[] buff = new byte[1024];
            final ByteArrayOutputStream bAOS = new ByteArrayOutputStream();
            while (true) {
                final int read = fIn.read(buff);
                if (read == -1) {
                    break;
                }
                bAOS.write(buff, 0, read);
            }
            return bAOS.toByteArray();
        } catch (final Exception e) {
            throw new CubearoniException("Could not read file \"" + fName
                    + "\": " + e.getMessage());
        }
    }

    /**
     * Return the contents of a URL.
     *
     * @param url
     *            URL to read
     * @return URL contents
     * @throws CubearoniException
     *             if the URL cannot be read
     */
    public static byte[] readFileFully(final URL url) throws CubearoniException {
        final byte[] inMemoryBytes = getOrLoadInMemoryFile(url.getPath());
        if (inMemoryBytes != null) {
            return inMemoryBytes;
        }
        try (final InputStream in = url.openStream()) {
            final ByteArrayOutputStream bAOS = new ByteArrayOutputStream();
            final byte[] buff = new byte[1024];
            while (true) {
                final int read = in.read(buff);
                if (read == -1) {
                    break;
                }
                bAOS.write(buff, 0, read);
            }
            return bAOS.toByteArray();
        } catch (final Exception e) {
            throw new CubearoniException("Could not read URL \"" + url
                    + "\": " + e.getClass().getName() + ": "
                    + e.getMessage());
        }
    }

    /**
     * Return the decompressed contents of of a GZipped file.
     *
     * @param fName
     *            The file name.
     * @return
     * @throws CubearoniException
     */
    public static byte[] readGZIPFully(final String fName) throws CubearoniException {
        final byte[] inMemoryBytes = getOrLoadInMemoryFile(fName);
        try {
            if (inMemoryBytes != null) {
                return readGZIPFully(new ByteArrayInputStream(inMemoryBytes));
            }
            return readGZIPFully(new FileInputStream(fName));
        } catch (final Exception e) {
            throw new CubearoniException("Could not read GZIP file \"" + fName
                    + "\": " + e.getClass().getName() + ": "
                    + e.getMessage());
        }
    }

    /**
     * Return the decompressed contents of of a GZipped file given a URL.
     *
     * @param url
     *            The URL.
     * @return
     * @throws CubearoniException
     */
    public static byte[] readGZIPFully(final URL url) throws CubearoniException {
        final byte[] inMemoryBytes = getOrLoadInMemoryFile(url.getPath());
        try {
            if (inMemoryBytes != null) {
                return readGZIPFully(new ByteArrayInputStream(inMemoryBytes));
            }
            return readGZIPFully(url.openStream());
        } catch (final Exception e) {
            throw new CubearoniException("Could not read GZIP URL \"" + url
                    + "\": " + e.getClass().getName() + ": "
                    + e.getMessage());
        }
    }

    // Private methods

    /**
     * Return the basename from a path-like string.
     *
     * @param path
     *            path to process
     * @return basename, or null if input is null
     */
    private static String basename(final String path) {
        if (path == null) {
            return null;
        }
        int idx = path.lastIndexOf('/');
        final int idxBack = path.lastIndexOf('\\');
        if (idxBack > idx) {
            idx = idxBack;
        }
        return (idx >= 0) ? path.substring(idx + 1) : path;
    }

    /**
     * Look up a registered in-memory file by exact name or basename.
     *
     * @param fileName
     *            file name or path
     * @return cloned contents, or null when no match exists
     */
    private static byte[] getInMemoryFile(final String fileName) {
        if ((fileName == null) || fileName.isEmpty()) {
            return null;
        }
        byte[] bytes = inMemoryFileMap.get(fileName);
        if (bytes != null) {
            return bytes.clone();
        }
        bytes = inMemoryFileMap.get(basename(fileName));
        return (bytes == null) ? null : bytes.clone();
    }

    /**
     * Returns cached bytes, invoking the configured loader on a cache miss.
     */
    private static byte[] getOrLoadInMemoryFile(final String fileName)
            throws CubearoniException {
        byte[] bytes = getInMemoryFile(fileName);
        if ((bytes != null) || (missingFileLoader == null)) {
            return bytes;
        }

        final String baseName = basename(fileName);
        try {
            bytes = missingFileLoader.load(baseName);
        } catch (final Throwable t) {
            throw new CubearoniException("Could not load file \"" + baseName
                    + "\": " + t.getClass().getName() + ": " + t.getMessage());
        }
        if (bytes == null) {
            throw new CubearoniException("Loader returned null for file \""
                    + baseName + "\".");
        }
        setInMemoryFile(baseName, bytes);
        return bytes.clone();
    }

    /**
     * Get the offset for a move. This is the numeric offset relative to the
     * root move.
     *
     * @param move The move to get the offset for.
     * @return The offset for the move, which is 0 for a root move.
     */
    public static int getOffset(final int move) {
        if (move <= Cube.M_NONE) {
            return Cube.OFF_NONE;
        }
        return move & Cube.M_FLAG_2P;
    }

    /**
     * This gets the offset, but as the amount of clockwise 90 degree rotations.
     * For root moves this method returns 1.
     *
     * @param move The move to get the offset for as a rotation.
     * @return The offset for the move as a rotation, in range [-2, 2].
     */
    public static int getOffsetAsRotation(final int move) {
        final int offset =  getOffset(move);
        switch (offset) {
            case Cube.OFF_NONE:
                return 1;
            case Cube.OFF_P:
                return -1;
            case Cube.OFF_2:
                return 2;
            case Cube.OFF_2P:
                return -2;
            default:
                // This shouldn't happen.
                throw new IllegalStateException("Invalid move offset: " + offset);
        }
    }

    /**
     * Convert the number of 90 degree clockwise rotations to an offset.
     * For example, 1 becomes Cube.OFF_NONE and -1 becomes Cube.OFF_P.
     *
     * @param rotation The rotation.
     * @return The offset.
     */
    public static int getOffsetFromRotation(final int rotation) {
        switch (rotation) {
            case -1:
                return Cube.OFF_P;
            case 1:
                return Cube.OFF_NONE;
            case 2:
                return Cube.OFF_2;
            default:
                // This shouldn't happen.
                throw new IllegalStateException("Invalid move rotation: " + rotation);
        }
    }

    /**
     * Get the root move for a move. This is the move minus its offset.
     *
     * @param move The move to get the root for.
     * @return The root of the move.
     */
    public static int getRootMove(final int move) {
        if (move <= Cube.M_NONE) {
            return Cube.M_NONE;
        }
        return move & ~Cube.M_FLAG_2P;
    }

    /** Returns whether a move offset includes the double-turn flag. */
    private static boolean isDoubleOffset(final int offset) {
        return (offset & Cube.M_FLAG_2) != 0;
    }

    /** Combines two move offsets into their equivalent resulting offset. */
    private static int combineOffsets(final int o1, final int o2) {
        if (isDoubleOffset(o1) || isDoubleOffset(o2)) {
            return Cube.OFF_2 | ((o1 ^ o2) & Cube.M_FLAG_P);
        }
        return (o1 ^ o2) & Cube.M_FLAG_P;
    }

    /** Returns whether a move has no prime or double-turn offset. */
    private static boolean isRootMove(final int move) {
        return getOffset(move) == 0;
    }

    /**
     * Apply the current move map to a root move.
     *
     * @param moveMap
     *            mapping from source root moves to mapped root moves
     * @param move
     *            source root move
     * @return mapped root move
     */
    private static int moveMapApply(final int[] moveMap, final int move) {
        return moveMap[move];
    }

    /**
     * Apply the inverse of the current move map to a root move.
     *
     * @param moveMap
     *            mapping from source root moves to mapped root moves
     * @param mappedMove
     *            mapped root move to invert
     * @return source root move that maps to mappedMove
     * @throws CubearoniException
     *             if mappedMove is not found in the map
     */
    private static int moveMapApplyInverse(final int[] moveMap,
            final int mappedMove) throws CubearoniException {
        for (int rootMove = Cube.M_R; rootMove <= Cube.M_B; rootMove += 4) {
            if (moveMap[rootMove] == mappedMove) {
                return rootMove;
            }
        }
        throw new CubearoniException("Inverse mapping for root move " + mappedMove
                + " was not found.");
    }

    /**
     * Create a new move map that has no effect my mapping moves to themselves.
     * This is a spares array that only has entries for root moves.
     *
     * @return The move map.
     */
    private static int[] moveMapCreate() {
        // Create a move map that relates the root moves to themselves. This
        // sparse array approach is used for performance.
        // TODO: We map want to introduce double prime ("2'") moves in which
        //  case this hard coded assumption needs to be revisited.
        final int[] moveMap = new int[Cube.M_B2P + 1];

        for (int rootMove = Cube.M_R; rootMove <= Cube.M_B; rootMove += 4) {
            moveMap[rootMove] = rootMove;
        }
        return moveMap;
    }

    /**
     * Update the move map for one cube rotation move.
     *
     * @param moveMap
     *            mapping from source root moves to mapped root moves
     * @param move
     *            cube rotation move (X/Y/Z, including prime and double turns)
     */
    private static void moveMapUpdate(final int[] moveMap, final int move) {
        int first;
        switch (move) {
            case Cube.M_X:
                first = moveMap[Cube.M_D];
                moveMap[Cube.M_D] = moveMap[Cube.M_B];
                moveMap[Cube.M_B] = moveMap[Cube.M_U];
                moveMap[Cube.M_U] = moveMap[Cube.M_F];
                moveMap[Cube.M_F] = first;
                break;
            case Cube.M_XP:
                first = moveMap[Cube.M_D];
                moveMap[Cube.M_D] = moveMap[Cube.M_F];
                moveMap[Cube.M_F] = moveMap[Cube.M_U];
                moveMap[Cube.M_U] = moveMap[Cube.M_B];
                moveMap[Cube.M_B] = first;
                break;
            case Cube.M_X2:
            case Cube.M_X2P:
                moveMapUpdate(moveMap, Cube.M_X);
                moveMapUpdate(moveMap, Cube.M_X);
                break;
            case Cube.M_Y:
                first = moveMap[Cube.M_L];
                moveMap[Cube.M_L] = moveMap[Cube.M_F];
                moveMap[Cube.M_F] = moveMap[Cube.M_R];
                moveMap[Cube.M_R] = moveMap[Cube.M_B];
                moveMap[Cube.M_B] = first;
                break;
            case Cube.M_YP:
                first = moveMap[Cube.M_L];
                moveMap[Cube.M_L] = moveMap[Cube.M_B];
                moveMap[Cube.M_B] = moveMap[Cube.M_R];
                moveMap[Cube.M_R] = moveMap[Cube.M_F];
                moveMap[Cube.M_F] = first;
                break;
            case Cube.M_Y2:
            case Cube.M_Y2P:
                moveMapUpdate(moveMap, Cube.M_Y);
                moveMapUpdate(moveMap, Cube.M_Y);
                break;
            case Cube.M_Z:
                first = moveMap[Cube.M_U];
                moveMap[Cube.M_U] = moveMap[Cube.M_L];
                moveMap[Cube.M_L] = moveMap[Cube.M_D];
                moveMap[Cube.M_D] = moveMap[Cube.M_R];
                moveMap[Cube.M_R] = first;
                break;
            case Cube.M_ZP:
                first = moveMap[Cube.M_U];
                moveMap[Cube.M_U] = moveMap[Cube.M_R];
                moveMap[Cube.M_R] = moveMap[Cube.M_D];
                moveMap[Cube.M_D] = moveMap[Cube.M_L];
                moveMap[Cube.M_L] = first;
                break;
            case Cube.M_Z2:
            case Cube.M_Z2P:
                moveMapUpdate(moveMap, Cube.M_Z);
                moveMapUpdate(moveMap, Cube.M_Z);
                break;
        }
    }

    /**
     * Convert a move string to integers using the provided token map.
     *
     * @param ms
     *            move string, or null or "none" for no moves.
     * @param moveMap
     *            map from move token to root move value
     * @return parsed move list
     * @throws CubearoniException
     *             if the string contains an unsupported token
     */
    private static int[] moveStrToMoves(final String ms,
                                        final Map<String, Integer> moveMap) throws CubearoniException {
        if ((ms == null) || ("none".endsWith(ms))) {
            // No moves.
            return new int[0];
        }

        final List<Integer> mal = new ArrayList<Integer>();
        int m = 0;
        for (final char mc : ms.toCharArray()) {
            switch (mc) {
                case ' ':
                case '+':
                    // Ignore whitespace and "+" as that does not do anything.
                    break;
                case '-':
                case '\'':
                    // TODO: Support true "2'" behavior distinctly from "2"
                    // in all execution and optimization paths. For now, this
                    // value is preserved but mostly treated as equivalent to 2.
                    m ^= Cube.M_FLAG_P;
                    break;
                case '2':
                    m |= Cube.M_FLAG_2;
                    break;
                default:
                    if (m != 0) {
                        mal.add(m);
                    }
                    final Integer mi = moveMap.get("" + mc);
                    if (mi == null) {
                        throw new CubearoniException("Invalid move character \"" + mc
                                + "\".");
                    }
                    m = mi;
            }
        }
        if (m != 0) {
            mal.add(m);
        }

        return arrayListToPrimitive(mal);
    }

    /**
     * Read and fully decompress a GZIP stream.
     *
     * @param in
     *            GZIP input stream
     * @return decompressed bytes
     * @throws Exception
     *             if reading or decompression fails
     */
    private static byte[] readGZIPFully(final InputStream in) throws Exception {
        try (final DataInputStream dIn = new DataInputStream(new GZIPInputStream(
                new BufferedInputStream(in)))) {
            final ByteArrayOutputStream baOut = new ByteArrayOutputStream();
            final byte[] buf = new byte[BUFF_SIZE];
            while (true) {
                final int len = dIn.read(buf);
                if (len <= 0) {
                    break;
                }
                baOut.write(buf, 0, len);
            }
            return baOut.toByteArray();
        }
    }
}
