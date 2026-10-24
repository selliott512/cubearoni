package org.selliott.cubearoni;

import java.util.Random;

/**
 * Scrambles the cube by applying the specified number of moves.
 */
public class Scrambler extends Mover {
    int seed;
    private Random rnd;

    public Scrambler() {
    }

    @Override
    public int[] exec(final Cube cube) {
        if (rnd == null) {
            // Seed being 0 means to be as non-deterministic as possible.
            rnd = (seed == 0) ? new Random() : new Random(seed);
        }
        final int[] ms = new int[limit];
        int i = 0;
        int sideOld = Cube.M_NONE;
        int axisOld = Cube.AXIS_NONE;
        int axisOldOld = Cube.AXIS_NONE;
        while (i < limit) {
            final int m = Cube.VALID_MOVES[rnd.nextInt(Cube.VALID_MOVES.length)];
            final int side = m - Util.getOffset(m);
            if (side == sideOld) {
                // No need to twist a given side more than once in a
                // row.
                continue;
            }
            final int axis = Cube.sideToAxis(side);
            if ((axis == axisOld) && (axis == axisOldOld)) {
                // No need to twist on a given axis more than twice in a
                // row.
                continue;
            }
            ms[i++] = m;
            sideOld = side;
            axisOldOld = axisOld;
            axisOld = axis;
        }
        return ms;
    }

    public int getSeed() {
        return seed;
    }

    public void setSeed(final int seed) {
        this.seed = seed;
    }
}
