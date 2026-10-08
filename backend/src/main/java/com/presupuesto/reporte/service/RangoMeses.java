package com.presupuesto.reporte.service;

import com.presupuesto.asignacion.service.MesParametro;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Rango de meses de un reporte, ambos extremos inclusive. */
record RangoMeses(YearMonth desde, YearMonth hasta) {

    static final String MENSAJE_DESDE_REQUERIDO = "El mes de inicio (desde) es obligatorio";
    static final String MENSAJE_HASTA_REQUERIDO = "El mes de fin (hasta) es obligatorio";
    static final String MENSAJE_RANGO_INVERTIDO =
            "El mes de inicio no puede ser posterior al mes de fin";
    static final String MENSAJE_RANGO_LARGO = "El rango supera el máximo de meses permitido";

    /**
     * @param hastaOpcional si es {@code true}, un {@code hasta} ausente significa el mismo mes
     *     que {@code desde}
     */
    static RangoMeses interpretar(
            String desdeTexto, String hastaTexto, int maximo, boolean hastaOpcional) {
        if (desdeTexto == null || desdeTexto.isBlank()) {
            throw new DatosInvalidosException(MENSAJE_DESDE_REQUERIDO);
        }
        YearMonth desde = MesParametro.interpretar(desdeTexto);
        YearMonth hasta;
        if (hastaTexto == null || hastaTexto.isBlank()) {
            if (!hastaOpcional) {
                throw new DatosInvalidosException(MENSAJE_HASTA_REQUERIDO);
            }
            hasta = desde;
        } else {
            hasta = MesParametro.interpretar(hastaTexto);
        }
        if (desde.isAfter(hasta)) {
            throw new DatosInvalidosException(MENSAJE_RANGO_INVERTIDO);
        }
        if (ChronoUnit.MONTHS.between(desde, hasta) + 1 > maximo) {
            throw new DatosInvalidosException(MENSAJE_RANGO_LARGO);
        }
        return new RangoMeses(desde, hasta);
    }

    /** Los meses del rango en orden. */
    List<YearMonth> meses() {
        List<YearMonth> meses = new ArrayList<>();
        for (YearMonth mes = desde; !mes.isAfter(hasta); mes = mes.plusMonths(1)) {
            meses.add(mes);
        }
        return meses;
    }
}
