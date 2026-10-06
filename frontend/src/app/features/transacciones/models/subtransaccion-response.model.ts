/** Una parte de una transacción dividida (`SubTransaccionResponse` del backend). */
export interface SubtransaccionResponse {
  id: number;
  categoriaId: number | null;
  /** Milésimas con signo. */
  monto: number;
  memo: string | null;
}
