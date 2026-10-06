import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatSelectHarness } from '@angular/material/select/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { FiltrosTransacciones } from '../models/filtros-transacciones.model';
import { filtrosVacios } from '../services/filtros-url';
import {
  ESPERA_BUSQUEDA_MS,
  FiltrosTransaccionesComponent,
} from './filtros-transacciones.component';

const ZONA_HORARIA_DE_LA_SUITE = 'America/New_York';

describe('FiltrosTransaccionesComponent', () => {
  let fixture: ComponentFixture<FiltrosTransaccionesComponent>;
  let cargador: HarnessLoader;
  let emitidos: FiltrosTransacciones[];

  const formulario = () =>
    (fixture.componentInstance as unknown as { formulario: FormGroup })['formulario'];
  const elemento = () => fixture.nativeElement as HTMLElement;

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    vi.stubGlobal('navigator', { language: 'es-BO', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    fixture = TestBed.createComponent(FiltrosTransaccionesComponent);
    fixture.componentRef.setInput('filtros', { ...filtrosVacios(), pagina: 3 });
    fixture.componentRef.setInput('cuentas', [
      { id: 5, nombre: 'Banco', enPresupuesto: true, cerrada: false },
      { id: 6, nombre: 'Inversiones', enPresupuesto: false, cerrada: false },
    ]);
    fixture.componentRef.setInput('grupos', [
      {
        id: 1,
        nombre: 'Necesidades',
        oculto: false,
        categorias: [{ id: 7, nombre: 'Comida', oculta: false }],
      },
    ]);
    emitidos = [];
    fixture.componentInstance.cambiar.subscribe((f) => emitidos.push(f));
    cargador = TestbedHarnessEnvironment.loader(fixture);
    await estable();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
  });

  it('elegir una cuenta emite el filtro en la página 0', async () => {
    const cuenta = await cargador.getHarness(
      MatSelectHarness.with({ selector: '[formControlName="cuentaId"]' }),
    );
    await cuenta.clickOptions({ text: 'Banco' });

    expect(emitidos.at(-1)).toEqual({ ...filtrosVacios(), cuentaId: 5, pagina: 0 });
  });

  it('agrupa las cuentas en "En el presupuesto" y "Seguimiento"', async () => {
    const cuenta = await cargador.getHarness(
      MatSelectHarness.with({ selector: '[formControlName="cuentaId"]' }),
    );
    await cuenta.open();
    const grupos = await cuenta.getOptionGroups();

    expect(await Promise.all(grupos.map((g) => g.getLabelText()))).toEqual([
      'En el presupuesto',
      'Seguimiento',
    ]);
  });

  it('categoría, estado y "Solo sin aprobar" emiten al momento', async () => {
    formulario().get('categoriaId')?.setValue(7);
    formulario().get('estado')?.setValue('CONCILIADA');
    formulario().get('soloSinAprobar')?.setValue(true);

    expect(emitidos.at(-1)).toEqual({
      ...filtrosVacios(),
      categoriaId: 7,
      estado: 'CONCILIADA',
      soloSinAprobar: true,
    });
  });

  it('la búsqueda espera 300 ms sin escribir y emite una sola vez', async () => {
    const campo = elemento().querySelector('input[type="search"]') as HTMLInputElement;
    for (const texto of ['s', 'su', 'super']) {
      campo.value = texto;
      campo.dispatchEvent(new Event('input'));
    }
    await new Promise((resolver) => setTimeout(resolver, ESPERA_BUSQUEDA_MS - 150));
    expect(emitidos).toEqual([]);

    await new Promise((resolver) => setTimeout(resolver, 250));
    expect(emitidos).toEqual([{ ...filtrosVacios(), q: 'super' }]);
  });

  it.each(['America/New_York', 'Asia/Tokyo'])(
    'en %s el rango de fechas se emite con el día local',
    (zona) => {
      process.env['TZ'] = zona;
      formulario()
        .get('desde')
        ?.setValue(new Date(2026, 9, 1));
      formulario()
        .get('hasta')
        ?.setValue(new Date(2026, 9, 31));

      expect(emitidos.at(-1)).toEqual({
        ...filtrosVacios(),
        desde: '2026-10-01',
        hasta: '2026-10-31',
      });
    },
  );

  it('refleja los filtros de la URL sin volver a emitir', async () => {
    fixture.componentRef.setInput('filtros', {
      ...filtrosVacios(),
      cuentaId: 6,
      desde: '2026-10-01',
      q: 'luz',
    });
    await estable();

    expect(formulario().get('cuentaId')?.value).toBe(6);
    expect((formulario().get('desde')?.value as Date).getDate()).toBe(1);
    expect(formulario().get('q')?.value).toBe('luz');
    expect(emitidos).toEqual([]);
  });

  it('Limpiar filtros emite limpiar', () => {
    let limpiado = false;
    fixture.componentInstance.limpiar.subscribe(() => (limpiado = true));

    Array.from(elemento().querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Limpiar filtros'))
      ?.click();

    expect(limpiado).toBe(true);
  });
});
