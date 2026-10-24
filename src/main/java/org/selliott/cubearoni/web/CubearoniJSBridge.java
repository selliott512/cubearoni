package org.selliott.cubearoni.web;

import org.selliott.cubearoni.Cube;
import org.selliott.cubearoni.CFOP;
import org.selliott.cubearoni.CubearoniException;
import org.selliott.cubearoni.Solver;
import org.selliott.cubearoni.Util;
import java.util.HashSet;
import java.util.Set;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSExceptions;
import org.teavm.jso.core.JSError;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.json.JSON;
import org.teavm.jso.typedarrays.Uint8Array;

/**
 * Minimal JS-facing bridge for Cubearoni so that Cubearoni may be invoked from JavaScript
 * using TeaVM. Methods that are suffixed with "JSON" return a JSON object
 * that indicates success or failure with a some subset of the following fields
 *   ok       boolean, true if the operation succeeded, false if it failed.
 *   result   string, a result value if the operation succeeded and has a result.
 *   error    string, an error message if the operation failed.
 *   stack    string, a stack trace if the operation failed and a stack trace is available.
 * Check "ok" before accessing any other fields.
 */
public final class CubearoniJSBridge {
    private static DBFileLoader dbFileLoader;

    private CubearoniJSBridge() {
        // Nothing to construct.
    }

    // Public exported methods.

    /** JavaScript callback used to load a DB file by basename. */
    @JSFunctor
    public interface DBFileLoader extends JSObject {
        JSPromise<Uint8Array> load(String fileName);
    }

    @JSExport
    public static void setDBFileLoader(final DBFileLoader loader) {
        dbFileLoader = loader;
        Util.setMissingFileLoader((loader == null) ? null
                : CubearoniJSBridge::loadDBFile);
    }

    @JSExport
    public static void clearDBFiles() {
        Util.clearInMemoryFiles();
    }

    @JSExport
    public static JSPromise<JSObject> solveJSON(final String cubeText,
                                     final String solverName,
                                     final String userFace, final String stopAfterPhase,
                                     final int limit, final int extMovesMode,
                                     final boolean markPhaseStarts,
                                     final String moves,
                                     final boolean leftHanded,
                                     final String[] options) {
        return JSPromise.callAsync(() -> solveJSONImpl(cubeText, solverName,
                userFace, stopAfterPhase, limit, extMovesMode, markPhaseStarts,
                moves, leftHanded, options));
    }

    private static JSObject solveJSONImpl(final String cubeText,
                                          final String solverName,
                                          final String userFace,
                                          final String stopAfterPhase,
                                          final int limit,
                                          final int extMovesMode,
                                          final boolean markPhaseStarts,
                                          final String moves,
                                          final boolean leftHanded,
                                          final String[] options) {
        try {
            final String solvedMoves = solve(cubeText, solverName, userFace,
                    stopAfterPhase, limit, extMovesMode, markPhaseStarts,
                    moves, leftHanded, options);
            return resultOkResultJSON(solvedMoves);
        } catch (final Throwable t) {
            return resultErrorJSON((t.getMessage() != null) ? t.getMessage() : t.toString(),
                    getStackTraceString(t));
        }
    }

    @JSExport
    public static JSObject movesToFaceletsJSON(final String moves,
                                               final String faceletsOrder) {
        try {
            final String facelets = movesToFacelets(moves, faceletsOrder);
            return resultOkResultJSON(facelets);
        } catch (final Throwable t) {
            return resultErrorJSON((t.getMessage() != null) ? t.getMessage() : t.toString(),
                    getStackTraceString(t));
        }
    }

    @JSExport
    public static JSPromise<JSObject> getCFOPMovesForNameJSON(
                                                   final String solverName,
                                                   final String name,
                                                   final boolean leftHanded) {
        return JSPromise.callAsync(() -> getCFOPMovesForNameJSONImpl(
                solverName, name, leftHanded));
    }

    private static JSObject getCFOPMovesForNameJSONImpl(
                                                   final String solverName,
                                                   final String name,
                                                   final boolean leftHanded) {
        try {
            final String moves = getCFOPMovesForName(solverName, name,
                    leftHanded);
            return resultOkResultJSON(moves);
        } catch (final Throwable t) {
            return resultErrorJSON((t.getMessage() != null) ? t.getMessage() : t.toString(),
                    getStackTraceString(t));
        }
    }

