package org.selliott.cubearoni;

import static org.selliott.cubearoni.Cube.P_LDB;
import static org.selliott.cubearoni.Cube.P_LDF;
import static org.selliott.cubearoni.Cube.P_LUB;
import static org.selliott.cubearoni.Cube.P_LUF;
import static org.selliott.cubearoni.Cube.P_RDB;
import static org.selliott.cubearoni.Cube.P_RDF;
import static org.selliott.cubearoni.Cube.P_RUB;
import static org.selliott.cubearoni.Cube.P_RUF;

/**
 * A simple 2x2-style solver that only tracks the eight corners.
 */
public class TwoByTwo extends Solver {
    public static final String NAME = Solver.getNameFromClass(TwoByTwo.class);

    protected static final int[] PIECES = new int[] { P_LDB, P_LDF, P_LUB,
            P_LUF, P_RDB, P_RDF, P_RUB, P_RUF };
    protected static final int[][] ALLOWED_COORDINATES = Cube.getSolvedLocs(
            P_LDB, P_LDF, P_LUB, P_LUF, P_RDB, P_RDF, P_RUB, P_RUF);

    public TwoByTwo() {
        super(PIECES, ALLOWED_COORDINATES);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
