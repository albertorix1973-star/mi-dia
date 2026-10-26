# Mi Día

App Android para organizar el día y recibir avisos de las tareas pendientes.

## Qué hace
- **Resumen de la mañana** (8:00 por defecto): notificación con las tareas del día.
- **Aviso a la hora de cada tarea**: notificación emergente con botones *Hecho*, *+10 min* y *Mañana*. Con el móvil bloqueado aparece una ventana a pantalla completa.
- **Repaso de la tarde** (20:00 por defecto): lo que queda pendiente, con el botón *Pasar todo a mañana*.
- **Tareas repetitivas**: todos los días, de lunes a viernes o cada semana.
- **Aviso insistente** (activado por defecto): el aviso se repite cada 30 min hasta que pulses *Tarea realizada*, y entonces la tarea se borra.
- **Vista del mes** (botón *Mes*): todos los avisos del mes agrupados por día.
- **Dictado por voz** (botón verde *Dictar*). Ejemplos:
  - "Llamar al gestor mañana a las 11 y media"
  - "Sacar la basura todos los días a las 9 de la noche"
  - "Gimnasio cada lunes a las 7"
  - "Pagar el seguro el 5 de noviembre"
  - "Avísame el 5 de noviembre a las 10:00 que haga un café"
- Las tareas se guardan solo en el móvil.

## Uso
- Toca una tarea para editarla; mantenla pulsada para borrarla.
- Las flechas ◀ ▶ cambian de día; toca la fecha para volver a hoy.
- En *Ajustes* cambias las horas del resumen y del repaso, y revisas permisos.

## Cómo se compila
Cada vez que se sube un cambio a GitHub, la acción *Compilar APK* genera `MiDia.apk` y lo publica en *Releases*.
