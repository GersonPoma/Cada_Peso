package com.presupuesto.importacion.dto.request;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import java.util.Map;

/**
 * Mapeo del CSV y opciones de la importación. Llegan como texto del formulario multipart y se
 * validan aquí (no con Bean Validation ni con la conversión de Spring) para que un valor mal
 * escrito no responda 400 antes del 404 de presupuesto o cuenta. Los índices de columna son
 * enteros desde 0. Con {@code columnaMonto} el monto es una columna con signo; si no, se usan
 * {@code columnaDebito} y {@code columnaCredito}.
 */
public record ParametrosImportacion(
        Separador separador,
        boolean tieneEncabezado,
        int columnaFecha,
        FormatoFecha formatoFecha,
        Integer columnaMonto,
        Integer columnaDebito,
        Integer columnaCredito,
        SeparadorDecimal separadorDecimal,
        SeparadorMiles separadorMiles,
        int columnaDescripcion,
        Integer columnaMemo,
        boolean omitirInvalidas) {

    public boolean montoEnUnaColumna() {
        return columnaMonto != null;
    }

    /** Igual que el otro {@code de}, con los parámetros del formulario por nombre. */
    public static ParametrosImportacion de(Map<String, String> p) {
        return de(
                p.get("separador"),
                p.get("tieneEncabezado"),
                p.get("columnaFecha"),
                p.get("formatoFecha"),
                p.get("columnaMonto"),
                p.get("columnaDebito"),
                p.get("columnaCredito"),
                p.get("separadorDecimal"),
                p.get("separadorMiles"),
                p.get("columnaDescripcion"),
                p.get("columnaMemo"),
                p.get("omitirInvalidas"));
    }

    /** 400 {@code DATOS_INVALIDOS} si falta un obligatorio o algún valor no es válido. */
    public static ParametrosImportacion de(
            String separador,
            String tieneEncabezado,
            String columnaFecha,
            String formatoFecha,
            String columnaMonto,
            String columnaDebito,
            String columnaCredito,
            String separadorDecimal,
            String separadorMiles,
            String columnaDescripcion,
            String columnaMemo,
            String omitirInvalidas) {
        Integer monto = indiceOpcional("columnaMonto", columnaMonto);
        Integer debito = indiceOpcional("columnaDebito", columnaDebito);
        Integer credito = indiceOpcional("columnaCredito", columnaCredito);
        boolean dosColumnas = debito != null || credito != null;
        if (monto != null && dosColumnas) {
            throw invalido("Indica columnaMonto o bien columnaDebito y columnaCredito, no ambas");
        }
        if (monto == null && (debito == null || credito == null)) {
            throw invalido("Indica columnaMonto o bien columnaDebito y columnaCredito");
        }
        SeparadorDecimal decimal = SeparadorDecimal.valueOf(
                enumeracion("separadorDecimal", separadorDecimal, SeparadorDecimal.values()));
        SeparadorMiles miles = vacio(separadorMiles)
                ? SeparadorMiles.NINGUNO
                : SeparadorMiles.valueOf(
                        enumeracion("separadorMiles", separadorMiles, SeparadorMiles.values()));
        if (miles.name().equals(decimal.name())) {
            throw invalido("separadorMiles no puede ser igual a separadorDecimal");
        }
        FormatoFecha formato = FormatoFecha.desdeEtiqueta(
                        formatoFecha == null ? null : formatoFecha.strip())
                .orElseThrow(() -> invalido(
                        "formatoFecha debe ser yyyy-MM-dd, dd/MM/yyyy, MM/dd/yyyy o dd-MM-yyyy"));
        return new ParametrosImportacion(
                Separador.valueOf(enumeracion("separador", separador, Separador.values())),
                booleano("tieneEncabezado", tieneEncabezado),
                indiceObligatorio("columnaFecha", columnaFecha),
                formato,
                monto,
                debito,
                credito,
                decimal,
                miles,
                indiceObligatorio("columnaDescripcion", columnaDescripcion),
                indiceOpcional("columnaMemo", columnaMemo),
                booleano("omitirInvalidas", omitirInvalidas));
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static DatosInvalidosException invalido(String mensaje) {
        return new DatosInvalidosException(mensaje);
    }

    private static int indiceObligatorio(String nombre, String texto) {
        Integer valor = indiceOpcional(nombre, texto);
        if (valor == null) {
            throw invalido("Falta el parámetro " + nombre);
        }
        return valor;
    }

    private static Integer indiceOpcional(String nombre, String texto) {
        if (vacio(texto)) {
            return null;
        }
        try {
            int valor = Integer.parseInt(texto.strip());
            if (valor < 0) {
                throw invalido(nombre + " debe ser 0 o mayor");
            }
            return valor;
        } catch (NumberFormatException e) {
            throw invalido(nombre + " debe ser un número entero");
        }
    }

    /** {@code false} si no llega; cualquier cosa distinta de true/false es un 400. */
    private static boolean booleano(String nombre, String texto) {
        if (vacio(texto)) {
            return false;
        }
        String limpio = texto.strip();
        if (limpio.equalsIgnoreCase("true")) {
            return true;
        }
        if (limpio.equalsIgnoreCase("false")) {
            return false;
        }
        throw invalido(nombre + " debe ser true o false");
    }

    private static String enumeracion(String nombre, String texto, Enum<?>[] valores) {
        if (vacio(texto)) {
            throw invalido("Falta el parámetro " + nombre);
        }
        String limpio = texto.strip();
        for (Enum<?> valor : valores) {
            if (valor.name().equals(limpio)) {
                return limpio;
            }
        }
        throw invalido(nombre + " no tiene un valor admitido");
    }
}
