const REGION_POR_DEFECTO = 'en-US';

export function regionUsuario(): string {
  const candidata = typeof navigator !== 'undefined' ? navigator.language : undefined;
  if (!candidata) {
    return REGION_POR_DEFECTO;
  }

  try {
    Intl.NumberFormat.supportedLocalesOf(candidata);
    return candidata;
  } catch {
    return REGION_POR_DEFECTO;
  }
}
