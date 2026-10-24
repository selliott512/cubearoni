package org.selliott.cubearoni;

import static org.selliott.cubearoni.Cube.P_LDB;
import static org.selliott.cubearoni.Cube.P_LDF;
import static org.selliott.cubearoni.Cube.P_LUB;
import static org.selliott.cubearoni.Cube.P_LUF;
import static org.selliott.cubearoni.Cube.P_RDB;
import static org.selliott.cubearoni.Cube.P_RDF;
import static org.selliott.cubearoni.Cube.P_RUB;
import static org.selliott.cubearoni.Cube.P_RUF;

/** Solves the lower-left-back corner without tracking other pieces. */
public class LDBCorner extends Solver {
    public static final String NAME = Solver.getNameFromClass(LDBCorner.class);

    // Piece order is derived from solved position using natural XYZ order.
    protected static final int[] PIECES = new int[] { P_LDB };
    protected static final int[][] ALLOWED_COORDINATES = Cube.getSolvedLocs(
            P_LDB, P_LDF, P_LUB, P_LUF, P_RDB, P_RDF, P_RUB, P_RUF);

    public LDBCorner() {
        super(PIECES, ALLOWED_COORDINATES);
    }
}
