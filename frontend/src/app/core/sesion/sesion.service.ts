import { Injectable, computed, signal } from '@angular/core';

/** Sesión de la persona: el token JWT y el instante (ISO-8601) en que expira. */
export interface Sesion {
  token: string;
  expiraEn: string;
}

/** Clave de `localStorage`; un solo valor para que token y expiración no se desincronicen. */
export const CLAVE_SESION = 'cada-peso.sesion';

/**
 * Guarda y expone la sesión. Al arrancar la restaura de `localStorage` solo si el token no ha
 * expirado; un valor ilegible, incompleto o vencido se borra y se arranca sin sesión. Si el
 * almacenamiento no está disponible, la sesión vive en memoria mientras la página siga abierta.
 * Durante el uso, un token que vence se detecta por el 401 del interceptor.
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly estado = signal<Sesion | null>(this.restaurar());

  readonly sesion = this.estado.asReadonly();
  readonly haySesion = computed(() => this.estado() !== null);

  token(): string | null {
    return this.estado()?.token ?? null;
  }

  iniciar(token: string, expiraEn: string): void {
    const sesion: Sesion = { token, expiraEn };
    try {
      localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
    } catch {
      // Sin almacenamiento: la sesión queda solo en memoria.
    }
    this.estado.set(sesion);
  }

  cerrar(): void {
    this.borrar();
    this.estado.set(null);
  }

  private restaurar(): Sesion | null {
    let texto: string | null;
    try {
      texto = localStorage.getItem(CLAVE_SESION);
    } catch {
      return null;
    }
    if (texto === null) {
      return null;
    }
    const sesion = this.interpretar(texto);
    if (sesion === null) {
      this.borrar();
    }
    return sesion;
  }

  /** Devuelve la sesión si el texto es un `{ token, expiraEn }` con expiración futura. */
  private interpretar(texto: string): Sesion | null {
    let valor: unknown;
    try {
      valor = JSON.parse(texto);
    } catch {
      return null;
    }
    if (valor === null || typeof valor !== 'object') {
      return null;
    }
    const { token, expiraEn } = valor as Record<string, unknown>;
    if (typeof token !== 'string' || typeof expiraEn !== 'string') {
      return null;
    }
    const expiracion = Date.parse(expiraEn);
    if (Number.isNaN(expiracion) || expiracion <= Date.now()) {
      return null;
    }
    return { token, expiraEn };
  }

  private borrar(): void {
    try {
      localStorage.removeItem(CLAVE_SESION);
    } catch {
      // Sin almacenamiento: no hay nada que borrar.
    }
  }
}
