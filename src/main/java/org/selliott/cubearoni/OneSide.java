package org.selliott.cubearoni;

import static org.selliott.cubearoni.Cube.AXIS_Y;
import static org.selliott.cubearoni.Cube.AXIS_Z;
import static org.selliott.cubearoni.Cube.P_LB;
import static org.selliott.cubearoni.Cube.P_LD;
import static org.selliott.cubearoni.Cube.P_LDB;
import static org.selliott.cubearoni.Cube.P_LDF;
import static org.selliott.cubearoni.Cube.P_LF;
import static org.selliott.cubearoni.Cube.P_LU;
import static org.selliott.cubearoni.Cube.P_LUB;
import static org.selliott.cubearoni.Cube.P_LUF;
import static org.selliott.cubearoni.Cube.P_RB;
import static org.selliott.cubearoni.Cube.P_RD;
import static org.selliott.cubearoni.Cube.P_RDB;
import static org.selliott.cubearoni.Cube.P_RDF;
import static org.selliott.cubearoni.Cube.P_RF;
import static org.selliott.cubearoni.Cube.P_RU;
import static org.selliott.cubearoni.Cube.P_RUB;
import static org.selliott.cubearoni.Cube.P_RUF;
import static org.selliott.cubearoni.Cube.P_UB;
import static org.selliott.cubearoni.Cube.P_UF;
import static org.selliott.cubearoni.Cube.P_DB;
import static org.selliott.cubearoni.Cube.P_DF;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * One-side solver implemented on top of the generic Solver ranking system.
 */
public class OneSide extends Solver {
    public static final String NAME = Solver.getNameFromClass(OneSide.class);

    protected static final int[] PIECES = new int[] { P_LUB, P_LU, P_LUF, P_UB,
            P_UF, P_RUB, P_RU, P_RUF };
    protected static final int[][] ALLOWED_COORDINATES = Cube.getSolvedLocs(
            P_LDB, P_LDF, P_LUB, P_LUF, P_RDB, P_RDF, P_RUB, P_RUF,
            P_LD, P_LB, P_LF, P_LU, P_DB, P_DF, P_UB, P_UF, P_RD, P_RB, P_RF,
            P_RU);

    private static final Set<Integer> PIECE_SET = new HashSet<Integer>();
    static {
        for (final int piece : PIECES) {
            PIECE_SET.add(piece);
        }
    }

    // Cache of twist offsets keyed by [piece index][xyz slot index].
    private final int[][] twistOffsetCache = new int[PIECES.length][27];

    public OneSide() {
        super(PIECES, ALLOWED_COORDINATES);
        setCornersIndistinguishable(true);
        setEdgesIndistinguishable(true);
        for (final int[] row : twistOffsetCache) {
            Arrays.fill(row, -1);
        }
    }

    private int pieceIndex(final int piece) throws CubearoniException {
        for (int i = 0; i < PIECES.length; i++) {
            if (PIECES[i] == piece) {
                return i;
            }
        }
        throw new CubearoniException("Piece " + piece + " is not in OneSide.");
    }

    private int getTwistOffset(final int piece, final int x, final int y,
            final int z) throws CubearoniException {
        final int pi = pieceIndex(piece);
        final int slot = (x * 9) + (y * 3) + z;
        int offset = twistOffsetCache[pi][slot];
        if (offset != -1) {
            return offset;
        }

        final int radix = Cube.isEdge(piece) ? 2 : 3;
        final int desiredAxis = y == 1 ? AXIS_Z: AXIS_Y;
        final Cube cube = new Cube();
        cube.setPiece(x, y, z, piece);
        for (int twist = 0; twist < radix; twist++) {
            cube.setTwist(x, y, z, twist);
            if (cube.getStickerAxis(x, y, z, 'U') == desiredAxis) {
                offset = (radix - twist) % radix;
                twistOffsetCache[pi][slot] = offset;
                return offset;
            }
        }
        throw new CubearoniException("Could not determine twist offset for piece "
                + piece + " at (" + x + "," + y + "," + z + ").");
    }

    @Override
    protected void adjustCube(final Cube cube) throws CubearoniException {
        final int[][] locsTwists = cube.findAllPieces(PIECES);
        for (int i = 0; i < PIECES.length; i++) {
            final int piece = PIECES[i];
            final int[] lt = locsTwists[i];
            final int x = lt[0];
            final int y = lt[1];
            final int z = lt[2];
            final int twist = lt[3];
            final int radix = Cube.isEdge(piece) ? 2 : 3;
            final int offset = getTwistOffset(piece, x, y, z);
            cube.setTwist(x, y, z, (twist + offset) % radix);
        }
    }

    @Override
    protected void unadjustCube(final Cube cube) throws CubearoniException {
        final int[][] locsTwists = cube.findAllPieces(PIECES);
        for (int i = 0; i < PIECES.length; i++) {
            final int piece = PIECES[i];
            final int[] lt = locsTwists[i];
            final int x = lt[0];
            final int y = lt[1];
            final int z = lt[2];
            final int twist = lt[3];
            final int radix = Cube.isEdge(piece) ? 2 : 3;
            final int offset = getTwistOffset(piece, x, y, z);
            cube.setTwist(x, y, z, (twist + radix - offset) % radix);
        }
    }

    @Override
    protected boolean isAdjustedCube() {
        return true;
    }

    @Override
    public boolean isSolved(final Cube cube) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                if ((x == 1) && (z == 1)) {
                    continue;
                }

                // The following assumes that cube is not adjusted.
                if (!PIECE_SET.contains(cube.getPiece(x, 2, z))
                        || (cube.getStickerAxis(x, 2, z, 'U') != AXIS_Y)) {
                    return false;
                }
            }
        }
        return true;
    }
}
