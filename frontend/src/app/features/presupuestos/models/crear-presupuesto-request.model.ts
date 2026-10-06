/** Cuerpo de `POST /api/v1/presupuestos`; sin `moneda`, el backend usa la del perfil. */
export interface CrearPresupuestoRequest {
  nombre: string;
  moneda?: string;
}
