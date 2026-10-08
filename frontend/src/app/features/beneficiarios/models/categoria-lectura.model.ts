/** Lo que esta feature necesita de una categoría (lectura de `GET /categorias`). */
export interface CategoriaLectura {
  id: number;
  nombre: string;
  oculta: boolean;
  /** Categoría de pago de una tarjeta: no puede ser predeterminada de un beneficiario. */
  esPagoTarjeta: boolean;
}
