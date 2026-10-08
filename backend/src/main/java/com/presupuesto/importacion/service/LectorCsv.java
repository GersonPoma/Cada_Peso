package com.presupuesto.importacion.service;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector de CSV mínimo: campos entre comillas dobles, comillas escapadas duplicándolas
 * ({@code ""}), separadores y saltos de línea ({@code LF}, {@code CRLF} o {@code CR}) dentro de
 * comillas. Las líneas completamente vacías se ignoran. Corta en cuanto supera el máximo de
 * registros para no materializar un archivo gigante.
 */
final class LectorCsv {

    static final String MENSAJE_COMILLA_SIN_CERRAR =
            "El archivo tiene una comilla sin cerrar";

    private LectorCsv() {}

    /**
     * @param maxRegistros máximo de registros (incluido el encabezado, si lo hay); pasarlo es un
     *     400
     */
    static List<List<String>> leer(String texto, char separador, int maxRegistros) {
        List<List<String>> registros = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        boolean campoComillado = false;
        boolean conContenido = false;
        int n = texto.length();
        for (int i = 0; i < n; i++) {
            char c = texto.charAt(i);
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < n && texto.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                    }
                } else {
                    campo.append(c);
                }
            } else if (c == '"' && campo.length() == 0 && !campoComillado) {
                entreComillas = true;
                campoComillado = true;
                conContenido = true;
            } else if (c == separador) {
                campos.add(campo.toString());
                campo.setLength(0);
                campoComillado = false;
                conContenido = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < n && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                if (conContenido) {
                    campos.add(campo.toString());
                    agregar(registros, campos, maxRegistros);
                    campos = new ArrayList<>();
                }
                campo.setLength(0);
                campoComillado = false;
                conContenido = false;
            } else {
                campo.append(c);
                conContenido = true;
            }
        }
        if (entreComillas) {
            throw new DatosInvalidosException(MENSAJE_COMILLA_SIN_CERRAR);
        }
        if (conContenido) {
            campos.add(campo.toString());
            agregar(registros, campos, maxRegistros);
        }
        return registros;
    }

    private static void agregar(
            List<List<String>> registros, List<String> campos, int maxRegistros) {
        registros.add(campos);
        if (registros.size() > maxRegistros) {
            throw new DatosInvalidosException(ImportacionService.MENSAJE_DEMASIADAS_FILAS);
        }
    }
}
