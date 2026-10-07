package com.presupuesto.meta.entity;

/** Qué quiere lograr la meta de una categoría. */
public enum TipoMeta {
    /** Asignar {@code monto} en cada vencimiento del mes (semanal, mensual o personalizado). */
    MONTO_MENSUAL,
    /** Juntar {@code monto} para la fecha objetivo, repartido en los meses que quedan. */
    MONTO_PARA_FECHA,
    /** Tener un saldo de {@code monto} en la categoría. */
    SALDO_OBJETIVO
}
