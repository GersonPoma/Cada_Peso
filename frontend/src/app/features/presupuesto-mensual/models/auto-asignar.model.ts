/** Cómo se calcula el nuevo asignado al auto-asignar (`EstrategiaAutoAsignar` del backend). */
export type EstrategiaAutoAsignar =
  | 'FALTANTE_META'
  | 'ASIGNADO_MES_PASADO'
  | 'GASTADO_MES_PASADO'
  | 'PROMEDIO_ASIGNADO'
  | 'PROMEDIO_GASTADO';

/**
 * Cuerpo de `POST /meses/{mes}/auto-asignar`. Sin `categoriaIds`, todas las visibles salvo las de
 * pago de tarjeta (una lista vacía responde 400); `simular: true` no guarda nada.
 */
export interface AutoAsignarRequest {
  estrategia: EstrategiaAutoAsignar;
  categoriaIds?: number[];
  simular: boolean;
}

/** El asignado de una categoría antes y después (`CambioAsignacionResponse`), en milésimas. */
export interface CambioAsignacionResponse {
  categoriaId: number;
  nombre: string;
  asignadoAntes: number;
  asignadoDespues: number;
}

/** Resultado de auto-asignar (`AutoAsignarResponse`); `aplicado` es `false` al simular. */
export interface AutoAsignarResponse {
  aplicado: boolean;
  listoParaAsignarAntes: number;
  listoParaAsignarDespues: number;
  cambios: CambioAsignacionResponse[];
}
