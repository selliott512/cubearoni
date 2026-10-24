package org.selliott.cubearoni;

import static org.selliott.cubearoni.Cube.VALID_MOVES;
import static org.selliott.cubearoni.Cube.VALID_MOVES_INV;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;

/**
 * Base class for all cube solvers.
 */
public abstract class Solver extends Mover {
    /**
     * The required header.
     */
    private static final String MAGIC = "Cubearoni\n";
    public static final String DEFAULT_DB_DIR = System.getenv("HOME") + "/git/cubearoni/db";

    /**
     * Properties that must be set and the same for all instances of a database
     * for a given solver.
     */
    private static String[] requiredProps = { "name", "version" };

    protected Phase stopAfterPhase;
    private int[][] solvedCoordinates;

    /** Returns the phase after which this solver stops. */
    public String getStopAfterPhase() {
        return "" + stopAfterPhase;
    }

    /** Sets the phase after which this solver stops. */
    public void setStopAfterPhase(final String stopAfterPhase) throws CubearoniException {
        try {
            this.stopAfterPhase = Phase.valueOf(stopAfterPhase);
        } catch (final IllegalArgumentException e) {
            throw new CubearoniException("\"" + stopAfterPhase
                    + "\" is not a valid phase.");
        }
    }

    /**
     * A map of solvers.
     */
    private static final Map<String, Solver> solverMap = new HashMap<String, Solver>();
    static {
        // Individual solvers. Older does not matter, so alphabetical.
        solverMap.put(Cross.NAME, new Cross());
        solverMap.put(LBSlotSafe.NAME, new LBSlotSafe());
        solverMap.put(LBSlotUnsafe.NAME, new LBSlotUnsafe());
        solverMap.put(LDBCorner.NAME, new LDBCorner());
        solverMap.put(OneSide.NAME, new OneSide());
        solverMap.put(TwoByTwo.NAME, new TwoByTwo());
        solverMap.put(UpCubiesUp.NAME, new UpCubiesUp());

        // Composite solvers. Older does matter as they depend on the above, and
        // CFOP depends on F2L.
        solverMap.put(F2L.NAME, new F2L());

        // Keep "cfop" as a short alias for the Speedsolving variant.
        solverMap.put(CFOP.ALIAS, new CFOP(CFOP.DEFAULT_NAME));
    }

    /**
     * A class object converted to a name.
     *
     * @param clazz
     *            class object to convert to a name
     * @return then name
     */
    public static String getNameFromClass(@SuppressWarnings("rawtypes") Class clazz) {
        final String sn = clazz.getSimpleName();

        // If sn is entirely upper case then the name is that converted to
        // lower case.
        if (sn.toUpperCase().equals(sn)) {
            return sn.toLowerCase();
        }

        final StringBuilder sb = new StringBuilder();
        boolean prevUpperCase = false;
        for (char c : sn.toCharArray()) {
            final boolean upperCase = Character.isUpperCase(c);
            if (upperCase) {
                // All upper case characters become lower case.
                c = Character.toLowerCase(c);
            } else {
                if (prevUpperCase && sb.length() > 1) {
                    // We transitioned from lower to upper case. This means that
                    // the previous upper case character was actually the start
                    // of a new word. Don't insert a hyphen at the beginning.
                    sb.insert(sb.length() - 1, "-");
                }
            }
            sb.append(c);
            prevUpperCase = upperCase;
        }
        return sb.toString();
    }

    /**
     * Gets an instance of a Solver subclass by name.
     *
     * @param name
     *            name of the solver
     * @return the solver
     * @throws CubearoniException
     */
    public static Solver getSolver(final String name) throws CubearoniException {
        return getSolver(name, false);
    }

    /**
     * Gets an instance of a Solver subclass by name.
     *
     * @param name
     *            name of the solver
     * @return the solver
     * @throws CubearoniException
     */
    public static Solver getSolver(final String name, final boolean verbose)
            throws CubearoniException {
        String solverName = name;
        Solver solver = solverMap.get(solverName);
        if (solver == null) {
            CFOP.registerIfValid(solverName);
            solver = solverMap.get(solverName);
        }
        if (solver == null) {
            throw new CubearoniException("Solver \"" + name + "\" does not exist.");
        }
        solver.verbose = verbose;
        return solver;
    }

    /** Registers a solver instance under a user-facing name. */
    static synchronized void register(final String name, final Solver solver) {
        solverMap.put(name, solver);
    }

    /** Removes a solver registration. */
    static synchronized void unregister(final String name) {
        solverMap.remove(name);
    }

    /** Returns a registered solver without attempting dynamic discovery. */
    static synchronized Solver findRegistered(final String name) {
        return solverMap.get(name);
    }

    /**
     * Return a list of saver names that can be passed to getSolver().
     *
     * @return list of solvers
     */
    public static String[] getSolverNames() {
        final String[] names = new String[solverMap.size()];
        int i = 0;
        for (final Iterator<Entry<String, Solver>> it = solverMap.entrySet()
                .iterator(); it.hasNext();) {
            final Entry<String, Solver> pair = it.next();
            names[i++] = pair.getKey();
        }
        return names;
    }

    protected String dbFile;
    protected URL dbURLFile;

    private int[] indexToValueMap;

    protected boolean verbose;
    private int dbLen;
    private Properties properties = new Properties();
    protected String dbVersion = "1";
    private final Map<String, String> options = new HashMap<String, String>();

    private char userFace;
    private boolean leftHanded;
    private final String explicitName;

    protected int[] pieces;
    protected int[][] allowedCoordinates;
    protected int[] edgePieces;
    protected int[] cornerPieces;
    protected int[][] edgeAllowedCoordinates;
    protected int[][] cornerAllowedCoordinates;
    private int[] edgeCoordinateIndex;
    private int[] cornerCoordinateIndex;
    private boolean cornersIndistinguishable;
    private boolean edgesIndistinguishable;
    private boolean cornerOrientationsIgnored;
    private boolean edgeOrientationsIgnored;

    /**
     * Constructor that assumes the DB is in the CWD.
     */
    // TODO: Make private.
    public Solver() {
        this(null);
    }

    /** Constructs a solver with an explicit name instead of a class-derived one. */
    protected Solver(final String explicitName) {
        this.explicitName = explicitName;
        cornersIndistinguishable = false;
        edgesIndistinguishable = false;
        cornerOrientationsIgnored = false;
        edgeOrientationsIgnored = false;
        // For composite do it later due to a tricky initialization order issue.
        if (!isComposite()) {
            setDefaultDBDirAndURL();
        }

        // Just name after the class for now.
        properties.setProperty("name", getClass().getSimpleName());
        setDBVersion(dbVersion);
        setDBProperty("sym-reduction", "false");
    }

