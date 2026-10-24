package org.selliott.cubearoni;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Command line options
 */
class Opts {
    String b;
    boolean c;
    boolean C;
    String d;
    boolean g;
    String i;
    String j;
    boolean F;
    boolean l;
    boolean u;
    String n;
    int N;
    String m;
    boolean I;
    Map<String, String> k = new HashMap<String, String>();
    boolean L;
    boolean M;
    String o;
    String p;
    boolean P;
    int r;
    int s;
    boolean S;
    boolean t;
    boolean v;
    boolean e;
    String f;
}

/**
 * Front end to the Cubearoni program
 */
public class Cubearoni {
    static final Opts opts = new Opts();

    // TODO: Should this be set by the build?
    public static final String version = "0.9.0";

    /**
     * Main entry point
     *
     * @param args
     *            command line arguments
     */
    public static void main(final String[] args) {
        parseArgs(args);

        PrintStream out = null;
        try {
            // All actual non-argument processing work begins here.
            if (opts.e || opts.g || opts.t || opts.l || opts.u) {
                CFOP.discover((opts.d != null)
                        ? opts.d : Solver.DEFAULT_DB_DIR);
            }

            if (opts.e) {
                // list the solvers
                final String[] names = Solver.getSolverNames();
                for (final String name : names) {
                    System.out.println(name);
                }
                System.exit(0);
            }

            Cube cube = null;
            if (opts.g) {
                // generate the database for the specified solver
                // TODO: Generate the database for a list of solvers, or "all"
                // solvers.
                final Solver solver = Solver.getSolver(opts.n, opts.v);
                solver.setVerbose(opts.v);
                solver.setOptions(opts.k);
                if (opts.d != null) {
                    solver.setDBDir(opts.d);
                }
                if (opts.N != 0) {
                    solver.setLimit(opts.N);
                }
                solver.setLeftHanded(opts.L);
                final File dbFile = new File(solver.getDbFile());
                if (dbFile.exists()
                        && (!dbFile.canWrite() || dbFile.isDirectory())) {
                    // Make sure the DB file can be written before spending
                    // possibly an hour generating it.
                    System.err.println("The database file \"" + dbFile
                            + "\" is not a writeable.");
                    System.exit(1);
                }
                solver.generateDB();
                solver.saveDB();
            }

            if (opts.t) {
                // test the database for the specified solver
                final Solver solver = Solver.getSolver(opts.n, opts.v);
                solver.setVerbose(opts.v);
                solver.setOptions(opts.k);
                if (opts.d != null) {
                    solver.setDBDir(opts.d);
                }
                if (opts.N != 0) {
                    solver.setLimit(opts.N);
                }
                solver.setLeftHanded(opts.L);
                solver.loadDB();
                solver.test();
                System.exit(0);
            }

            boolean output = false;
            if (opts.o != null) {
                out = new PrintStream(opts.o);
            } else {
                out = System.out;
            }

            if (opts.i != null) {
                // read from input file
                cube = new Cube(opts.i);
            } else if (opts.j != null) {
                // read from 54-char facelets string
                cube = new Cube(null, opts.j, null);
            } else {
                // solved
                cube = new Cube();
            }

            // Configure and check the input cube
            cube.setVerbose(opts.v);
            cube.getFormat().setColor(opts.c);
            if (opts.b != null) {
                if (isFaceletsOrder(opts.b)) {
                    cube.getFormat().setFaceletsOrder(parseFaceletsOrder(opts.b));
                } else {
                    cube.getFormat().setFaceletsOrder(null);
                    cube.getFormat().setBackPos(parseBackPos(opts.b));
                }
            }
            if (!cube.isSolveable()) {
                throw new CubearoniException("Cube file \"" + opts.i
                        + "\" is not solveable: " + cube.getError());
            }

            // Total moves applied due to various things that can be done to the
            // input cube to produce the output cube.
            String movesApplied = "";

            if (opts.m != null) {
                // apply user specified moves before any other changes.
                String movesToApply = opts.m;
                if (opts.I) {
                    final int[] movesExt = Util.moveStrToExtendedMoves(opts.m);
                    movesToApply = Cube.extMovesToStr(Cube.inverseMoves(movesExt));
                }
                if (opts.F) {
                    final int[] movesExt = Util.moveStrToExtendedMoves(movesToApply);
                    movesToApply = Cube.extMovesToStr(Cube.mirrorMoves(movesExt));
                }
                // TODO: Do we need normalize option in place of "false"?
                cube.extMoves(movesToApply, Util.EXT_TO_HTM_MODE_NONE);
                movesApplied = movesToApply;
            }

            if (opts.s > 0) {
                // scramble with the specified number of moves
                final Scrambler scrambler = new Scrambler();
                scrambler.setLimit(opts.s);
                scrambler.setSeed(opts.r);
                final int[] ms = scrambler.exec(cube);
                cube.moves(ms);
                movesApplied += ((movesApplied.length() > 0) ? " " : "")
                        + Cube.movesToStr(ms);
            }

            if (opts.l || opts.u) {
                if (opts.S) {
                    // Output all moves leading up to the current pre-solved
                    // cube.
                    out.println(movesApplied);
                    output = true;
                    movesApplied = "";

                    // Output the cube before solving it.
                    System.out.println("");
                    cube.print(System.out);
                }

                // solve with the specified solver
                Solver solver = Solver.getSolver(opts.n, opts.v);
                solver.setOptions(opts.k);
                if (opts.d != null) {
                    solver.setDBDir(opts.d);
                }
                if (opts.N != 0) {
                    solver.setLimit(opts.N);
                }
                if (opts.p != null) {
                    solver.setStopAfterPhase(opts.p);
                }
                solver.setLeftHanded(opts.L);
                if (opts.f != null) {
                    if (opts.f.length() != 1) {
                        System.err.println("The face specified \"" + opts.f
                                + "\" must be exactly one character.");
                        System.exit(1);
                    }
                    solver.setUserFace(opts.f.charAt(0));
                }
                solver.loadDB();
                if (opts.u) {
                    // Upgrade by saving again.
                    solver.saveDB();
                } else {
                    final String solvedMoves;
                    if (opts.P) {
                        if (!(solver instanceof CFOP)) {
                            throw new CubearoniException("Option -P is only supported "
                                    + "with solver \"cfop\".");
                        }
                        solvedMoves = ((CFOP) solver).execMarked(cube);
                    } else {
                        solvedMoves = Cube.extMovesToStr(solver.exec(cube));
                    }
                    movesApplied += ((movesApplied.length() > 0) ? " " : "")
                            + solvedMoves;
                }
            }

            if (opts.v) {
                System.out.println("");
                System.out.println("Cube piece locations and twists:");
                for (int x = 0; x <= 2; x++) {
                    for (int y = 0; y <= 2; y++) {
                        for (int z = 0; z <= 2; z++) {
                            final int piece = cube.getPiece(x, y, z);
                            if (piece == Cube.P_NONE) {
                                continue;
                            }
                            final int twist = cube.getTwist(x, y, z);
                            System.out.println(String.format(
                                    "  %3s @ (%d,%d,%d) twist=%d",
                                    Cube.getPieceLabel(piece), x, y, z, twist));
                        }
                    }
                }
                output = true;
            }

            if (opts.M && (movesApplied.length() > 0)) {
                // output all moves applied
                if (output) {
                    System.out.println("");
                }
                out.println(movesApplied);
                output = true;
            }

            if (opts.C) {
                // output the resulting cube
                if (output) {
                    out.println("");
                }
                cube.print(out);
            }

            if (out != System.out) {
                out.close();
            }
        } catch (final CubearoniException e) {
            System.err.println("Exception: " + e);
            System.exit(1);
        } catch (final FileNotFoundException e) {
            System.err.println("Unable to write to output file \"" + opts.o
                    + "\": " + e);
        }
        finally {
            if (out != null) {
                out.close();
            }
        }
    }

