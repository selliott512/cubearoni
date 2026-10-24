package org.selliott.cubearoni;

import java.util.Arrays;
import java.util.List;

import static org.selliott.cubearoni.Cube.*;

/** Solves all four first-two-layer corner-edge slots using a slot solver. */
public class F2L extends Composite {
    public static final String NAME = Solver.getNameFromClass(F2L.class);
    // TODO: Does this make sense? Maybe delete entirely and get of arg concept.
    // TODO: Probably don't hard code name.
    private static final String DEFAULT_SUB_SOLVER = "lb-slot-safe";
    private String subSolverName = DEFAULT_SUB_SOLVER;

    // This is the pieces in XZ order, a bit different than the natural XYZ order,
    // so that the pieces that make up each slot are together.
    protected static final int[] PIECES = new int[] { P_LDB, P_LB, P_LDF, P_LF,
            P_RDB, P_RB, P_RDF, P_RF };
    // Slot order is LB, LF, RB, RF for all slot-oriented tables in this solver.
    private static final int SLOT_COUNT = 4;
    // Rotations that map each slot to the LB slot.
    private static final int[][] SLOT_ROTATE_AXES = new int[][] {
            {},
            { AXIS_Y },
            { -AXIS_Y },
            { AXIS_Y, AXIS_Y } };
    // Solved (x,z) coordinates for each slot in LB, LF, RB, RF order.
    private static final int[][] SLOT_SOLVED_XZ = new int[][] {
            { 0, 0 },
            { 0, 2 },
            { 2, 0 },
            { 2, 2 } };

