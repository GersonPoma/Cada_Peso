/** Parte de una transacción dividida (`SubTransaccionRequest` del backend). */
export interface SubtransaccionRequest {
  categoriaId: number | null;
  /** Milésimas con signo, distinto de 0. */
  monto: number;
  memo: string | null;
}
