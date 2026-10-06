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

/**
 * Lo inverso de `aFechaNegocio`: el `Date` local (a medianoche) de una fecha `yyyy-MM-dd`, para
 * mostrarla en un datepicker sin que se corra de día. `null` si el texto no es una fecha real.
 */
export function deFechaNegocio(texto: string | null | undefined): Date | null {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(texto ?? '');
  if (!partes) {
    return null;
  }
  const [anio, mes, dia] = [Number(partes[1]), Number(partes[2]), Number(partes[3])];
  const fecha = new Date(anio, mes - 1, dia);
  fecha.setFullYear(anio);
  return fecha.getMonth() === mes - 1 && fecha.getDate() === dia ? fecha : null;
}
