import { FormControl } from '@angular/forms';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EstadoConciliacionResponse } from '../models/estado-conciliacion-response.model';
import { TransaccionNoConciliada } from '../models/transaccion-no-conciliada.model';
import { hoy, noConciliadasHasta, noFutura, textoDiferencia } from './presentacion-conciliacion';

const ZONA_HORARIA_DE_LA_SUITE = 'America/New_York';

function normalizar(texto: string): string {
  return texto.replace(/[  ]/g, ' ');
}

function pendiente(id: number, fecha: string): TransaccionNoConciliada {
  return { id, cuentaId: 5, fecha, monto: -1000, beneficiario: null, estado: 'NO_CONCILIADA' };
}

function estado(
  noConciliadas: TransaccionNoConciliada[],
  totalNoConciliadas = noConciliadas.length,
): EstadoConciliacionResponse {
  return {
    cuentaId: 5,
    fecha: '2026-09-30',
    saldoExtracto: 150000,
    saldoConciliado: 120000,
    saldoConciliadoAlCorte: 120000,
    diferencia: 30000,
    totalNoConciliadas,
    noConciliadas,
  };
}

describe('textoDiferencia', () => {
  beforeEach(() => vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' }));
  afterEach(() => vi.unstubAllGlobals());

  it('con 0 cuadra', () => {
    expect(textoDiferencia(0, 'USD')).toEqual({ texto: 'Cuadra con el extracto', tono: 'cuadra' });
  });

  it('positiva: a lo conciliado le falta', () => {
    const { texto, tono } = textoDiferencia(30000, 'USD');

    expect(normalizar(texto)).toBe('A lo conciliado le faltan $30.00');
    expect(tono).toBe('falta');
  });

  it('negativa: lo conciliado supera al extracto, en valor absoluto', () => {
    const { texto, tono } = textoDiferencia(-5000, 'USD');

    expect(normalizar(texto)).toBe('Lo conciliado supera al extracto en $5.00');
    expect(tono).toBe('sobra');
  });
});

describe('noConciliadasHasta', () => {
  it('cuenta las de fecha anterior o igual al corte, no las posteriores', () => {
    const resultado = noConciliadasHasta(
      estado([
        pendiente(3, '2026-10-02'),
        pendiente(2, '2026-09-30'),
        pendiente(1, '2026-09-12'),
      ]),
    );

    expect(resultado).toEqual({ cantidad: 2, minimo: false });
  });

  it('sin pendientes hasta la fecha da 0', () => {
    expect(noConciliadasHasta(estado([pendiente(1, '2026-10-01')]))).toEqual({
      cantidad: 0,
      minimo: false,
    });
  });

  it('con la lista recortada por la API el conteo es mínimo', () => {
    const lista = Array.from({ length: 100 }, (_, i) => pendiente(i + 1, '2026-09-01'));

    expect(noConciliadasHasta(estado(lista, 130))).toEqual({ cantidad: 100, minimo: true });
  });
});

describe('noFutura', () => {
  const validar = (fecha: Date | null) => noFutura(new FormControl<Date | null>(fecha));

  afterEach(() => {
    vi.useRealTimers();
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
  });

  // Las fechas se arman dentro de cada caso, después de cambiar la zona horaria.
  it.each([
    ['America/New_York', 23],
    ['Asia/Tokyo', 1],
  ])('en %s compara con el día local (hora %i)', (zona, hora) => {
    process.env['TZ'] = zona;
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 8, hora));

    expect(validar(new Date(2026, 9, 8))).toBeNull();
    expect(validar(new Date(2026, 9, 7))).toBeNull();
    expect(validar(new Date(2026, 9, 9))).toEqual({ futura: true });
    expect(hoy()).toEqual(new Date(2026, 9, 8));
    // El día en UTC es otro: la comparación no lo usa.
    expect(new Date().toISOString().slice(0, 10)).not.toBe('2026-10-08');
  });

  it('sin fecha no da error (de eso se encarga required)', () => {
    expect(validar(null)).toBeNull();
  });
});
