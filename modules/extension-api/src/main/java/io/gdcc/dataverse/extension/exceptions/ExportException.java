/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.gdcc.dataverse.extension.exceptions;

/**
 *
 * @author Leonid Andreev
 */
public class ExportException extends Exception {
    public ExportException(String message) {
        super(message);
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
