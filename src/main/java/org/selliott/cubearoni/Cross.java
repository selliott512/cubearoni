package org.selliott.cubearoni;

import static org.selliott.cubearoni.Cube.P_LD;
import static org.selliott.cubearoni.Cube.P_LB;
import static org.selliott.cubearoni.Cube.P_LF;
import static org.selliott.cubearoni.Cube.P_LU;
import static org.selliott.cubearoni.Cube.P_RD;
import static org.selliott.cubearoni.Cube.P_RB;
import static org.selliott.cubearoni.Cube.P_RF;
import static org.selliott.cubearoni.Cube.P_RU;
import static org.selliott.cubearoni.Cube.P_DB;
import static org.selliott.cubearoni.Cube.P_DF;
import static org.selliott.cubearoni.Cube.P_UB;
import static org.selliott.cubearoni.Cube.P_UF;

/** Solves the four edge pieces that form the cross on the bottom face. */
public class Cross extends Solver {
    public static final String NAME = Solver.getNameFromClass(Cross.class);

    // Piece order is derived from solved position using natural XYZ order.
    protected static final int[] PIECES = new int[] { P_LD, P_DB, P_DF, P_RD };
    protected static final int[][] ALLOWED_COORDINATES = Cube.getSolvedLocs(P_LD, P_LB,
            P_LF, P_LU, P_DB, P_DF, P_UB, P_UF, P_RD, P_RB, P_RF, P_RU);

    public Cross() {
        super(PIECES, ALLOWED_COORDINATES);
    }
}
