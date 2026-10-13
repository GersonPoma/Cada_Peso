/** Lo que esta feature necesita de una categoría (lectura de `GET /categorias`). */
export interface CategoriaResumen {
  id: number;
  nombre: string;
  oculta: boolean;
  /** Es la categoría de pago de una tarjeta; el backend la envía siempre (aditivo). */
  esPagoTarjeta?: boolean;
  /** La tarjeta de una categoría de pago; `null` en las demás. */
  cuentaId?: number | null;
}
