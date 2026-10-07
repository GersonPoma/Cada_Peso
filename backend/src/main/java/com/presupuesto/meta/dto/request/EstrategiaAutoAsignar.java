package com.presupuesto.meta.dto.request;

/** Cómo se calcula el nuevo asignado de cada categoría al auto-asignar. */
public enum EstrategiaAutoAsignar {
    /** Suma el faltante de la meta al asignado actual. */
    FALTANTE_META,
    /** Lo asignado el mes anterior. */
    ASIGNADO_MES_PASADO,
    /** Lo gastado el mes anterior: {@code max(0, -actividad)}. */
    GASTADO_MES_PASADO,
    /** Promedio de lo asignado en los 3 meses anteriores. */
    PROMEDIO_ASIGNADO,
    /** Promedio de lo gastado en los 3 meses anteriores. */
    PROMEDIO_GASTADO
}
