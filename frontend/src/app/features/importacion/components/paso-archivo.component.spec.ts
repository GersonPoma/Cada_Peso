import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AnalisisArchivo, ErrorArchivo } from '../services/validacion-archivo';
import { PasoArchivoComponent } from './paso-archivo.component';

function analisis(cambios: Partial<AnalisisArchivo> = {}): AnalisisArchivo {
  return {
    registros: [
      ['fecha', 'monto'],
      ['13/10/2026', '-1'],
    ],
    filasDeDatos: 12,
    columnas: 2,
    error: null,
    ...cambios,
  };
}

describe('PasoArchivoComponent', () => {
  let fixture: ComponentFixture<PasoArchivoComponent>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => (elemento().textContent ?? '').replace(/\s+/g, ' ');
  const entrada = () => elemento().querySelector('input[type="file"]') as HTMLInputElement;
  const continuar = () => elemento().querySelector('button.continuar') as HTMLButtonElement;
  const alerta = () => elemento().querySelector('[role="alert"]');

  function poner(entradas: Record<string, unknown>): void {
    for (const [nombre, valor] of Object.entries(entradas)) {
      fixture.componentRef.setInput(nombre, valor);
    }
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    fixture = TestBed.createComponent(PasoArchivoComponent);
    fixture.detectChanges();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('ofrece un input de archivo nativo con etiqueta visible', () => {
    const etiqueta = elemento().querySelector('label[for="archivo-csv"]');

    expect(entrada()).not.toBeNull();
    expect(entrada().id).toBe('archivo-csv');
    expect(etiqueta?.textContent?.trim()).toBe('Archivo CSV');
  });

  it('el título del paso es enfocable por código', () => {
    const titulo = elemento().querySelector('h2.titulo-paso') as HTMLElement;

    expect(titulo.getAttribute('tabindex')).toBe('-1');
    expect(titulo.textContent).toContain('Elige el archivo');
  });

  it('sin archivo, Continuar está deshabilitado', () => {
    expect(continuar().disabled).toBe(true);
  });

  it('con un archivo válido muestra su nombre, su tamaño y sus filas de datos y habilita Continuar', () => {
    const archivo = new File([new Uint8Array(3 * 1024)], 'extracto.csv');
    poner({ archivo, analisis: analisis({ filasDeDatos: 12 }) });

    expect(texto()).toContain('extracto.csv');
    expect(texto()).toContain('3 KB');
    expect(texto()).toContain('12 filas de datos');
    expect(continuar().disabled).toBe(false);
  });

  it('elegir un archivo emite el archivo y deja el input listo para elegir el mismo otra vez', () => {
    const elegidos: File[] = [];
    fixture.componentInstance.elegido.subscribe((f) => elegidos.push(f));
    const archivo = new File(['a,b'], 'nuevo.csv');
    const input = entrada();
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: { item: (i: number) => (i === 0 ? archivo : null), length: 1 },
    });

    input.dispatchEvent(new Event('change'));

    expect(elegidos).toEqual([archivo]);
    expect(input.value).toBe('');
  });

  it('si se cancela la elección no emite nada', () => {
    const elegidos: File[] = [];
    fixture.componentInstance.elegido.subscribe((f) => elegidos.push(f));
    Object.defineProperty(entrada(), 'files', {
      configurable: true,
      value: { item: () => null, length: 0 },
    });

    entrada().dispatchEvent(new Event('change'));

    expect(elegidos).toEqual([]);
  });

  it('Continuar emite', () => {
    let pulsado = 0;
    fixture.componentInstance.continuar.subscribe(() => pulsado++);
    poner({ archivo: new File(['a'], 'a.csv'), analisis: analisis() });

    continuar().click();

    expect(pulsado).toBe(1);
  });

  describe('errores', () => {
    const casos: [ErrorArchivo, string][] = [
      ['TAMANO', 'El archivo supera el máximo de 2 MB'],
      ['VACIO', 'El archivo está vacío o no tiene filas de datos'],
      ['NO_UTF8', 'El archivo debe estar codificado en UTF-8'],
      ['DEMASIADAS_FILAS', 'El archivo tiene más de 5000 filas de datos'],
      ['COMILLA_SIN_CERRAR', 'El archivo tiene una comilla sin cerrar'],
    ];

    it.each(casos)('%s se anuncia con su mensaje y no deja continuar', (error, mensaje) => {
      poner({ archivo: new File(['a'], 'a.csv'), errorCarga: error });

      expect(alerta()?.textContent).toContain(mensaje);
      expect(alerta()?.querySelector('mat-icon')?.textContent?.trim()).toBe('error');
      expect(continuar().disabled).toBe(true);
    });

    it('un error del análisis (más de 5000 filas) también bloquea', () => {
      poner({
        archivo: new File(['a'], 'a.csv'),
        analisis: analisis({ error: 'DEMASIADAS_FILAS', filasDeDatos: 0 }),
      });

      expect(alerta()?.textContent).toContain('El archivo tiene más de 5000 filas de datos');
      expect(continuar().disabled).toBe(true);
    });

    it('el error de carga prevalece sobre el del análisis', () => {
      poner({
        archivo: new File(['a'], 'a.csv'),
        errorCarga: 'NO_UTF8',
        analisis: analisis({ error: 'VACIO' }),
      });

      expect(alerta()?.textContent).toContain('UTF-8');
    });

    it('al elegir otro archivo válido el error desaparece y se habilita Continuar', () => {
      poner({ archivo: new File(['a'], 'a.csv'), errorCarga: 'TAMANO' });
      expect(alerta()).not.toBeNull();

      poner({ archivo: new File(['b'], 'b.csv'), errorCarga: null, analisis: analisis() });

      expect(alerta()).toBeNull();
      expect(continuar().disabled).toBe(false);
    });
  });

  describe('lectura en curso', () => {
    it('muestra Leyendo el archivo… con aria-busy y Continuar deshabilitado', () => {
      poner({ archivo: new File(['a'], 'a.csv'), leyendo: true });

      expect(texto()).toContain('Leyendo el archivo…');
      expect(elemento().querySelector('[role="status"]')?.textContent).toContain('Leyendo');
      expect(elemento().querySelector('section')?.getAttribute('aria-busy')).toBe('true');
      expect(continuar().disabled).toBe(true);
    });

    it('al terminar quita el aviso y aria-busy', () => {
      poner({ archivo: new File(['a'], 'a.csv'), leyendo: true });
      poner({ leyendo: false, analisis: analisis() });

      expect(texto()).not.toContain('Leyendo el archivo…');
      expect(elemento().querySelector('section')?.getAttribute('aria-busy')).toBe('false');
    });
  });
});
