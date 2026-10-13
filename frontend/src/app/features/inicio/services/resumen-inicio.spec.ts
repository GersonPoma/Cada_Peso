import { describe, expect, it } from 'vitest';
import { CuentaResumen, SaldoResumen } from '../models/cuenta-resumen.model';
import { MesResumen } from '../models/mes-resumen.model';
import { EstadoMetaResumen, MetaMesResumen } from '../models/metas-resumen.model';
import {
  contarMetasQueFaltan,
  contarSobregastadas,
  saldoTotalDelPresupuesto,
} from './resumen-inicio';

/** El mismo conjunto que usa `cuentas.page.spec.ts`: el total esperado es 1050000. */
const CUENTAS: CuentaResumen[] = [
  { id: 1, enPresupuesto: true, cerrada: false },
  { id: 2, enPresupuesto: true, cerrada: false },
  { id: 3, enPresupuesto: false, cerrada: false },
  { id: 4, enPresupuesto: true, cerrada: true },
];
const SALDOS: SaldoResumen[] = [
  { cuentaId: 1, saldo: 1200000 },
  { cuentaId: 2, saldo: -150000 },
  { cuentaId: 3, saldo: 600000 },
  { cuentaId: 4, saldo: 300000 },
];

describe('saldoTotalDelPresupuesto', () => {
  it('suma las abiertas del presupuesto con las tarjetas con su signo', () => {
    expect(saldoTotalDelPresupuesto(CUENTAS, SALDOS)).toBe(1050000);
  });

  it('ignora las de seguimiento y las cerradas', () => {
    expect(saldoTotalDelPresupuesto(CUENTAS.slice(2), SALDOS)).toBe(0);
  });

  it('una cuenta sin saldo cuenta cero y sin cuentas el total es cero', () => {
    expect(saldoTotalDelPresupuesto([CUENTAS[0]], [])).toBe(0);
    expect(saldoTotalDelPresupuesto([], SALDOS)).toBe(0);
  });

  it('es una suma entera sin error de coma flotante', () => {
    const cuentas = [1, 2, 3].map((id) => ({ id, enPresupuesto: true, cerrada: false }));
    const saldos = [100, 200, 300].map((saldo, i) => ({ cuentaId: i + 1, saldo }));
    expect(saldoTotalDelPresupuesto(cuentas, saldos)).toBe(600);
  });
});

function mes(...sobregastadas: boolean[][]): MesResumen {
  return {
    mes: '2026-10',
    listoParaAsignar: 0,
    grupos: sobregastadas.map((grupo, g) => ({
      categorias: grupo.map((sobregastada, c) => ({ categoriaId: g * 10 + c, sobregastada })),
    })),
  };
}

describe('contarSobregastadas', () => {
  it('cuenta las marcadas en todos los grupos', () => {
    expect(contarSobregastadas(mes([true, false], [true, true]))).toBe(3);
  });

  it('sin sobregastos o sin grupos es cero', () => {
    expect(contarSobregastadas(mes([false]))).toBe(0);
    expect(contarSobregastadas(mes())).toBe(0);
  });
});

describe('contarMetasQueFaltan', () => {
  const meta = (estado: EstadoMetaResumen, id: number): MetaMesResumen => ({
    categoriaId: id,
    estado,
  });

  it('cuenta solo las FALTA entre estados mezclados', () => {
    const metas = [
      meta('FALTA', 1),
      meta('FALTA', 2),
      meta('FALTA', 3),
      meta('SOBREGASTADA', 4),
      meta('FINANCIADA', 5),
      meta('POSPUESTA', 6),
    ];
    expect(contarMetasQueFaltan(metas)).toBe(3);
  });

  it('sin FALTA o sin metas es cero', () => {
    expect(contarMetasQueFaltan([meta('FINANCIADA', 1), meta('POSPUESTA', 2)])).toBe(0);
    expect(contarMetasQueFaltan([])).toBe(0);
  });
});
