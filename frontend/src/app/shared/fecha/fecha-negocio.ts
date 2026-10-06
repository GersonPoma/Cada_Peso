/**
 * Convierte el `Date` elegido en un datepicker en una fecha de negocio `yyyy-MM-dd` (`LocalDate`
 * del backend), con el año, mes y día **locales**: el día que el usuario vio en el calendario.
 *
 * Nunca usar `toISOString()`, `toJSON()` ni `getUTC*()` para esto: pasan a UTC y, según la zona
 * horaria, corren la fecha un día (ej. medianoche del 1 de octubre en Tokio → `2026-09-30`).
 *
 * @returns `null` si no hay fecha elegida.
 * @throws Error si recibe un `Date` inválido (error de programación, no del usuario).
 */
export function aFechaNegocio(fecha: Date | null | undefined): string | null {
  if (fecha === null || fecha === undefined) {
    return null;
  }
  if (isNaN(fecha.getTime())) {
    throw new Error('aFechaNegocio: la fecha recibida no es válida');
  }
  const anio = String(fecha.getFullYear()).padStart(4, '0');
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}
