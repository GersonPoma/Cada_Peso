import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import {
  FilaBarra,
  GraficoBarrasHorizontalesComponent,
} from './grafico-barras-horizontales.component';

const FILAS: FilaBarra[] = [
  { clave: 8, etiqueta: 'Comida', valor: 140000, porcentaje: '52,83%' },
  { clave: 9, etiqueta: 'Hogar', valor: 20000, porcentaje: '7,55%', marca: 'Oculta' },
  {
    clave: 10,
    etiqueta: 'Ropa',
    valor: -20000,
    porcentaje: '0,00%',
    nota: 'Reembolsos mayores que el gasto',
    enlace: { texto: 'Ver transacciones', ruta: ['/x'], queryParams: { categoriaId: 10 } },
  },
];

describe('GraficoBarrasHorizontalesComponent', () => {
  let fixture: ComponentFixture<GraficoBarrasHorizontalesComponent>;

  const filas = () => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('li'));

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(GraficoBarrasHorizontalesComponent);
    fixture.componentRef.setInput('filas', FILAS);
    fixture.componentRef.setInput('moneda', 'USD');
    fixture.detectChanges();
  });

  it('cada fila dice su etiqueta, su monto y su porcentaje', () => {
    expect(filas()).toHaveLength(3);
    expect(filas()[0].textContent).toContain('Comida');
    expect(filas()[0].textContent).toContain('$140.00');
    expect(filas()[0].textContent).toContain('52,83%');
    expect(filas()[1].textContent).toContain('Oculta');
  });

  it('el ancho de la barra es proporcional al mayor valor', () => {
    const barras = filas().map(
      (fila) => (fila.querySelector('.barra') as HTMLElement | null)?.style.width ?? null,
    );
    expect(barras[0]).toBe('100%');
    expect(barras[1]).toBe('14.2%');
  });

  it('un valor negativo no tiene barra y muestra su nota', () => {
    expect(filas()[2].querySelector('.barra')).toBeNull();
    expect(filas()[2].textContent).toContain('-$20.00');
    expect(filas()[2].textContent).toContain('Reembolsos mayores que el gasto');
  });

  it('el enlace lleva a su ruta con los parámetros', () => {
    const enlace = filas()[2].querySelector('a') as HTMLAnchorElement;
    expect(enlace.getAttribute('href')).toBe('/x?categoriaId=10');
    expect(enlace.textContent).toContain('Ver transacciones de Ropa');
  });

  it('las barras no se leen como contenido', () => {
    for (const pista of filas().map((f) => f.querySelector('.pista'))) {
      expect(pista?.getAttribute('aria-hidden')).toBe('true');
    }
  });

  it('acepta un máximo externo', () => {
    fixture.componentRef.setInput('maximo', 280000);
    fixture.detectChanges();

    expect((filas()[0].querySelector('.barra') as HTMLElement).style.width).toBe('50%');
  });
});