    /** Configures the default database location. */
    protected void setDefaultDBDirAndURL() {
        setDBDir(DEFAULT_DB_DIR);
        try {
            // setDBURL(Path.of(DEFAULT_DB_DIR).toUri().toURL());
        } catch (Exception e) {
            // This should not happen, so just use a RuntimeException.
            throw new RuntimeException(e);
        }
    }

    /** Constructs an indexed solver for selected pieces and allowed coordinates. */
    public Solver(final int[] pieces, final int[][] allowedCoordinates) {
        this();

        this.pieces = pieces;
        this.solvedCoordinates = Cube.getSolvedLocs(pieces);
        this.allowedCoordinates = allowedCoordinates;
        splitPieces();
        splitAllowedCoordinates();
        validatePieceAndCoordinateTypes();
        initCoordinateIndices();
    }

    /** Returns whether a coordinate is an edge slot. */
    private static boolean isEdgeCoordinate(final int[] slot) {
        int ones = 0;
        if (slot[0] == 1) {
            ones++;
        }
        if (slot[1] == 1) {
            ones++;
        }
        if (slot[2] == 1) {
            ones++;
        }
        return ones == 1;
    }

    /** Returns whether a coordinate is a corner slot. */
    private static boolean isCornerCoordinate(final int[] slot) {
        return (slot[0] != 1) && (slot[1] != 1) && (slot[2] != 1);
    }

    /** Splits configured pieces into ordered edge and corner groups. */
    private void splitPieces() {
        final ArrayList<Integer> edges = new ArrayList<Integer>();
        final ArrayList<Integer> corners = new ArrayList<Integer>();
        int lastPiece = -1;
        for (final int piece : pieces) {
            // To enforce consistency pieces must be in strictly increasing order.
            if (piece <= lastPiece) {
                throw new IllegalArgumentException("Pieces must be in "
                        + "strictly increasing order. pieces=" +
                        Arrays.toString(pieces));
            }
            if (Cube.isEdge(piece)) {
                edges.add(piece);
            } else {
                corners.add(piece);
            }
        }
        edgePieces = new int[edges.size()];
        for (int i = 0; i < edgePieces.length; i++) {
            edgePieces[i] = edges.get(i);
        }
        cornerPieces = new int[corners.size()];
        for (int i = 0; i < cornerPieces.length; i++) {
            cornerPieces[i] = corners.get(i);
        }
    }

    /** Splits allowed coordinates into edge and corner slots. */
    private void splitAllowedCoordinates() {
        final ArrayList<int[]> edgeCoords = new ArrayList<int[]>();
        final ArrayList<int[]> cornerCoords = new ArrayList<int[]>();
        for (final int[] slot : allowedCoordinates) {
            if (isEdgeCoordinate(slot)) {
                edgeCoords.add(slot);
            } else if (isCornerCoordinate(slot)) {
                cornerCoords.add(slot);
            } else {
                throw new IllegalArgumentException("Coordinate ["
                        + slot[0] + "," + slot[1] + "," + slot[2]
                        + "] is not edge or corner.");
            }
        }
        edgeAllowedCoordinates = edgeCoords.toArray(new int[edgeCoords.size()][]);
        cornerAllowedCoordinates = cornerCoords
                .toArray(new int[cornerCoords.size()][]);
    }

    /** Ensures each piece group has enough allowed coordinates. */
    private void validatePieceAndCoordinateTypes() {
        if (edgePieces.length > edgeAllowedCoordinates.length) {
            throw new IllegalArgumentException("Not enough edge coordinates.");
        }
        if (cornerPieces.length > cornerAllowedCoordinates.length) {
            throw new IllegalArgumentException("Not enough corner coordinates.");
        }
    }

    /** Builds a direct XYZ-to-slot-index lookup table. */
    private int[] buildCoordinateIndex(final int[][] coords) {
        final int[] coordIndex = new int[27];
        Arrays.fill(coordIndex, -1);
        for (int i = 0; i < coords.length; i++) {
            final int[] slot = coords[i];
            coordIndex[(slot[0] * 9) + (slot[1] * 3) + slot[2]] = i;
        }
        return coordIndex;
    }

    /** Initializes direct coordinate lookup tables for edges and corners. */
    private void initCoordinateIndices() {
        edgeCoordinateIndex = buildCoordinateIndex(edgeAllowedCoordinates);
        cornerCoordinateIndex = buildCoordinateIndex(cornerAllowedCoordinates);
    }

    /** Returns the allowed-slot index for an XYZ coordinate. */
    protected int slotIndex(final int x, final int y, final int z,
            final int[] coordIndex) {
        if (coordIndex == null) {
            return -1;
        }
        return coordIndex[(x * 9) + (y * 3) + z];
    }

    /** Computes a nonnegative integer power using integer arithmetic. */
    private int powInt(final int base, final int exp) {
        int ret = 1;
        for (int i = 0; i < exp; i++) {
            ret *= base;
        }
        return ret;
    }

    /** Returns the number of ordered selections of r items from n items. */
    private int nPr(final int n, final int r) {
        int ret = 1;
        for (int i = 0; i < r; i++) {
            ret *= (n - i);
        }
        return ret;
    }

    /** Returns the number of unordered selections of r items from n items. */
    private int nCr(final int n, final int r) {
        if ((r < 0) || (r > n)) {
            return 0;
        }
        final int k = Math.min(r, n - r);
        long ret = 1;
        for (int i = 1; i <= k; i++) {
            ret = (ret * (n - k + i)) / i;
        }
        return (int) ret;
    }

    /** Returns a piece group's permutation or combination count. */
    private int getPermSize(final int coordLen, final int pieceLen,
            final boolean indistinguishable) {
        return indistinguishable ? nCr(coordLen, pieceLen)
                : nPr(coordLen, pieceLen);
    }

    /** Ranks a sorted combination using colexicographic order. */
    private int rankCombination(final int[] selected, final int n, final int k) {
        int rank = 0;
        for (int i = 0; i < k; i++) {
            rank += nCr(selected[i], i + 1);
        }
        return rank;
    }

    /** Reconstructs a colexicographically ranked combination. */
    private int[] unrankCombination(int rank, final int n, final int k) {
        final int[] selected = new int[k];
        int upper = n - 1;

        for (int i = k; i >= 1; i--) {
            int x = upper;
            while (x >= 0 && nCr(x, i) > rank) {
                x--;
            }
            selected[i - 1] = x;
            rank -= nCr(x, i);
            upper = x - 1;
        }

        return selected;
    }

