/** Grupo de categorías sin sus categorías (`GrupoCategoriaResponse` del backend). */
export interface GrupoCategoriaResponse {
  id: number;
  nombre: string;
  /** Posición entre los grupos del presupuesto, desde 0, contando también los ocultos. */
  orden: number;
  oculto: boolean;
  /** `Instant` del backend, ISO-8601 en UTC. */
  fechaCreacion: string;
  /** `Instant` del backend, ISO-8601 en UTC. */
  fechaActualizacion: string;
}
