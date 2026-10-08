import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AVISO_BORRADO } from '../services/mensajes-programada';
import {
  DatosConfirmarBorrado,
  DialogoConfirmarBorradoComponent,
} from './dialogo-confirmar-borrado.component';

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoConfirmarBorradoComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let resultado: boolean | undefined | 'abierto';

  const botones = () =>
    Array.from(document.querySelectorAll('mat-dialog-container button')) as HTMLButtonElement[];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    fixture = TestBed.createComponent(AnfitrionDePrueba);
    TestBed.inject(MatDialog)
      .open<DialogoConfirmarBorradoComponent, DatosConfirmarBorrado, boolean>(
        DialogoConfirmarBorradoComponent,
        { data: { nombre: 'Inmobiliaria (Banco)' } },
      )
      .afterClosed()
      .subscribe((valor) => (resultado = valor));
    resultado = 'abierto';
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => TestBed.inject(MatDialog).closeAll());

  it('nombra la programada y aclara que lo generado se conserva', () => {
    const texto = document.querySelector('mat-dialog-container')?.textContent ?? '';
    expect(texto).toContain('Inmobiliaria (Banco)');
    expect(texto).toContain(AVISO_BORRADO);
  });

  it('el foco inicial está en Cancelar', () => {
    expect(botones()[0].textContent?.trim()).toBe('Cancelar');
    expect(botones()[0].hasAttribute('cdkFocusInitial')).toBe(true);
  });

  it.each([
    ['Cancelar', false],
    ['Borrar', true],
  ])('%s cierra con %s', async (texto, valor) => {
    botones()
      .find((b) => b.textContent?.trim() === texto)
      ?.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(resultado).toBe(valor);
  });
});
