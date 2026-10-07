package com.presupuesto.asignacion.service;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import java.time.YearMonth;
import java.util.regex.Pattern;

/** Interpreta el {@code mes} de la URL: {@code yyyy-MM} con año entre 2000 y 2100. */
public final class MesParametro {

    public static final int ANIO_MINIMO = 2000;
    public static final int ANIO_MAXIMO = 2100;
    static final String MENSAJE_MES_INVALIDO =
            "El mes debe tener el formato yyyy-MM, con año entre 2000 y 2100";

    private static final Pattern FORMATO = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private MesParametro() {}

    public static YearMonth interpretar(String texto) {
        if (texto == null || !FORMATO.matcher(texto).matches()) {
            throw new DatosInvalidosException(MENSAJE_MES_INVALIDO);
        }
        YearMonth mes = YearMonth.parse(texto);
        if (mes.getYear() < ANIO_MINIMO || mes.getYear() > ANIO_MAXIMO) {
            throw new DatosInvalidosException(MENSAJE_MES_INVALIDO);
        }
        return mes;
    }
}
