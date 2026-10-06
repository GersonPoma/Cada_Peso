import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, Validators } from '@angular/forms';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../core/material/proveer-material';
import { CampoMontoComponent } from './campo-monto.component';

@Component({
  imports: [CampoMontoComponent],
  template: `<app-campo-monto
    [control]="monto"
    etiqueta="Importe"
    [requerido]="true"
    [mensajes]="{ min: 'Debe ser mayor que 0' }"
  />`,
})
class AnfitrionDePrueba {
  readonly monto = new FormControl<number | null>(12500, [Validators.required, Validators.min(1)]);
}

describe('CampoMontoComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const entrada = () => elemento().querySelector('input') as HTMLInputElement;
  const control = () => fixture.componentInstance.monto;

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function escribir(texto: string): Promise<void> {
    entrada().value = texto;
    entrada().dispatchEvent(new Event('input'));
    await estable();
  }

  async function salir(): Promise<void> {
    entrada().dispatchEvent(new Event('blur'));
    await estable();
  }

  beforeEach(async () => {
    vi.stubGlobal('navigator', { language: 'es-BO', userAgent: '' });
    TestBed.configureTestingModule({ providers: [...proveerMaterial()] });
    fixture = TestBed.createComponent(AnfitrionDePrueba);
    await estable();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('muestra la etiqueta y el valor inicial con el separador de la región', () => {
    expect(elemento().querySelector('mat-label')?.textContent).toBe('Importe');
    expect(entrada().value).toBe('12,5');
    expect(entrada().required).toBe(true);
  });

  it('al salir evalúa la expresión, reescribe el texto y pone el valor en milésimas', async () => {
    await escribir('30+20,5');
    await salir();

    expect(entrada().value).toBe('50,5');
    expect(control().value).toBe(50500);
  });

  it('Enter también evalúa', async () => {
    await escribir('10*3');
    entrada().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await estable();

    expect(control().value).toBe(30000);
    expect(entrada().value).toBe('30');
  });

  it('muestra la pista con el resultado mientras se escribe una expresión', async () => {
    await escribir('10*3');

    expect(elemento().querySelector('.pista')?.textContent).toBe('= 30');
  });

  it('sin operador no muestra pista', async () => {
    await escribir('-10');

    expect(elemento().querySelector('.pista')).toBeNull();
  });

  it('una expresión inválida marca "Monto no válido" sin borrar lo escrito', async () => {
    await escribir('5+');
    await salir();

    expect(entrada().value).toBe('5+');
    expect(control().value).toBe(12500);
    expect(elemento().querySelector('mat-error')?.textContent).toContain('Monto no válido');
  });

  it('vacío deja el control en null y muestra que es obligatorio', async () => {
    await escribir('');
    await salir();

    expect(control().value).toBeNull();
    expect(elemento().querySelector('mat-error')?.textContent).toContain('El monto es obligatorio');
  });

  it('usa los mensajes propios de quien lo usa', async () => {
    await escribir('0');
    await salir();

    expect(control().value).toBe(0);
    expect(elemento().querySelector('mat-error')?.textContent).toContain('Debe ser mayor que 0');
  });

  it('se sincroniza cuando el control cambia desde fuera', async () => {
    control().setValue(7000);
    await estable();

    expect(entrada().value).toBe('7');
  });
});