    /** Encodes one piece group's permutation and orientations. */
    private int[] getPermOrient(final Cube cube, final int[] groupPieces,
            final int[][] groupCoords, final int[] groupCoordIndex,
            final int orientationRadix, final boolean indistinguishable)
            throws CubearoniException {
        if (groupPieces.length == 0) {
            return new int[] { 0, 0 };
        }
        final int[][] locsTwists = cube.findAllPieces(groupPieces);
        if (!indistinguishable) {
            final boolean[] used = new boolean[groupCoords.length];
            int permRank = 0;
            int orient = 0;
            int orientPlace = 1;
            for (int i = 0; i < locsTwists.length; i++) {
                final int[] locTwist = locsTwists[i];
                final int spatial = slotIndex(locTwist[0], locTwist[1], locTwist[2],
                        groupCoordIndex);
                if (spatial < 0) {
                    throw new CubearoniException("Piece is not in an allowed slot.");
                }
                int compact = spatial;
                for (int s = 0; s < spatial; s++) {
                    if (used[s]) {
                        compact--;
                    }
                }
                used[spatial] = true;
                permRank = (permRank * (groupCoords.length - i)) + compact;
                if (orientationRadix > 1) {
                    orient += locTwist[3] * orientPlace;
                    orientPlace *= orientationRadix;
                }
            }
            return new int[] { permRank, orient };
        }

        final int n = groupCoords.length;
        final int k = groupPieces.length;
        final int[] spatials = new int[k];
        final int[] twistBySpatial = new int[n];
        Arrays.fill(twistBySpatial, -1);
        for (int i = 0; i < locsTwists.length; i++) {
            final int[] locTwist = locsTwists[i];
            final int spatial = slotIndex(locTwist[0], locTwist[1], locTwist[2],
                    groupCoordIndex);
            if (spatial < 0) {
                throw new CubearoniException("Piece is not in an allowed slot.");
            }
            if (twistBySpatial[spatial] != -1) {
                throw new CubearoniException("Duplicate piece location found.");
            }
            spatials[i] = spatial;
            twistBySpatial[spatial] = orientationRadix > 1 ? locTwist[3] : 0;
        }
        Arrays.sort(spatials);
        final int permRank = rankCombination(spatials, n, k);
        int orient = 0;
        int orientPlace = 1;
        for (final int spatial : spatials) {
            if (orientationRadix > 1) {
                orient += twistBySpatial[spatial] * orientPlace;
                orientPlace *= orientationRadix;
            }
        }
        return new int[] { permRank, orient };
    }

    /** Decodes one piece group's permutation and orientations into a cube. */
    private void setFromPermOrient(final Cube cube, final int permRankIn,
            final int orientIn, final int[] groupPieces, final int[][] groupCoords,
            final int orientationRadix, final boolean indistinguishable)
            throws CubearoniException {
        if (groupPieces.length == 0) {
            return;
        }
        int permRank = permRankIn;
        int orient = orientIn;
        if (!indistinguishable) {
            final int[] compact = new int[groupPieces.length];
            for (int i = groupPieces.length - 1; i >= 0; i--) {
                final int radix = groupCoords.length - i;
                compact[i] = permRank % radix;
                permRank /= radix;
            }

            final boolean[] used = new boolean[groupCoords.length];
            for (int i = 0; i < groupPieces.length; i++) {
                final int compactIdx = compact[i];
                int spatialIdx = -1;
                int seen = 0;
                for (int s = 0; s < groupCoords.length; s++) {
                    if (used[s]) {
                        continue;
                    }
                    if (seen == compactIdx) {
                        spatialIdx = s;
                        break;
                    }
                    seen++;
                }
                if (spatialIdx < 0) {
                    throw new CubearoniException("Invalid compact index.");
                }
                used[spatialIdx] = true;
                final int[] slot = groupCoords[spatialIdx];
                cube.setPiece(slot[0], slot[1], slot[2], groupPieces[i]);
                cube.setTwist(slot[0], slot[1], slot[2],
                        orientationRadix > 1 ? orient % orientationRadix : 0);
                if (orientationRadix > 1) {
                    orient /= orientationRadix;
                }
            }
            return;
        }

        final int[] selected = unrankCombination(permRank, groupCoords.length,
                groupPieces.length);
        for (int i = 0; i < groupPieces.length; i++) {
            final int[] slot = groupCoords[selected[i]];
            cube.setPiece(slot[0], slot[1], slot[2], groupPieces[i]);
            cube.setTwist(slot[0], slot[1], slot[2],
                    orientationRadix > 1 ? orient % orientationRadix : 0);
            if (orientationRadix > 1) {
                orient /= orientationRadix;
            }
        }
    }

    /**
     * Convert the current cube to an integer and return it.
     *
     * @param cube
     *            the cube
     * @return
     * @throws CubearoniException
     */
    protected int getIndex(final Cube cube) throws CubearoniException {
        if ((pieces == null) || (allowedCoordinates == null)) {
            throw new CubearoniException("pieces/coordinates are not configured.");
        }
        final int edgePermSize = getPermSize(edgeAllowedCoordinates.length,
                edgePieces.length, edgesIndistinguishable);
        final int cornerRadix = cornerOrientationsIgnored ? 1 : 3;
        final int edgeRadix = edgeOrientationsIgnored ? 1 : 2;
        final int cornerTwistSize = powInt(cornerRadix, cornerPieces.length);
        final int edgeTwistSize = powInt(edgeRadix, edgePieces.length);

        final Cube adjustedCube;
        if (isAdjustedCube()) {
            adjustedCube = cube.dup();
            adjustCube(adjustedCube);
        } else {
            adjustedCube = cube;
        }

        final int[] corner = getPermOrient(adjustedCube, cornerPieces,
                cornerAllowedCoordinates, cornerCoordinateIndex, cornerRadix,
                cornersIndistinguishable);
        final int[] edge = getPermOrient(adjustedCube, edgePieces,
                edgeAllowedCoordinates, edgeCoordinateIndex, edgeRadix,
                edgesIndistinguishable);

        int index = corner[0];
        index = (index * edgePermSize) + edge[0];
        index = (index * cornerTwistSize) + corner[1];
        index = (index * edgeTwistSize) + edge[1];
        return index;
    }

