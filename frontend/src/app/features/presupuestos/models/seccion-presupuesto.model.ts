/**
 * Enlace del menú lateral de un presupuesto. Cada ruta hija de `presupuestos/:presupuestoId` lo
 * declara en `data.seccion` y el layout arma el menú a partir de esas rutas.
 */
export interface SeccionPresupuesto {
  etiqueta: string;
  /** Nombre del ícono de Material Symbols Outlined. */
  icono: string;
}
