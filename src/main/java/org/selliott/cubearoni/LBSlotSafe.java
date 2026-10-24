package org.selliott.cubearoni;

import java.util.Arrays;

import static org.selliott.cubearoni.Cube.*;

/**
 * Solves the lower-back corner-edge slot while protecting each other slot
 * that contains neither piece involved in the solve.
 */
public class LBSlotSafe extends LBSlotUnsafe {
    public static final String NAME = Solver.getNameFromClass(LBSlotSafe.class);

    @Override
    protected boolean isMovesAllowed(final Cube cube, final int[] mvs,
                                     final boolean inverse) {
        // Note: This method assumes the moves passed in are from
        // buildMoveSequences(). If not then this will probably break.

        if (mvs.length == 1) {
            // This must be a Ux move - it is always allowed since it does not
            // disturb the slot.
            return true;
        }

        // Determine what move would be first if executed in a forward direction.
        // This depends on the fact that for this solver all non-length 1 mvs
        // have form X Ux X'. If forward, then it is simply the first entry. If
        // inverse then it is the inverse of the last entry, which is the same
        // thing. This means that the "inverse" parameter is not used.
        final int forwardMove = mvs[0];

        // Determine the slot that is disturbed keyed off of the first forward
        // move. Slots are disturbed when moved up since they are then
        // vulnerable to be scrambled by the Ux move. Note that we are using the
        // edge piece to represent the slot.
        final int disturbedSlotEdge;
        switch (forwardMove) {
            case M_R:
            case M_FP:
                disturbedSlotEdge = P_RF;
                break;
            case M_RP:
            case M_B:
                disturbedSlotEdge = P_RB;
                break;
            case M_L:
            case M_BP:
                disturbedSlotEdge = P_LB;
                break;
            case M_LP:
            case M_F:
                disturbedSlotEdge = P_LF;
                break;
            default:
                // This should not happen.
                throw new IllegalStateException("Unexpected moves "
                        + Arrays.toString(mvs) + " can not be inverted.");
        }

        if (disturbedSlotEdge == P_LB) {
            // Since P_LB is the target slot can always be disturbed.
            return true;
        }

        // If either piece is in the disturbed slot, then the move is allowed.
        final int[] disturbedLoc = getSolvedLoc(disturbedSlotEdge);
        int[][] locs = cube.findPieces(new int[]{P_LB, P_LDB});
        for (final int[] loc : locs) {
            // To be in the disturbed slot the X and Z coordinates must match the
            // disturbed slot and the Y coordinate must not be at the top of the
            // (<= 1).
            if (loc[0] == disturbedLoc[0] && loc[1] <= 1 && loc[2] == disturbedLoc[2]) {
                return true;
            }
        }

        // Neither piece is in the disturbed slot, so the move is not allowed.
        return false;
    }
}
