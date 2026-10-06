/** Cuerpo de `PUT /api/v1/presupuestos/{id}`: solo el nombre, la moneda no se edita. */
export interface ActualizarPresupuestoRequest {
  nombre: string;
}
