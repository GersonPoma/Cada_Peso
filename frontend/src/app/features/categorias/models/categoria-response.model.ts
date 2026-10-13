/** Categoría (`CategoriaResponse` del backend). */
export interface CategoriaResponse {
  id: number;
  grupoId: number;
  nombre: string;
  /** Posición en su grupo, desde 0, contando también las ocultas. */
  orden: number;
  oculta: boolean;
  nota: string | null;
  /** Es la categoría de pago de una tarjeta; el backend la envía siempre (aditivo). */
  esPagoTarjeta?: boolean;
  /** La tarjeta de una categoría de pago; `null` en las demás. */
  cuentaId?: number | null;
  /** `Instant` del backend, ISO-8601 en UTC. */
  fechaCreacion: string;
  /** `Instant` del backend, ISO-8601 en UTC. */
  fechaActualizacion: string;
}
