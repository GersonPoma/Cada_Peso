import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSelectHarness } from '@angular/material/select/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { BarraLoteComponent } from './barra-lote.component';
import { DialogoConfirmacionComponent } from './dialogo-confirmacion.component';

@Component({
  imports: [BarraLoteComponent],
  template: `<app-barra-lote
    [cantidad]="cantidad"
    [grupos]="grupos"
    (aprobar)="eventos.push('aprobar')"
    (categorizar)="eventos.push('categorizar:' + $event)"
    (borrar)="eventos.push('borrar')"
    (limpiar)="eventos.push('limpiar')"
  />`,
})
class AnfitrionDePrueba {
  cantidad = 3;
  readonly grupos = [
    {
      id: 1,
      nombre: 'Necesidades',
      oculto: false,
      categorias: [{ id: 7, nombre: 'Comida', oculta: false }],
    },
  ];
  readonly eventos: string[] = [];
}

describe('BarraLoteComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;

  const boton = (texto: RegExp) => cargador.getHarness(MatButtonHarness.with({ text: texto }));

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    fixture = TestBed.createComponent(AnfitrionDePrueba);
    cargador = TestbedHarnessEnvironment.loader(fixture);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => TestBed.inject(MatDialog).closeAll());

  it('muestra la cantidad seleccionada', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('3 seleccionadas');
  });

  it('emite cada acción; Categorizar solo con una categoría elegida', async () => {
    await (await boton(/Aprobar/)).click();
    const categorizar = await boton(/Categorizar/);
    expect(await categorizar.isDisabled()).toBe(true);
    await (await cargador.getHarness(MatSelectHarness)).clickOptions({ text: 'Comida' });
    await categorizar.click();
    await (await boton(/Borrar/)).click();
    await (await boton(/Quitar selección/)).click();

    expect(fixture.componentInstance.eventos).toEqual([
      'aprobar',
      'categorizar:7',
      'borrar',
      'limpiar',
    ]);
  });

  it('el diálogo de confirmación se cierra con true al confirmar', async () => {
    let resultado: unknown;
    TestBed.inject(MatDialog)
      .open(DialogoConfirmacionComponent, {
        data: { titulo: 'Borrar', mensaje: '¿Seguro?', confirmar: 'Borrar' },
      })
      .afterClosed()
      .subscribe((r) => (resultado = r));
    fixture.detectChanges();
    await fixture.whenStable();

    const loader = TestbedHarnessEnvironment.documentRootLoader(fixture);
    expect(document.querySelector('mat-dialog-container')?.textContent).toContain('¿Seguro?');
    await (
      await loader.getHarness(
        MatButtonHarness.with({ text: 'Borrar', ancestor: 'mat-dialog-container' }),
      )
    ).click();
    await fixture.whenStable();

    expect(resultado).toBe(true);
  });
});
