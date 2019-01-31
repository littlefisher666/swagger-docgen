package com.github.litttlefisher.swagger.docgen.exception;

/**
 * @author littlefisher
 */
public class GenerateException extends RuntimeException {

    private static final long serialVersionUID = -1641016437077276797L;

    public GenerateException(String errorMessage, Throwable cause) {
        super(errorMessage, cause);
    }

    public GenerateException(String errorMessage) {
        super(errorMessage);
    }

    public GenerateException(Exception e) {
        super(e);
    }
}

