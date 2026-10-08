package com.presupuesto.importacion.dto.request;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

/**
 * Formatos de fecha admitidos (lista cerrada). Se leen de forma estricta: una fecha inexistente o
 * con ceros de más o de menos que el formato no admite es inválida.
 */
public enum FormatoFecha {
    ISO("yyyy-MM-dd", "uuuu-MM-dd"),
    DIA_MES_ANIO("dd/MM/yyyy", "dd/MM/uuuu"),
    MES_DIA_ANIO("MM/dd/yyyy", "MM/dd/uuuu"),
    DIA_MES_ANIO_GUION("dd-MM-yyyy", "dd-MM-uuuu");

    private final String etiqueta;
    private final DateTimeFormatter formateador;

    FormatoFecha(String etiqueta, String patron) {
        this.etiqueta = etiqueta;
        // En modo estricto, "yyyy" (año de la era) exige la era: se usa "uuuu".
        this.formateador =
                DateTimeFormatter.ofPattern(patron).withResolverStyle(ResolverStyle.STRICT);
    }

    public static Optional<FormatoFecha> desdeEtiqueta(String texto) {
        for (FormatoFecha formato : values()) {
            if (formato.etiqueta.equals(texto)) {
                return Optional.of(formato);
            }
        }
        return Optional.empty();
    }

    /** La fecha, o vacío si el texto no cumple el formato. */
    public Optional<LocalDate> leer(String texto) {
        try {
            return Optional.of(LocalDate.parse(texto, formateador));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
