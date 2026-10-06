import { CuentaResponse } from './cuenta-response.model';

/** Datos con los que se abre el diálogo de cuenta: crear una nueva o editar una existente. */
export type DatosDialogoCuenta = { modo: 'crear' } | { modo: 'editar'; cuenta: CuentaResponse };
