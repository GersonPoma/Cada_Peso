import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { GastoPorCategoriaResponse } from '../models/gasto-por-categoria-response.model';
import {
  MENSAJE_RANGO_INVALIDO,
  MENSAJE_SIN_MOVIMIENTOS,
  NOTA_PAGOS_TARJETA,
  NOTA_REEMBOLSOS,
} from '../services/mensajes-reporte';
import { GastoReporteComponent } from './gasto-reporte.component';

const URL = '/api/v1/presupuestos/3/reportes/gasto-por-categoria';

const OCTUBRE: GastoPorCategoriaResponse = {
  desde: '2026-10',
  hasta: '2026-10',
  total: 265000,
  grupos: [
    {
      grupoId: 1,
      nombre: 'Gastos diarios',
      total: 140000,
      porcentaje: 5283,
      categorias: [
        { categoriaId: 8, nombre: 'Comida', oculta: false, total: 140000, porcentaje: 5283 },
      ],
    },
    {
      grupoId: 2,
      nombre: 'Ahorro',
      total: 100000,
      porcentaje: 3774,
      categorias: [
        {
          categoriaId: 9,
          nombre: 'Metas de ahorro',
          oculta: false,
          total: 100000,
          porcentaje: 3774,
        },
      ],
    },
    {
      grupoId: 3,
      nombre: 'Casa',
      total: 20000,
      porcentaje: 755,
      categorias: [
        { categoriaId: 10, nombre: 'Hogar', oculta: true, total: 20000, porcentaje: 755 },
      ],
    },
  ],
  sinCategoria: { total: 5000, porcentaje: 189 },
};

/** Texto de un elemento con un espacio entre cada nodo de texto (como lo leería una persona). */
const textoSeparado = (elemento: Element) => {
  const recorrido = document.createTreeWalker(elemento, NodeFilter.SHOW_TEXT);
  const partes: string[] = [];
  while (recorrido.nextNode()) {
    partes.push(normalizar(recorrido.currentNode.textContent));
  }
  return partes.filter(Boolean).join(' ');
};

