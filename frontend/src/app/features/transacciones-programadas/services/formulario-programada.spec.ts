import { FormControl, FormGroup } from '@angular/forms';
import { afterEach, describe, expect, it } from 'vitest';
import { GrupoCategoriasLectura } from '../models/grupo-categorias-lectura.model';
import {
  ValorFormularioProgramada,
  aActualizarRequest,
  aCrearRequest,
  finNoAnteriorAInicio,
  opcionesCategoria,
} from './formulario-programada';

const ZONA_HORARIA_DE_LA_SUITE = 'America/New_York';

function valor(cambios: Partial<ValorFormularioProgramada> = {}): ValorFormularioProgramada {
  return {
    cuentaId: 5,
    tipo: 'salida',
    monto: 1500000,
    frecuencia: 'MENSUAL',
    fechaInicio: new Date(2026, 10, 5),
    fechaFin: null,
    categoriaId: 7,
    beneficiario: '  Inmobiliaria  ',
    memo: '   ',
    ...cambios,
  };
}

describe('aCrearRequest y aActualizarRequest', () => {
  afterEach(() => {
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
  });

  it('una salida va en negativo, con fechas locales y textos recortados', () => {
    expect(aCrearRequest(valor())).toEqual({
      cuentaId: 5,
      fechaInicio: '2026-11-05',
      frecuencia: 'MENSUAL',
      fechaFin: null,
      monto: -1500000,
      categoriaId: 7,
      beneficiario: 'Inmobiliaria',
      memo: null,
    });
  });

  it('una entrada va en positivo', () => {
    expect(aCrearRequest(valor({ tipo: 'entrada', monto: 50500 })).monto).toBe(50500);
  });

  it('el PUT no lleva la cuenta ni la fecha de inicio', () => {
    const cuerpo = aActualizarRequest(valor({ fechaFin: new Date(2026, 11, 31) }));
    expect(cuerpo).toEqual({
      monto: -1500000,
      categoriaId: 7,
      beneficiario: 'Inmobiliaria',
      memo: null,
      frecuencia: 'MENSUAL',
      fechaFin: '2026-12-31',
    });
  });

  it.each(['America/La_Paz', 'Asia/Tokyo'])('la fecha no se corre de día en %s', (zona) => {
    process.env['TZ'] = zona;
    const inicio = new Date(2026, 9, 1);

    expect(aCrearRequest(valor({ fechaInicio: inicio })).fechaInicio).toBe('2026-10-01');
  });
});

describe('finNoAnteriorAInicio', () => {
  function grupo(inicio: Date | null, fin: Date | null) {
    const formulario = new FormGroup({
      fechaInicio: new FormControl<Date | null>({ value: inicio, disabled: true }),
      fechaFin: new FormControl<Date | null>(fin, finNoAnteriorAInicio),
    });
    formulario.controls.fechaFin.updateValueAndValidity();
    return formulario.controls.fechaFin.errors;
  }

  it('rechaza un fin anterior al inicio, también con el inicio deshabilitado', () => {
    expect(grupo(new Date(2026, 10, 5), new Date(2026, 10, 1))).toEqual({ finAnterior: true });
  });

  it('acepta un fin igual o posterior, o vacío', () => {
    expect(grupo(new Date(2026, 10, 5), new Date(2026, 10, 5))).toBeNull();
    expect(grupo(new Date(2026, 10, 5), new Date(2027, 0, 1))).toBeNull();
    expect(grupo(new Date(2026, 10, 5), null)).toBeNull();
    expect(grupo(null, new Date(2026, 10, 1))).toBeNull();
  });
});

describe('opcionesCategoria', () => {
  const GRUPOS: GrupoCategoriasLectura[] = [
    {
      id: 1,
      nombre: 'Casa',
      oculto: false,
      categorias: [
        { id: 7, nombre: 'Alquiler', oculta: false, esPagoTarjeta: false },
        { id: 8, nombre: 'Vieja', oculta: true, esPagoTarjeta: false },
      ],
    },
    {
      id: 2,
      nombre: 'Pagos de tarjeta',
      oculto: false,
      categorias: [{ id: 20, nombre: 'Pago: Visa', oculta: false, esPagoTarjeta: true }],
    },
    {
      id: 3,
      nombre: 'Archivo',
      oculto: true,
      categorias: [{ id: 30, nombre: 'Archivada', oculta: false, esPagoTarjeta: false }],
    },
  ];

  it('quita las de pago de tarjeta y las ocultas', () => {
    expect(opcionesCategoria(GRUPOS, null).map((g) => g.categorias.map((c) => c.id))).toEqual([
      [7],
    ]);
  });

  it('conserva la categoría actual aunque esté oculta', () => {
    expect(opcionesCategoria(GRUPOS, 8)[0].categorias.map((c) => c.id)).toEqual([7, 8]);
    expect(opcionesCategoria(GRUPOS, 30).map((g) => g.id)).toEqual([1, 3]);
  });

  it('nunca ofrece una de pago de tarjeta, aunque sea la actual', () => {
    expect(opcionesCategoria(GRUPOS, 20).map((g) => g.id)).toEqual([1]);
  });
});
