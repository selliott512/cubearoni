package org.selliott.cubearoni;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles keeping track of and translating to and from the format apparent in
 * the cubearoni file read.
 */
public class Format {
    public static final char BACK_BOTTOM = 'b';
    public static final char BACK_RIGHT = 'r';
    public static final char BACK_TOP = 't';
    public static final char BACK_LEFT = 'l';

    // TODO: Build this dynamically depending on the terminal.
    public static final String FG_BLACK = "\u001b[39m";
    public static final String FG_BLUE = "\u001b[34m";
    public static final String FG_GREEN = "\u001b[32m";
    public static final String FG_ORANGE = "\u001b[35m";
    public static final String FG_RED = "\u001b[31m";
    public static final String FG_WHITE = "\u001b[39m";
    public static final String FG_YELLOW = "\u001b[33m";

    static final Map<Character, String> colorEscs = new HashMap<Character, String>();
    static {
        // Colorize canonical face labels using the project's fixed scheme.
        colorEscs.put('L', FG_BLUE);
        colorEscs.put('R', FG_RED);
        colorEscs.put('U', FG_YELLOW);
        colorEscs.put('D', FG_WHITE);
        colorEscs.put('F', FG_GREEN);
        colorEscs.put('B', FG_ORANGE);
    }

    /**
     * Adds color escape codes to a string
     *
     * @param str
     *            string to be colorized
     * @return the string colorized
     */
    static String colorize(final String str) {
        final StringBuilder sb = new StringBuilder();
        final char[] chars = str.toCharArray();
        for (final char c : chars) {
            final String esc = colorEscs.get(c);
            if (esc != null) {
                sb.append(esc + c + FG_BLACK);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    boolean color;

    // Map to and from user colors to internal colors.
    private final Map<Character, Character> intToUserColorMap = new HashMap<Character, Character>();

    private final Map<Character, Character> userToIntColorMap = new HashMap<Character, Character>();

    // If true then the file read is long mode (bottom layer right most).
    private boolean wide;

    // Preferred text-net position for the back face.
    private char backPos = BACK_BOTTOM;

    // Optional facelets output order. Null means use text-net output.
    private String faceletsOrder;

    public void addColorMapping(final char user, final char internal) {
        if (user == internal) {
            return;
        }
        intToUserColorMap.put(internal, user);
        userToIntColorMap.put(user, internal);
    }

    public boolean isColor() {
        return color;
    }

    /**
     * True if there colors are being translated (internal and user colors are
     * different).
     *
     * @return if translated
     */
    public boolean isDiff() {
        return intToUserColorMap.size() > 0;
    }

    public boolean isWide() {
        return (backPos == BACK_RIGHT) || (backPos == BACK_LEFT) || wide;
    }

    public void setColor(final boolean color) {
        this.color = color;
    }

    public char getBackPos() {
        return backPos;
    }

    public void setBackPos(final char backPos) {
        switch (backPos) {
        case BACK_BOTTOM:
        case BACK_RIGHT:
        case BACK_TOP:
        case BACK_LEFT:
            this.backPos = backPos;
            this.wide = (backPos == BACK_RIGHT) || (backPos == BACK_LEFT);
            break;
        default:
            throw new IllegalArgumentException("Unsupported back position: "
                    + backPos);
        }
    }

    public String getFaceletsOrder() {
        return faceletsOrder;
    }

    public void setFaceletsOrder(final String faceletsOrder) {
        this.faceletsOrder = faceletsOrder;
    }

    /**
     * Return the internal color given a user color. Assume missing colors are
     * standard.
     *
     * @param user
     *            user color
     * @return internal color
     */
    public char toInt(final char user) {
        final Character internal = userToIntColorMap.get(user);
        return (internal != null) ? internal : user;
    }

    /**
     * Return the internal color given a user color. Assume missing colors are
     * standard.
     *
     * @param user
     *            user color
     * @return internal color
     */
    public String toInt(final String user) {
        final StringBuilder sb = new StringBuilder(user.length());
        for (final char i : user.toCharArray()) {
            sb.append(toInt(i));
        }
        return sb.toString();
    }

    /**
     * Return the user internal color given an internal color. Assume missing
     * colors are standard.
     *
     * @param internal
     *            internal color
     * @return user color
     */
    public char toUser(final char internal) {
        final Character user = intToUserColorMap.get(internal);
        return (user != null) ? user : internal;
    }

    /**
     * Return the user internal color given an internal color. Assume missing
     * colors are standard.
     *
     * @param internal
     *            internal color
     * @return user color
     */
    public String toUser(final String internal) {
        final StringBuilder sb = new StringBuilder(internal.length());
        for (final char i : internal.toCharArray()) {
            sb.append(toUser(i));
        }
        return sb.toString();
    }

    /**
     * Convert to user form and colorize if that was selected.
     *
     * @param internal
     *            internal color
     * @return user color
     */
    public String toUserColor(final char internal) {
        return color ? colorize(toUser("" + internal)) : toUser("" + internal);
    }

    /**
     * Convert to user form and colorize if that was selected.
     *
     * @param str
     *            internal color
     * @return user color
     */
    public String toUserColor(final String str) {
        return color ? colorize(toUser(str)) : toUser(str);
    }
}
