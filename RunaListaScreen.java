package com.runaarco;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Lista de runas (se abre con una tecla, como el menú de Litematica).
 * Dos pestañas: runa de jugadores y runa de mobs. Un clic en una runa la activa y cierra la lista.
 */
public class RunaListaScreen extends Screen {
	private static final int ANCHO = 220;
	private static final int ALTO_BOTON = 20;
	private static final int PASO = 24;
	private static final int INICIO_LISTA = 64;

	// Se recuerda la última pestaña usada
	private static boolean enMobs = false;

	private final List<String> runas = RunaLibrary.listar();
	private int pagina = 0;

	public RunaListaScreen() {
		super(Component.literal("Runas"));
	}

	private int porPagina() {
		return Math.max(3, Math.min(10, (this.height - INICIO_LISTA - 70) / PASO));
	}

	@Override
	protected void init() {
		int x = this.width / 2 - ANCHO / 2;
		int pp = porPagina();
		int paginas = Math.max(1, (runas.size() + pp - 1) / pp);
		if (pagina >= paginas) pagina = paginas - 1;

		// Pestañas: Jugadores | Mobs
		Button tJug = Button.builder(Component.literal(enMobs ? "Jugadores" : "[ Jugadores ]"), b -> cambiarPestana(false))
				.bounds(x, 12, ANCHO / 2 - 2, ALTO_BOTON).build();
		tJug.active = enMobs;
		this.addRenderableWidget(tJug);
		Button tMob = Button.builder(Component.literal(enMobs ? "[ Mobs ]" : "Mobs"), b -> cambiarPestana(true))
				.bounds(x + ANCHO / 2 + 2, 12, ANCHO / 2 - 2, ALTO_BOTON).build();
		tMob.active = !enMobs;
		this.addRenderableWidget(tMob);

		// Encabezado (botón desactivado, solo muestra el texto)
		Button titulo = Button.builder(
				Component.literal((enMobs ? "Runa de los mobs" : "Tu runa") + "  (" + (pagina + 1) + "/" + paginas + ")"), b -> {})
				.bounds(x, 38, ANCHO, ALTO_BOTON).build();
		titulo.active = false;
		this.addRenderableWidget(titulo);

		// Una runa por botón
		String sel = enMobs ? RunaConfig.runaMobs : RunaConfig.runa;
		String activa = sel.isEmpty() ? RunaLibrary.PREDETERMINADA : sel;
		int desde = pagina * pp;
		int hasta = Math.min(runas.size(), desde + pp);
		int y = INICIO_LISTA;
		for (int i = desde; i < hasta; i++) {
			String nombre = runas.get(i);
			String visible = nombre.toLowerCase().endsWith(".runa")
					? nombre.substring(0, nombre.length() - 5) : nombre;
			String texto = nombre.equals(activa) ? "» " + visible + " «" : visible;
			this.addRenderableWidget(Button.builder(Component.literal(texto), b -> elegir(nombre))
					.bounds(x, y, ANCHO, ALTO_BOTON).build());
			y += PASO;
		}

		// Fila de abajo: anterior / siguiente
		int yAbajo = this.height - 52;
		Button ant = Button.builder(Component.literal("<  Anterior"), b -> cambiarPagina(-1))
				.bounds(x, yAbajo, ANCHO / 2 - 2, ALTO_BOTON).build();
		ant.active = pagina > 0;
		this.addRenderableWidget(ant);
		Button sig = Button.builder(Component.literal("Siguiente  >"), b -> cambiarPagina(1))
				.bounds(x + ANCHO / 2 + 2, yAbajo, ANCHO / 2 - 2, ALTO_BOTON).build();
		sig.active = pagina < paginas - 1;
		this.addRenderableWidget(sig);

		// Última fila: configuración (si está Cloth Config) y cerrar
		int yUlt = this.height - 28;
		boolean hayMenu = FabricLoader.getInstance().isModLoaded("cloth-config");
		if (hayMenu) {
			this.addRenderableWidget(Button.builder(Component.literal("Configuración"), b -> abrirConfig())
					.bounds(x, yUlt, ANCHO / 2 - 2, ALTO_BOTON).build());
			this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> cerrar())
					.bounds(x + ANCHO / 2 + 2, yUlt, ANCHO / 2 - 2, ALTO_BOTON).build());
		} else {
			this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> cerrar())
					.bounds(x, yUlt, ANCHO, ALTO_BOTON).build());
		}
	}

	private void cambiarPestana(boolean mobs) {
		enMobs = mobs;
		pagina = 0;
		this.rebuildWidgets();
	}

	private void cambiarPagina(int d) {
		pagina += d;
		this.rebuildWidgets();
	}

	private void elegir(String nombre) {
		String valor = nombre.equals(RunaLibrary.PREDETERMINADA) ? "" : nombre;
		if (enMobs) RunaConfig.runaMobs = valor;
		else RunaConfig.runa = valor;
		RunaConfig.guardar();
		cerrar();
	}

	private void abrirConfig() {
		try {
			Minecraft.getInstance().setScreen(RunaMenu.crear(this));
		} catch (Throwable t) {
			System.err.println("[runaarco] No se pudo abrir el menú de configuración: " + t);
		}
	}

	private void cerrar() {
		Minecraft.getInstance().setScreen(null);
	}
}
