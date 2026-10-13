import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { EtiquetaPagoTarjetaComponent } from './etiqueta-pago-tarjeta.component';

describe('EtiquetaPagoTarjetaComponent', () => {
  function crear(): HTMLElement {
    const fixture = TestBed.createComponent(EtiquetaPagoTarjetaComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra el ícono credit_card y el texto Pago de tarjeta', () => {
    const etiqueta = crear();

    expect(etiqueta.querySelector('mat-icon')?.textContent?.trim()).toBe('credit_card');
    expect(etiqueta.querySelector('.texto')?.textContent?.trim()).toBe('Pago de tarjeta');
  });

  it('el estado se lee con texto: el ícono queda oculto a los lectores de pantalla', () => {
    const etiqueta = crear();

    expect(etiqueta.querySelector('mat-icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(etiqueta.textContent).toContain('Pago de tarjeta');
  });
});
