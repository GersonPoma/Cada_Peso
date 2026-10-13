import { Injectable, computed, inject } from '@angular/core';
import { forkJoin, map } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { mesActual } from '../../../shared/fecha/mes';
import { CuentasConSaldos } from '../models/cuenta-resumen.model';
import { crearCargador } from './cargador-tarjeta';
import { ResumenLecturaService } from './resumen-lectura.service';

/**
 * Las fuentes de las tarjetas de Inicio, cada una con su propio cargador: salen todas a la vez al
 * crearse (con presupuesto activo) y el fallo o el reintento de una no toca a las otras. `mes`
 * alimenta dos tarjetas (`Listo para asignar` y `Categorías sobregastadas`) con una sola petición.
 * Se provee en `InicioPage`, así que se destruye con la pantalla y cancela lo pendiente.
 */
@Injectable()
export class ResumenInicioStore {
  private readonly lectura = inject(ResumenLecturaService);
  private readonly activo = inject(PresupuestoActivoService);

  /** El mes actual (fecha local), fijado al abrir la pantalla. */
  readonly periodo = mesActual();
  readonly presupuestoId = computed(() => this.activo.presupuesto()?.id ?? null);

  readonly mes = crearCargador(this.presupuestoId, (id) => this.lectura.mes(id, this.periodo));
  /** El saldo total necesita las cuentas y los saldos: si una falla, falla la tarjeta. */
  readonly saldo = crearCargador(this.presupuestoId, (id) =>
    forkJoin([this.lectura.cuentas(id), this.lectura.saldos(id)]).pipe(
      map(([cuentas, saldos]): CuentasConSaldos => ({ cuentas, saldos })),
    ),
  );
  readonly metas = crearCargador(this.presupuestoId, (id) => this.lectura.metas(id, this.periodo));
  readonly sinAprobar = crearCargador(this.presupuestoId, (id) => this.lectura.sinAprobar(id));
  readonly ingresosGastos = crearCargador(this.presupuestoId, (id) =>
    this.lectura.ingresosGastos(id, this.periodo),
  );
}