    /** Parses command-line arguments into the shared options object. */
    private static void parseArgs(final String[] args) {
        if (args.length < 1) {
            usage();
        }

        int argIdx;
        for (argIdx = 0; argIdx < args.length; argIdx++) {
            final String opt = args[argIdx];
            final String next = argIdx < (args.length - 1) ? args[argIdx + 1] : null;
            if (opt.charAt(0) != '-') {
                // End of options found
                break;
            }

            for (int i = 1; i < opt.length(); i++) {
                final char optChar = opt.charAt(i);
                switch (optChar) {
                case 'c':
                    // use color for printing
                    opts.c = true;
                    break;
                case 'b':
                    // Preferred location for the back side in text output.
                    opts.b = next;
                    argIdx++;
                    break;
                case 'C':
                    // include the final cube in the output
                    opts.C = true;
                    break;
                case 'd':
                    // database directory
                    opts.d = next;
                    argIdx++;
                    break;
                case 'e':
                    // list the solvers
                    opts.e = true;
                    break;
                case 'f':
                    // treat the specified face as up for the overall solve.
                    opts.f = next;
                    argIdx++;
                    break;
                case 'F':
                    // mirror the -m move list across the YZ plane
                    opts.F = true;
                    break;
                case 'g':
                    // generate the database for a solver and save it
                    opts.g = true;
                    break;
                case 'h':
                    // help / usage statement
                    usage();
                    System.exit(0);
                    break;
                case 'i':
                    // input cube file. solved by default.
                    opts.i = next;
                    argIdx++;
                    break;
                case 'j':
                    // input cube as a 54-character facelets string
                    opts.j = next;
                    argIdx++;
                    break;
                case 'l':
                    // solve with the solver specified
                    opts.l = true;
                    break;
                case 'm':
                    // input list of moves
                    opts.m = next;
                    argIdx++;
                    break;
                case 'I':
                    // apply the inverse of moves passed with -m
                    opts.I = true;
                    break;
                case 'k':
                    // solver option key-value pair (key=value)
                    if ((next == null) || !next.contains("=")) {
                        System.err.println("Option -k requires a key=value pair.");
                        System.exit(1);
                    }
                    final int eq = next.indexOf('=');
                    final String key = next.substring(0, eq);
                    final String value = next.substring(eq + 1);
                    if (key.isEmpty()) {
                        System.err.println("Option -k key must not be empty.");
                        System.exit(1);
                    }
                    opts.k.put(key, value);
                    argIdx++;
                    break;
                case 'L':
                    // left-handed solver mode
                    opts.L = true;
                    break;
                case 'M':
                    // include the moves applied in the output
                    opts.M = true;
                    break;
                case 'n':
                    // the name of the solver to use
                    opts.n = next;
                    argIdx++;
                    break;
                case 'N':
                    // maximum goal (limit) that the mover will generate upon
                    // exec()
                    opts.N = parseInt(next, 'N');
                    argIdx++;
                    break;
                case 'o':
                    // output moves and or cube file
                    opts.o = next;
                    argIdx++;
                    break;
                case 'p':
                    // stop after phase
                    opts.p = next;
                    argIdx++;
                    break;
                case 'P':
                    // include phase start markers in the output moves
                    opts.P = true;
                    break;
                case 'r':
                    // seed used for the random generator
                    opts.r = parseInt(next, 'r');
                    argIdx++;
                    break;
                case 's':
                    // scramble with a given number of HTM moves
                    opts.s = parseInt(next, 's');
                    argIdx++;
                    break;
                case 'S':
                    // output the cube pre-solved
                    opts.S = true;
                    break;
                case 't':
                    // test the database
                    opts.t = true;
                    break;
                case 'v':
                    // verbose
                    opts.v = true;
                    break;
                default:
                    System.err.println("Unknown option letter \"" + optChar
                            + "\" in \"" + opt + "\"");
                    System.exit(1);
                }
            }
        }

        // Make sure we say the expected number of arguments.
        final int argCount = args.length - argIdx;
        if (argCount != 0) {
            System.err.println("Got " + argCount + " argument, but expected 0");
            System.exit(1);
        }

        if ((opts.g || opts.l || opts.t) && (opts.n == null)) {
            System.err.println("If option -g (generate), -l (solve), or -t "
                    + "(test DB) is specifed then -n (solver name) must also "
                    + "be specifed.");
            System.exit(1);
        }
        if ((opts.i != null) && (opts.j != null)) {
            System.err.println("Options -i and -j are mutually exclusive. Use "
                    + "either an input file (-i) or a facelets string (-j).");
            System.exit(1);
        }
        if (opts.I && (opts.m == null)) {
            System.err.println("If option -I is specified then -m must also be "
                    + "specified.");
            System.exit(1);
        }
        if (opts.F && (opts.m == null)) {
            System.err.println("If option -F is specified then -m must also be "
                    + "specified.");
            System.exit(1);
        }
    }

