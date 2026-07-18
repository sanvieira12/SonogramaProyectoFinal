package com.sonograma.service.importacion;

import java.io.IOException;

/** A workbook error with enough context for the user to correct the source file. */
public class ExcelImportException extends IOException {

    public ExcelImportException(int row, String column, String value, String explanation) {
        super("Fila Excel " + row + ", columna " + column + ", valor \"" + value
                + "\": " + explanation);
    }
}