    /** Reconstructs the solver-relevant cube state represented by an index. */
    protected void setIndex(final Cube cube, int index) throws CubearoniException {
        if ((pieces == null) || (allowedCoordinates == null)) {
            throw new IllegalStateException("pieces/coordinates are not configured.");
        }
        final int cornerPermSize = getPermSize(cornerAllowedCoordinates.length,
                cornerPieces.length, cornersIndistinguishable);
        final int edgePermSize = getPermSize(edgeAllowedCoordinates.length,
                edgePieces.length, edgesIndistinguishable);
        final int cornerRadix = cornerOrientationsIgnored ? 1 : 3;
        final int edgeRadix = edgeOrientationsIgnored ? 1 : 2;
        final int cornerTwistSize = powInt(cornerRadix, cornerPieces.length);
        final int edgeTwistSize = powInt(edgeRadix, edgePieces.length);

        final int edgeOrient = index % edgeTwistSize;
        index /= edgeTwistSize;
        final int cornerOrient = index % cornerTwistSize;
        index /= cornerTwistSize;
        final int edgePerm = index % edgePermSize;
        index /= edgePermSize;
        final int cornerPerm = index % cornerPermSize;

        cube.clear();
        setFromPermOrient(cube, cornerPerm, cornerOrient, cornerPieces,
                cornerAllowedCoordinates, cornerRadix, cornersIndistinguishable);
        setFromPermOrient(cube, edgePerm, edgeOrient, edgePieces,
                edgeAllowedCoordinates, edgeRadix, edgesIndistinguishable);
        if (isAdjustedCube()) {
            unadjustCube(cube);
        }
    }

    /**
     * The size of the array needed to hold all indexes. This is maximum index
     * value that getIndex() can possibly return plus one. Ideally this should be
     * the same as dbLen.
     *
     * @return
     * @throws CubearoniException
     */
    protected int getIndexArrayLength() {
        if ((pieces == null) || (allowedCoordinates == null)) {
            throw new IllegalStateException("pieces/coordinates are not configured.");
        }
        final int cornerPermSize = getPermSize(cornerAllowedCoordinates.length,
                cornerPieces.length, cornersIndistinguishable);
        final int edgePermSize = getPermSize(edgeAllowedCoordinates.length,
                edgePieces.length, edgesIndistinguishable);
        final int cornerRadix = cornerOrientationsIgnored ? 1 : 3;
        final int edgeRadix = edgeOrientationsIgnored ? 1 : 2;
        final int cornerTwistSize = powInt(cornerRadix, cornerPieces.length);
        final int edgeTwistSize = powInt(edgeRadix, edgePieces.length);
        return cornerPermSize * edgePermSize * cornerTwistSize * edgeTwistSize;
    }

    /** Returns whether indexing requires solver-specific cube adjustment. */
    protected boolean isAdjustedCube() {
        return false;
    }

    /** Converts a cube to this solver's alternate indexing representation. */
    protected void adjustCube(final Cube cube) throws CubearoniException {
        throw new CubearoniException("adjustCube() is not implemented for solver \""
                + getName() + "\".");
    }

    /** Restores a cube from this solver's alternate indexing representation. */
    protected void unadjustCube(final Cube cube) throws CubearoniException {
        throw new CubearoniException("unadjustCube() is not implemented for solver \""
                + getName() + "\".");
    }

    /** Configures whether indexed corner identities are interchangeable. */
    protected final void setCornersIndistinguishable(
            final boolean cornersIndistinguishable) {
        this.cornersIndistinguishable = cornersIndistinguishable;
    }

    /** Configures whether indexed edge identities are interchangeable. */
    protected final void setEdgesIndistinguishable(
            final boolean edgesIndistinguishable) {
        this.edgesIndistinguishable = edgesIndistinguishable;
    }

    /** Configures whether indexed corner orientations are ignored. */
    protected final void setCornerOrientationsIgnored(
            final boolean cornerOrientationsIgnored) {
        this.cornerOrientationsIgnored = cornerOrientationsIgnored;
    }

    /** Configures whether indexed edge orientations are ignored. */
    protected final void setEdgeOrientationsIgnored(
            final boolean edgeOrientationsIgnored) {
        this.edgeOrientationsIgnored = edgeOrientationsIgnored;
    }

    /** Sets the database version stored in generated headers. */
    protected void setDBVersion(final String dbVersion) {
        this.dbVersion = dbVersion;
        properties.setProperty("version", dbVersion);
    }

    /** Sets a database header property. */
    protected void setDBProperty(final String key, final String value) {
        properties.setProperty(key, value);
    }

    /**
     * Returns custom move sequences for this solver. Each entry is a sequence
     * of moves to be treated as a single transition in the DB graph. Return
     * null to use VALID_MOVES as 1-move sequences.
     */
    protected int[][] getMoveSequences() {
        return null;
    }

    /** Returns custom transitions or one transition per standard move. */
    private int[][] getEffectiveMoveSequences() {
        final int[][] mvsSeq = getMoveSequences();
        if (mvsSeq != null) {
            return mvsSeq;
        }
        final int[][] ret = new int[VALID_MOVES.length][1];
        for (int i = 0; i < VALID_MOVES.length; i++) {
            ret[i][0] = VALID_MOVES[i];
        }
        return ret;
    }

    /** Returns the inverse of a standard move. */
    protected static int getMoveInverse(final int mv) throws CubearoniException {
        for (int i = 0; i < VALID_MOVES.length; i++) {
            if (VALID_MOVES[i] == mv) {
                return VALID_MOVES_INV[i];
            }
        }
        throw new CubearoniException("Move " + mv + " does not have an inverse.");
    }

    /** Returns a move sequence's inverse in execution order. */
    protected static int[] getMoveSequenceInverse(final int[] mvs) throws CubearoniException {
        final int[] inv = new int[mvs.length];
        for (int i = 0; i < mvs.length; i++) {
            inv[i] = getMoveInverse(mvs[mvs.length - 1 - i]);
        }
        return inv;
    }

    /** Applies a sequence of ordinary moves to a cube. */
    private void applyMoves(final Cube cube, final int[] mvs) throws CubearoniException {
        for (final int mv : mvs) {
            cube.move(mv);
        }
    }