    /** Prints command-line usage information. */
    private static void usage() {
        PrintStream out = System.out;
        out.println("Usage: Cubearoni [options]");
        out.println("  -b back-pos    back position in text output: "
                + "bottom|right|top|left or b|r|t|l; or facelets order like URFDLB");
        out.println("  -c             use color for output");
        out.println("  -C             include the final cube in the "
                + "output");
        out.println("  -d dir         database directory (default: db)");
        out.println("  -e             list the solvers");
        out.println("  -f up-face     face to treat as up for the overall "
                + "solve (default: U)");
        out.println("  -F             mirror the -m move list across the YZ plane");
        out.println("  -g             generate the database");
        out.println("  -h             this help message");
        out.println("  -i input-cube  input cube (default: solved)");
        out.println("  -j facelets    input cube as 54-char facelets string");
        out.println("  -I             apply the inverse of moves passed with -m");
        out.println("  -k key=value   solver option override (repeatable)");
        out.println("  -L             left-handed solver mode");
        out.println("  -l             solve with the solver specified");
        out.println("  -m input-list  input list of moves");
        out.println("  -M             include the moves applied in the "
                + "output");
        out.println("  -n solver-name name of the solver to use");
        out.println("  -N limit       the maximum number of moves the "
                + "mover will generate");
        out.println("  -o output-file output cube or moves file");
        out.println("  -p last-phase  stop after the phase specified");
        out.println("  -P             include phase start markers in the output moves");
        out.println("  -r rnd-seed    seed to random generator.  makes " +
        		"the scrambler deterministic");
        out.println("  -s num-moves   scramble with a given number of HTM "
                + "moves");
        out.println("  -S             output the cube pre-solve");
        out.println("  -t             test the database");
        out.println("  -v             verbose");
    }