const normalizar = (texto: string | null | undefined) =>
  (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();

describe('GastoReporteComponent', () => {
  let fixture: ComponentFixture<GastoReporteComponent>;
  let backend: HttpTestingController;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => textoSeparado(elemento());
  const peticiones = () => backend.match((p) => p.url === URL);

  function crear(rango = { desde: '2026-10', hasta: '2026-10' }): void {
    fixture = TestBed.createComponent(GastoReporteComponent);
    fixture.componentRef.setInput('presupuestoId', 3);
    fixture.componentRef.setInput('rango', rango);
    fixture.componentRef.setInput('moneda', 'USD');
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('pide el gasto del rango y muestra total, grupos y porcentajes en orden', () => {
    crear();
    const [peticion] = peticiones();
    expect(peticion.request.params.get('desde')).toBe('2026-10');
    expect(peticion.request.params.get('hasta')).toBe('2026-10');
    peticion.flush(OCTUBRE);
    fixture.detectChanges();

    expect(texto()).toContain('Gasto total $265.00');
    const nombres = Array.from(elemento().querySelectorAll('h3')).map((h) => textoSeparado(h));
    expect(nombres).toEqual([
      'Gastos diarios $140.00 52.83%',
      'Ahorro $100.00 37.74%',
      'Casa $20.00 7.55%',
    ]);
    expect(texto()).toContain('Hogar Oculta');
    expect(texto()).toContain(NOTA_PAGOS_TARJETA);
  });

  it('muestra Sin categoría aparte con su porcentaje', () => {
    crear();
    peticiones()[0].flush(OCTUBRE);
    fixture.detectChanges();

    const sinCategoria = elemento().querySelector('.sin-categoria') as HTMLElement;
    expect(textoSeparado(sinCategoria)).toBe('Sin categoría $5.00 1.89%');
    expect(sinCategoria.querySelector('a')).toBeNull();
  });

  it('muestra los porcentajes de la API aunque no sumen 100 %', () => {
    crear();
    peticiones()[0].flush({
      ...OCTUBRE,
      sinCategoria: { total: 5000, porcentaje: 190 },
    });
    fixture.detectChanges();

    expect(texto()).toContain('1.90%');
  });

  it('una categoría con reembolsos mayores que el gasto va con su signo, nota y sin barra', () => {
    crear();
    peticiones()[0].flush({
      ...OCTUBRE,
      total: -20000,
      grupos: [
        {
          grupoId: 1,
          nombre: 'Ropa',
          total: -20000,
          porcentaje: 0,
          categorias: [
            { categoriaId: 11, nombre: 'Zapatos', oculta: false, total: -20000, porcentaje: 0 },
          ],
        },
      ],
      sinCategoria: { total: 0, porcentaje: 0 },
    });
    fixture.detectChanges();

    const fila = elemento().querySelector('.grupo li') as HTMLElement;
    expect(textoSeparado(fila)).toContain('-$20.00');
    expect(fila.textContent).toContain(NOTA_REEMBOLSOS);
    expect(fila.querySelector('.barra')).toBeNull();
  });

  it('Ver transacciones lleva a la lista filtrada por categoría y fechas', () => {
    crear({ desde: '2026-09', hasta: '2026-10' });
    peticiones()[0].flush(OCTUBRE);
    fixture.detectChanges();

    const enlace = elemento().querySelector('.grupo a') as HTMLAnchorElement;
    expect(enlace.getAttribute('href')).toBe(
      '/presupuestos/3/transacciones?categoriaId=8&desde=2026-09-01&hasta=2026-10-31',
    );
  });

  it('sin movimientos muestra el aviso de vacío', () => {
    crear();
    peticiones()[0].flush({
      desde: '2026-10',
      hasta: '2026-10',
      total: 0,
      grupos: [],
      sinCategoria: { total: 0, porcentaje: 0 },
    });
    fixture.detectChanges();

    expect(texto()).toBe(MENSAJE_SIN_MOVIMIENTOS);
  });

  it('un 400 muestra el aviso del rango y Reintentar repite la petición', () => {
    crear();
    peticiones()[0].flush(
      { codigo: 'DATOS_INVALIDOS', detail: 'no mostrar' },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(elemento().querySelector('[role="alert"]')?.textContent).toBe(MENSAJE_RANGO_INVALIDO);
    expect(texto()).not.toContain('no mostrar');
    (elemento().querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    const repetidas = peticiones();
    expect(repetidas).toHaveLength(1);
    repetidas[0].flush(OCTUBRE);
  });

  it('al cambiar el rango cancela la petición anterior y muestra solo la nueva', () => {
    crear({ desde: '2026-01', hasta: '2026-03' });
    const [vieja] = peticiones();
    fixture.componentRef.setInput('rango', { desde: '2026-04', hasta: '2026-06' });
    fixture.detectChanges();

    expect(vieja.cancelled).toBe(true);
    const [nueva] = peticiones();
    expect(nueva.request.params.get('desde')).toBe('2026-04');
    nueva.flush(OCTUBRE);
    fixture.detectChanges();
    expect(texto()).toContain('$265.00');
  });

  it('inactiva no pide nada y al activarse pide una sola vez', () => {
    fixture = TestBed.createComponent(GastoReporteComponent);
    fixture.componentRef.setInput('presupuestoId', 3);
    fixture.componentRef.setInput('rango', { desde: '2026-10', hasta: '2026-10' });
    fixture.componentRef.setInput('moneda', 'USD');
    fixture.componentRef.setInput('activa', false);
    fixture.detectChanges();
    expect(peticiones()).toHaveLength(0);

    fixture.componentRef.setInput('activa', true);
    fixture.detectChanges();
    peticiones()[0].flush(OCTUBRE);

    fixture.componentRef.setInput('activa', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('activa', true);
    fixture.detectChanges();
    expect(peticiones()).toHaveLength(0);
  });
});
