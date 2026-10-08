import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { DatosDialogoMoverCuenta } from '../models/datos-dialogos-transacciones.model';
import { TransaccionResponse } from '../models/transaccion-response.model';
import {
  DialogoMoverCuentaComponent,
  MENSAJE_NO_SE_PUEDE_MOVER,
} from './dialogo-mover-cuenta.component';

const TRANSACCION: TransaccionResponse = {
  id: 40,
  cuentaId: 5,
  fecha: '2026-10-06',
  monto: -1000,
  categoriaId: null,
  beneficiario: null,
  beneficiarioId: null,
  memo: null,
  estado: 'NO_CONCILIADA',
  aprobada: true,
  subtransacciones: [],
  transaccionParId: null,
  programadaId: null,
  fechaCreacion: '',
  fechaActualizacion: '',
};

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoMoverCuentaComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let resultado: TransaccionResponse | undefined | 'abierto';

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AnfitrionDePrueba);
    cargador = TestbedHarnessEnvironment.documentRootLoader(fixture);
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoMoverCuentaComponent,
      DatosDialogoMoverCuenta,
      TransaccionResponse
    >(DialogoMoverCuentaComponent, {
      data: {
        transaccion: TRANSACCION,
        cuentas: [
          { id: 5, nombre: 'Banco', enPresupuesto: true, cerrada: false },
          { id: 6, nombre: 'Efectivo', enPresupuesto: true, cerrada: false },
          { id: 7, nombre: 'Vieja', enPresupuesto: true, cerrada: true },
          { id: 8, nombre: 'Inversiones', enPresupuesto: false, cerrada: false },
        ],
      },
    });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  });

  afterEach(() => {
    backend.verify();
    TestBed.inject(MatDialog).closeAll();
  });

  it('ofrece las cuentas abiertas sin la actual', async () => {
    const selector = await cargador.getHarness(MatSelectHarness);
    await selector.open();

    expect(await Promise.all((await selector.getOptions()).map((o) => o.getText()))).toEqual([
      'Efectivo',
      'Inversiones',
    ]);
  });

  it('mueve a la cuenta elegida y se cierra con la transacción movida', async () => {
    const mover = await cargador.getHarness(MatButtonHarness.with({ text: 'Mover' }));
    expect(await mover.isDisabled()).toBe(true);
    await (await cargador.getHarness(MatSelectHarness)).clickOptions({ text: 'Efectivo' });
    await mover.click();

    const peticion = backend.expectOne('/api/v1/presupuestos/3/transacciones/40/mover-cuenta');
    expect(peticion.request.body).toEqual({ cuentaId: 6 });
    peticion.flush({ ...TRANSACCION, cuentaId: 6 });
    await estable();
    expect(resultado).toEqual({ ...TRANSACCION, cuentaId: 6 });
  });

  it('un 422 se muestra en el diálogo', async () => {
    await (await cargador.getHarness(MatSelectHarness)).clickOptions({ text: 'Efectivo' });
    await (await cargador.getHarness(MatButtonHarness.with({ text: 'Mover' }))).click();
    backend
      .expectOne('/api/v1/presupuestos/3/transacciones/40/mover-cuenta')
      .flush({ codigo: 'REGLA_NEGOCIO_VIOLADA' }, { status: 422, statusText: 'Unprocessable' });
    await estable();

    expect(document.querySelector('mat-dialog-container [role="alert"]')?.textContent).toBe(
      MENSAJE_NO_SE_PUEDE_MOVER,
    );
    expect(resultado).toBe('abierto');
  });
});
