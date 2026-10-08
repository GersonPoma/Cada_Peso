package com.presupuesto.reporte.dto.response;

import java.time.YearMonth;

/**
 * Activos, pasivos y patrimonio al cierre de un mes, en milésimas. Los pasivos son las deudas con
 * el signo cambiado y {@code patrimonio = activos - pasivos}.
 */
public record MesPatrimonioResponse(String mes, long activos, long pasivos, long patrimonio) {

    public static MesPatrimonioResponse desde(YearMonth mes, long activos, long pasivos) {
        return new MesPatrimonioResponse(mes.toString(), activos, pasivos, activos - pasivos);
    }
}