    @JSExport
    public static int getExtMovesModeNone() {
        return Util.EXT_TO_HTM_MODE_NONE;
    }

    @JSExport
    public static int getExtMovesModePrefix() {
        return Util.EXT_TO_HTM_MODE_PREFIX;
    }

    @JSExport
    public static int getExtMovesModeApplied() {
        return Util.EXT_TO_HTM_MODE_APPLIED;
    }

    // Private methods.

    private static byte[] loadDBFile(final String fileName)
            throws CubearoniException {
        if (dbFileLoader == null) {
            throw new CubearoniException("No DB file loader is configured.");
        }
        final JSPromise<Uint8Array> promise = dbFileLoader.load(fileName);
        if (promise == null) {
            throw new CubearoniException("DB file loader returned no promise for \""
                    + fileName + "\".");
        }
        final Uint8Array contents;
        try {
            contents = promise.await();
        } catch (final Throwable t) {
            throw new CubearoniException("DB file loader failed for \"" + fileName
                    + "\": " + getThrowableMessage(t));
        }
        if (contents == null) {
            throw new CubearoniException("DB file loader returned null for \""
                    + fileName + "\".");
        }
        final byte[] bytes = new byte[contents.getLength()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (contents.get(i) & 0xff);
        }
        return bytes;
    }

    private static String getThrowableMessage(final Throwable t) {
        if ((t.getMessage() != null) && !t.getMessage().isEmpty()) {
            return t.getMessage();
        }
        try {
            final JSObject jsObj = JSExceptions.getJSException(t);
            if ((jsObj != null) && JSError.isError(jsObj)) {
                final String message = ((JSError) jsObj).getMessage();
                if ((message != null) && !message.isEmpty()) {
                    return message;
                }
            }
        } catch (final Throwable ignored) {
            // Fall through to the Java class name.
        }
        return t.getClass().getName();
    }

