package org.selliott.cubearoni;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * A cube class that can read and write from a cube files. The internal
 * representation is two 3x3x3 arrays of pieces and the amount they are twisted.
 *
 */
public class Cube {
    // Constants initialized with XYZ values so that they will be in numeric
    // order. This means the cube is serialized in an Z-first manner.
    // @formatter:off
    public static final int P_NONE =  -1;
    public static final int P_LDB  =   0;
    public static final int P_LD   =   1;
    public static final int P_LDF  =   2;
    public static final int P_LB   =  10;
    public static final int P_LF   =  12;
    public static final int P_LUB  =  20;
    public static final int P_LU   =  21;
    public static final int P_LUF  =  22;
    public static final int P_DB   = 100;
    public static final int P_DF   = 102;
    public static final int P_UB   = 120;
    public static final int P_UF   = 122;
    public static final int P_RDB  = 200;
    public static final int P_RD   = 201;
    public static final int P_RDF  = 202;
    public static final int P_RB   = 210;
    public static final int P_RF   = 212;
    public static final int P_RUB  = 220;
    public static final int P_RU   = 221;
    public static final int P_RUF  = 222;
    // @formatter:on

    // Reading the stickers in line by line produces some pieces who's strings
    // are not in XYZ order, so this fixes it.
    private static final int[][][][] strRepairs = new int[3][3][3][0];

    static {
        strRepairs[0][0][2] = new int[]{0, 2, 1};
        strRepairs[0][2][0] = new int[]{1, 0, 2};
        strRepairs[0][2][1] = new int[]{1, 0};
        strRepairs[0][2][2] = new int[]{1, 0, 2};
        strRepairs[1][0][2] = new int[]{1, 0};
        strRepairs[2][0][2] = new int[]{1, 2, 0};
        strRepairs[2][1][2] = new int[]{1, 0};
        strRepairs[2][2][0] = new int[]{1, 0, 2};
        strRepairs[2][2][1] = new int[]{1, 0};
        strRepairs[2][2][2] = new int[]{2, 0, 1};
    }

    private static final Map<String, Integer> strToPiece = new HashMap<String, Integer>();

    static {
        strToPiece.put("LDB", P_LDB);
        strToPiece.put("LD", P_LD);
        strToPiece.put("LDF", P_LDF);
        strToPiece.put("LB", P_LB);
        strToPiece.put("LF", P_LF);
        strToPiece.put("LUB", P_LUB);
        strToPiece.put("LU", P_LU);
        strToPiece.put("LUF", P_LUF);
        strToPiece.put("DB", P_DB);
        strToPiece.put("DF", P_DF);
        strToPiece.put("UB", P_UB);
        strToPiece.put("UF", P_UF);
        strToPiece.put("RDB", P_RDB);
        strToPiece.put("RD", P_RD);
        strToPiece.put("RDF", P_RDF);
        strToPiece.put("RB", P_RB);
        strToPiece.put("RF", P_RF);
        strToPiece.put("RUB", P_RUB);
        strToPiece.put("RU", P_RU);
        strToPiece.put("RUF", P_RUF);
    }

    private static final Map<Integer, String> pieceToStr = new HashMap<Integer, String>();

    static {
        pieceToStr.put(P_LDB, "LDB");
        pieceToStr.put(P_LD, "LD");
        pieceToStr.put(P_LDF, "LDF");
        pieceToStr.put(P_LB, "LB");
        pieceToStr.put(P_LF, "LF");
        pieceToStr.put(P_LUB, "LUB");
        pieceToStr.put(P_LU, "LU");
        pieceToStr.put(P_LUF, "LUF");
        pieceToStr.put(P_DB, "DB");
        pieceToStr.put(P_DF, "DF");
        pieceToStr.put(P_UB, "UB");
        pieceToStr.put(P_UF, "UF");
        pieceToStr.put(P_RDB, "RDB");
        pieceToStr.put(P_RD, "RD");
        pieceToStr.put(P_RDF, "RDF");
        pieceToStr.put(P_RB, "RB");
        pieceToStr.put(P_RF, "RF");
        pieceToStr.put(P_RUB, "RUB");
        pieceToStr.put(P_RU, "RU");
        pieceToStr.put(P_RUF, "RUF");
    }

    private static final Map<Integer, int[]> pieceToLoc = new HashMap<Integer, int[]>();

    static {
        // @formatter:off
        pieceToLoc.put(P_LDB, new int[] { 0, 0, 0 });
        pieceToLoc.put(P_LD,  new int[] { 0, 0, 1 });
        pieceToLoc.put(P_LDF, new int[] { 0, 0, 2 });
        pieceToLoc.put(P_LB,  new int[] { 0, 1, 0 });
        pieceToLoc.put(P_LF,  new int[] { 0, 1, 2 });
        pieceToLoc.put(P_LUB, new int[] { 0, 2, 0 });
        pieceToLoc.put(P_LU,  new int[] { 0, 2, 1 });
        pieceToLoc.put(P_LUF, new int[] { 0, 2, 2 });
        pieceToLoc.put(P_DB,  new int[] { 1, 0, 0 });
        pieceToLoc.put(P_DF,  new int[] { 1, 0, 2 });
        pieceToLoc.put(P_UB,  new int[] { 1, 2, 0 });
        pieceToLoc.put(P_UF,  new int[] { 1, 2, 2 });
        pieceToLoc.put(P_RDB, new int[] { 2, 0, 0 });
        pieceToLoc.put(P_RD,  new int[] { 2, 0, 1 });
        pieceToLoc.put(P_RDF, new int[] { 2, 0, 2 });
        pieceToLoc.put(P_RB,  new int[] { 2, 1, 0 });
        pieceToLoc.put(P_RF,  new int[] { 2, 1, 2 });
        pieceToLoc.put(P_RUB, new int[] { 2, 2, 0 });
        pieceToLoc.put(P_RU,  new int[] { 2, 2, 1 });
        pieceToLoc.put(P_RUF, new int[] { 2, 2, 2 });
        // @formatter:on
    }

    /**
     * Return the solved XYZ location for a piece.
     *
     * @param piece piece constant (for example P_RUF)
     * @return solved XYZ location
     */
    public static int[] getSolvedLoc(final int piece) {
        final int[] loc = pieceToLoc.get(piece);
        if (loc == null) {
            throw new IllegalArgumentException("Unknown piece " + piece);
        }
        return Arrays.copyOf(loc, 3);
    }

    /**
     * Return solved XYZ locations for pieces in the same order as the input.
     *
     * @param pcs piece constants
     * @return solved XYZ locations
     */
    public static int[][] getSolvedLocs(final int... pcs) {
        final int[][] locs = new int[pcs.length][];
        for (int i = 0; i < pcs.length; i++) {
            locs[i] = getSolvedLoc(pcs[i]);
        }
        return locs;
    }

    /**
     * Return the canonical label for a piece.
     *
     * @param piece piece constant
     * @return piece label, or the numeric value as a string if unknown
     */
    public static String getPieceLabel(final int piece) {
        final String label = pieceToStr.get(piece);
        return (label != null) ? label : String.valueOf(piece);
    }

    public static final int M_FLAG_P = 1;
    public static final int M_FLAG_2 = 2;
    public static final int M_FLAG_2P = 3; // This is M_FLAG_2 | M_FLAG_P.

    // Offsets encoded in the low-order two bits.
    public static final int OFF_NONE = 0;
    public static final int OFF_P = M_FLAG_P;
    public static final int OFF_2 = M_FLAG_2;
    public static final int OFF_2P = M_FLAG_2P;

    // All possible half turn moves (HTM)
    public static final int M_NONE = 0;

    public static final int M_R = 4;
    public static final int M_RP = 5;
    public static final int M_R2 = 6;
    public static final int M_R2P = 7;

    public static final int M_L = 8;
    public static final int M_LP = 9;
    public static final int M_L2 = 10;
    public static final int M_L2P = 11;

    public static final int M_U = 12;
    public static final int M_UP = 13;
    public static final int M_U2 = 14;
    public static final int M_U2P = 15;

    public static final int M_D = 16;
    public static final int M_DP = 17;
    public static final int M_D2 = 18;
    public static final int M_D2P = 19;

    public static final int M_F = 20;
    public static final int M_FP = 21;
    public static final int M_F2 = 22;
    public static final int M_F2P = 23;

    public static final int M_B = 24;
    public static final int M_BP = 25;
    public static final int M_B2 = 26;
    public static final int M_B2P = 27;

    // Additional middle moves that are not used by the solver and that are not
    // considered to be valid moves.
    public static final int M_M = 28;
    public static final int M_MP = 29;
    public static final int M_M2 = 30;
    public static final int M_M2P = 31;
    public static final int M_E = 32;
    public static final int M_EP = 33;
    public static final int M_E2 = 34;
    public static final int M_E2P = 35;
    public static final int M_S = 36;
    public static final int M_SP = 37;
    public static final int M_S2 = 38;
    public static final int M_S2P = 39;

    // Additional cube rotations that are not used by the solver and that are not
    // considered to be valid moves.
    public static final int M_X = 40;
    public static final int M_XP = 41;
    public static final int M_X2 = 42;
    public static final int M_X2P = 43;
    public static final int M_Y = 44;
    public static final int M_YP = 45;
    public static final int M_Y2 = 46;
    public static final int M_Y2P = 47;
    public static final int M_Z = 48;
    public static final int M_ZP = 49;
    public static final int M_Z2 = 50;
    public static final int M_Z2P = 51;

    // Additional wide moves that are not used by the solver and that are not
    // considered to be valid moves.
    public static final int M_RW = 52;
    public static final int M_RWP = 53;
    public static final int M_RW2 = 54;
    public static final int M_RW2P = 55;
    public static final int M_LW = 56;
    public static final int M_LWP = 57;
    public static final int M_LW2 = 58;
    public static final int M_LW2P = 59;
    public static final int M_UW = 60;
    public static final int M_UWP = 61;
    public static final int M_UW2 = 62;
    public static final int M_UW2P = 63;
    public static final int M_DW = 64;
    public static final int M_DWP = 65;
    public static final int M_DW2 = 66;
    public static final int M_DW2P = 67;
    public static final int M_FW = 68;
    public static final int M_FWP = 69;
    public static final int M_FW2 = 70;
    public static final int M_FW2P = 71;
    public static final int M_BW = 72;
    public static final int M_BWP = 73;
    public static final int M_BW2 = 74;
    public static final int M_BW2P = 75;