    /** Executes database-guided solving with optional exhaustive move search. */
    private int[] execInternal(final Cube cube, final boolean exhaustive,
            final boolean applyUserFace) throws CubearoniException {
        if ((indexToValueMap == null) || (indexToValueMap.length == 0)) {
            throw new CubearoniException(
                    "The database has not been generated or loaded");
        }

        char intFace = 0;
        if (applyUserFace && (userFace != 0)) {
            // Before solving rotate the cube so that the specified face, if
            // any, is on top.
            intFace = cube.getFormat().toInt(userFace);
            cube.rotateKeepColor(null, intFace, false, null);
        }

        int index = getIndex(cube);
        int value = indexToValueMap[index];
        if (value == 0) {
            throw new CubearoniException("Unable to find index " + index
                    + " in database " + dbFile);
        }

        final int[][] moveSeqs = getEffectiveMoveSequences();
        final int[][] moveSeqsInv = new int[moveSeqs.length][];
        for (int i = 0; i < moveSeqs.length; i++) {
            moveSeqsInv[i] = getMoveSequenceInverse(moveSeqs[i]);
        }

        final int valueLimit = limit + 1; // value is 1 based and limit is 0 based.

        // The value of "value" before being updated.
        int valueOld = -1;

        final ArrayList<Integer> movesAL = new ArrayList<Integer>();
        while (value > 1) {
            int[] bestMoves = null;
            int bestValue = Integer.MAX_VALUE;
            int bestTotal = Integer.MAX_VALUE;
            boolean moveFound = false;
            for (int i = 0; i < moveSeqs.length; i++) {
                final int[] mvs = moveSeqs[i];
                if (!isMovesAllowed(cube, mvs, false)) {
                    // "mvs" is not allowed for "cube".
                    continue;
                }
                applyMoves(cube, mvs);
                index = getIndex(cube);
                final int valueNew = indexToValueMap[index];
                if (valueNew == 0) {
                    if (value < valueLimit) {
                        // This should only happen for truncated databases if
                        // the same limit (-N) was not specified for solving as
                        // was for generating the DB.
                        throw new CubearoniException("Index " + index
                                + " is not in database " + dbFile);
                    }
                    applyMoves(cube, moveSeqsInv[i]);
                    continue;
                }

                // total is the new value plus the cost of getting there.
                // This should be equal to the old value when the best move
                // is selected.
                final int total = valueNew + mvs.length;
                if (total <= value) {
                    moveFound = true;
                    if (total < bestTotal) {
                        bestMoves = mvs;
                        bestValue = valueNew;
                        bestTotal = total;
                    }
                    if (!exhaustive) {
                        for (final int mv : mvs) {
                            movesAL.add(mv);
                        }
                        valueOld = value;
                        value = valueNew;
                        break;
                    }
                }
                applyMoves(cube, moveSeqsInv[i]);
            }

            if (!moveFound) {
                throw new CubearoniException("Unable to find a sufficiently good " +
                        "move starting from value " + value + " for index "
                        + getIndex(cube) + " in database " + dbFile);
            }

            if (exhaustive) {
                applyMoves(cube, bestMoves);
                for (final int mv : bestMoves) {
                    movesAL.add(mv);
                }
                valueOld = value;
                value = bestValue;
            }

            // Is the new value reduced by exactly the number of moves it takes to get there?
            if (bestTotal != valueOld) {
                throw new CubearoniException("Index " + getIndex(cube)
                        + " has value " + value + " but best neighbor total is " + bestTotal + ".");
            }

            // Extra check, probably redundant to the above.
            if (bestMoves.length != (valueOld - value)) {
                throw new CubearoniException("Index " + getIndex(cube)
                        + " has value " + value + " but best move sequence length is "
                        + bestMoves.length + " (expected " + (valueOld - value) + ").");
            }
        }

        if (!isSolved(cube)) {
            // This should not happen.
            throw new CubearoniException("Solver \"" + getName()
                    + "\" produced a cube that is not solved");
        }

        final int[] mvs = Util.arrayListToPrimitive(movesAL);

        if (applyUserFace && (userFace != 0)) {
            // Undo the rotation so that the solved face will now be on the user
            // specified face.
            intFace = cube.getFormat().toInt(userFace);
            cube.rotateKeepColor(null, intFace, true, mvs);
        }
        return mvs;
    }

    /**
     * Solve the specified cube and return an array of moves that solves it, or
     * throw an exception if it can't be solved.
     *
     * @param cube
     *            the cube to be solved
     * @return array of moves that solves the cube
     * @throws CubearoniException
     *             throw if the cube can't be solved.
     */
    @Override
    public int[] exec(final Cube cube) throws CubearoniException {
        long before = 0;
        if (verbose) {
            before = System.currentTimeMillis();
        }
        final int[] mvs = execInternal(cube, false, true);

        if (verbose) {
            final long elapsedMsecs = System.currentTimeMillis() - before;
            logExecStats(elapsedMsecs, mvs.length);
        }

        return mvs;
    }

    /** Verifies every populated database index and its optimal solution. */
    public void test() throws CubearoniException {
        if ((indexToValueMap == null) || (indexToValueMap.length == 0)) {
            throw new CubearoniException(
                    "The database has not been generated or loaded");
        }

        final int indexCount = Integer.parseInt(
                properties.getProperty("index-count",
                        Integer.toString(indexToValueMap.length)));
        final int indexMin = Integer.parseInt(
                properties.getProperty("index-min", "0"));
        final int indexMax = Integer.parseInt(
                properties.getProperty("index-max",
                        Integer.toString(indexCount - 1)));
        final int indexSolved = Integer.parseInt(
                properties.getProperty("index-solved", "0"));
        final boolean symReduction = Boolean.parseBoolean(
                properties.getProperty("sym-reduction", "false"));

        if (symReduction) {
            // TODO: add test logic that understands symmetry-reduced indexes.
            throw new CubearoniException("Database test does not yet support "
                    + "symmetry-reduction.");
        }

        if ((indexMin < 0) || (indexMax >= indexCount) || (indexMin > indexMax)
                || (indexCount > indexToValueMap.length)) {
            throw new CubearoniException("Invalid index range in database header: "
                    + "index-min=" + indexMin + " index-max=" + indexMax
                    + " index-count=" + indexCount);
        }

        final int valueLimit = limit + 1; // value is 1 based and limit is 0 based.
        final boolean allowMissingValues = limit < (Integer.MAX_VALUE - 1);
        int solvedCount = 0;
        for (int index = indexMin; index <= indexMax; index++) {
            final int value = indexToValueMap[index];
            if (value == 0) {
                if (allowMissingValues) {
                    continue;
                }
                throw new CubearoniException("Missing value for index " + index
                        + " in non-symmetry-reduced database.");
            }
            if (value > valueLimit) {
                throw new CubearoniException("Index " + index + " has value "
                        + value + " which exceeds configured limit value "
                        + valueLimit + " (-N " + limit + ").");
            }
            final Cube cube = getSolvedCube();
            setIndex(cube, index);
            final int[] mvs = execInternal(cube, true, false);
            if ((mvs.length + 1) != value) {
                throw new CubearoniException("Index " + index + " has value "
                        + value + " but solved in " + mvs.length
                        + " moves (expected value " + (mvs.length + 1) + ").");
            }
            if (mvs.length == 0) {
                solvedCount++;
                if (index != indexSolved) {
                    throw new CubearoniException("Solved index mismatch. header="
                            + indexSolved + " actual=" + index + ".");
                }
            }
            if (verbose) {
                System.out.println(index + "\t" + value + "\t"
                        + Cube.movesToStr(mvs));
            }
        }
        if (solvedCount != 1) {
            throw new CubearoniException("Expected exactly one solved index, but "
                    + solvedCount + " were found.");
        }
    }