    private static String escapeJSON(final String s) {
        if (s == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    private static String getStackTraceString(final Throwable t) {
        // Try Java stack trace first.
        try {
            final java.io.StringWriter sw = new java.io.StringWriter();
            final java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            final String javaStack = sw.toString();
            if ((javaStack != null) && !javaStack.isEmpty()
                    && (javaStack.contains("\tat ") || javaStack.contains("\n at "))) {
                return javaStack;
            }
        } catch (final Throwable ignored) {
            // Fall through and try JS stack extraction below.
        }

        // Fallback for TeaVM: extract the underlying JS Error stack.
        try {
            final JSObject jsObj = JSExceptions.getJSException(t);
            if ((jsObj != null) && JSError.isError(jsObj)) {
                final JSError jsError = (JSError) jsObj;
                final String jsStack = jsError.getStack();
                if ((jsStack != null) && !jsStack.isEmpty()) {
                    return jsStack;
                }
                final String jsMessage = jsError.getMessage();
                if ((jsMessage != null) && !jsMessage.isEmpty()) {
                    return jsMessage;
                }
            }
        } catch (final Throwable ignored) {
            // No stack info available.
        }

        return null;
    }

    private static JSObject resultErrorJSON(final String message, final String stack) {
        final StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":false,\"error\":\"").append(escapeJSON(message))
                .append("\"");
        if ((stack != null) && !stack.isEmpty()) {
            sb.append(",\"stack\":\"").append(escapeJSON(stack)).append("\"");
        }
        sb.append("}");
        return JSON.parse(sb.toString());
    }

    private static JSObject resultOkResultJSON(final String moves) {
        return JSON.parse("{\"ok\":true,\"result\":\"" + escapeJSON(moves) + "\"}");
    }

    private static String solve(final String cubeText, final String solverName,
                                final String userFace, final String stopAfterPhase,
                                final int limit, final int extMovesMode,
                                final boolean markPhaseStarts,
                                final String moves,
                                final boolean leftHanded,
                                final String[] options)
            throws CubearoniException {
        if ((solverName == null) || solverName.isEmpty()) {
            throw new CubearoniException("solverName must not be empty.");
        }

        final Cube cube = ((cubeText != null) && !cubeText.isEmpty())
                ? new Cube(null, cubeText, null)
                : new Cube();
        if ((moves != null) && !moves.isEmpty()) {
            cube.extMoves(moves, extMovesMode);
        }
        final Solver solver = Solver.getSolver(solverName, false);
        solver.setOptions(parseOptions(options));

        // Reset mutable per-call settings because solver instances are shared.
        solver.setVerbose(false);
        solver.setLimit(Integer.MAX_VALUE - 1);
        solver.setUserFace((char) 0);
        solver.setStopAfterPhase("PLL");
        solver.setLeftHanded(false);

        if (limit > 0) {
            solver.setLimit(limit);
        }
        if ((userFace != null) && !userFace.isEmpty()) {
            solver.setUserFace(userFace.charAt(0));
        }
        if ((stopAfterPhase != null) && !stopAfterPhase.isEmpty()) {
            solver.setStopAfterPhase(stopAfterPhase.toUpperCase());
        }
        if (leftHanded) {
            solver.setLeftHanded(true);
        }

        solver.loadDB();
        if (markPhaseStarts && (solver instanceof CFOP)) {
            return ((CFOP) solver).execMarked(cube);
        }
        final int[] solvedMoves = solver.exec(cube);
        return Cube.extMovesToStr(solvedMoves);
    }

    private static String getCFOPMovesForName(final String solverName,
                                              final String name,
                                              final boolean leftHanded)
            throws CubearoniException {
        if ((solverName == null) || solverName.isEmpty()) {
            throw new CubearoniException("solverName must not be empty.");
        }
        if ((name == null) || name.isEmpty()) {
            throw new CubearoniException("name must not be empty.");
        }
        final Solver solver = Solver.getSolver(solverName, false);
        if (!(solver instanceof CFOP)) {
            throw new CubearoniException("Solver \"" + solverName
                    + "\" is not a CFOP solver.");
        }

        // Reset mutable per-call settings because solver instances are shared.
        solver.setVerbose(false);
        solver.setLeftHanded(leftHanded);
        solver.loadDB();
        return ((CFOP) solver).getMovesForName(name);
    }

    private static java.util.Map<String, String> parseOptions(final String[] options)
            throws CubearoniException {
        final java.util.Map<String, String> map = new java.util.HashMap<String, String>();
        if (options == null) {
            return map;
        }
        for (int i = 0; i < options.length; i++) {
            final String entry = options[i];
            if (entry == null) {
                continue;
            }
            final String s = entry.trim();
            if (s.isEmpty()) {
                continue;
            }
            final int eq = s.indexOf('=');
            if ((eq <= 0) || (eq >= (s.length() - 1))) {
                throw new CubearoniException("Invalid option entry \"" + s
                        + "\". Expected key=value.");
            }
            final String key = s.substring(0, eq);
            final String value = s.substring(eq + 1);
            map.put(key, value);
        }
        return map;
    }

    private static String movesToFacelets(final String moves,
                                          final String faceletsOrder)
            throws CubearoniException {
        if ((faceletsOrder == null) || faceletsOrder.isEmpty()) {
            throw new CubearoniException("faceletsOrder must not be empty.");
        }
        final String order = parseFaceletsOrder(faceletsOrder);
        final Cube cube = new Cube();
        if ((moves != null) && !moves.isEmpty()) {
            cube.extMoves(moves, Util.EXT_TO_HTM_MODE_NONE);
        }
        cube.getFormat().setFaceletsOrder(order);
        return cube.toString().trim();
    }

    private static String parseFaceletsOrder(final String orderArg)
            throws CubearoniException {
        final String order = orderArg.toUpperCase();
        if (order.length() != 6) {
            throw new CubearoniException("faceletsOrder must be exactly 6 characters.");
        }
        final Set<Character> seen = new HashSet<>();
        for (int i = 0; i < order.length(); i++) {
            final char c = order.charAt(i);
            if ("LRDUBF".indexOf(c) == -1) {
                throw new CubearoniException("faceletsOrder contains unsupported face \""
                        + c + "\". Expected only L,R,D,U,B,F.");
            }
            if (!seen.add(c)) {
                throw new CubearoniException("faceletsOrder contains duplicate face \""
                        + c + "\".");
            }
        }
        return order;
    }
}
