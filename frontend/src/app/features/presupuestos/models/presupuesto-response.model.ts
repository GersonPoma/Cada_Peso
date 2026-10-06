/** Respuesta de `/api/v1/presupuestos` (`PresupuestoResponse` del backend). */
export interface PresupuestoResponse {
  id: number;
  nombre: string;
  /** Código ISO 4217 (ej. `BOB`). */
  moneda: string;
  /** `Instant` del backend, ISO-8601 en UTC. */
  fechaCreacion: string;
  /** `Instant` del backend, ISO-8601 en UTC. */
  fechaActualizacion: string;
}
