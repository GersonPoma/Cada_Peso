import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { DatosDialogoConfirmacionMeta } from '../models/datos-dialogos-metas.model';
import { DialogoConfirmacionMetaComponent } from './dialogo-confirmacion-meta.component';

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoConfirmacionMetaComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let resultado: boolean | undefined | 'abierto';

  async function abrir(): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoConfirmacionMetaComponent,
      DatosDialogoConfirmacionMeta,
      boolean
    >(DialogoConfirmacionMetaComponent, {
      data: { titulo: 'Quitar meta', mensaje: 'Se quitará la meta.', confirmar: 'Quitar' },
    });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    fixture = TestBed.createComponent(AnfitrionDePrueba);
    cargador = TestbedHarnessEnvironment.documentRootLoader(fixture);
  });

  afterEach(() => TestBed.inject(MatDialog).closeAll());

  it('muestra el título y el mensaje', async () => {
    await abrir();
    const contenedor = document.querySelector('mat-dialog-container');

    expect(contenedor?.querySelector('h2')?.textContent).toBe('Quitar meta');
    expect(contenedor?.textContent).toContain('Se quitará la meta.');
  });

  it('confirmar cierra con true', async () => {
    await abrir();
    await (await cargador.getHarness(MatButtonHarness.with({ text: 'Quitar' }))).click();

    expect(resultado).toBe(true);
  });

  it('cancelar cierra con false', async () => {
    await abrir();
    await (await cargador.getHarness(MatButtonHarness.with({ text: 'Cancelar' }))).click();

    expect(resultado).toBe(false);
  });
});
