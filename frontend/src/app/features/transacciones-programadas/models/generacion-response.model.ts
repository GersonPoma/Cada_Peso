/** Respuesta de `POST .../transacciones-programadas/generar`. */
export interface GeneracionResponse {
  generadas: number;
  plantillasConError: number;
}
