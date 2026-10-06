/** Datos con los que se abre el diálogo de presupuesto: crear uno nuevo o renombrar uno. */
export type DatosDialogoPresupuesto =
  { modo: 'crear' } | { modo: 'renombrar'; presupuesto: { id: number; nombre: string } };
