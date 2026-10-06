/** Cuerpo de `POST /categorias`. Una nota vacía se envía como `null`. */
export interface CrearCategoriaRequest {
  grupoId: number;
  nombre: string;
  nota: string | null;
}
