package org.selliott.cubearoni;

/**
 * Base class for all movers (classes that apply moves to cubes).
 */
public abstract class Mover {
    protected int limit = Integer.MAX_VALUE - 1;
    protected boolean verbose;

    /**
     * Apply this mover's moves.
     *
     * @param cube
     *            the cube to apply moves to.
     * @return Moves that were applied to the cube.
     * @throws CubearoniException
     */
    public abstract int[] exec(final Cube cube) throws CubearoniException;

    public int getLimit() {
        return limit;
    }

    public boolean isVerbose() {
        return verbose;
    }

    /**
     * The maximum number of moves that the mover will produce upon calling
     * exec().
     *
     * @param limit
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * Verbose output
     *
     * @param verbose
     *            verbose
     */
    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }
}
