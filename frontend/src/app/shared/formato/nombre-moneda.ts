import { regionUsuario } from './region-usuario';

/**
 * Moneda para mostrar a la persona: su código ISO 4217 y su nombre en el idioma de la región
 * detectada, con la primera letra en mayúscula (`USD · Dólar estadounidense`). Si el navegador no
 * conoce el nombre, el código no es válido o `Intl.DisplayNames` no existe, devuelve solo el
 * código recibido, sin lanzar errores.
 */
export function nombreDeMoneda(codigo: string): string {
  try {
    const region = regionUsuario();
    const nombre = new Intl.DisplayNames([region], { type: 'currency' }).of(codigo);
    if (!nombre || nombre.toUpperCase() === codigo.toUpperCase()) {
      return codigo;
    }
    return `${codigo} · ${nombre.charAt(0).toLocaleUpperCase(region)}${nombre.slice(1)}`;
  } catch {
    return codigo;
  }
}