    /** Parses a back-position option into its internal format value. */
    private static char parseBackPos(final String backPosArg) {
        if ((backPosArg == null) || (backPosArg.length() < 1)) {
            System.err.println("Option -b requires a back position.");
            System.exit(1);
        }
        final String bp = backPosArg.toLowerCase();
        switch (bp) {
        case "bottom":
        case "b":
            return Format.BACK_BOTTOM;
        case "right":
        case "r":
            return Format.BACK_RIGHT;
        case "top":
        case "t":
            return Format.BACK_TOP;
        case "left":
        case "l":
            return Format.BACK_LEFT;
        default:
            System.err.println("For -b option unknown value \"" + backPosArg
                    + "\". Expected bottom|right|top|left or b|r|t|l, "
                    + "or a 6 character facelets order string such as URFDLB.");
            System.exit(1);
            return Format.BACK_BOTTOM; // not used
        }
    }

    /** Returns whether a value has the basic form of a facelets order. */
    private static boolean isFaceletsOrder(final String value) {
        if ((value == null) || (value.length() != 6)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if ("LRDUBF".indexOf(value.charAt(i)) == -1) {
                return false;
            }
        }
        return true;
    }

    /** Validates and normalizes a facelets order. */
    private static String parseFaceletsOrder(final String orderArg) {
        if (!isFaceletsOrder(orderArg)) {
            System.err.println("For -b option unknown facelets order \"" + orderArg
                    + "\". Expected six unique letters from L,R,D,U,B,F.");
            System.exit(1);
        }
        final String order = orderArg.toUpperCase();
        final boolean[] seen = new boolean[256];
        for (int i = 0; i < order.length(); i++) {
            final char c = order.charAt(i);
            if (seen[c]) {
                System.err.println("For -b option facelets order \"" + orderArg
                        + "\" contains duplicate face \"" + c + "\".");
                System.exit(1);
            }
            seen[c] = true;
        }
        return order;
    }

    /** Parses an integer option value or exits with an explanatory error. */
    private static int parseInt(final String intStr, final char option) {
        try {
            return Integer.parseInt(intStr);
        } catch (final NumberFormatException e) {
            System.err.println("For -" + option + " option unable to parse "
                    + "integer \"" + intStr + "\".");
            System.exit(1);
            return -1; // not used
        }
    }
}