    /** Logs elapsed time and move count for a completed solve. */
    protected void logExecStats(final long elapsedMsecs, final int movesCount) {
        System.out.println(String.format(
                "solver: %-16s time: %5d msecs moves: %3d", getName(),
                elapsedMsecs, movesCount));
    }

    /**
     * Generate the DB in memory. This needs to be saved with saveDB().
     *
     * @throws CubearoniException
     */
    public void generateDB() throws CubearoniException {
        // Manually seed with the solved cube.
        final Cube cube = getSolvedCube(); // solved
        if (!isSolved(cube)) {
            // Consistency check. This should not happen.
            throw new CubearoniException("Initial cube is not solved for "
                    + "solver \"" + getName() + "\".");
        }

        // Allocate memory for the DB.
        if (indexToValueMap == null) {
            indexToValueMap = new int[getIndexArrayLength()];
        } else {
            Arrays.fill(indexToValueMap, 0);
        }

        int index = getIndex(cube);
        final int indexSolved = index;
        int indexMin = Integer.MAX_VALUE;
        int indexMax = Integer.MIN_VALUE;
        long valueSum = 0;
        final int[] valueHist = new int[256];
        final int valueLimit = limit + 1; // value is 1 based and limit is 0 based.
        int maxValueSeen = 0;
        final int[][] moveSeqs = getEffectiveMoveSequences();
        final int[][] moveSeqsInv = new int[moveSeqs.length][];
        for (int i = 0; i < moveSeqs.length; i++) {
            moveSeqsInv[i] = getMoveSequenceInverse(moveSeqs[i]);
        }
        final PriorityQueue<ValueIndex> valueIndexesToTry = new PriorityQueue<>(
                Comparator.comparingInt(a -> a.value));

        // 1 based so that 0 can mean unset.
        indexToValueMap[index] = 1;
        valueIndexesToTry.add(new ValueIndex(1, index));

        while (!valueIndexesToTry.isEmpty()) {
            final ValueIndex valueIndex = valueIndexesToTry.poll();
            final int value = valueIndex.value;
            index = valueIndex.index;

            if (indexToValueMap[index] != value) {
                // Stale queue item. We can safely discard it because if the
                // queued value does not match the index, then a better value
                // has already been found and queued.
                continue;
            }

            // This is the first and only non-stale visit for this index.
            if (value >= valueHist.length) {
                throw new CubearoniException("Value " + value
                        + " is out of histogram range " + valueHist.length
                        + ".");
            }

            // Update histogram and other stats.
            valueHist[value]++;
            valueSum += value;
            if (index < indexMin) {
                indexMin = index;
            }
            if (index > indexMax) {
                indexMax = index;
            }
            if (value > maxValueSeen) {
                if (verbose && (maxValueSeen > 0)) {
                    System.out.println("For " + valueHist[maxValueSeen]
                            + " indexes found value " + maxValueSeen + ".");
                }
                maxValueSeen = value;
            }

            if (value >= valueLimit) {
                // Values at the strict cap cannot produce any admissible
                // successor because all move sequences have positive cost.
                continue;
            }

            setIndex(cube, index);
            for (int i = 0; i < moveSeqs.length; i++) {
                final int[] mvs = moveSeqs[i];

                final int valueNew = value + mvs.length;
                if (valueNew > valueLimit) {
                    // This value has reached the configured limit.
                    continue;
                }

                applyMoves(cube, mvs);
                if (!isMovesAllowed(cube, mvs, true)) {
                    // Sometimes it's not obvious that a move is not allowed
                    // until the move is applied and the resulting cube is inspected.
                    applyMoves(cube, moveSeqsInv[i]);
                    continue;
                }

                final int indexNew = getIndex(cube);
                // There could be a isSolved() check here, but that's redundant to test / -t.
                final int valueOld = indexToValueMap[indexNew];
                if ((valueOld == 0) || (valueNew < valueOld)) {
                    indexToValueMap[indexNew] = valueNew;
                    valueIndexesToTry.add(new ValueIndex(valueNew, indexNew));
                }
                applyMoves(cube, moveSeqsInv[i]);
            }
        }
        setDBLen();
        final int valueMax = maxValueSeen;
        final StringBuilder valueHistSB = new StringBuilder();
        for (int value = 1; value <= valueMax; value++) {
            if (value > 1) {
                valueHistSB.append(' ');
            }
            valueHistSB.append(valueHist[value]);
        }
        setDBProperty("index-count", Integer.toString(dbLen));
        setDBProperty("index-max", Integer.toString(indexMax));
        setDBProperty("index-min", Integer.toString(indexMin));
        setDBProperty("index-solved", Integer.toString(indexSolved));
        setDBProperty("value-histogram", valueHistSB.toString());
        setDBProperty("value-max", Integer.toString(valueMax));
        setDBProperty("value-sum", Long.toString(valueSum));
        if (verbose) {
            System.out.println("For " + valueHist[maxValueSeen]
                    + " indexes found value " + maxValueSeen + ".");
        }
        System.out.println("Generated database has length " + dbLen + ".");
    }

    /** Returns whether a move sequence is allowed for the resulting cube state. */
    protected boolean isMovesAllowed(final Cube cube, final int[] mvs, boolean inverse) {
        // For most solvers are moves are permitted regardless of the cube state.
        return true;
    }

    /**
     * Name of this solver
     *
     * @return the name
     */
    public String getName() {
        return (explicitName != null) ? explicitName
                : getNameFromClass(getClass());
    }

    /** Returns the filesystem database path. */
    public String getDbFile() {
        return dbFile;
    }

    /** Returns the database URL, if configured. */
    public URL getDbURLFile() {
        return dbURLFile;
    }

    /** Returns the user-selected face. */
    protected char getUserFace() {
        return userFace;
    }

