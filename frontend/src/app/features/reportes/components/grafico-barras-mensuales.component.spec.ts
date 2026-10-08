import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { GraficoBarrasMensualesComponent } from './grafico-barras-mensuales.component';

describe('GraficoBarrasMensualesComponent', () => {
  let fixture: ComponentFixture<GraficoBarrasMensualesComponent>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const barras = () => Array.from(elemento().querySelectorAll('svg[role="img"] rect.barra'));
  const numero = (e: Element, atributo: string) => Number(e.getAttribute(atributo));

  beforeEach(() => {
    fixture = TestBed.createComponent(GraficoBarrasMensualesComponent);
    fixture.componentRef.setInput('meses', ['2026-10', '2026-11']);
    fixture.componentRef.setInput('series', [
      { nombre: 'Ingresos', valores: [500000, 0], patron: 'solido' },
      { nombre: 'Gastos', valores: [265000, 30000], patron: 'rayado' },
    ]);
    fixture.componentRef.setInput('linea', { nombre: 'Neto', valores: [235000, -30000] });
    fixture.componentRef.setInput('moneda', 'USD');
    fixture.componentRef.setInput('descripcion', 'Ingresos contra gastos');
    fixture.detectChanges();
  });

  it('es una imagen con su descripción y se adapta al ancho', () => {
    const svg = elemento().querySelector('svg[role="img"]') as SVGElement;
    expect(svg.getAttribute('aria-label')).toBe('Ingresos contra gastos');
    expect(svg.getAttribute('viewBox')).toBe('0 0 640 280');
  });

  it('dibuja una barra por serie y mes, con coordenadas enteras', () => {
    expect(barras()).toHaveLength(4);
    for (const barra of barras()) {
      for (const atributo of ['x', 'y', 'width', 'height']) {
        expect(Number.isInteger(numero(barra, atributo))).toBe(true);
      }
    }
  });

  it('las series se distinguen por el relleno además del color', () => {
    const rayadas = barras().filter((b) => b.classList.contains('barra-rayado'));
    expect(rayadas).toHaveLength(2);
    expect(rayadas[0].getAttribute('fill')).toMatch(/^url\(#rayado-\d+\)$/);
  });

  it('una barra más alta representa un monto mayor', () => {
    const [ingresosOctubre, gastosOctubre] = barras();
    expect(numero(ingresosOctubre, 'height')).toBeGreaterThan(numero(gastosOctubre, 'height'));
  });

  it('cada barra y cada punto tienen un título con el monto', () => {
    expect(barras()[0].querySelector('title')?.textContent).toContain('$500.00');
    const puntos = elemento().querySelectorAll('svg[role="img"] circle.punto');
    expect(puntos).toHaveLength(2);
    expect(puntos[1].querySelector('title')?.textContent).toContain('-$30.00');
  });

  it('el neto negativo queda por debajo de la línea del cero', () => {
    const cero = numero(elemento().querySelector('line.cero') as Element, 'y1');
    const puntos = elemento().querySelectorAll('svg[role="img"] circle.punto');
    expect(numero(puntos[1], 'cy')).toBeGreaterThan(cero);
  });

  it('la leyenda nombra cada serie y la línea', () => {
    expect(elemento().querySelector('figcaption')?.textContent?.replace(/\s+/g, ' ')).toContain(
      'Ingresos Gastos Neto',
    );
  });
});
