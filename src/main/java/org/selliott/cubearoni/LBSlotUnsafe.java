package org.selliott.cubearoni;

import static org.selliott.cubearoni.Cube.*;

/**
 * Solves the lower-back corner-edge slot without protecting the other three
 * first-two-layer slots.
 */
public class LBSlotUnsafe extends Solver {
    public static final String NAME = Solver.getNameFromClass(LBSlotUnsafe.class);

    // Piece order is derived from solved position using natural XYZ order.
    protected static final int[] PIECES = new int[] { P_LDB, P_LB };
    protected static final int[][] ALLOWED_COORDINATES = Cube.getSolvedLocs(
            P_LDB, P_LDF, P_LB, P_LF, P_LUB, P_LU, P_LUF, P_UB, P_UF,
            P_RDB, P_RDF, P_RB, P_RF, P_RUB, P_RU, P_RUF);
    private static final int[][] MOVE_SEQUENCES = buildMoveSequences();

    public LBSlotUnsafe() {
        super(PIECES, ALLOWED_COORDINATES);
    }

    private static int[][] buildMoveSequences() {
        try {
            // Keep this as a subset of Cube.VALID_MOVES order.
            final int[] sideMoves = new int[] { M_R, M_RP, M_L, M_LP, M_F, M_FP,
                    M_B, M_BP };
            final int[][] mvs = new int[(3 + (3 * sideMoves.length))][];
            int i = 0;
            mvs[i++] = new int[] { M_U };
            mvs[i++] = new int[] { M_UP };
            mvs[i++] = new int[] { M_U2 };
            for (final int x : sideMoves) {
                mvs[i++] = new int[] { x, M_U,  getMoveInverse(x) };
                mvs[i++] = new int[] { x, M_UP, getMoveInverse(x) };
                mvs[i++] = new int[] { x, M_U2, getMoveInverse(x) };
            }
            return mvs;
        } catch (CubearoniException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected int[][] getMoveSequences() {
        return MOVE_SEQUENCES;
    }
}