    /** Returns whether left-handed algorithm preferences are enabled. */
    public boolean isLeftHanded() {
        return leftHanded;
    }

    /**
     * Return a cube that is solved based on the solver's definition of solved.
     * By default this is just a solved cube.
     *
     * @return solved cube
     */
    protected Cube getSolvedCube() {
        return new Cube();
    }

    /**
     * Returns true if the cube is solved.
     *
     * @param cube
     *            cube to check
     * @return if solved
     */
    public boolean isSolved(final Cube cube) {
        for (int i = 0; i < pieces.length; i++) {
            final int[] loc = solvedCoordinates[i];
            if ((cube.getPiece(loc[0], loc[1], loc[2]) != pieces[i])
             || (cube.getTwist(loc[0], loc[1], loc[2]) != 0)) {
                return false;
            }
        }
        return true;
    }
    /** Returns whether verbose logging is enabled. */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Load the database that matches the current solver.
     *
     * @throws CubearoniException
     */
    public void loadDB() throws CubearoniException {
        final long startMillis = System.currentTimeMillis();
        int count = 0;
        int even = 0;
        int odd = 0;
        int indexMin = Integer.MAX_VALUE;
        int indexMax = 0;
        long begin = 0;
        if (verbose) {
            begin = System.currentTimeMillis();
        }

        // Allocate memory for the DB.
        if (indexToValueMap == null) {
            indexToValueMap = new int[getIndexArrayLength()];
        }

        try {
            final byte[] buff;
            if (dbURLFile != null) {
                buff = Util.readGZIPFully(dbURLFile);
            } else {
                buff = Util.readGZIPFully(dbFile);
            }

            if (verbose) {
                System.out.printf("%04d: gunzipd DB\n",
                        System.currentTimeMillis() - startMillis);
            }

            // Make sure it has the expected magic header.
            int off = MAGIC.length();
            if (!(new String(buff, 0, off)).equals(MAGIC)) {
                throw new CubearoniException("Database file \"" + dbFile
                        + "\" does " + "not have expected magic header \""
                        + MAGIC + "\".");
            }
            off++; // an extra new line after the magic.

            int propStart = off;
            // The end of the properties is marked by two consecutive new lines.
            for (; off < buff.length; off++) {
                if ((buff[off - 1] == '\n') && (buff[off] == '\n')) {
                    // Set off to point to the start of the actual database.
                    off++;
                    break;
                }
            }

            final ByteArrayInputStream inBA = new ByteArrayInputStream(buff,
                    propStart, off - propStart);
            final Properties propsNew = new Properties();
            propsNew.load(inBA);
            inBA.close();

            // Make sure that certain required properties have the same value as
            // the existing properties.
            for (final String rp : requiredProps) {
                final Object valueNew = propsNew.get(rp);
                if (valueNew == null) {
                    throw new CubearoniException("Database file \"" + dbFile
                            + "\" is missing requried property \"" + rp);
                }
                final Object value = properties.get(rp);
                if (!valueNew.equals(value)) {
                    throw new CubearoniException("Database file \"" + dbFile
                            + "\" has value \"" + valueNew + "\" \", but "
                            + "value \"" + value + "\" is expected.");
                }
            }

            // The new properties are acceptable, so overwrite the old ones.
            properties = propsNew;

            if (isCompactFormat(propsNew)) {
                loadCompactDB(buff, off, propsNew);
                if (verbose) {
                    System.out.printf("%04d: loadCompactDB() completed\n",
                            System.currentTimeMillis() - startMillis);
                }
            } else {
                throw new CubearoniException("Database file \"" + dbFile
                        + "\" uses an unsupported legacy format. Regenerate "
                        + "it with -g.");
            }
        } catch (final IOException e) {
            throw new CubearoniException("Could not open database file \"" + dbFile
                    + "\" for read: " + e);
        }

        setDBLen();
        if (verbose) {
            for (int index = 0; index < dbLen; index++) {
                if (indexToValueMap[index] == 0) {
                    continue;
                }
                if (index < indexMin) {
                    indexMin = index;
                }
                if (index > indexMax) {
                    indexMax = index;
                }
                count++;
                if ((1 & index) == 0) {
                    even++;
                } else {
                    odd++;
                }
            }
            if (count == 0) {
                indexMin = 0;
            }
        }

        if (verbose) {
            final long end = System.currentTimeMillis();
            System.out.println("File       : " + dbFile);
            System.out.println(String.format("Load time  : %9d msecs",
                    (end - begin)));
            System.out.println(String.format("Count      : %9d", count));
            System.out.println(String.format("Len        : %9d", dbLen));
            System.out.println(String.format("Even       : %9d", even));
            System.out.println(String.format("Odd        : %9d", odd));
            System.out.println(String.format("Min index  : %9d", indexMin));
            System.out.println(String.format("Max index  : %9d", indexMax));
            System.out.println(String.format("Density    : %9.2f",
                    (indexMax == 0) ? 0.0 : ((100.0f * count) / indexMax)));

            System.out.println("");

            System.out.printf("%04d:loadDB() completed\n",
                    System.currentTimeMillis() - startMillis);
        }
    }

    /**
     * Save the database using the compact binary format.
     *
     * @throws CubearoniException
     */
    public void saveDB() throws CubearoniException {
        long begin = 0;
        setDBLen();
        if (verbose) {
            begin = System.currentTimeMillis();
        }
        try (final GZIPOutputStream out = new GZIPOutputStream(
                new BufferedOutputStream(new FileOutputStream(dbFile)));
             final PrintStream outPS = new PrintStream(out)) {
            writeDB(out);
        } catch (final IOException e) {
            throw new CubearoniException("Could not open database file \"" + dbFile
                    + "\" for write: " + e);
        }

        if (verbose) {
            long end = System.currentTimeMillis();
            System.out.println("Saved database \"" + dbFile + "\" in "
                    + (end - begin) + " msecs with length " + dbLen + ".");
        }
    }

    /**
     * Set the actual size of the database by searching backward.
     */
    private void setDBLen() {
        if (dbLen != 0) {
            // Assume that once set it never changes.
            return;
        }
        int len;
        // Find the end of the map manually. All real scores should be non-zero.
        // The end should be found quickly for efficient tightly packed maps.
        for (len = indexToValueMap.length; len > 0; len--) {
            if (indexToValueMap[len - 1] != 0) {
                break;
            }
        }
        dbLen = len;
    }

