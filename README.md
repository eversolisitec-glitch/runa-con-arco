# Runa del Arco (Fabric, Minecraft 26.1.2)

Mod de cliente: tu runa animada aparece a tu espalda (y a la de otros jugadores)
mientras tensan un arco. Flota, late y suelta un eco mágico; se orienta hacia donde mira el jugador.

## Compilar con GitHub
1. Crea un repositorio nuevo en github.com.
2. Sube TODO el contenido de esta carpeta (incluida la carpeta oculta `.github`).
3. Entra a la pestaña **Actions** → el workflow **build** se ejecuta solo.
4. Cuando termine, abre la ejecución y descarga el artefacto **runa-arco-jar**.
5. Descomprime el zip y copia `runa-arco-1.0.0.jar` (no el `-sources`) a la carpeta `mods` de tu instancia.

Si falla, copia el texto rojo del error del paso "gradle build" y pégalo en el chat.

## Ajustes
En `src/main/java/com/runaarco/RunaArcoClient.java`, arriba: tamaño, altura, distancia y velocidad de aparición.

## Configuración
Al abrir el juego se crea `config/runaarco.json` en la carpeta de la instancia.
Edítalo con el Bloc de notas y guarda: se aplica en ~2 segundos con el juego abierto.
- `tamano`: media anchura en bloques
- `altura`: altura sobre los pies
- `distancia`: distancia detrás de la espalda
- `brillo`: 1.0 normal, 0.5 tenue, 1.5 intenso
- `color`: tinte en hexadecimal (`#FFFFFF` = colores originales)

## Menú en el juego
Necesita Mod Menu y Cloth Config en la carpeta `mods` (el mod funciona sin ellos, solo que sin menú).
Menú principal o de pausa → **Mods** → Runa del Arco → engranaje.

## Archivos .runa
Un `.runa` es un ZIP con `runa.png` (tira vertical de cuadros) y `runa.json` (opcional):
```json
{ "nombre": "Mi runa", "cuadros": 12, "ticksPorFrame": 2, "interpolar": true }
```
Ponlos en la carpeta `runas` de tu instancia (se crea sola, con 3 ejemplos) y elige uno en el menú
o en `config/runaarco.json` (campo `runa`).

## Lista de runas con una tecla
Dentro del juego pulsa **R** (se cambia en Opciones → Controles → Runa del Arco).
Se abre la lista de archivos de la carpeta `runas`; un clic en una runa la activa al instante.
