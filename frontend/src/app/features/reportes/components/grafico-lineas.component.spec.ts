import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { GraficoLineasComponent, SerieLineas } from './grafico-lineas.component';

const SERIES: SerieLineas[] = [
  { nombre: 'Patrimonio', valores: [-1250000, -1000000], trazo: 'solido', marcador: 'circulo' },
  { nombre: 'Activos', valores: [1800000, 2000000], trazo: 'discontinuo', marcador: 'cuadrado' },
  { nombre: 'Pasivos', valores: [3050000, 3000000], trazo: 'punteado', marcador: 'triangulo' },
];

describe('GraficoLineasComponent', () => {
  let fixture: ComponentFixture<GraficoLineasComponent>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const svg = () => elemento().querySelector('svg[role="img"]') as SVGElement;

  function crear(meses: string[], series: SerieLineas[]): void {
    fixture = TestBed.createComponent(GraficoLineasComponent);
    fixture.componentRef.setInput('meses', meses);
    fixture.componentRef.setInput('series', series);
    fixture.componentRef.setInput('moneda', 'USD');
    fixture.componentRef.setInput('descripcion', 'Patrimonio por mes');
    fixture.detectChanges();
  }

  beforeEach(() => crear(['2026-09', '2026-10'], SERIES));

  it('es una imagen con su descripción', () => {
    expect(svg().getAttribute('aria-label')).toBe('Patrimonio por mes');
  });

  it('dibuja una línea por serie con trazo y marcador distintos', () => {
    const grupos = Array.from(svg().querySelectorAll('g.serie'));
    expect(grupos.map((g) => g.getAttribute('class'))).toEqual([
      'serie serie-0 trazo-solido',
      'serie serie-1 trazo-discontinuo',
      'serie serie-2 trazo-punteado',
    ]);
    expect(grupos[0].querySelectorAll('circle.marcador')).toHaveLength(2);
    expect(grupos[1].querySelectorAll('rect.marcador')).toHaveLength(2);
    expect(grupos[2].querySelectorAll('polygon.marcador')).toHaveLength(2);
  });

  it('con negativos dibuja la línea del cero', () => {
    expect(svg().querySelector('line.cero')).not.toBeNull();
  });

  it('sin negativos no dibuja la línea del cero', () => {
    crear(['2026-09'], [SERIES[1]]);

    expect(svg().querySelector('line.cero')).toBeNull();
  });

  it('con un solo mes dibuja un punto sin línea', () => {
    crear(['2026-10'], [SERIES[1]]);

    expect(svg().querySelector('polyline')).toBeNull();
    expect(svg().querySelectorAll('.marcador')).toHaveLength(1);
  });

  it('cada punto tiene un título con el monto y la leyenda nombra las series', () => {
    expect(svg().querySelector('g.serie-0 title')?.textContent).toContain('-$1,250.00');
    expect(elemento().querySelector('figcaption')?.textContent?.replace(/\s+/g, ' ')).toContain(
      'Patrimonio Activos Pasivos',
    );
  });
});
