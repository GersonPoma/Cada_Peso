import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonToggleHarness } from '@angular/material/button-toggle/testing';
import { MatCheckboxHarness } from '@angular/material/checkbox/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatFormFieldHarness } from '@angular/material/form-field/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { MapeoImportacion } from '../models/mapeo-importacion.model';
import { SugerenciasMapeo } from '../services/sugerencias-mapeo';
import { AnalisisArchivo } from '../services/validacion-archivo';
import { PasoMapeoComponent } from './paso-mapeo.component';

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

function analisis(cambios: Partial<AnalisisArchivo> = {}): AnalisisArchivo {
  const registros = [
    ['Fecha', 'Descripción', 'Monto', 'Saldo'],
    ...Array.from({ length: 99 }, (_, i) => [`13/10/2026`, `Fila ${i + 1}`, '-1,50', '100']),
  ];
  return { registros, filasDeDatos: 99, columnas: 4, error: null, ...cambios };
}

const SUGERENCIAS: SugerenciasMapeo = {
  separador: 'COMA',
  tieneEncabezado: true,
  formatoFecha: 'dd/MM/yyyy',
  separadorDecimal: 'COMA',
};

describe('PasoMapeoComponent', () => {
  let fixture: ComponentFixture<PasoMapeoComponent>;
  let cargador: HarnessLoader;
  let cambios: Partial<MapeoImportacion>[];

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => (elemento().textContent ?? '').replace(/\s+/g, ' ');
  const boton = (clase: string) =>
    elemento().querySelector(`button.${clase}`) as HTMLButtonElement | null;

  async function poner(entradas: Record<string, unknown>): Promise<void> {
    for (const [nombre, valor] of Object.entries(entradas)) {
      fixture.componentRef.setInput(nombre, valor);
    }
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function selector(etiqueta: string): Promise<MatSelectHarness> {
    const campo = await cargador.getHarness(
      MatFormFieldHarness.with({ floatingLabelText: etiqueta }),
    );
    return (await campo.getControl(MatSelectHarness)) as MatSelectHarness;
  }

  async function opciones(etiqueta: string): Promise<string[]> {
    const select = await selector(etiqueta);
    await select.open();
    const textos = await Promise.all((await select.getOptions()).map((o) => o.getText()));
    await select.close();
    return textos;
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    fixture = TestBed.createComponent(PasoMapeoComponent);
    cargador = TestbedHarnessEnvironment.loader(fixture);
    cambios = [];
    fixture.componentInstance.cambio.subscribe((c) => cambios.push(c));
    await poner({ mapeo: mapeo(), analisis: analisis(), sugerencias: SUGERENCIAS });
  });

  describe('las primeras filas', () => {
    it('muestra las primeras 10 filas del archivo, no las 100, con las columnas desde 1', () => {
      const filas = elemento().querySelectorAll('tbody tr');
      const columnas = Array.from(elemento().querySelectorAll('thead th')).map((th) =>
        th.textContent?.trim(),
      );

      expect(filas).toHaveLength(10);
      expect(columnas).toEqual(['Fila', 'Columna 1', 'Columna 2', 'Columna 3', 'Columna 4']);
      expect(filas[1].textContent).toContain('Fila 1');
    });

    it('la región es enfocable, tiene nombre y marca el encabezado', () => {
      const region = elemento().querySelector('[role="region"]') as HTMLElement;

      expect(region.getAttribute('tabindex')).toBe('0');
      expect(region.getAttribute('aria-labelledby')).toBe('titulo-muestra');
      expect(elemento().querySelector('#titulo-muestra')?.textContent).toContain(
        'Primeras 10 filas',
      );
      expect(elemento().querySelectorAll('tbody tr.encabezado')).toHaveLength(1);
    });

    it('avisa que la vista previa del servidor es la que decide', () => {
      expect(texto()).toContain('la vista previa del servidor es la que decide');
    });

    it('el título del paso es enfocable por código', () => {
      const titulo = elemento().querySelector('h2.titulo-paso') as HTMLElement;

      expect(titulo.getAttribute('tabindex')).toBe('-1');
      expect(titulo.textContent).toContain('Elige las columnas');
    });
  });

  describe('las columnas', () => {
    it('ofrece solo las columnas detectadas, de Columna 1 a Columna 4', async () => {
      expect(await opciones('Columna de la fecha')).toEqual([
        'Columna 1',
        'Columna 2',
        'Columna 3',
        'Columna 4',
      ]);
    });

    it('el memo agrega Ninguna', async () => {
      expect(await opciones('Columna del memo (opcional)')).toEqual([
        'Ninguna',
        'Columna 1',
        'Columna 2',
        'Columna 3',
        'Columna 4',
      ]);
    });

    it('elegir Columna 3 emite el índice 2 (desde 0)', async () => {
      await (await selector('Columna de la fecha')).clickOptions({ text: 'Columna 3' });

      expect(cambios).toEqual([{ columnaFecha: 2 }]);
    });

    it('elegir la descripción y el monto emite sus índices', async () => {
      await (await selector('Columna de la descripción')).clickOptions({ text: 'Columna 4' });
      await (await selector('Columna del monto')).clickOptions({ text: 'Columna 1' });

      expect(cambios).toEqual([{ columnaDescripcion: 3 }, { columnaMonto: 0 }]);
    });

    it('elegir Ninguna en el memo emite null', async () => {
      await poner({ mapeo: mapeo({ columnaMemo: 2 }) });

      await (await selector('Columna del memo (opcional)')).clickOptions({ text: 'Ninguna' });

      expect(cambios).toEqual([{ columnaMemo: null }]);
    });
  });

  describe('el mapeo', () => {
    it('cambiar el separador emite su nombre', async () => {
      await (await selector('Separador de campos')).clickOptions({ text: 'Punto y coma (;)' });

      expect(cambios).toEqual([{ separador: 'PUNTO_Y_COMA' }]);
    });

    it('el formato de la fecha ofrece los cuatro y emite el elegido', async () => {
      expect(await opciones('Formato de la fecha')).toEqual([
        'yyyy-MM-dd',
        'dd/MM/yyyy',
        'MM/dd/yyyy',
        'dd-MM-yyyy',
      ]);

      await (await selector('Formato de la fecha')).clickOptions({ text: 'MM/dd/yyyy' });

      expect(cambios).toEqual([{ formatoFecha: 'MM/dd/yyyy' }]);
    });

    it('decimal y miles ofrecen sus valores', async () => {
      expect(await opciones('Separador decimal')).toEqual(['Punto (1234.50)', 'Coma (1234,50)']);
      expect(await opciones('Separador de miles')).toEqual([
        'Ninguno',
        'Punto (1.234)',
        'Coma (1,234)',
        'Espacio (1 234)',
      ]);
    });

    it('la casilla del encabezado emite', async () => {
      const casilla = await cargador.getHarness(MatCheckboxHarness);
      expect(await casilla.isChecked()).toBe(true);

      await casilla.uncheck();

      expect(cambios).toEqual([{ tieneEncabezado: false }]);
    });

    it('Débito y crédito cambia el modo y muestra dos selects en vez del monto', async () => {
      const toggle = await cargador.getHarness(
        MatButtonToggleHarness.with({ text: 'Débito y crédito' }),
      );
      await toggle.check();
      expect(cambios).toEqual([{ modoMonto: 'DEBITO_CREDITO' }]);

      await poner({ mapeo: mapeo({ modoMonto: 'DEBITO_CREDITO' }) });

      expect(
        await cargador.getAllHarnesses(
          MatFormFieldHarness.with({ floatingLabelText: 'Columna del monto' }),
        ),
      ).toHaveLength(0);
      expect((await opciones('Columna de débito'))[0]).toBe('Columna 1');
      expect((await opciones('Columna de crédito'))[3]).toBe('Columna 4');
    });

    it('en débito y crédito elegir Columna 4 y Columna 5 emite 3 y 4', async () => {
      await poner({
        mapeo: mapeo({ modoMonto: 'DEBITO_CREDITO' }),
        analisis: analisis({ columnas: 5, registros: [['a', 'b', 'c', 'd', 'e']] }),
      });

      await (await selector('Columna de débito')).clickOptions({ text: 'Columna 4' });
      await (await selector('Columna de crédito')).clickOptions({ text: 'Columna 5' });

      expect(cambios).toEqual([{ columnaDebito: 3 }, { columnaCredito: 4 }]);
    });
  });

  describe('las sugerencias', () => {
    it('marca como Sugerido lo que coincide con la sugerencia', () => {
      expect(elemento().querySelectorAll('.sugerido').length).toBe(4);
    });

    it('deja de marcar lo que la persona cambió', async () => {
      await poner({ mapeo: mapeo({ separador: 'TABULADOR', formatoFecha: 'MM/dd/yyyy' }) });

      expect(elemento().querySelectorAll('.sugerido').length).toBe(2);
    });

    it('sin sugerencias no marca nada', async () => {
      await poner({ sugerencias: null });

      expect(elemento().querySelectorAll('.sugerido')).toHaveLength(0);
    });
  });

  describe('validaciones y botón', () => {
    it('un mapeo completo habilita Ver vista previa y lo emite', () => {
      let pedido = 0;
      fixture.componentInstance.pedirVistaPrevia.subscribe(() => pedido++);

      expect(boton('pedir')?.disabled).toBe(false);
      boton('pedir')?.click();

      expect(pedido).toBe(1);
    });

    const casos: [string, Partial<MapeoImportacion>, string][] = [
      [
        'miles igual al decimal',
        { separadorMiles: 'COMA' },
        'El separador de miles no puede ser igual al de decimales',
      ],
      ['sin fecha', { columnaFecha: null }, 'Elige la columna de la fecha'],
      ['sin descripción', { columnaDescripcion: null }, 'Elige la columna de la descripción'],
      ['sin monto', { columnaMonto: null }, 'Elige la columna del monto'],
      [
        'débito sin crédito',
        { modoMonto: 'DEBITO_CREDITO', columnaDebito: 3 },
        'Elige las columnas de débito y de crédito',
      ],
    ];

    it.each(casos)(
      '%s: muestra el mensaje y deshabilita el botón',
      async (_nombre, cambio, mensaje) => {
        await poner({ mapeo: mapeo(cambio) });

        expect(texto()).toContain(mensaje);
        expect(elemento().querySelector('[role="alert"]')).not.toBeNull();
        expect(boton('pedir')?.disabled).toBe(true);
      },
    );

    it('un error del archivo con este separador (filas o comillas) bloquea y se anuncia', async () => {
      await poner({ analisis: analisis({ error: 'DEMASIADAS_FILAS', filasDeDatos: 0 }) });

      expect(texto()).toContain('El archivo tiene más de 5000 filas de datos');
      expect(boton('pedir')?.disabled).toBe(true);
    });

    it('con la petición en camino el botón está deshabilitado y se anuncia', async () => {
      await poner({ pidiendo: true });

      expect(boton('pedir')?.disabled).toBe(true);
      expect(boton('volver')?.disabled).toBe(true);
      expect(texto()).toContain('Pidiendo la vista previa…');
    });

    it('muestra el mensaje fijo de un error de la última petición', async () => {
      await poner({ errorServidor: 'No pudimos leer el archivo con ese mapeo.' });

      expect(texto()).toContain('No pudimos leer el archivo con ese mapeo.');
    });

    it('Volver emite', () => {
      let pulsado = 0;
      fixture.componentInstance.volver.subscribe(() => pulsado++);

      boton('volver')?.click();

      expect(pulsado).toBe(1);
    });
  });

  describe('vista previa anterior', () => {
    it('sin vista previa vigente no ofrece volver a ella', () => {
      expect(boton('volver-a-vista')).toBeNull();
    });

    it('con una vista previa vigente ofrece volver a ella sin pedirla otra vez', async () => {
      let usada = 0;
      fixture.componentInstance.volverAVistaPrevia.subscribe(() => usada++);
      await poner({ hayVistaVigente: true });

      boton('volver-a-vista')?.click();

      expect(usada).toBe(1);
    });

    it('desactualizada: avisa que hay que pedirla de nuevo', async () => {
      await poner({ vistaDesactualizada: true });

      expect(texto()).toContain('Cambiaste el mapeo: pide la vista previa de nuevo para importar');
      expect(boton('volver-a-vista')).toBeNull();
    });
  });
});