    @Override
    public int[] exec(final Cube cube) throws CubearoniException {
        final long startTime = System.currentTimeMillis();
        // Each slot solve will append to this.
        int[] moves = new int[0];

        // Moves for one slot.
        int[] slotMoves;

        // The moves produced by the previous safe iteration. It is raw in the
        // sense it came directly from the solver for the rotated cube.
        int[] lastRawSafeslotMoves = null;

        // Whether each slot is solved (LB, LF, RB, RF).
        final boolean[] isSolved = new boolean[SLOT_COUNT];

        // There are five attempts so that we ca make sure all slots are
        // solved on the final attempt. The first four attempts will solve one
        // slot each.
        for (int solveAttempt = 1; solveAttempt <= SLOT_COUNT; solveAttempt++) {
            String bestSlotName = null;
            int bestSlotIndex = -1;
            int bestSlotMovesScore = Integer.MAX_VALUE;
            int[] bestSlotMoves = null;
            final int solvedCountBefore = updateIsSolved(cube, isSolved);
            if (solvedCountBefore == SLOT_COUNT) {
                // Done in less than four attempts. One or more slots were
                // probably already solved.
                break;
            }
            int slotIndex = 0;
            for (final int[] axes : SLOT_ROTATE_AXES) {
                if (isSolved[slotIndex]) {
                    if (verbose) {
                        System.out.println("       slot # " + slotIndex
                                + " slot is already solved.");
                    }
                    slotIndex++;
                    continue;
                }
                for (final Solver solver : new Solver[]{lbSlotSafe, lbSlotUnsafe}) {
                    final boolean isSafe = solver == lbSlotSafe;
                    final String name = isSafe ? "safe  " : "unsafe";
                    final Cube dupCube = cube.dup();

                    for (final int axis : axes) {
                        dupCube.rotateKeepColor(axis, (char) 0, false, null);
                    }

                    slotMoves = solver.exec(dupCube);
                    if (isSafe) {
                        // Keep this to compare to the unsafe solver.
                        lastRawSafeslotMoves = Arrays.copyOf(slotMoves, slotMoves.length);
                    } else {
                        // Did the unsafe solver just produce the same moves?
                        if (Arrays.equals(slotMoves, lastRawSafeslotMoves)) {
                            if (verbose) {
                                System.out.println(name + " slot # " + slotIndex
                                        + " slot is redundant to the safe solver, skipping.");
                            }
                            continue;
                        }
                    }

                    for (int i = axes.length - 1; i >= 0; i--) {
                        dupCube.rotateKeepColor(axes[i], (char) 0, true, slotMoves);
                    }

                    // Get the number of slots solved after, which increase by one
                    // in most cases, but can occasionally increase by more than one
                    // if the slot moves also solve other slots.
                    final int solvedCountAfter = updateIsSolved(dupCube, null);

                    // For the score lower is better. The score is dominated by the
                    // change in the number of solved slots, then the number of
                    // moves, less is better, and finally move preference.
                    final int slotMovesScore = (1_000_000 * (solvedCountBefore -
                            solvedCountAfter + 1)) + Util.scoreMoves(slotMoves);
                    if (verbose) {
                        System.out.println(name + " slot # " + slotIndex + " slot moves: "
                                + Cube.movesToStr(slotMoves) + " (score: "
                                + slotMovesScore + ")");
                    }
                    if (slotMovesScore == 0) {
                        // The slot is solved, but we previously determined it was not.
                        throw new CubearoniException("Solver \"" + subSolverName
                                + "\" returned a score of 0 for the slot, but "
                                + "the slot is not solved. This should not happen.");
                    }
                    // The slot is not solved. Keep track of the best to solve
                    // it for real.
                    if (slotMovesScore < bestSlotMovesScore) {
                        bestSlotName = name;
                        bestSlotIndex = slotIndex;
                        bestSlotMovesScore = slotMovesScore;
                        bestSlotMoves = slotMoves;
                    }
                }
                slotIndex++;
            }

            if (verbose) {
                System.out.println(bestSlotName + " slot # " + bestSlotIndex
                        + " is best with score: " + bestSlotMovesScore + "\n");
            }

            // Solve the easiest slot for real, and then append to the list.
            if (bestSlotMoves != null) {
                cube.moves(bestSlotMoves);
            }
            moves = Util.concat(moves, bestSlotMoves);
        }

        // All slots should be solved.
        final int solvedCountFinal = updateIsSolved(cube, isSolved);
        if (solvedCountFinal != SLOT_COUNT) {
            throw new CubearoniException("Solver \"f2l\" failed to solve all "
                    + "slots. Solved count: " + solvedCountFinal);
        }

        if (verbose) {
            System.out.println("F2L solved in "
                    + (System.currentTimeMillis() - startTime) + " ms.");
        }
        return Cube.pruneMoves(moves);
    }

    private int updateIsSolved(final Cube cube, final boolean[] isSolved) throws CubearoniException {
        int solvedCount = 0;
        final int[][] locs = cube.findPieces(PIECES);
        for (int locIndex = 0; locIndex < locs.length; locIndex += 2) {
            final int[] corner = locs[locIndex];
            final int[] edge = locs[locIndex + 1];
            final int solvedIndex = locIndex / 2;
            // Check the twist first since it's easy.
            if (corner[3] != 0 || edge[3] != 0) {
                // Not solved.
                if (isSolved != null) {
                    isSolved[solvedIndex] = false;
                }
                continue;
            }

            // Check the location.
            final int[] xz = SLOT_SOLVED_XZ[solvedIndex];
            final int x = xz[0];
            final int z = xz[1];
            if (corner[0] != x || corner[1] != 0 || corner[2] != z ||
                  edge[0] != x ||   edge[1] != 1 ||   edge[2] != z) {
                // Not solved.
                if (isSolved != null) {
                    isSolved[solvedIndex] = false;
                }
                continue;
            }

            // Solved.
            if (isSolved != null) {
                isSolved[solvedIndex] = true;
            }
            solvedCount++;

        }
        return solvedCount;
    }

    @Override
    protected List<String> getSubSolverNames() {
        return List.of(LBSlotSafe.NAME, LBSlotUnsafe.NAME);
    }
}
