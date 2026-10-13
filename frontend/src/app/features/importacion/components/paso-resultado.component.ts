import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatAnchor } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { CuentaImportacion } from '../models/cuenta-importacion.model';
import { ImportacionResponse } from '../models/importacion-response.model';
import { notaListoParaAsignar } from '../services/nota-listo-para-asignar';

/**
 * Paso 5: resultado de la importación. Las cifras del `201` (`omitidas` se rotula `Inválidas
 * omitidas`), la nota sobre `Listo para asignar` según la cuenta y el enlace a las transacciones sin
 * aprobar de esa cuenta. El resultado se anuncia con `aria-live`.
 */
@Component({
  selector: 'app-paso-resultado',
  imports: [MatAnchor, MatIcon, RouterLink],
  templateUrl: './paso-resultado.component.html',
  styleUrl: './paso-resultado.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasoResultadoComponent {
  readonly resultado = input.required<ImportacionResponse>();
  readonly cuenta = input.required<CuentaImportacion>();
  readonly presupuestoId = input.required<number>();

  protected readonly cifras = computed(() => {
    const r = this.resultado();
    return [
      { clave: 'importadas', rotulo: 'Importadas', valor: r.importadas },
      { clave: 'duplicadas', rotulo: 'Duplicadas', valor: r.duplicadas },
      { clave: 'omitidas', rotulo: 'Inválidas omitidas', valor: r.omitidas },
      { clave: 'sin-categoria', rotulo: 'Sin categoría', valor: r.sinCategoria },
    ];
  });
  protected readonly nota = computed(() => notaListoParaAsignar(this.cuenta()));
  protected readonly rutaTransacciones = computed(() => [
    '/presupuestos',
    this.presupuestoId(),
    'transacciones',
  ]);
  protected readonly parametros = computed(() => ({
    cuentaId: this.cuenta().id,
    sinAprobar: 1,
  }));
}
