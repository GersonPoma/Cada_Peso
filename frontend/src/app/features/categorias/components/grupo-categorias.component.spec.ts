import { CdkDrag } from '@angular/cdk/drag-drop';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { CategoriaResponse } from '../models/categoria-response.model';
import { GrupoCategoriaConCategoriasResponse } from '../models/grupo-categoria-con-categorias-response.model';
import { GrupoCategoriasComponent } from './grupo-categorias.component';

const INSTANTE = '2026-10-01T00:00:00Z';

function categoria(id: number, nombre: string, extra: Partial<CategoriaResponse> = {}) {
  return {
    id,
    grupoId: 1,
    nombre,
    orden: id,
    oculta: false,
    nota: null,
    esPagoTarjeta: false,
    cuentaId: null,
    fechaCreacion: INSTANTE,
    fechaActualizacion: INSTANTE,
    ...extra,
  } satisfies CategoriaResponse;
}

function grupo(
  categorias: CategoriaResponse[],
  extra: Partial<GrupoCategoriaConCategoriasResponse> = {},
): GrupoCategoriaConCategoriasResponse {
  return {
    id: 1,
    nombre: 'Necesidades',
    orden: 0,
    oculto: false,
    tipo: 'NORMAL',
    categorias,
    ...extra,
  };
}

const GRUPO_NORMAL = grupo([categoria(10, 'Luz', { nota: 'Vence el 10' })]);

const GRUPO_PAGOS = grupo([categoria(20, 'Pago: Visa', { esPagoTarjeta: true, cuentaId: 4 })], {
  id: 2,
  nombre: 'Pagos de tarjetas de crédito',
  tipo: 'PAGOS_TARJETA',
});

describe('GrupoCategoriasComponent', () => {
  let fixture: ComponentFixture<GrupoCategoriasComponent>;

  function crear(datos: GrupoCategoriaConCategoriasResponse): HTMLElement {
    fixture = TestBed.createComponent(GrupoCategoriasComponent);
    fixture.componentRef.setInput('grupo', datos);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const etiquetas = (raiz: HTMLElement) => raiz.querySelectorAll('app-etiqueta-pago-tarjeta');
  const boton = (raiz: HTMLElement, etiqueta: string) =>
    raiz.querySelector(`button[aria-label="${etiqueta}"]`);
  const textoBoton = (raiz: HTMLElement, texto: string) =>
    Array.from(raiz.querySelectorAll('button')).find((b) => b.textContent?.includes(texto));
  const filasCategoria = (raiz: HTMLElement) => raiz.querySelectorAll('.categoria');

  describe('un grupo normal', () => {
    it('conserva su asa, su menú, Agregar categoría y las acciones de sus categorías', () => {
      const raiz = crear(GRUPO_NORMAL);

      expect(raiz.querySelector('.cabecera [cdkDragHandle], .cabecera .asa')).not.toBeNull();
      expect(textoBoton(raiz, 'Agregar categoría')).toBeDefined();
      expect(boton(raiz, 'Acciones del grupo Necesidades')).not.toBeNull();
      expect(boton(raiz, 'Acciones de Luz')).not.toBeNull();
      expect(filasCategoria(raiz)[0].querySelector('.asa')).not.toBeNull();
      expect(raiz.querySelector('.nota')?.textContent?.trim()).toBe('Vence el 10');
    });

    it('no lleva ninguna etiqueta de pago de tarjeta', () => {
      expect(etiquetas(crear(GRUPO_NORMAL)).length).toBe(0);
    });
  });

  describe('el grupo de pagos', () => {
    it('lleva la etiqueta y no tiene asa, menú ni Agregar categoría', () => {
      const raiz = crear(GRUPO_PAGOS);
      const cabecera = raiz.querySelector('.cabecera') as HTMLElement;

      expect(cabecera.querySelector('app-etiqueta-pago-tarjeta')?.textContent).toContain(
        'Pago de tarjeta',
      );
      expect(cabecera.querySelector('.asa')).toBeNull();
      expect(textoBoton(raiz, 'Agregar categoría')).toBeUndefined();
      expect(boton(raiz, 'Acciones del grupo Pagos de tarjetas de crédito')).toBeNull();
    });
  });

  describe('la categoría de pago', () => {
    it('lleva la etiqueta y la nota, sin asa ni menú de acciones', () => {
      const raiz = crear(GRUPO_PAGOS);
      const fila = filasCategoria(raiz)[0] as HTMLElement;

      expect(fila.querySelector('app-etiqueta-pago-tarjeta')).not.toBeNull();
      expect(fila.querySelector('.nota')?.textContent?.trim()).toBe(
        'Se crea y se renombra sola con la tarjeta',
      );
      expect(fila.querySelector('.asa')).toBeNull();
      expect(boton(raiz, 'Acciones de Pago: Visa')).toBeNull();
    });

    it('no se puede arrastrar', () => {
      crear(GRUPO_PAGOS);
      const arrastre = fixture.debugElement
        .queryAll((d) => d.nativeElement.classList?.contains('categoria'))[0]
        .injector.get(CdkDrag);

      expect(arrastre.disabled).toBe(true);
    });

    it('oculta por una tarjeta cerrada: ícono de oculta, etiqueta y sin acciones', () => {
      const raiz = crear(
        grupo([categoria(20, 'Pago: Visa', { esPagoTarjeta: true, oculta: true })], {
          id: 2,
          tipo: 'PAGOS_TARJETA',
        }),
      );
      const fila = filasCategoria(raiz)[0] as HTMLElement;

      expect(fila.classList).toContain('oculta');
      expect(fila.querySelector('.icono-oculto')?.getAttribute('aria-label')).toBe('Oculta');
      expect(fila.querySelector('app-etiqueta-pago-tarjeta')).not.toBeNull();
      expect(fila.querySelector('button')).toBeNull();
    });

    it('una categoría normal junto a ella conserva su asa y su menú', () => {
      const raiz = crear(
        grupo([categoria(10, 'Luz'), categoria(20, 'Pago: Visa', { esPagoTarjeta: true })], {
          tipo: 'NORMAL',
        }),
      );

      expect(filasCategoria(raiz)[0].querySelector('.asa')).not.toBeNull();
      expect(boton(raiz, 'Acciones de Luz')).not.toBeNull();
      expect(boton(raiz, 'Acciones de Pago: Visa')).toBeNull();
    });
  });

  describe('qué acepta la lista al soltar', () => {
    type Predicado = (arrastre: { data?: unknown }) => boolean;
    const predicado = () =>
      (fixture.componentInstance as unknown as { soloCategorias: Predicado }).soloCategorias;
    const categoriaArrastrada = { data: { tipo: 'categoria', categoria: categoria(10, 'Luz') } };
    const grupoArrastrado = { data: { tipo: 'grupo', grupo: GRUPO_NORMAL } };

    it('un grupo normal acepta categorías y rechaza grupos', () => {
      crear(GRUPO_NORMAL);

      expect(predicado()(categoriaArrastrada)).toBe(true);
      expect(predicado()(grupoArrastrado)).toBe(false);
    });

    it('el grupo de pagos no acepta ninguna categoría', () => {
      crear(GRUPO_PAGOS);

      expect(predicado()(categoriaArrastrada)).toBe(false);
      expect(predicado()(grupoArrastrado)).toBe(false);
    });
  });
});
