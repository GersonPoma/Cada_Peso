/** Categoría (`CategoriaResponse` del backend). */
export interface CategoriaResponse {
  id: number;
  grupoId: number;
  nombre: string;
  /** Posición en su grupo, desde 0, contando también las ocultas. */
  orden: number;
  oculta: boolean;
  nota: string | null;
  /** `Instant` del backend, ISO-8601 en UTC. */
  fechaCreacion: string;
  /** `Instant` del backend, ISO-8601 en UTC. */
  fechaActualizacion: string;
}
