package org.selliott.cubearoni;

import java.util.HashSet;
import java.util.Set;

/**
 * Solves only the placement of the upper-layer cubies into the upper layer,
 * ignoring their permutation and orientation.
 */
public class UpCubiesUp extends Solver {
    public static final String NAME = Solver.getNameFromClass(UpCubiesUp.class);

    private static final Set<Integer> PIECE_SET = new HashSet<Integer>();
    static {
        for (final int piece : OneSide.PIECES) {
            PIECE_SET.add(piece);
        }
    }

    public UpCubiesUp() {
        super(OneSide.PIECES, OneSide.ALLOWED_COORDINATES);
        setCornersIndistinguishable(true);
        setEdgesIndistinguishable(true);
        setCornerOrientationsIgnored(true);
        setEdgeOrientationsIgnored(true);
    }

    @Override
    public boolean isSolved(final Cube cube) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                if ((x == 1) && (z == 1)) {
                    continue;
                }
                if (!PIECE_SET.contains(cube.getPiece(x, 2, z))) {
                    return false;
                }
            }
        }
        return true;
    }
}