    // Preferred move order. Solvers that pick the first improving move use this
    // to produce more predictable move sequences.
    public static final int[] VALID_MOVES = new int[]{M_R, M_RP, M_R2, M_L,
            M_LP, M_L2, M_U, M_UP, M_U2, M_D, M_DP, M_D2, M_F, M_FP, M_F2, M_B,
            M_BP, M_B2};

    // Inverse moves aligned by index with VALID_MOVES.
    public static final int[] VALID_MOVES_INV = new int[]{M_RP, M_R, M_R2,
            M_LP, M_L, M_L2, M_UP, M_U, M_U2, M_DP, M_D, M_D2, M_FP, M_F, M_F2,
            M_BP, M_B, M_B2};

    // This map relates the root of each of the extended moves to the equivalent
    // HTM moves. Note that the cube rotation is last since it make it easier
    // to understand.
    public static final Map<Integer, int[]> rootExtMoveToHTMMoveMap;
    static {
        final Map<Integer, int[]> eth =new HashMap<>();

        // Middle / slice moves.
        eth.put(M_M, new int[] { M_R, M_LP, M_XP });
        eth.put(M_E, new int[] { M_U, M_DP, M_YP });
        eth.put(M_S, new int[] { M_FP, M_B, M_Z });

        // Wide moves.
        eth.put(M_RW, new int[] { M_L, M_X });
        eth.put(M_LW, new int[] { M_R, M_XP });
        eth.put(M_UW, new int[] { M_D, M_Y });
        eth.put(M_DW, new int[] { M_U, M_YP });
        eth.put(M_FW, new int[] { M_B, M_Z });
        eth.put(M_BW, new int[] { M_F, M_ZP });

        rootExtMoveToHTMMoveMap = Map.copyOf(eth);
    }

    static final Map<String, Integer> strToMoveMap = new HashMap<String, Integer>();
    static {
        strToMoveMap.put("R", M_R);
        strToMoveMap.put("L", M_L);
        strToMoveMap.put("U", M_U);
        strToMoveMap.put("D", M_D);
        strToMoveMap.put("F", M_F);
        strToMoveMap.put("B", M_B);
    }

    // Superset parser map used by translation helpers.
    static final Map<String, Integer> extStrToMoveMap = new HashMap<String, Integer>();
    static {
        extStrToMoveMap.putAll(strToMoveMap);
        extStrToMoveMap.put("X", M_X);
        extStrToMoveMap.put("Y", M_Y);
        extStrToMoveMap.put("Z", M_Z);
        extStrToMoveMap.put("M", M_M);
        extStrToMoveMap.put("E", M_E);
        extStrToMoveMap.put("S", M_S);
        extStrToMoveMap.put("r", M_RW);
        extStrToMoveMap.put("l", M_LW);
        extStrToMoveMap.put("u", M_UW);
        extStrToMoveMap.put("d", M_DW);
        extStrToMoveMap.put("f", M_FW);
        extStrToMoveMap.put("b", M_BW);
    }

    private static final Map<Integer, String> moveToStrMap = new HashMap<Integer, String>();
    static {
        moveToStrMap.put(M_R, "R");
        moveToStrMap.put(M_L, "L");
        moveToStrMap.put(M_U, "U");
        moveToStrMap.put(M_D, "D");
        moveToStrMap.put(M_F, "F");
        moveToStrMap.put(M_B, "B");
    }

    private static final Map<Integer, String> extMoveToStrMap = new HashMap<Integer, String>();
    static {
        extMoveToStrMap.putAll(moveToStrMap);
        extMoveToStrMap.put(M_X, "X");
        extMoveToStrMap.put(M_Y, "Y");
        extMoveToStrMap.put(M_Z, "Z");
        extMoveToStrMap.put(M_M, "M");
        extMoveToStrMap.put(M_E, "E");
        extMoveToStrMap.put(M_S, "S");
        extMoveToStrMap.put(M_RW, "r");
        extMoveToStrMap.put(M_LW, "l");
        extMoveToStrMap.put(M_UW, "u");
        extMoveToStrMap.put(M_DW, "d");
        extMoveToStrMap.put(M_FW, "f");
        extMoveToStrMap.put(M_BW, "b");
    }

    // Each move permeates the pieces by with a cycle that contains two cycle -
    // the edges and then the corners. Each cycle begins with the piece that has
    // the lowest XYZ value.
    private static final Map<Integer, int[][]> movePieceMap = new HashMap<Integer, int[][]>();
    static {
        movePieceMap.put(M_NONE, new int[][] {});

        movePieceMap.put(M_R, new int[][] { { P_RD, P_RF, P_RU, P_RB },
                { P_RDB, P_RDF, P_RUF, P_RUB } });
        movePieceMap.put(M_RP, new int[][] { { P_RD, P_RB, P_RU, P_RF },
                { P_RDB, P_RUB, P_RUF, P_RDF } });
        movePieceMap.put(M_R2, new int[][] { { P_RD, P_RU }, { P_RB, P_RF },
                { P_RDB, P_RUF }, { P_RDF, P_RUB } });

        movePieceMap.put(M_L, new int[][] { { P_LD, P_LB, P_LU, P_LF },
                { P_LDB, P_LUB, P_LUF, P_LDF } });
        movePieceMap.put(M_LP, new int[][] { { P_LD, P_LF, P_LU, P_LB },
                { P_LDB, P_LDF, P_LUF, P_LUB } });
        movePieceMap.put(M_L2, new int[][] { { P_LD, P_LU }, { P_LB, P_LF },
                { P_LDB, P_LUF }, { P_LDF, P_LUB } });

        movePieceMap.put(M_U, new int[][] { { P_LU, P_UB, P_RU, P_UF },
                { P_LUB, P_RUB, P_RUF, P_LUF } });
        movePieceMap.put(M_UP, new int[][] { { P_LU, P_UF, P_RU, P_UB },
                { P_LUB, P_LUF, P_RUF, P_RUB } });
        movePieceMap.put(M_U2, new int[][] { { P_LU, P_RU }, { P_UB, P_UF },
                { P_LUB, P_RUF }, { P_LUF, P_RUB } });

        movePieceMap.put(M_D, new int[][] { { P_LD, P_DF, P_RD, P_DB },
                { P_LDB, P_LDF, P_RDF, P_RDB } });
        movePieceMap.put(M_DP, new int[][] { { P_LD, P_DB, P_RD, P_DF },
                { P_LDB, P_RDB, P_RDF, P_LDF } });
        movePieceMap.put(M_D2, new int[][] { { P_LD, P_RD }, { P_DB, P_DF },
                { P_LDB, P_RDF }, { P_LDF, P_RDB } });

        movePieceMap.put(M_F, new int[][] { { P_LF, P_UF, P_RF, P_DF },
                { P_LDF, P_LUF, P_RUF, P_RDF } });
        movePieceMap.put(M_FP, new int[][] { { P_LF, P_DF, P_RF, P_UF },
                { P_LDF, P_RDF, P_RUF, P_LUF } });
        movePieceMap.put(M_F2, new int[][] { { P_LF, P_RF }, { P_DF, P_UF },
                { P_LDF, P_RUF }, { P_LUF, P_RDF } });

        movePieceMap.put(M_B, new int[][] { { P_LB, P_DB, P_RB, P_UB },
                { P_LDB, P_RDB, P_RUB, P_LUB } });
        movePieceMap.put(M_BP, new int[][] { { P_LB, P_UB, P_RB, P_DB },
                { P_LDB, P_LUB, P_RUB, P_RDB } });
        movePieceMap.put(M_B2, new int[][] { { P_LB, P_RB }, { P_DB, P_UB },
                { P_LDB, P_RUB }, { P_LUB, P_RDB } });

        // Additional middle moves. Note that these are incomplete because there
        // are no middle pieces. Only the four edges are moved.

        movePieceMap.put(M_M, new int[][] { { P_DB, P_UB, P_UF, P_DF } });
        movePieceMap.put(M_MP, new int[][] { { P_DB, P_DF, P_UF, P_UB } });
        movePieceMap.put(M_M2, new int[][] { { P_DB, P_UF }, { P_DF, P_UB } });

        movePieceMap.put(M_E, new int[][] { { P_LB, P_LF, P_RF, P_RB } });
        movePieceMap.put(M_EP, new int[][] { { P_LB, P_RB, P_RF, P_LF } });
        movePieceMap.put(M_E2, new int[][] { { P_LB, P_RF }, { P_LF, P_RB } });

        movePieceMap.put(M_S, new int[][] { { P_LD, P_LU, P_RU, P_RD } });
        movePieceMap.put(M_SP, new int[][] { { P_LD, P_RD, P_RU, P_LU } });
        movePieceMap.put(M_S2, new int[][] { { P_LD, P_RU }, { P_LU, P_RD } });
    }

