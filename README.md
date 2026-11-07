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
