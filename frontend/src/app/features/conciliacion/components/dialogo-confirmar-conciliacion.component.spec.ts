import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { DatosDialogoConfirmarConciliacion } from '../models/datos-dialogo-confirmar-conciliacion.model';
import {
  AVISO_IRREVERSIBLE,
  DialogoConfirmarConciliacionComponent,
} from './dialogo-confirmar-conciliacion.component';

const DATOS: DatosDialogoConfirmarConciliacion = {
  cuenta: 'Banco',
  fecha: '2026-09-30',
  saldoExtracto: 150000,
  ajuste: -5000,
  moneda: 'USD',
};

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoConfirmarConciliacionComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let resultado: boolean | undefined | 'abierto';

  const contenedor = () => document.querySelector('mat-dialog-container') as HTMLElement | null;
  const texto = () => (contenedor()?.textContent ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ');
  const dato = (clase: string) =>
    (contenedor()?.querySelector(`dd.${clase}`)?.textContent ?? '')
      .replace(/[  ]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  const boton = (t: string) =>
    Array.from(contenedor()?.querySelectorAll('button') ?? []).find(
      (b) => b.textContent?.trim() === t,
    ) as HTMLButtonElement | undefined;

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function abrir(datos: Partial<DatosDialogoConfirmarConciliacion> = {}): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoConfirmarConciliacionComponent,
      DatosDialogoConfirmarConciliacion,
      boolean
    >(DialogoConfirmarConciliacionComponent, { data: { ...DATOS, ...datos } });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  }

  beforeEach(() => {
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    fixture = TestBed.createComponent(AnfitrionDePrueba);
  });

  afterEach(() => {
    TestBed.inject(MatDialog).closeAll();
    vi.unstubAllGlobals();
  });

  it('resume la fecha, el saldo y el ajuste, con el aviso de irreversibilidad', async () => {
    await abrir();

    expect(texto()).toContain('Reconciliar Banco');
    expect(dato('fecha')).toBe('September 30, 2026');
    expect(dato('saldo')).toBe('$150.00');
    expect(dato('ajuste')).toBe('-$5.00 (salida)');
    expect(texto()).toContain(AVISO_IRREVERSIBLE);
  });

  it('un ajuste positivo es una entrada', async () => {
    await abrir({ ajuste: 2000 });

    expect(dato('ajuste')).toBe('$2.00 (entrada)');
  });

  it('sin ajuste lo dice', async () => {
    await abrir({ ajuste: null });

    expect(dato('ajuste')).toBe('Sin ajuste');
  });

  // jsdom no calcula la visibilidad que el FocusTrap exige para mover el foco: se comprueba la
  // marca que lo dirige a Cancelar.
  it('el foco inicial va a Cancelar', async () => {
    await abrir();

    expect(boton('Cancelar')?.hasAttribute('cdkFocusInitial')).toBe(true);
    expect(boton('Reconciliar')?.hasAttribute('cdkFocusInitial')).toBe(false);
  });

  it('Cancelar cierra con false', async () => {
    await abrir();
    boton('Cancelar')?.click();
    await estable();

    expect(resultado).toBe(false);
  });

  it('Reconciliar cierra con true', async () => {
    await abrir();
    boton('Reconciliar')?.click();
    await estable();

    expect(resultado).toBe(true);
  });
});
