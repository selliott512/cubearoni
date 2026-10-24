package org.selliott.cubearoni;

/**
 * General purpose exception for Cubearoni.
 */
@SuppressWarnings("serial")
public class CubearoniException extends Exception {
    public CubearoniException(final String message) {
        super(message);
    }
}
