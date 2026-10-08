package com.presupuesto.importacion.service;

import com.presupuesto.importacion.dto.request.ParametrosImportacion;
import com.presupuesto.importacion.dto.request.SeparadorDecimal;
import com.presupuesto.importacion.dto.request.SeparadorMiles;
import java.time.LocalDate;
import java.util.List;

/**
 * Interpreta una fila del CSV con el mapeo recibido. Los motivos de invalidez son textos fijos:
 * nunca incluyen el contenido de la celda.
 */
final class InterpreteFilas {

    static final int MAXIMO_BENEFICIARIO = 100;
    static final int MAXIMO_MEMO = 500;

    private static final int DECIMALES = 3;

    private InterpreteFilas() {}

    /** Motivo de invalidez sin traza: es un flujo de control, no un error del programa. */
    private static final class FilaInvalida extends RuntimeException {

        FilaInvalida(String motivo) {
            super(motivo, null, false, false);
        }
    }

    static FilaInterpretada interpretar(
            int fila, List<String> campos, ParametrosImportacion parametros) {
        try {
            exigirColumnas(campos, parametros);
            LocalDate fecha = parametros.formatoFecha()
                    .leer(campos.get(parametros.columnaFecha()).strip())
                    .orElseThrow(() -> new FilaInvalida("La fecha no es válida"));
            long monto = leerMontoDeLaFila(campos, parametros);
            String beneficiario = texto(
                    campos.get(parametros.columnaDescripcion()), MAXIMO_BENEFICIARIO);
            String memo = parametros.columnaMemo() == null
                    ? null
                    : texto(campos.get(parametros.columnaMemo()), MAXIMO_MEMO);
            return new FilaInterpretada(fila, fecha, monto, beneficiario, memo, null);
        } catch (FilaInvalida e) {
            return new FilaInterpretada(fila, null, null, null, null, e.getMessage());
        }
    }

    private static void exigirColumnas(List<String> campos, ParametrosImportacion p) {
        exigir(campos, p.columnaFecha());
        exigir(campos, p.columnaDescripcion());
        if (p.montoEnUnaColumna()) {
            exigir(campos, p.columnaMonto());
        } else {
            exigir(campos, p.columnaDebito());
            exigir(campos, p.columnaCredito());
        }
        if (p.columnaMemo() != null) {
            exigir(campos, p.columnaMemo());
        }
    }

    private static void exigir(List<String> campos, int indice) {
        if (indice >= campos.size()) {
            throw new FilaInvalida("Falta la columna con índice " + indice);
        }
    }

    private static long leerMontoDeLaFila(List<String> campos, ParametrosImportacion p) {
        long monto;
        if (p.montoEnUnaColumna()) {
            monto = leerMonto(
                    campos.get(p.columnaMonto()), p.separadorDecimal(), p.separadorMiles());
        } else {
            long debito = Math.abs(leerMontoOpcional(
                    campos.get(p.columnaDebito()), p.separadorDecimal(), p.separadorMiles()));
            long credito = Math.abs(leerMontoOpcional(
                    campos.get(p.columnaCredito()), p.separadorDecimal(), p.separadorMiles()));
            if (debito != 0 && credito != 0) {
                throw new FilaInvalida("La fila tiene débito y crédito a la vez");
            }
            monto = credito - debito;
        }
        if (monto == 0) {
            throw new FilaInvalida("El monto no puede ser 0");
        }
        return monto;
    }

    /** Celda vacía = 0 (una de las dos columnas de débito y crédito). */
    private static long leerMontoOpcional(
            String celda, SeparadorDecimal decimal, SeparadorMiles miles) {
        return celda.isBlank() ? 0L : leerMonto(celda, decimal, miles);
    }

    /**
     * Texto de un monto a milésimas, sin coma flotante: signo opcional, dígitos, un separador
     * decimal con hasta 3 decimales y, en la parte entera, el separador de miles.
     */
    static long leerMonto(String texto, SeparadorDecimal decimal, SeparadorMiles miles) {
        String limpio = texto.strip();
        boolean negativo = false;
        if (!limpio.isEmpty() && (limpio.charAt(0) == '+' || limpio.charAt(0) == '-')) {
            negativo = limpio.charAt(0) == '-';
            limpio = limpio.substring(1);
        }
        int posicionDecimal = limpio.indexOf(decimal.caracter());
        if (posicionDecimal >= 0 && limpio.indexOf(decimal.caracter(), posicionDecimal + 1) >= 0) {
            throw montoInvalido();
        }
        String parteEntera = posicionDecimal < 0 ? limpio : limpio.substring(0, posicionDecimal);
        String parteDecimal = posicionDecimal < 0 ? "" : limpio.substring(posicionDecimal + 1);
        parteEntera = quitarMiles(parteEntera, miles);
        if ((parteEntera.isEmpty() && parteDecimal.isEmpty())
                || !soloDigitos(parteEntera)
                || !soloDigitos(parteDecimal)
                || parteDecimal.length() > DECIMALES) {
            throw montoInvalido();
        }
        try {
            long enteros = parteEntera.isEmpty() ? 0L : Long.parseLong(parteEntera);
            long decimales = parteDecimal.isEmpty()
                    ? 0L
                    : Long.parseLong((parteDecimal + "00").substring(0, DECIMALES));
            long valor = Math.addExact(Math.multiplyExact(enteros, 1000L), decimales);
            return negativo ? -valor : valor;
        } catch (ArithmeticException | NumberFormatException e) {
            throw new FilaInvalida("El monto está fuera de rango");
        }
    }

    private static String quitarMiles(String parteEntera, SeparadorMiles miles) {
        StringBuilder sinMiles = new StringBuilder(parteEntera.length());
        for (int i = 0; i < parteEntera.length(); i++) {
            char c = parteEntera.charAt(i);
            if (!miles.es(c)) {
                sinMiles.append(c);
            }
        }
        return sinMiles.toString();
    }

    private static boolean soloDigitos(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static FilaInvalida montoInvalido() {
        return new FilaInvalida("El monto no es válido");
    }

    /**
     * Recorta, deja en {@code null} si queda vacío y trunca por puntos de código (sin partir un
     * par sustituto); tras truncar vuelve a recortar.
     */
    static String texto(String crudo, int maximo) {
        String recortado = crudo.strip();
        if (recortado.codePointCount(0, recortado.length()) > maximo) {
            recortado = recortado.substring(0, recortado.offsetByCodePoints(0, maximo)).strip();
        }
        return recortado.isEmpty() ? null : recortado;
    }
}
