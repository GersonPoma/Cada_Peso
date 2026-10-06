/** Cuerpo de `POST /meses/{mes}/mover-dinero`. `monto` en milésimas, mayor que 0. */
export interface MoverDineroRequest {
  origenId: number;
  destinoId: number;
  monto: number;
}