    private static final Map<Integer, int[][]> moveTwistMap = new HashMap<Integer, int[][]>();
    static {
        moveTwistMap.put(M_NONE, new int[][] {});

        moveTwistMap.put(M_R, new int[][] { { 0, 0, 0, 0 }, { -1, 1, -1, 1 } });
        moveTwistMap
                .put(M_RP, new int[][] { { 0, 0, 0, 0 }, { -1, 1, -1, 1 } });
        moveTwistMap.put(M_R2, new int[][] { { 0, 0 }, { 0, 0 }, { 0, 0 },
                { 0, 0 } });

        moveTwistMap.put(M_L, new int[][] { { 0, 0, 0, 0 }, { 1, -1, 1, -1 } });
        moveTwistMap
                .put(M_LP, new int[][] { { 0, 0, 0, 0 }, { 1, -1, 1, -1 } });
        moveTwistMap.put(M_L2, new int[][] { { 0, 0 }, { 0, 0 }, { 0, 0 },
                { 0, 0 } });

        moveTwistMap.put(M_U, new int[][] { { 0, 0, 0, 0 }, { 1, -1, 1, -1 } });
        moveTwistMap
                .put(M_UP, new int[][] { { 0, 0, 0, 0 }, { 1, -1, 1, -1 } });
        moveTwistMap.put(M_U2, new int[][] { { 0, 0 }, { 0, 0 }, { 0, 0 },
                { 0, 0 } });

        moveTwistMap.put(M_D, new int[][] { { 0, 0, 0, 0 }, { -1, 1, -1, 1 } });
        moveTwistMap
                .put(M_DP, new int[][] { { 0, 0, 0, 0 }, { -1, 1, -1, 1 } });
        moveTwistMap.put(M_D2, new int[][] { { 0, 0 }, { 0, 0 }, { 0, 0 },
                { 0, 0 } });

        moveTwistMap.put(M_F, new int[][] { { 1, 1, 1, 1 }, { 0, 0, 0, 0 } });
        moveTwistMap.put(M_FP, new int[][] { { 1, 1, 1, 1 }, { 0, 0, 0, 0 } });
        moveTwistMap.put(M_F2, new int[][] { { 0, 0 }, { 0, 0 }, { 0, 0 },
                { 0, 0 } });

        moveTwistMap.put(M_B, new int[][] { { 1, 1, 1, 1 }, { 0, 0, 0, 0 } });
        moveTwistMap.put(M_BP, new int[][] { { 1, 1, 1, 1 }, { 0, 0, 0, 0 } });
        moveTwistMap.put(M_B2, new int[][] { { 0, 0 }, { 0, 0 }, { 0, 0 },
                { 0, 0 } });

        // Middle moves. See the comment about "middle moves" in the
        // movePieceMap section above.

        moveTwistMap.put(M_M, new int[][] { { 1, 1, 1, 1 } });
        moveTwistMap.put(M_MP, new int[][] { { 1, 1, 1, 1 } });
        moveTwistMap.put(M_M2, new int[][] { { 0, 0 }, { 0, 0 } });

        moveTwistMap.put(M_E, new int[][] { { 1, 1, 1, 1 } });
        moveTwistMap.put(M_EP, new int[][] { { 1, 1, 1, 1 } });
        moveTwistMap.put(M_E2, new int[][] { { 0, 0 }, { 0, 0 } });

        moveTwistMap.put(M_S, new int[][] { { 1, 1, 1, 1 } });
        moveTwistMap.put(M_SP, new int[][] { { 1, 1, 1, 1 } });
        moveTwistMap.put(M_S2, new int[][] { { 0, 0 }, { 0, 0 } });
    }

    // Relate the offset of a move to the amount the side is rotated.
    public static int[] offsetToRotation = new int[] { 1, 3, 2, 2 };
    public static int[] rotationToOffset = new int[] { -1, 0, 2, 1 };

    // All stickers are perpendicular to one of three axis. Note that the code
    // assumes these specific values.
    public static final int AXIS_NONE = 0;
    public static final int AXIS_X = 1;
    public static final int AXIS_Y = 2;
    public static final int AXIS_Z = 3;

    // Relate the axis (above) to rotate about to a set of moves. Note that the
    // middles are not moved. The order of the moves is along the axis.
    private static final Map<Integer, int[]> axisMoveMap = new HashMap<Integer, int[]>();
    static {
        axisMoveMap.put(AXIS_X, new int[] { M_LP, M_MP, M_R });
        axisMoveMap.put(AXIS_Y, new int[] { M_DP, M_EP, M_U });
        axisMoveMap.put(AXIS_Z, new int[] { M_BP, M_S, M_F });
    }

    // Relate an internal face label to a set of axis rotations
    // (above) that places that face on the top of the cube.
    private static final Map<Character, int[]> faceAxisMap = new HashMap<Character, int[]>();
    static {
        faceAxisMap.put('L', new int[] { AXIS_Z });
        faceAxisMap.put('R', new int[] { -AXIS_Z });
        faceAxisMap.put('D', new int[] { AXIS_X, AXIS_X });
        faceAxisMap.put('U', new int[] {});
        faceAxisMap.put('B', new int[] { -AXIS_X });
        faceAxisMap.put('F', new int[] { AXIS_X });
    }

    // Relates an axis to a set of replacements that need to be done so a set of
    // moves can move with the cube.
    private static final Map<Integer, int[][]> axisMoveReplMap = new HashMap<Integer, int[][]>();
    static {
        axisMoveReplMap.put(AXIS_X, new int[][] { { M_U, M_B }, { M_D, M_F },
                { M_F, M_U }, { M_B, M_D } });
        axisMoveReplMap.put(AXIS_Y, new int[][] { { M_R, M_F }, { M_L, M_B },
                { M_F, M_L }, { M_B, M_R } });
        axisMoveReplMap.put(AXIS_Z, new int[][] { { M_R, M_D }, { M_L, M_U },
                { M_U, M_R }, { M_D, M_L } });
    }

    // Relates numeric values in AnimCube's "cube" array to color characters.
    public static final String acStickerToColorStr = "DUBFRL";
    static final Map<Integer, Character> acStickerToColorMap = new HashMap<Integer, Character>();
    static final Map<Character, Integer> acColorToStickerMap = new HashMap<Character, Integer>();
    static {
        for (int st = 10; st < 16; st++) {
            acStickerToColorMap.put(st, acStickerToColorStr.charAt(st - 10));
            acColorToStickerMap.put(acStickerToColorStr.charAt(st - 10), st);
        }
    }

    // Relates the order that sides are processed when printed in wide format to
    // the index in the AnimCube's array of stickers.
    public static final int[] acWideToSideNum = { 0, 5, 3, 4, 2, 1 };

    /**
     * Return true if it's a corner piece.
     *
     * @param piece
     *            the piece
     * @return true if a corner
     */
    public static boolean isCorner(final int piece) {
        return (piece == P_LDB) || (piece == P_LDF) || (piece == P_LUB)
                || (piece == P_LUF) || (piece == P_RDB) || (piece == P_RDF)
                || (piece == P_RUB) || (piece == P_RUF);
    }

    /**
     * Return true if it's an edge piece.
     *
     * @param piece
     *            the piece
     * @return true if an edge
     */
    public static boolean isEdge(final int piece) {
        return !isCorner(piece);
    }

    /**
     * Convert an array of move integers to a human readable string.
     *
     * @param ms
     *            array of moves
     * @return human readable string
     */
    public static String movesToStr(final int[] ms, final Map<Integer, String> moveMap) {
        if (ms == null) {
            return "";
        }
        String mStr = "";
        for (final int m : ms) {
            if (m == M_NONE) {
                // A noop. Probably does not happen.
                mStr += "N";
                continue;
            }
            final int offset = Util.getOffset(m);
            final int side = m - offset;
            mStr += moveMap.get(side);
            switch (offset) {
            case OFF_NONE:
                break;
            case OFF_P:
                mStr += "'";
                break;
            case OFF_2:
                mStr += "2";
                break;
            case OFF_2P:
                mStr += "2'";
            }
        }
        return mStr;
    }

    /**
     * Convert an array of HTM move integers to a human readable string.
     *
     * @param ms
     *            array of moves
     * @return human readable string
     */
    public static String movesToStr(final int[] ms) {
        return movesToStr(ms, moveToStrMap);
    }

    /**
     * Convert an array of extended move integers to a human readable string.
     *
     * @param ms
     *            array of moves
     * @return human readable string
     */
    public static String extMovesToStr(final int[] ms) {
        return movesToStr(ms, extMoveToStrMap);
    }

    /**
     * Return the inverse of the moves passed in.
     *
     * @param mvs
     *            moves to invert
     * @return inverse moves
     */
    public static int[] inverseMoves(final int[] mvs) {
        final int[] inverse = new int[mvs.length];
        for (int i = 0; i < mvs.length; i++) {
            final int mv = mvs[mvs.length - 1 - i];
            int offset = Util.getOffset(mv);
            final int side = mv - offset;
            // For inverse notation we flip prime for all moves:
            // R <-> R', R2 <-> R2', etc.
            offset ^= OFF_P;
            inverse[i] = side + offset;
        }
        return inverse;
    }

    /**
     * Return the moves mirrored about the YZ plane.
     *
     * @param mvs
     *            Input list of moves
     * @return Mirrored moves
     */
    public static int[] mirrorMoves(final int[] mvs) {
        final int[] ret = new int[mvs.length];
        for (int i = 0; i < mvs.length; i++) {
            final int mv = mvs[i];
            int offset = Util.getOffset(mv);
            int side = mv - offset;
            boolean invert = true;
            switch (side) {
            case M_R:
                side = M_L;
                break;
            case M_L:
                side = M_R;
                break;
            case M_RW:
                side = M_LW;
                break;
            case M_LW:
                side = M_RW;
                break;
            case M_X:
            case M_M:
                invert = false;
                break;
            }
            if (invert) {
                // Reflection flips chirality, so toggle prime for all offsets:
                // R <-> R', R2 <-> R2', etc.
                offset ^= OFF_P;
            }
            ret[i] = side + offset;
        }
        return ret;
    }

    /**
     * Prunes out consecutive moves. This is done recursively until there is no
     * improvement.
     *
     * @param mvs
     *            Input list of moves
     * @return Input with redundant moves removed.
     */
    public static int[] pruneMoves(final int[] mvs) {
        final int[] ret = new int[mvs.length];

        // Pointers to the input and output arrays for the current iteration.
        int[] mIn = mvs;
        int[] mOut = ret;
        int mInLen = mIn.length;

        int outIdx;
        while (true) {
            int sideOld = -1;
            int rotationTotal = 0;
            outIdx = 0;
            for (int inIdx = 0; inIdx <= mInLen; inIdx++) {
                // Terminate mIn with M_M, which never occurs in mvs, so that
                // the last move is copied.
                int mv = (inIdx == mInLen) ? M_M : mIn[inIdx];
                int offset = Util.getOffset(mv);
                int rotation = offsetToRotation[offset];
                int side = mv - offset;

                // If the side changed and this is not the first move processed
                // or if the end has been reached then write what we have to the
                // output array.
                if ((side != sideOld) && (sideOld != -1)) {
                    // U2U2, etc., doing nothing.
                    rotationTotal %= 4;
                    if (rotationTotal != 0) {
                        mOut[outIdx++] = sideOld
                                + rotationToOffset[rotationTotal];
                        rotationTotal = 0;
                    }
                }
                rotationTotal += rotation;
                sideOld = side;
            }
            if (outIdx < mInLen) {
                // If something was pruned then keep trying until nothing is
                // pruned.
                mIn = mOut;
                mInLen = outIdx;
            } else {
                // Nothing was pruned, so we must be done.
                break;
            }
        }
        return Arrays.copyOf(mOut, outIdx);
    }

