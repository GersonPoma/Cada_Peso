import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { SesionService } from '../sesion/sesion.service';
import { sesionGuard } from './sesion.guard';

describe('sesionGuard', () => {
  function ejecutar(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      sesionGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('deja pasar con sesión', () => {
    TestBed.inject(SesionService).iniciar('abc', '2026-10-07T12:00:00Z');

    expect(ejecutar()).toBe(true);
  });

  it('sin sesión lleva a /login', () => {
    const resultado = ejecutar();

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/login');
  });
});
