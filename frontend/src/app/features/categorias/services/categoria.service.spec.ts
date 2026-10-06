import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CategoriaService } from './categoria.service';

const BASE = '/api/v1/presupuestos/3';

describe('CategoriaService', () => {
  let servicio: CategoriaService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(CategoriaService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it.each([false, true])('obtenerArbol pide el árbol con incluirOcultas=%s', (incluirOcultas) => {
    let arbol: unknown;

    servicio.obtenerArbol(3, incluirOcultas).subscribe((respuesta) => (arbol = respuesta));
    const peticion = backend.expectOne(
      (p) =>
        p.url === `${BASE}/categorias` && p.params.get('incluirOcultas') === String(incluirOcultas),
    );
    peticion.flush([]);

    expect(peticion.request.method).toBe('GET');
    expect(arbol).toEqual([]);
  });

  const casos: [string, () => Observable<unknown>, string, string, unknown][] = [
    [
      'crearGrupo',
      () => TestBed.inject(CategoriaService).crearGrupo(3, { nombre: 'Mascotas' }),
      'POST',
      `${BASE}/grupos-categorias`,
      { nombre: 'Mascotas' },
    ],
    [
      'renombrarGrupo',
      () => TestBed.inject(CategoriaService).renombrarGrupo(3, 4, { nombre: 'Gustos' }),
      'PUT',
      `${BASE}/grupos-categorias/4`,
      { nombre: 'Gustos' },
    ],
    [
      'ocultarGrupo',
      () => TestBed.inject(CategoriaService).ocultarGrupo(3, 4),
      'POST',
      `${BASE}/grupos-categorias/4/ocultar`,
      null,
    ],
    [
      'mostrarGrupo',
      () => TestBed.inject(CategoriaService).mostrarGrupo(3, 4),
      'POST',
      `${BASE}/grupos-categorias/4/mostrar`,
      null,
    ],
    [
      'moverGrupo',
      () => TestBed.inject(CategoriaService).moverGrupo(3, 4, { posicion: 2 }),
      'POST',
      `${BASE}/grupos-categorias/4/mover`,
      { posicion: 2 },
    ],
    [
      'crearCategoria',
      () =>
        TestBed.inject(CategoriaService).crearCategoria(3, {
          grupoId: 4,
          nombre: 'Libros',
          nota: null,
        }),
      'POST',
      `${BASE}/categorias`,
      { grupoId: 4, nombre: 'Libros', nota: null },
    ],
    [
      'editarCategoria',
      () => TestBed.inject(CategoriaService).editarCategoria(3, 7, { nombre: 'Luz', nota: 'x' }),
      'PUT',
      `${BASE}/categorias/7`,
      { nombre: 'Luz', nota: 'x' },
    ],
    [
      'ocultarCategoria',
      () => TestBed.inject(CategoriaService).ocultarCategoria(3, 7),
      'POST',
      `${BASE}/categorias/7/ocultar`,
      null,
    ],
    [
      'mostrarCategoria',
      () => TestBed.inject(CategoriaService).mostrarCategoria(3, 7),
      'POST',
      `${BASE}/categorias/7/mostrar`,
      null,
    ],
    [
      'moverCategoria',
      () => TestBed.inject(CategoriaService).moverCategoria(3, 7, { grupoId: 4, posicion: 0 }),
      'POST',
      `${BASE}/categorias/7/mover`,
      { grupoId: 4, posicion: 0 },
    ],
  ];

  it.each(casos)('%s hace %s %s con el cuerpo', (_nombre, llamar, metodo, url, cuerpo) => {
    let respuesta: unknown;

    llamar().subscribe((r) => (respuesta = r));
    const peticion = backend.expectOne(url);
    peticion.flush({ id: 1 });

    expect(servicio).toBeTruthy();
    expect(peticion.request.method).toBe(metodo);
    expect(peticion.request.body).toEqual(cuerpo);
    expect(respuesta).toEqual({ id: 1 });
  });
});