    // Piece type and orientations.
    private int[][][] pieces = new int[3][3][3];

    private int[][][] twists = new int[3][3][3];

    private String error;

    private Format format;

    private boolean verbose;

    /**
     * Constructor that initializes a solved cube.
     *
     * @throws CubearoniException
     */
    public Cube() {
        format = new Format();

        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    if ((x == 1) && (y == 1) || (y == 1) && (z == 1)
                            || (z == 1) && (x == 1)) {
                        // Not a valid piece.
                        pieces[x][y][z] = P_NONE;
                    } else {
                        pieces[x][y][z] = 100 * x + 10 * y + z;
                    }
                }
            }
        }
    }

    /**
     * Copy constructor. pieces and twists are deeply copied.
     *
     * @param cube
     *            cube to be copied
     */
    public Cube(final Cube cube) {
        // Shallow copy for the format. Generally all cubes have the same format
        // in a given run.
        format = cube.format;

        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    pieces[x][y][z] = cube.pieces[x][y][z];
                    twists[x][y][z] = cube.twists[x][y][z];
                }
            }
        }
    }

    /**
     * Internal constructor that initializes given a description of the cube.
     *
     * @param cFName
     *            cubearoni filename
     * @param cStr
     *            the contents of an cubearoni file
     * @param stickers
     *            the stickers in a 2x2 array that is compatible with "cube" in
     *            AnimCube
     * @throws CubearoniException
     */
    public Cube(String cFName, String cStr, final int[][] stickers)
            throws CubearoniException {
        format = new Format();

        InputStream cfHand;
        if ((cFName != null) && (cStr == null) && (stickers == null)) {
            try {
                cfHand = new FileInputStream(cFName);
            } catch (FileNotFoundException e) {
                throw new CubearoniException("Could not open \"" + cFName
                        + "\" for read: " + e);
            }
        } else if ((cFName == null) && (cStr != null) && (stickers == null)) {
            cfHand = new ByteArrayInputStream(cStr.getBytes());
            cFName = "<cube-string>";
        } else if ((cFName == null) && (cStr == null) && (stickers != null)) {
            String stStr = stickersToString(stickers);
            cfHand = new ByteArrayInputStream(stStr.getBytes());
            cFName = "<ac-stickers>";
        } else {
            throw new CubearoniException("Exactly one of \"cFName\" and "
                    + "\"stickers\" must not be null.");
        }

        if (verbose) {
            System.out.println("Calling setCubearoniFile(" + cFName + ") to read cube.");
        }
        setCubearoniFile(cfHand);
    }

    /**
     * Constructor that initializes given a human readable cubearoni file.
     *
     * @param cFName
     *            cubearoni filename
     * @throws CubearoniException
     */
    public Cube(final String cFName) throws CubearoniException {
        this(cFName, null, null);
    }

    /**
     * Constructor that initializes given an array of stickers.
     *
     * @param stickers
     *            the stickers in a 2x2 array that is compatible with "cube" in
     *            AnimCube
     * @throws CubearoniException
     */
    public Cube(final int[][] stickers) throws CubearoniException {
        this(null, null, stickers);
    }

    /** Converts AnimCube sticker data to the unfolded text representation. */
    private static String stickersToString(final int[][] stickers) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream out = new PrintStream(baos);
        int stSide, stNum = 0;
        int row, col;

        for (row = 0; row < 3; row++) {
            out.print("    ");
            for (col = 0; col < 3; col++) {
                stNum = 3 * (2 - row) + col;
                out.print(acStickerToColorMap
                        .get(stickers[acWideToSideNum[0]][stNum]));
            }
            out.println("");
        }
        out.println("    ...");

        for (row = 0; row < 3; row++) {
            boolean first = true;
            for (int wSide = 0; wSide < 4; wSide++) {
                stSide = (wSide + 2) % 4 + 1;
                if (!first) {
                    out.print(".");
                }
                first = false;
                for (col = 0; col < 3; col++) {
                    switch (stSide) {
                    case 1:
                    case 2:
                        stNum = row + 3 * col;
                        break;
                    case 3:
                        stNum = 3 * row + (2 - col);
                        break;
                    case 4:
                        stNum = row + 3 * col;
                        break;
                    }
                    out.print(acStickerToColorMap
                            .get(stickers[acWideToSideNum[stSide]][stNum]));
                }
            }
            out.println("");
        }
        out.println("    ...");

        for (row = 0; row < 3; row++) {
            out.print("    ");
            for (col = 0; col < 3; col++) {
                stNum = row + 3 * col;
                out.print(acStickerToColorMap
                        .get(stickers[acWideToSideNum[5]][stNum]));
            }
            out.println("");
        }
        return baos.toString();
    }

    /**
     * Set all pieces to P_NONE and all twists to 0. The resulting invalid cube
     * is helpful to solvers that only care about a subset of the cube, which
     * they'll set manually.
     */
    public void clear() {
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    pieces[x][y][z] = P_NONE;
                    twists[x][y][z] = 0;
                }
            }
        }
    }

    /**
     * Like clone(), but avoids some of the overhead and complexity of clone().
     *
     * @return new Cube instance
     */
    public Cube dup() {
        return new Cube(this);
    }

    /**
     * Like {@code move()} except that extended moves are supported by
     * translation to HTM moves. Note that rotation is correct for so that
     * moves have the expected meaning at the end.
     *
     * @param extMovesStr
     *            move string
     * @param extMovesMode
     *            one of Util.EXT_TO_HTM_MODE_*
     * @return this, so that it can be chained
     * @throws CubearoniException
     */
    public Cube extMoves(final String extMovesStr, final int extMovesMode)
            throws CubearoniException {
        final int[] extMoves = Util.moveStrToExtendedMoves(extMovesStr);
        final int[] htmMoves = Util.extMovesToHTMMoves(extMoves, extMovesMode);
        for (final int move : htmMoves) {
            move(move);
        }
        return this;
    }

    /**
     * For the array of pieces given return an array of arrays that indicate
     * each pieces' location and position. The returned array for each piece is
     * 4 ints long, XYZ followed by the twist. This is similar to findPieces()
     * except an exception is thrown if a piece can not be found.
     *
     * @param pcs
     *            the pieces to search for
     * @return array of location and twist information for each piece
     */
    public int[][] findAllPieces(final int pcs[]) throws CubearoniException {
        final int[][] locsTwists = findPieces(pcs);
        for (int i = 0; i < locsTwists.length; i++) {
            if (locsTwists[i] == null) {
                throw new CubearoniException("Piece " + pcs[i] + " was not found");
            }
        }
        return locsTwists;
    }

    /**
     * For the array of pieces given return an array of arrays that indicate
     * each pieces' location and position. The returned array for each piece is
     * 4 ints long, XYZ followed by the twist.
     *
     * @param pcs
     *            the pieces to search for
     * @return array of location and twist information, or null, for each piece
     */
    public int[][] findPieces(final int pcs[]) {
        final int[][] locsTwists = new int[pcs.length][];
        for (int i = 0; i < pcs.length; i++) {
            final int pc = pcs[i];
            boolean found = false;
            for (int x = 0; (!found) && (x <= 2); x++) {
                for (int y = 0; (!found) && (y <= 2); y++) {
                    for (int z = 0; (!found) && (z <= 2); z++) {
                        if (pc == pieces[x][y][z]) {
                            locsTwists[i] = new int[] { x, y, z,
                                    twists[x][y][z] };
                            found = true;
                            break;
                        }
                    }
                }
            }
        }
        return locsTwists;
    }

    /**
     * Return true if the cube is fully solved.
     *
     * Every piece must be in its solved XYZ location and have zero twist.
     *
     * @return true if solved
     */
    public boolean isSolved() {
        return isSolvedThroughY(2);
    }

    /**
     * Returns true if every piece whose solved location is at or below the
     * specified Y coordinate is solved.
     *
     * @param maxY highest solved-location Y coordinate to check
     * @return true if the selected layers are solved
     */
    public boolean isSolvedThroughY(final int maxY) {
        if ((maxY < 0) || (maxY > 2)) {
            throw new IllegalArgumentException("maxY must be between 0 and 2.");
        }
        for (final Entry<Integer, int[]> entry : pieceToLoc.entrySet()) {
            final int piece = entry.getKey();
            final int[] loc = entry.getValue();
            final int x = loc[0];
            final int y = loc[1];
            final int z = loc[2];
            if (y > maxY) {
                continue;
            }
            if ((pieces[x][y][z] != piece) || (twists[x][y][z] != 0)) {
                return false;
            }
        }
        return true;
    }

    /** Returns the most recent cube validation error. */
    public String getError() {
        return error;
    }

    /** Returns the cube's input and output format configuration. */
    public Format getFormat() {
        return format;
    }

    /**
     * Return the piece at the location specified.
     *
     * @param x
     *            X coordinate
     * @param y
     *            Y coordinate
     * @param z
     *            Z coordinate
     * @return the piece
     */
    public int getPiece(int x, int y, int z) {
        return pieces[x][y][z];
    }

    /**
     * Get the stickers in a 2x2 array that is compatible with "cube" in
     * AnimCube.
     *
     * @return the stickers
     */
    public int[][] getStickers() {
        return null;
    }

    /**
     * Return the axis of a sticker with a given internal sticker color.
     *
     * @param x
     *            X coordinate
     * @param y
     *            Y coordinate
     * @param z
     *            Z coordinate
     * @param color
     *            X, Y or Z axis
     * @return The axis, or AXIS_NONE;
     */
    public int getStickerAxis(final int x, final int y, final int z, final char color) {
        // TODO: There is probably a more efficient way of doing this.
        final boolean edge = (x == 1) || (y == 1) || (z == 1);
        for (int axis = AXIS_X; axis <= AXIS_Z; axis++) {
            if (edge
                    && ((x == 1) && (axis == AXIS_X) || (y == 1)
                            && (axis == AXIS_Y) || (z == 1) && (axis == AXIS_Z))) {
                continue;
            }
            if (getStickerColor(x, y, z, axis) == color) {
                return axis;
            }
        }

        return AXIS_NONE;
    }

    /**
     * Return the face label of a sticker at a particular location.
     *
     * @param x
     *            X coordinate
     * @param y
     *            Y coordinate
     * @param z
     *            Z coordinate
     * @param axis
     *            X, Y or Z axis
     * @return a face label
     */
    public char getStickerColor(final int x, final int y, final int z, final int axis) {
        final int piece = pieces[x][y][z];
        if (piece == -1) {
            if ((x == 1) && (y == 1) || (y == 1) && (z == 1) || (z == 1)
                    && (x == 1)) {
                // It's a middle sticker, so there is no piece.
                switch (axis) {
                case AXIS_X:
                    return (x == 0) ? 'L' : 'R';
                case AXIS_Y:
                    return (y == 0) ? 'D' : 'U';
                case AXIS_Z:
                    return (z == 0) ? 'B' : 'F';
                }
            } else {
                // It's an invalid / unset piece.
                return '-';
            }
        }

        final int twist = twists[x][y][z];
        int off = 0;
        final String pieceStr = pieceToStr.get(piece);
        if ((x == 1) || (y == 1) || (z == 1)) {
            // edge

            int xSt = pieceStr.indexOf("L");
            if (xSt == -1) {
                xSt = pieceStr.indexOf("R");
            }
            int ySt = pieceStr.indexOf("D");
            if (ySt == -1) {
                ySt = pieceStr.indexOf("U");
            }
            int zSt = pieceStr.indexOf("B");
            if (zSt == -1) {
                zSt = pieceStr.indexOf("F");
            }

            if ((zSt == -1) || (z == 1)) {
                // Edge does not contain a Z sticker, or is not on a
                // Z side. Here we are only concerned with X and Y
                // stickers and sides

                if ((x != 1) && (xSt != -1)) {
                    off = xSt;
                    if (axis != AXIS_X) {
                        off++;
                    }
                } else if ((y != 1) && (ySt != -1)) {
                    off = ySt;
                    if (axis != AXIS_Y) {
                        off++;
                    }
                }
            } else {
                // There is a Z sticker and we're the sticker is on the Z axis
                // then start by assuming the offset is the Z index, which must
                // be last.
                off = (axis == AXIS_Z) ? 1 : 0;

                // The edge has been rotated on a Z side.
                if (((xSt != -1) && (y != 1)) || ((ySt != -1) && (x != 1))) {
                    off++;
                }
            }
            off = (off + twist) % 2;
        } else {
            // corner
            // "loc" refers to the location of the piece in a solved cube.
            final int[] sLoc = pieceToLoc.get(piece);

            // When the P_LDB corner at (0, 0, 0) is rotated clockwise the
            // stickers are mapped X -> Y-> Z, or (X, Y, Z) in cycle notation.
            // Half the pieces rotates the opposite way, are considered to be
            // reverse, or "revP" for reverse piece.
            final boolean revP = (sLoc[0] + sLoc[1] + sLoc[2]) % 4 == 2;

            // An axis is reversed or not as a function of the actual location
            // of the piece.
            final boolean revA = (x + y + z) % 4 == 2;
            off = (2 + (revP ? twist : (3 - twist)) + ((revP ^ revA) ? (3 - axis)
                    : axis)) % 3;
        }
        return pieceStr.charAt(off);
    }

    /**
     * Return the twist at the location specified.
     *
     * @param x
     *            X coordinate
     * @param y
     *            Y coordinate
     * @param z
     *            Z coordinate
     * @return the twist
     */
    public int getTwist(final int x, final int y, final int z) {
        return twists[x][y][z];
    }

    /**
     * Tests if the cube is solveable. If not "error" is set to a string
     * explaining the problem.
     *
     * @return True if the cube is solveable
     */
    public boolean isSolveable() {
        // Each randomly assembled cube has a 1/12 chance of being solvable.
        // To be solvable it must pass three tests.
        // 1) It must be an even permutation (two pieces may not be swapped).
        // 2) The total edge twist must be even (a single edge may not be
        // twisted). This excludes 1/2 of randomly assembled cubes.
        // 3) The total corner twist must be divisible by three (a single corner
        // may not be twisted). This excludes 1/3 of randomly assembled cubes.

        int edgeTwist = 0;
        int cornerTwist = 0;
        int ei = 0;
        int ci = 0;
        final int[] edgeList = new int[12];
        final int[] cornerList = new int[8];
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    if (pieces[x][y][z] == P_NONE) {
                        continue;
                    }
                    if (x == 1 || y == 1 || z == 1) {
                        // edge
                        edgeTwist += twists[x][y][z];
                        if (ei >= edgeList.length) {
                            error = "3D array \"pieces\" has more than 12 edges in it.";
                            return false;
                        }
                        edgeList[ei++] = pieces[x][y][z];
                    } else {
                        // corner
                        cornerTwist += twists[x][y][z];
                        if (ci >= cornerList.length) {
                            error = "3D array \"pieces\" has more than 8 corners in it.";
                            return false;
                        }
                        cornerList[ci++] = pieces[x][y][z];
                    }
                }
            }
        }

        // 1) Permutation check - flatten "pieces" into a one dimensional array,
        // bubble sort it, and note the number of swaps.
        int swaps = Util.bubbleSort(edgeList);
        swaps += Util.bubbleSort(cornerList);

        // 1) Permutation check - flatten "pieces" into a one dimensional array,
        // bubble sort it, and note the number of swaps.
        if ((swaps % 2) != 0) {
            error = "Permutation is not even.";
            return false;
        }

        // 2) Edge twist.
        if ((edgeTwist % 2) != 0) {
            error = "Total edge twist is not even.";
            return false;
        }

        // 3) Corner twist.
        if ((cornerTwist % 3) != 0) {
            error = "Total corner twist is not divisible by three.";
            return false;
        }

        return true;
    }

    /** Returns whether verbose logging is enabled. */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Apply a single move
     *
     * @param m
     *            the move to apply
     * @return this, so that it can be chained
     * @throws CubearoniException
     */
    public Cube move(final int m) throws CubearoniException {
        final int mExec;
        if (Util.getOffset(m) == OFF_2P) {
            // For now 2' has the same execution behavior as 2.
            mExec = m - OFF_2P + OFF_2;
        } else {
            mExec = m;
        }
        // moveTwistMap
        final int[][] mCycles = movePieceMap.get(mExec);
        final int[][] mTwists = moveTwistMap.get(mExec);
        if (mCycles == null) {
            throw new CubearoniException("Invalid move number " + m);
        }

        final int cNum = mCycles.length;
        for (int n = 0; n < cNum; n++) {
            final int[] mCycle = mCycles[n];
            final int[] mTwist = mTwists[n];
            int piece = P_NONE;
            int twist = 0;
            int oldPiece = P_NONE;
            int oldTwist = 0;
            final int cLen = mCycle.length;
            // Iterates cLen + 1 times as the first iteration does not write.
            for (int i = 0; i <= cLen; i++) {
                final int[] loc = pieceToLoc.get(mCycle[i % cLen]);
                final int x = loc[0];
                final int y = loc[1];
                final int z = loc[2];
                if (i < cLen) {
                    // Read if not the last iteration.
                    piece = pieces[x][y][z];
                    twist = twists[x][y][z];
                    twist += mTwist[i];
                    final boolean edge = x == 1 || y == 1 || z == 1;
                    final int mod = edge ? 2 : 3;
                    twist += mod; // avoid negative numbers
                    twist %= mod;
                }
                if (i > 0) {
                    // Write if not the first iteration.
                    pieces[x][y][z] = oldPiece;
                    twists[x][y][z] = oldTwist;
                }
                oldPiece = piece;
                oldTwist = twist;
            }
        }
        return this;
    }

    /**
     * Apply an array of moves
     *
     * @param ms
     *            array of moves
     * @return this, so that it can be chained
     * @throws CubearoniException
     */
    public Cube moves(int[] ms) throws CubearoniException {
        for (final int m : ms) {
            move(m);
        }
        return this;
    }

    /**
     * Apply a human readable move string
     *
     * @param ms
     *            move string
     * @return this, so that it can be chained
     * @throws CubearoniException
     */
    public Cube moves(final String ms) throws CubearoniException {
        final int[] ma = Util.moveStrToMoves(ms);
        for (final int m : ma) {
            move(m);
        }
        return this;
    }

    /**
     * Print the cube in humane readable cubearoni file format.
     *
     * @param out
     *            stream to write to, if not null
     * @param stickers
     *            sticker array to write to, if not null
     * @throws CubearoniException
     */
    private void printLegacy(final PrintStream out, final int[][] stickers) {
        // Zero based row and column of a particular character.
        // Random random = new Random();
        if (stickers != null) {
            format.setBackPos(Format.BACK_RIGHT);
        }
        final boolean wide = format.isWide();
        for (int row = 0; row < (wide ? 11 : 15); row++) {
            for (int col = 0; col < (wide ? 15 : 11); col++) {
                // Zero based row and column of a side.
                final int siRow = row / 4;
                final int siCol = col / 4;

                // Zero based row and column of a sticker within a side.
                final int stRow = row % 4;
                final int stCol = col % 4;

                // Determine if we're to the left of, to the right of, or inside
                // the cross.
                if ((row < 4) || (row > 6)) {
                    if ((col < 4)) {
                        // left
                        if (out != null) {
                            out.print(' ');
                        }
                        continue;
                    } else if (col > 6) {
                        // right
                        // Nothing is to the right of the cross.
                        continue;
                    } else {
                        // inside
                    }
                }

                // At this point we are inside the cross, so print the '.' if
                // need be and continue.
                if ((stRow == 3) || (stCol == 3)) {
                    if (out != null) {
                        out.print('.');
                    }
                    continue;
                }

                // Only stickers inside the cross are to be printed from this
                // point forward.

                int axis = AXIS_NONE;
                int x = 0, y = 0, z = 0;
                // stSide to colors for a solved cube
                // 0W 1Y 2O 3R 4G 5B
                int stSide = 0, stNum = 0;
                switch (siRow) {
                case 0:
                    // siCol must be 1
                    axis = AXIS_Y;
                    x = stCol;
                    y = 2;
                    z = stRow;
                    stSide = 0;
                    stNum = x + 3 * (2 - z);
                    break;

                case 1:
                    switch (siCol) {
                    case 0:
                        axis = AXIS_X;
                        x = 0;
                        y = 2 - stRow;
                        z = stCol;
                        stSide = 4;
                        stNum = 3 * (2 - y) + (2 - z);
                        break;
                    case 1:
                        axis = AXIS_Z;
                        x = stCol;
                        y = 2 - stRow;
                        z = 2;
                        stSide = 2;
                        stNum = 3 * x + (2 - y);
                        break;
                    case 2:
                        axis = AXIS_X;
                        x = 2;
                        y = 2 - stRow;
                        z = 2 - stCol;
                        stSide = 5;
                        stNum = (2 - y) + 3 * (2 - z);
                        break;
                    case 3:
                        // wide only
                        axis = AXIS_Z;
                        x = 2 - stCol;
                        y = 2 - stRow;
                        z = 0;
                        stSide = 3;
                        stNum = 3 * (2 - x) + (2 - y);
                        break;
                    }
                    break;

                case 2:
                    // siCol must be 1
                    axis = AXIS_Y;
                    x = stCol;
                    y = 0;
                    z = 2 - stRow;
                    stSide = 1;
                    stNum = 3 * x + (2 - z);
                    break;

                case 3:
                    // siCol must be 1. non-wide only.
                    axis = AXIS_Z;
                    x = stCol;
                    y = stRow;
                    z = 0;
                    break;
                }
                final char sc = getStickerColor(x, y, z, axis);
                final String uc = format.toUserColor(sc);
                if (out != null) {
                    out.print(uc);
                }
                if (stickers != null) {
                    stickers[stSide][stNum] = acColorToStickerMap.get(uc
                            .charAt(0));
                }
            }
            if (out != null) {
                out.println("");
            }
        }
    }

    /** Returns one sticker from an oriented face grid. */
    private char getFaceSticker(final char face, final int row, final int col,
            final char backPos) {
        int axis = AXIS_NONE;
        int x = 0;
        int y = 0;
        int z = 0;

        switch (face) {
        case 'U':
            axis = AXIS_Y;
            x = col;
            y = 2;
            z = row;
            break;
        case 'L':
            axis = AXIS_X;
            x = 0;
            y = 2 - row;
            z = col;
            break;
        case 'F':
            axis = AXIS_Z;
            x = col;
            y = 2 - row;
            z = 2;
            break;
        case 'R':
            axis = AXIS_X;
            x = 2;
            y = 2 - row;
            z = 2 - col;
            break;
        case 'D':
            axis = AXIS_Y;
            x = col;
            y = 0;
            z = 2 - row;
            break;
        case 'B':
            axis = AXIS_Z;
            z = 0;
            if ((backPos == Format.BACK_RIGHT)
                    || (backPos == Format.BACK_LEFT)) {
                x = 2 - col;
                y = 2 - row;
            } else {
                x = col;
                y = row;
            }
            break;
        default:
            throw new IllegalArgumentException("Unexpected face: " + face);
        }

        return getStickerColor(x, y, z, axis);
    }

    /** Returns one formatted row from an oriented face grid. */
    private String getFaceRow(final char face, final int row,
            final char backPos) {
        final StringBuilder sb = new StringBuilder();
        for (int col = 0; col < 3; col++) {
            final char sc = getFaceSticker(face, row, col, backPos);
            sb.append(format.toUserColor(sc));
        }
        return sb.toString();
    }

    /** Prints a compact 54-character facelets string in the requested order. */
    private void printFacelets(final PrintStream out, final String order) {
        final StringBuilder sb = new StringBuilder(54);
        for (int i = 0; i < order.length(); i++) {
            final char face = order.charAt(i);
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    final char sc = getFaceSticker(face, row, col, Format.BACK_RIGHT);
                    sb.append(format.toUser(sc));
                }
            }
        }
        out.println(sb.toString());
    }

    /** Returns indentation for the requested number of face-width blocks. */
    private static String indent(final int blocks) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < (4 * blocks); i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** Prints the cube as an unfolded text cross. */
    private void printText(final PrintStream out) {
        final char backPos = format.getBackPos();
        int singleIndent = 1;
        char[] middleFaces = new char[] { 'L', 'F', 'R' };
        char[] firstSingleFaces = new char[] { 'U' };
        boolean hasSecondSingleFace = true;
        char secondSingleFace = 'D';
        char thirdSingleFace = 0;

        switch (backPos) {
        case Format.BACK_BOTTOM:
            thirdSingleFace = 'B';
            break;
        case Format.BACK_RIGHT:
            middleFaces = new char[] { 'L', 'F', 'R', 'B' };
            break;
        case Format.BACK_TOP:
            firstSingleFaces = new char[] { 'B', 'U' };
            break;
        case Format.BACK_LEFT:
            singleIndent = 2;
            middleFaces = new char[] { 'B', 'L', 'F', 'R' };
            break;
        default:
            throw new IllegalArgumentException("Unsupported back position: "
                    + backPos);
        }

        for (final char face : firstSingleFaces) {
            for (int row = 0; row < 3; row++) {
                out.println(indent(singleIndent) + getFaceRow(face, row, backPos));
            }
            out.println(indent(singleIndent) + "...");
        }

        for (int row = 0; row < 3; row++) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < middleFaces.length; i++) {
                if (i > 0) {
                    sb.append('.');
                }
                sb.append(getFaceRow(middleFaces[i], row, backPos));
            }
            out.println(sb.toString());
        }
        out.println(indent(singleIndent) + "...");

        if (hasSecondSingleFace) {
            for (int row = 0; row < 3; row++) {
                out.println(indent(singleIndent)
                        + getFaceRow(secondSingleFace, row, backPos));
            }
        }

        if (thirdSingleFace != 0) {
            out.println(indent(singleIndent) + "...");
            for (int row = 0; row < 3; row++) {
                out.println(indent(singleIndent)
                        + getFaceRow(thirdSingleFace, row, backPos));
            }
        }
    }

    /** Prints the cube using either the configured format or AnimCube stickers. */
    public void print(final PrintStream out, final int[][] stickers) {
        if (stickers != null) {
            // AnimCube expects the historical wide layout.
            printLegacy(out, stickers);
            return;
        }
        if (out != null) {
            final String faceletsOrder = format.getFaceletsOrder();
            if (faceletsOrder != null) {
                printFacelets(out, faceletsOrder);
            } else {
                printText(out);
            }
        }
    }

    /**
     * Print the cube in humane readable cubearoni file format.
     *
     * @param out
     *            stream to write to, if not null
     */
    public void print(final PrintStream out) {
        print(out, null);
    }

    /**
     * Print out the internal state of the cube. This mostly for debugging.
     *
     * @param desc
     *            A description that is to precede the output
     */
    public void printPiecesTwists(final String desc) {
        if (desc != null) {
            System.out.println(desc + ":");
        }
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    int piece = pieces[x][y][z];
                    if (piece == P_NONE) {
                        continue;
                    }
                    final String pieceStr = pieceToStr.get(piece);
                    final int twist = twists[x][y][z];
                    final String line = String.format("%d %d %d %s%s %d", x, y, z,
                            pieceStr, pieceStr.length() == 2 ? " " : "", twist);
                    System.out.println(format.toUserColor(line));
                }
            }
        }
    }

    /**
     * Sets the this cube given a 3x3x3 array of cube stings.
     *
     * @param cubeStrs
     *            3x3x3 array of cube strings (strings with one character per
     *            sticker for that piece in XYZ order)
     * @throws CubearoniException
     */
    public void setCubeStrs(final String[][][] cubeStrs) throws CubearoniException {
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    final String pieceStr = cubeStrs[x][y][z];
                    String pieceStrStd = null; // Standard from, ready to
                                               // lookup.
                    final int pieceLen = pieceStr.length();
                    if (pieceLen < 2) {
                        pieces[x][y][z] = P_NONE;
                        continue;
                    }
                    int twist = 0;
                    int xSt = pieceStr.indexOf("L");
                    if (xSt == -1) {
                        xSt = pieceStr.indexOf("R");
                    }
                    int ySt = pieceStr.indexOf("D");
                    if (ySt == -1) {
                        ySt = pieceStr.indexOf("U");
                    }
                    int zSt = pieceStr.indexOf("B");
                    if (zSt == -1) {
                        zSt = pieceStr.indexOf("F");
                    }
                    if (pieceLen == 2) {
                        // edge
                        // Some variables used to calculate if each edge is
                        // flipped:
                        // *On - If the edge is on that side.
                        // *Si - Index of the side in pieceStr. -2 used to mean
                        // that the side in question is not faced by either
                        // sticker of the edge. -2 chosen to not match -1.
                        // *St - Index of the sticker in pieceStr.
                        // *Par - If the sticker is facing it's side.
                        // zFlip - Z is a special case. The edge is flipped.
                        final int NO_SIDE = -2;
                        boolean xPar = false;
                        boolean yPar = false;
                        boolean zParFlip = false;
                        if ((zSt == -1) || (z == 1)) {
                            // Edge does not contain a Z sticker, or is not on a
                            // Z side. Here we are only concerned with X and Y
                            // stickers and sides
                            boolean xOn = x != 1;
                            int xSi = (x == 1) ? NO_SIDE : 0;
                            xPar = xOn && (xSt == xSi);

                            boolean yOn = y != 1;
                            int ySi = (y == 1) ? NO_SIDE : ((z == 1) ? 1 : 0);
                            yPar = yOn && (ySt == ySi);
                        } else {
                            // Does contain a Z sticker and is on a Z side.
                            // Here we are only concerned the Z sticker and
                            // side with some additional flipping issues.

                            // Z is as special case. We want the same
                            // definition, but then flip the edge if it has been
                            // twisted an odd multiple of 90 degrees on the Z
                            // side.
                            boolean zOn = z != 1;
                            int zSi = (z == 1) ? NO_SIDE : 1;
                            boolean zFlip = !((xSt != -1) && (y == 1) || (ySt != -1)
                                    && (x == 1));
                            zParFlip = zOn && ((zSt == zSi) ^ zFlip);
                        }

                        if (xPar || yPar || zParFlip) {
                            // not flipped
                        } else {
                            // flipped
                            twist = 1;
                        }
                    } else {
                        // corner
                        if (zSt == -1) {
                            throw new CubearoniException("Corner piece \""
                                    + pieceStr
                                    + "\" does not contain Z-axis stickers");
                        }
                        twist = (zSt + 1) % 3;
                        if ((x + y + z) % 4 == 2) {
                            // twist is actually the opposite way.
                            twist = (3 - twist) % 3;
                        }
                    }
                    pieceStrStd = "" + (xSt == -1 ? "" : pieceStr.charAt(xSt))
                            + (ySt == -1 ? "" : pieceStr.charAt(ySt))
                            + (zSt == -1 ? "" : pieceStr.charAt(zSt));
                    Integer pieceNum = strToPiece.get(pieceStrStd);
                    if (pieceNum == null) {
                        throw new CubearoniException(
                                "Could not find piece string \"" + pieceStrStd
                                        + "\".");
                    }
                    pieces[x][y][z] = pieceNum;
                    twists[x][y][z] = twist;
                }
            }
        }
    }

    /**
     * Sets the error strings
     *
     * @param error
     *            the error
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Set the format that this cube uses.
     *
     * @param format
     *            format to set
     */
    public void setFormat(Format format) {
        this.format = format;
    }

    /**
     * Set the piece to the specified location to the specified value. Note that
     * this may produce an invalid cube. It's used by solvers that may only care
     * about a subset of the pieces.
     *
     * @param x
     *            X coordinate
     * @param y
     *            Y coordinate
     * @param z
     *            Z coordinate
     * @param piece
     *            piece number to set
     */
    public void setPiece(final int x, final int y, final int z, final int piece) {
        pieces[x][y][z] = piece;
    }

    /**
     * Set the twist to the specified location to the specified value. Note that
     * this may produce an invalid cube. It's used by solvers that may only care
     * about a subset of the pieces.
     *
     * @param x
     *            X coordinate
     * @param y
     *            Y coordinate
     * @param z
     *            Z coordinate
     * @param twist
     *            twist amount to set
     */
    public void setTwist(final int x, final int y, final int z, final int twist) {
        twists[x][y][z] = twist;
    }

    /**
     * More verbose logging.
     *
     * @param verbose
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /** Returns the cube in its configured textual representation. */
    @Override
    public String toString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        print(new PrintStream(baos));
        return baos.toString();
    }

    /**
     * Sets the twist such that the specified axis has the specified color.
     *
     * @param x
     *            X coordinate
     * @param y
     *            X coordinate
     * @param z
     *            X coordinate
     * @param axis
     *            The axis
     * @param color
     *            Internal sticker color
     * @throws CubearoniException
     */
    public void setAxis(final int x, final int y, final int z, final int axis, final char color)
            throws CubearoniException {
        // TODO: This could probably be done more efficiently.
        final boolean edge = (x == 1) || (y == 1) || (z == 1);
        for (int twist = 0; twist < (edge ? 2 : 3); twist++) {
            twists[x][y][z] = twist;
            if (getStickerColor(x, y, z, axis) == color) {
                return;
            }
        }
        throw new CubearoniException("Could not set the axis at " + x + ", " + y
                + ", " + z + ", to axis " + axis + " to color " + color);
    }

    /** Resolves an explicit axis or face into one or more rotation axes. */
    private int[] getRotateAxes(final Integer axis, final char intFace)
            throws CubearoniException {
        if (axis != null) {
            final int axisAbs = Math.abs(axis);
            if ((axisAbs != AXIS_X) && (axisAbs != AXIS_Y)
                    && (axisAbs != AXIS_Z)) {
                throw new CubearoniException("Unsupported axis " + axis + ".");
            }
            return new int[] { axis };
        }
        final int[] axes = faceAxisMap.get(intFace);
        if (axes == null) {
            throw new CubearoniException("Unsupported face \"" + intFace
                    + "\" for rotate/switchColor.");
        }
        return axes;
    }

    /**
     * Rotate the cube by axis, or when axis is null, rotate so intFace is up.
     *
     * @param axis
     *            Axis to rotate around; null means rotate based on intFace.
     * @param intFace
     *            Internal face used only when axis is null.
     * @param reverse
     *            Do the reverse (undo).
     * @param mvs
     *            Optional moves to adjust in place so they follow the rotation.
     * @throws CubearoniException
     */
    public void rotate(final Integer axis, final char intFace,
            final boolean reverse, final int[] mvs) throws CubearoniException {
        for (int axisToApply : getRotateAxes(axis, intFace)) {
            int axisSigned = axisToApply;
            if (reverse) {
                axisSigned = -axisSigned;
            }
            boolean negative = (axisSigned < 0);
            if (negative) {
                axisSigned = -axisSigned;
            }

            // Move the pieces.
            for (int mv : axisMoveMap.get(axisSigned)) {
                if (negative) {
                    // Assume that the moves do not interact, so it's possible
                    // to reverse the moves without reversing the order of the
                    // moves. Switching regular moves with prime moves is
                    // sufficient.
                    int offset = Util.getOffset(mv);
                    mv -= offset;
                    switch (offset) {
                    case OFF_NONE:
                        offset = OFF_P;
                        break;
                    case OFF_P:
                        offset = OFF_NONE;
                        break;
                    }
                    mv += offset;
                }
                move(mv);
            }

            // Move the moves.
            if (mvs != null) {
                for (int i = 0; i < mvs.length; i++) {
                    final int[][] pairs = axisMoveReplMap.get(axisSigned);
                    for (final int[] pair : pairs) {
                        final int offset = Util.getOffset(mvs[i]);
                        int side = mvs[i] - offset;
                        if (negative) {
                            if (side == pair[1]) {
                                side = pair[0];
                                mvs[i] = side + offset;
                                break;
                            }
                        } else {
                            if (side == pair[0]) {
                                side = pair[1];
                                mvs[i] = side + offset;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Switch colors based on rotate() with same axis/face semantics.
     *
     * @param axis
     *            Axis to rotate around; null means rotate based on intFace.
     * @param intFace
     *            Internal face used only when axis is null.
     * @param reverse
     *            Do the reverse (undo).
     * @throws CubearoniException
     */
    public void switchColor(final Integer axis, final char intFace,
            final boolean reverse) throws CubearoniException {
        // An inefficient but simple means of relating pieces with the old color
        // pattern to the new color pattern.
        final Cube rotated = new Cube();
        rotated.rotate(axis, intFace, reverse, null);

        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    if (pieces[x][y][z] == P_NONE) {
                        // All cubes have P_NONE in the same places, so any cube
                        // may be used to ignore invalid locations.
                        continue;
                    }

                    // Convert the piece to what it's location would be in a
                    // solved and then lookup ("l" prefix) the piece in the
                    // rotated cube.
                    int piece = pieces[x][y][z];
                    final int lx = piece / 100;
                    piece %= 100;
                    final int ly = piece / 10;
                    piece %= 10;
                    final int lz = piece;
                    piece = rotated.getPiece(lx, ly, lz);

                    // Set the replacement piece with the swapped colors.
                    pieces[x][y][z] = piece;

                    // Adjust the twist. Looking into the rotated cube gives us
                    // the difference twist between the piece in an original
                    // unsolved cube (twist zero everywhere) and the replacement
                    // piece. This twist is added to what the old piece had.
                    int twist = twists[x][y][z];
                    twist += rotated.getTwist(lx, ly, lz);
                    // Keep the twist in range.
                    final boolean edge = (x == 1) || (y == 1) || (z == 1);
                    if (edge) {
                        twist = (twist + 2) % 2;
                    } else {
                        twist = (twist + 3) % 3;
                    }
                    twists[x][y][z] = twist;
                }
            }
        }
    }

    /**
     * Rotate the cube while keeping color centers fixed.
     *
     * @param axis
     *            Axis to rotate around; null means rotate based on intFace.
     * @param intFace
     *            Internal face used only when axis is null.
     * @param reverse
     *            Do the reverse (undo).
     * @param mvs
     *            Optional moves to adjust in place so they follow the rotation.
     * @throws CubearoniException
     */
    public void rotateKeepColor(final Integer axis, final char intFace,
            final boolean reverse, final int[] mvs) throws CubearoniException {
        rotate(axis, intFace, reverse, mvs);
        switchColor(axis, intFace, !reverse);
    }

    /**
     * Backward-compatible wrapper around rotate().
     */
    public void rotateFaceUp(final char intFace, final boolean reverse,
            final int[] mvs) throws CubearoniException {
        rotate(null, intFace, reverse, mvs);
    }

    /**
     * Backward-compatible wrapper around switchColor().
     */
    public void switchColorFaceUp(final char intFace, final boolean reverse)
            throws CubearoniException {
        switchColor(null, intFace, reverse);
    }

    /**
     * Convert a side to the corresponding axis.
     *
     * @param side
     *            the side
     * @return the axis
     */
    public static int sideToAxis(final int side) {
        switch (side) {
        case M_R:
        case M_L:
            return AXIS_X;
        case M_U:
        case M_D:
            return AXIS_Y;
        case M_F:
        case M_B:
            return AXIS_Z;
        default:
            // should not happen
            return AXIS_NONE;
        }
    }

    /** Parses a cube from an unfolded or facelets input stream. */
    private void setCubearoniFile(final InputStream cfHand) throws CubearoniException {
        // Step 1: read the file into lines.
        final List<String> lines = new ArrayList<>();
        try (final BufferedReader br = new BufferedReader(
                new InputStreamReader(cfHand))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Treat tabs in cubearoni text as indentation whitespace.
                // Parsing uses fixed character offsets, so expand tabs first.
                line = line.replace("\t", "    ");
                lines.add(line);
            }
        } catch (final IOException e) {
            throw new CubearoniException("Unable to read cubearoni stream: " + e);
        }
        if (lines.isEmpty()) {
            throw new CubearoniException("The cube file is empty.");
        }

        if (lines.size() == 1) {
            setFaceletsLine(lines.get(0));
            return;
        }

        // Step 2 determine which of four back position layouts.
        final char backPos;
        final boolean wide;
        final boolean hasRowSeparators;
        switch (lines.size()) {
            case 11:
                // Wide left or right
                wide = true;
                hasRowSeparators = true;
                if (countLeadingWhitespace(lines.get(0)) > 4) {
                    backPos = Format.BACK_LEFT;
                } else {
                    backPos = Format.BACK_RIGHT;
                }
                break;
            case 9:
                // Wide left or right with no horizontal separator lines.
                wide = true;
                hasRowSeparators = false;
                if (countLeadingWhitespace(lines.get(0)) > 4) {
                    backPos = Format.BACK_LEFT;
                } else {
                    backPos = Format.BACK_RIGHT;
                }
                break;
            case 15:
                // Not wide top or bottom
                wide = false;
                hasRowSeparators = true;
                if (countLeadingWhitespace(lines.get(5)) > 0) {
                    backPos = Format.BACK_TOP;
                } else {
                    backPos = Format.BACK_BOTTOM;
                }
                break;
            case 12:
                // Not wide top or bottom with no horizontal separator lines.
                wide = false;
                hasRowSeparators = false;
                if (countLeadingWhitespace(lines.get(4)) > 0) {
                    backPos = Format.BACK_TOP;
                } else {
                    backPos = Format.BACK_BOTTOM;
                }
                break;
            default:
                // What is this? Error.
                throw new CubearoniException("Unrecognized cube format: found "
                        + lines.size() + " lines; expected 9, 11, 12, or 15 "
                        + "lines for an unfolded cube net.");
        }
        format.setBackPos(backPos);

        final boolean hasColSeparators = lines.stream().anyMatch(
                line -> line.indexOf('.') != -1);
        // Single-face rows can use arbitrary leading whitespace in user input.
        // Require consistency with line 1 and normalize to canonical spacing.
        final String expectedIndentPrefix = lines.get(0).replaceAll("^(\\s*).*$", "$1");
        final int canonicalIndentLen = (wide && (backPos == Format.BACK_LEFT))
                ? 2 * (hasColSeparators ? 4 : 3)
                : (hasColSeparators ? 4 : 3);
        final String normalizedIndentPrefix = " ".repeat(canonicalIndentLen);
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            final String trimmed = line.trim();
            if (trimmed.length() != 3) {
                continue;
            }
            if (!line.startsWith(expectedIndentPrefix)) {
                throw new CubearoniException("Expected line " + (i + 1)
                        + " to use the same indentation prefix as line 1.");
            }
            lines.set(i, normalizedIndentPrefix + trimmed);
        }
        final int rowStep = hasRowSeparators ? 4 : 3;
        final int colStep = hasColSeparators ? 4 : 3;

        // Determine the location of the center of the cross
        int crossRow = hasRowSeparators ? 5 : 4;
        int crossCol = hasColSeparators ? 5 : 4;
        if (backPos == Format.BACK_TOP) {
            crossRow += rowStep;
        } else if (backPos == Format.BACK_LEFT) {
            crossCol += colStep;
        }

        final String[][][] cubeStrs = new String[3][3][3];
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    cubeStrs[x][y][z] = "";
                }
            }
        }

        // All six middle stickers seen.
        final Set<Character> middleStickers = new HashSet<>();

        // Iterate through the sides in XYZ order.
        for (char face : "LRDUBF".toCharArray()) {
            // For non-back face there is a small offset center to center.
            int faceUpperLeftRow = crossRow - 1;
            int faceUpperLeftCol = crossCol - 1;
          switch (face) {
                case 'L':
                    faceUpperLeftCol -= colStep;
                    for (int row = 0; row < 3; row++) {
                        for (int col = 0; col < 3; col++) {
                            final char sticker = lines.get(faceUpperLeftRow + row).charAt(
                                    faceUpperLeftCol + col);
                            cubeStrs[0][2 - row][col] +=
                                    sticker;
                        }
                    }
                    break;
                case 'R':
                    faceUpperLeftCol += colStep;
                    for (int row = 0; row < 3; row++) {
                        for (int col = 0; col < 3; col++) {
                          cubeStrs[2][2 - row][2 - col] +=
                                  lines.get(faceUpperLeftRow + row).charAt(
                                          faceUpperLeftCol + col);
                        }
                    }
                    break;
                case 'D':
                    faceUpperLeftRow += rowStep;
                    for (int row = 0; row < 3; row++) {
                        for (int col = 0; col < 3; col++) {
                          cubeStrs[col][0][2 - row] +=
                                  lines.get(faceUpperLeftRow + row).charAt(
                                          faceUpperLeftCol + col);
                        }
                    }
                    break;
                case 'U':
                    faceUpperLeftRow -= rowStep;
                    for (int row = 0; row < 3; row++) {
                        for (int col = 0; col < 3; col++) {
                          cubeStrs[col][2][row] +=
                                  lines.get(faceUpperLeftRow + row).charAt(
                                          faceUpperLeftCol + col);
                        }
                    }
                    break;
                case 'F':
                    // F is always in the middle.
                    for (int row = 0; row < 3; row++) {
                        for (int col = 0; col < 3; col++) {
                          cubeStrs[col][2 - row][2] +=
                                  lines.get(faceUpperLeftRow + row).charAt(
                                          faceUpperLeftCol + col);
                        }
                    }
                    break;
                case 'B':
                    // Back is a special case. The offset is a function of the
                    // back position.
                    switch (backPos) {
                        case Format.BACK_BOTTOM:
                            faceUpperLeftRow += 2 * rowStep;
                            break;
                        case Format.BACK_TOP:
                            faceUpperLeftRow -= 2 * rowStep;
                            break;
                        case Format.BACK_RIGHT:
                            faceUpperLeftCol += 2 * colStep;
                            break;
                        case Format.BACK_LEFT:
                            faceUpperLeftCol -= 2 * colStep;
                            break;
                    }
                    for (int row = 0; row < 3; row++) {
                        for (int col = 0; col < 3; col++) {
                            final char sticker = lines.get(faceUpperLeftRow + row).charAt(
                                    faceUpperLeftCol + col);
                            if (wide)
                            {
                                cubeStrs[2 - col][2 - row][0] +=
                                        sticker;
                            } else {
                                cubeStrs[col][row][0] +=
                                        sticker;
                            }
                        }
                    }
                    break;
            }

            // Apply the external to internal mapping based on the center sticker.
            final char middleSticker = lines.get(faceUpperLeftRow + 1).
                    charAt(faceUpperLeftCol + 1);
            middleStickers.add(middleSticker);
            format.addColorMapping(middleSticker, face);
        }

        if (middleStickers.size() != 6) {
            throw new CubearoniException("Expected to see six different middle " +
                    "stickers, but saw: " + middleStickers);
        }

        // Convert to internal format.
        if (format.isDiff()) {
            for (int x = 0; x <= 2; x++) {
                for (int y = 0; y <= 2; y++) {
                    for (int z = 0; z <= 2; z++) {
                        cubeStrs[x][y][z] = format.toInt(cubeStrs[x][y][z]);
                    }
                }
            }
        }

        setCubeStrs(cubeStrs);
    }

    /** Parses a compact 54-character facelets representation. */
    private void setFaceletsLine(final String line) throws CubearoniException {
        final String facelets = line.trim();
        if (facelets.length() != 54) {
            throw new CubearoniException("Expected 54 facelets characters, but got "
                    + facelets.length() + ".");
        }

        final Set<Character> allowedStickers = new HashSet<>();
        for (final char face : "LRDUBF".toCharArray()) {
            allowedStickers.add(face);
        }

        final Set<Character> seenCenters = new HashSet<>();
        final StringBuilder faceletsOrder = new StringBuilder(6);
        for (int faceIdx = 0; faceIdx < 6; faceIdx++) {
            final char centerSticker = facelets.charAt(9 * faceIdx + 4);
            if (!allowedStickers.contains(centerSticker)) {
                throw new CubearoniException("Unexpected center sticker \""
                        + centerSticker + "\". Expected one of L,R,D,U,B,F.");
            }
            if (!seenCenters.add(centerSticker)) {
                throw new CubearoniException("Facelets string has duplicate center "
                        + "sticker \"" + centerSticker + "\".");
            }
            faceletsOrder.append(centerSticker);
        }
        if (seenCenters.size() != 6) {
            throw new CubearoniException("Expected six distinct center stickers.");
        }
        format.setFaceletsOrder(faceletsOrder.toString());

        for (int i = 0; i < facelets.length(); i++) {
            final char sticker = facelets.charAt(i);
            if (!allowedStickers.contains(sticker)) {
                throw new CubearoniException("Unexpected sticker \"" + sticker
                        + "\" at facelets index " + i + ".");
            }
        }

        final String[][][] cubeStrs = new String[3][3][3];
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    cubeStrs[x][y][z] = "";
                }
            }
        }

        final Map<Character, Integer> faceToStart = new HashMap<>();
        for (int faceIdx = 0; faceIdx < 6; faceIdx++) {
            final char centerSticker = facelets.charAt(9 * faceIdx + 4);
            faceToStart.put(centerSticker, 9 * faceIdx);
        }

        // Keep append order consistent with setCubearoniFile() to preserve twist parity.
        fillFaceletsFace(facelets, faceToStart.get('L'), (row, col, sticker) ->
                cubeStrs[0][2 - row][col] += sticker);
        fillFaceletsFace(facelets, faceToStart.get('R'), (row, col, sticker) ->
                cubeStrs[2][2 - row][2 - col] += sticker);
        fillFaceletsFace(facelets, faceToStart.get('D'), (row, col, sticker) ->
                cubeStrs[col][0][2 - row] += sticker);
        fillFaceletsFace(facelets, faceToStart.get('U'), (row, col, sticker) ->
                cubeStrs[col][2][row] += sticker);
        fillFaceletsFace(facelets, faceToStart.get('F'), (row, col, sticker) ->
                cubeStrs[col][2 - row][2] += sticker);
        fillFaceletsFace(facelets, faceToStart.get('B'), (row, col, sticker) ->
                cubeStrs[2 - col][2 - row][0] += sticker);

        if (format.isDiff()) {
            for (int x = 0; x <= 2; x++) {
                for (int y = 0; y <= 2; y++) {
                    for (int z = 0; z <= 2; z++) {
                        cubeStrs[x][y][z] = format.toInt(cubeStrs[x][y][z]);
                    }
                }
            }
        }

        setCubeStrs(cubeStrs);
    }

    /** Receives a sticker and its row and column within one face. */
    @FunctionalInterface
    private interface FaceletConsumer {
        /** Accepts one facelet from a face grid. */
        void accept(int row, int col, char sticker);
    }

    /** Visits all nine stickers belonging to one facelets face. */
    private static void fillFaceletsFace(final String facelets, final int faceStart,
            final FaceletConsumer consumer) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                final char sticker = facelets.charAt(faceStart + row * 3 + col);
                consumer.accept(row, col, sticker);
            }
        }
    }

    /** Counts leading whitespace characters in a line. */
    private static int countLeadingWhitespace(final String s) {
        int i = 0;
        while ((i < s.length()) && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }
}