    /** Returns whether database properties describe a compact format. */
    private static boolean isCompactFormat(final Properties props) {
        if (props.getProperty("bits-per-entry") != null) {
            return true;
        }
        return "compact-v1".equals(props.getProperty("format"));
    }

    /** Decodes a compact database payload into the index map. */
    private void loadCompactDB(final byte[] buff, final int start,
            final Properties props) throws CubearoniException {
        final String lenStr = props.getProperty("index-count");
        final String bitsPerEntryStr = props.getProperty("bits-per-entry");
        final String packing = props.getProperty("packing");
        final int bitsPerEntry;
        if (bitsPerEntryStr != null) {
            try {
                bitsPerEntry = Integer.parseInt(bitsPerEntryStr);
            } catch (final NumberFormatException e) {
                throw new CubearoniException(
                        "Compact DB has invalid bits-per-entry property: "
                                + bitsPerEntryStr);
            }
        } else if ("nibble".equals(packing)) {
            bitsPerEntry = 4;
        } else if ("byte".equals(packing)) {
            bitsPerEntry = 8;
        } else {
            throw new CubearoniException(
                    "Compact DB is missing bits-per-entry property");
        }
        final int length;
        if (lenStr != null) {
            try {
                length = Integer.parseInt(lenStr);
            } catch (final NumberFormatException e) {
                throw new CubearoniException(
                        "Compact DB has invalid index-count property: " + lenStr);
            }
        } else {
            length = getIndexArrayLength();
        }
        if (indexToValueMap == null || indexToValueMap.length != length) {
            indexToValueMap = new int[length];
        }
        if (bitsPerEntry == 4) {
            final int expectedBytes = (length + 1) / 2;
            if (buff.length < start + expectedBytes) {
                throw new CubearoniException("Compact DB payload is truncated.");
            }
            for (int i = 0; i < length; i++) {
                final int b = buff[start + (i / 2)] & 0xff;
                final int value = (i % 2 == 0) ? (b & 0x0f)
                        : ((b >> 4) & 0x0f);
                indexToValueMap[i] = value;
            }
        } else if (bitsPerEntry == 8) {
            if (buff.length < start + length) {
                throw new CubearoniException("Compact DB payload is truncated.");
            }
            for (int i = 0; i < length; i++) {
                indexToValueMap[i] = buff[start + i] & 0xff;
            }
        } else {
            throw new CubearoniException(
                    "Unsupported bits-per-entry value: " + bitsPerEntry);
        }
    }

    /** Writes the database header, properties, and compact payload. */
    private void writeDB(final GZIPOutputStream out) throws IOException {
        // Write the magic header followed by a newline.
        out.write(MAGIC.getBytes());
        out.write('\n');

        final int length = indexToValueMap.length;
        int maxValue = 0;
        for (final int value : indexToValueMap) {
            if (value > maxValue) {
                maxValue = value;
            }
        }
        final int bitsPerEntry = (maxValue <= 15) ? 4 : 8;
        properties.setProperty("bits-per-entry", Integer.toString(bitsPerEntry));
        properties.setProperty("index-count", Integer.toString(length));

        writeProperties(out);
        out.write('\n');

        if (bitsPerEntry == 4) {
            final int byteLen = (length + 1) / 2;
            final byte[] data = new byte[byteLen];
            for (int i = 0; i < length; i++) {
                final int value = indexToValueMap[i];
                if (value > 15) {
                    throw new IOException(
                            "Value " + value + " exceeds nibble range.");
                }
                final int idx = i / 2;
                if ((i & 1) == 0) {
                    data[idx] |= (byte) (value & 0x0f);
                } else {
                    data[idx] |= (byte) ((value & 0x0f) << 4);
                }
            }
            out.write(data);
        } else {
            final byte[] data = new byte[length];
            for (int i = 0; i < length; i++) {
                final int value = indexToValueMap[i];
                data[i] = (byte) (value & 0xff);
            }
            out.write(data);
        }
    }

    /** Writes database properties in sorted key order. */
    private void writeProperties(final java.io.OutputStream out)
            throws IOException {
        final TreeMap<String, String> sorted = new TreeMap<String, String>();
        for (final String key : properties.stringPropertyNames()) {
            sorted.put(key, properties.getProperty(key));
        }
        for (final Entry<String, String> entry : sorted.entrySet()) {
            out.write((entry.getKey() + "=" + entry.getValue() + "\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Sets the directory containing this solver's database. */
    public void setDBDir(final String dbDir) {
        if ((dbDir != null) && (!dbDir.equals(""))) {
            dbFile = dbDir + "/" + getName() + "." + getDBExtension();
        }
    }

    /** Sets the base URL containing this solver's database. */
    public void setDBURL(final URL dbURLDir) throws CubearoniException {
        try {
            dbURLFile = new URL(dbURLDir, getName() + "." + getDBExtension());
        } catch (MalformedURLException e) {
            throw new CubearoniException("Unable to build DB URL:" + e);
        }
    }

    /** Sets the user-selected face by face character. */
    public void setUserFace(final char userFace) {
        this.userFace = userFace;
    }

    /** Sets the user-selected face by internal face index. */
    public void setUserFace(final int userFace) {
        this.userFace = Cube.acStickerToColorMap.get(userFace);
    }

    /** Enables or disables left-handed algorithm preferences. */
    public void setLeftHanded(final boolean leftHanded) {
        this.leftHanded = leftHanded;
        // TODO: Non-CFOP solvers may eventually use this for tie-break move
        // ordering preferences (e.g. R/L bias), but no behavior changes yet.
    }

    /**
     * Replaces solver options with the provided key/value pairs.
     *
     * @param options
     *            options map, or null to clear
     */
    public void setOptions(final Map<String, String> options) {
        this.options.clear();
        if (options != null) {
            this.options.putAll(options);
        }
    }

    /**
     * Sets one solver option value. If value is null, the key is removed.
     *
     * @param key
     *            option key
     * @param value
     *            option value, or null
     */
    public void setOption(final String key, final String value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            options.remove(key);
        } else {
            options.put(key, value);
        }
    }

    /**
     * Returns an immutable view of currently configured solver options.
     *
     * @return options map
     */
    public Map<String, String> getOptions() {
        return Collections.unmodifiableMap(options);
    }

    /** Returns the database filename extension used by this solver. */
    public String getDBExtension() {
        // TODO: Maybe "txt" for composit.
        return isComposite() ? "txt" : "crdb.gz";
    }

    /** Returns whether this solver is composed from other solvers. */
    public boolean isComposite() {
        return false;
    }

    /** Associates a database index with its queued value. */
    private record ValueIndex(int value, int index) {
    }
}
