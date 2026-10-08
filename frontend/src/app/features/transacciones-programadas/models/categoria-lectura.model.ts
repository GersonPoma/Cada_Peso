/** Lo que esta feature necesita de una categoría (lectura de `GET /categorias`). */
export interface CategoriaLectura {
  id: number;
  nombre: string;
  oculta: boolean;
  /** Categoría de pago de una tarjeta: no admite programadas (422 en el backend). */
  esPagoTarjeta: boolean;
}
