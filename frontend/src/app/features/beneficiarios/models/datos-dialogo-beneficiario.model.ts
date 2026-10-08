import { BeneficiarioResponse } from './beneficiario-response.model';
import { GrupoCategoriasLectura } from './grupo-categorias-lectura.model';

/** Datos del diálogo de beneficiario: crear (`beneficiario` nulo) o editar, con el árbol. */
export interface DatosDialogoBeneficiario {
  beneficiario: BeneficiarioResponse | null;
  grupos: GrupoCategoriasLectura[];
}

/** Cómo terminó el diálogo de beneficiario. */
export type ResultadoDialogoBeneficiario = { tipo: 'guardado' } | { tipo: 'recargar' };
