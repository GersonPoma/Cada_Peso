import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { CategoriaMesResponse } from '../models/categoria-mes-response.model';
import { GrupoMesResponse } from '../models/grupo-mes-response.model';
import { GrupoMesComponent } from './grupo-mes.component';

function categoria(
  categoriaId: number,
  nombre: string,
  cifras: Partial<CategoriaMesResponse> = {},
): CategoriaMesResponse {
  return {
    categoriaId,
    nombre,
    oculta: false,
    asignado: 0,
    actividad: 0,
    disponible: 0,
    sobregastada: false,
    esPagoTarjeta: false,
    cuentaId: null,
    ...cifras,
  };
}

const GRUPO: GrupoMesResponse = {
  id: 2,
  nombre: 'Pagos de tarjetas de crédito',
  orden: 1,
  oculto: false,
  categorias: [
    categoria(30, 'Pago: Visa', {
      esPagoTarjeta: true,
      cuentaId: 4,
      asignado: 0,
      actividad: 30000,
      disponible: 30000,
    }),
    categoria(31, 'Comida'),
  ],
};

/** Las filas de pago de tarjeta de `GrupoMesComponent`, que necesitan el router para el enlace. */
describe('GrupoMesComponent: categoría de pago de tarjeta', () => {
  let fixture: ComponentFixture<GrupoMesComponent>;
  let cargador: HarnessLoader;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const fila = (nombre: string) =>
    Array.from(elemento().querySelectorAll('.categoria')).find((f) =>
      f.querySelector('.nombre-categoria')?.textContent?.includes(nombre),
    ) as HTMLElement;

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'USD' });
    fixture = TestBed.createComponent(GrupoMesComponent);
    fixture.componentRef.setInput('grupo', GRUPO);
    fixture.componentRef.setInput('moneda', 'USD');
    cargador = TestbedHarnessEnvironment.loader(fixture);
    await estable();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('la fila de pago lleva la etiqueta, la aclaración de reserva y el enlace', () => {
    const pago = fila('Pago: Visa');

    expect(pago.querySelector('app-etiqueta-pago-tarjeta')?.textContent).toContain(
      'Pago de tarjeta',
    );
    expect(pago.querySelector('.reserva')?.textContent?.trim()).toBe(
      'Reserva para pagar la tarjeta',
    );
    expect(pago.querySelector('.enlace-movimientos')?.textContent?.trim()).toBe('Ver movimientos');
  });

  it('Ver movimientos lleva a las transacciones filtradas por la tarjeta', () => {
    const enlace = fila('Pago: Visa').querySelector('.enlace-movimientos');

    expect(enlace?.getAttribute('href')).toBe('/presupuestos/3/transacciones?cuentaId=4');
  });

  it('mueve dinero y edita metas como en cualquier categoría, y su asignado se edita', async () => {
    const menu = await cargador.getHarness(
      MatMenuHarness.with({ selector: '[aria-label="Acciones de Pago: Visa"]' }),
    );
    await menu.open();
    const items = await Promise.all((await menu.getItems()).map((i) => i.getText()));

    expect(items.some((t) => t.includes('Mover dinero...'))).toBe(true);
    expect(items.some((t) => t.includes('Agregar meta'))).toBe(true);
    expect(fila('Pago: Visa').querySelector('app-celda-asignado')).not.toBeNull();
  });

  it('el texto y el enlace se apilan bajo el nombre y pueden partirse en pantallas estrechas', () => {
    const pago = fila('Pago: Visa').querySelector('.pago-tarjeta') as HTMLElement;

    expect(pago.closest('.nombre-y-meta')).not.toBeNull();
    expect(pago.querySelectorAll(':scope > *').length).toBe(3);
  });

  it('las demás filas no tienen etiqueta, aclaración ni enlace', () => {
    const comida = fila('Comida');

    expect(comida.querySelector('app-etiqueta-pago-tarjeta')).toBeNull();
    expect(comida.querySelector('.reserva')).toBeNull();
    expect(comida.querySelector('.enlace-movimientos')).toBeNull();
  });

  it('sin cuentaId no ofrece el enlace pero sí la etiqueta', async () => {
    fixture.componentRef.setInput('grupo', {
      ...GRUPO,
      categorias: [categoria(30, 'Pago: Visa', { esPagoTarjeta: true, cuentaId: null })],
    });
    await estable();

    expect(fila('Pago: Visa').querySelector('app-etiqueta-pago-tarjeta')).not.toBeNull();
    expect(fila('Pago: Visa').querySelector('.enlace-movimientos')).toBeNull();
  });
});
