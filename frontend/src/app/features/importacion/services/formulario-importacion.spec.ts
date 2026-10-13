import { describe, expect, it } from 'vitest';
import { MapeoImportacion } from '../models/mapeo-importacion.model';
import { MapeoIncompletoError, armarFormulario } from './formulario-importacion';

function mapeo(cambios: Partial<MapeoImportacion> = {}): MapeoImportacion {
  return {
    separador: 'COMA',
    tieneEncabezado: true,
    columnaFecha: 0,
    formatoFecha: 'dd/MM/yyyy',
    modoMonto: 'UNA_COLUMNA',
    columnaMonto: 2,
    columnaDebito: null,
    columnaCredito: null,
    separadorDecimal: 'COMA',
    separadorMiles: 'PUNTO',
    columnaDescripcion: 1,
    columnaMemo: null,
    ...cambios,
  };
}

const archivo = () => new File(['fecha,descripcion,monto\n13/10/2026,Cafe,-12,5'], 'extracto.csv');

/** Los campos de texto del formulario, por nombre. */
function campos(formulario: FormData): Record<string, string> {
  const texto: Record<string, string> = {};
  formulario.forEach((valor, nombre) => {
    if (typeof valor === 'string') {
      texto[nombre] = valor;
    }
  });
  return texto;
}

describe('armarFormulario', () => {
  it('lleva la parte archivo con su nombre', () => {
    const parte = armarFormulario(archivo(), mapeo()).get('archivo') as File;

    expect(parte).toBeInstanceOf(File);
    expect(parte.name).toBe('extracto.csv');
    expect(parte.size).toBe(archivo().size);
  });

  it('el monto en una columna lleva los nombres exactos del servidor', () => {
    expect(campos(armarFormulario(archivo(), mapeo()))).toEqual({
      separador: 'COMA',
      tieneEncabezado: 'true',
      columnaFecha: '0',
      formatoFecha: 'dd/MM/yyyy',
      columnaMonto: '2',
      separadorDecimal: 'COMA',
      separadorMiles: 'PUNTO',
      columnaDescripcion: '1',
    });
  });

  it('los enums van por nombre y formatoFecha por su etiqueta', () => {
    const enviado = campos(
      armarFormulario(
        archivo(),
        mapeo({
          separador: 'PUNTO_Y_COMA',
          formatoFecha: 'MM/dd/yyyy',
          separadorDecimal: 'PUNTO',
          separadorMiles: 'ESPACIO',
        }),
      ),
    );

    expect(enviado['separador']).toBe('PUNTO_Y_COMA');
    expect(enviado['formatoFecha']).toBe('MM/dd/yyyy');
    expect(enviado['separadorDecimal']).toBe('PUNTO');
    expect(enviado['separadorMiles']).toBe('ESPACIO');
  });

  it('el monto en débito y crédito no lleva columnaMonto', () => {
    const enviado = campos(
      armarFormulario(
        archivo(),
        mapeo({
          modoMonto: 'DEBITO_CREDITO',
          columnaMonto: 2,
          columnaDebito: 3,
          columnaCredito: 4,
        }),
      ),
    );

    expect(enviado['columnaDebito']).toBe('3');
    expect(enviado['columnaCredito']).toBe('4');
    expect('columnaMonto' in enviado).toBe(false);
  });

  it('en una columna no lleva débito ni crédito aunque el mapeo los recuerde', () => {
    const enviado = campos(
      armarFormulario(archivo(), mapeo({ columnaDebito: 3, columnaCredito: 4 })),
    );

    expect('columnaDebito' in enviado).toBe(false);
    expect('columnaCredito' in enviado).toBe(false);
    expect(enviado['columnaMonto']).toBe('2');
  });

  it('columnaMemo solo viaja si se eligió, incluso en la columna 0', () => {
    expect('columnaMemo' in campos(armarFormulario(archivo(), mapeo()))).toBe(false);
    expect(campos(armarFormulario(archivo(), mapeo({ columnaMemo: 5 })))['columnaMemo']).toBe('5');
    expect(campos(armarFormulario(archivo(), mapeo({ columnaMemo: 0 })))['columnaMemo']).toBe('0');
  });

  it('omitirInvalidas solo viaja al importar', () => {
    expect('omitirInvalidas' in campos(armarFormulario(archivo(), mapeo()))).toBe(false);
    expect(campos(armarFormulario(archivo(), mapeo(), false))['omitirInvalidas']).toBe('false');
    expect(campos(armarFormulario(archivo(), mapeo(), true))['omitirInvalidas']).toBe('true');
  });

  it('tieneEncabezado viaja como true o false', () => {
    expect(
      campos(armarFormulario(archivo(), mapeo({ tieneEncabezado: false })))['tieneEncabezado'],
    ).toBe('false');
  });

  it('sin separador de miles viaja NINGUNO y ningún campo queda vacío', () => {
    const enviado = campos(armarFormulario(archivo(), mapeo({ separadorMiles: 'NINGUNO' })));

    expect(enviado['separadorMiles']).toBe('NINGUNO');
    expect(Object.values(enviado).every((valor) => valor !== '')).toBe(true);
  });

  it('un mapeo sin una columna obligatoria no se arma', () => {
    expect(() => armarFormulario(archivo(), mapeo({ columnaFecha: null }))).toThrowError(
      MapeoIncompletoError,
    );
    expect(() => armarFormulario(archivo(), mapeo({ columnaDescripcion: null }))).toThrowError(
      MapeoIncompletoError,
    );
    expect(() => armarFormulario(archivo(), mapeo({ columnaMonto: null }))).toThrowError(
      MapeoIncompletoError,
    );
    expect(() =>
      armarFormulario(archivo(), mapeo({ modoMonto: 'DEBITO_CREDITO', columnaDebito: 3 })),
    ).toThrowError(MapeoIncompletoError);
  });
});
