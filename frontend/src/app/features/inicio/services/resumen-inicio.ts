import { CuentaResumen, SaldoResumen } from '../models/cuenta-resumen.model';
import { MesResumen } from '../models/mes-resumen.model';
import { MetaMesResumen } from '../models/metas-resumen.model';

/*
 * Lo poco que Inicio hace con las cifras del backend: contar y sumar enteros (milésimas), sin
 * recalcular ningún dominio.
 */

/**
 * Suma entera, en milésimas, del saldo de las cuentas abiertas que están en el presupuesto, con las
 * tarjetas de crédito con su signo. Es la misma regla que `Total en el presupuesto` de Cuentas (el
 * backend no expone el total); el mismo conjunto de datos se prueba en ambas pantallas.
 */
export function saldoTotalDelPresupuesto(cuentas: CuentaResumen[], saldos: SaldoResumen[]): number {
  const saldoPorCuenta = new Map(saldos.map((saldo) => [saldo.cuentaId, saldo.saldo]));
  return cuentas
    .filter((cuenta) => !cuenta.cerrada && cuenta.enPresupuesto)
    .reduce((suma, cuenta) => suma + (saldoPorCuenta.get(cuenta.id) ?? 0), 0);
}

/** Categorías con `sobregastada` del backend, también las ocultas (se pidieron con ellas). */
export function contarSobregastadas(mes: MesResumen): number {
  return mes.grupos.reduce(
    (suma, grupo) => suma + grupo.categorias.filter((categoria) => categoria.sobregastada).length,
    0,
  );
}

/** Metas en estado `FALTA`. `totalFaltante` no se usa: suma también las `SOBREGASTADA`. */
export function contarMetasQueFaltan(metas: MetaMesResumen[]): number {
  return metas.filter((meta) => meta.estado === 'FALTA').length;
}
