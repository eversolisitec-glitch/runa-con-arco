package com.runaarco;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Pantalla de configuración (necesita Cloth Config). */
public class RunaMenu {
	public static Screen crear(Screen padre) {
		ConfigBuilder b = ConfigBuilder.create()
				.setParentScreen(padre)
				.setTitle(Component.literal("Runa del Arco"))
				.setSavingRunnable(RunaConfig::guardar);

		ConfigCategory cat = b.getOrCreateCategory(Component.literal("Jugadores"));
		ConfigEntryBuilder e = b.entryBuilder();

		// --- Qué runa usar (archivos .runa de la carpeta "runas") ---
		List<String> lista = RunaLibrary.listar();
		String[] valores = lista.toArray(new String[0]);
		String actual = RunaConfig.runa.isEmpty() || !lista.contains(RunaConfig.runa)
				? RunaLibrary.PREDETERMINADA : RunaConfig.runa;
		cat.addEntry(e.startSelector(Component.literal("Runa de jugadores (archivo .runa)"), valores, actual)
				.setDefaultValue(RunaLibrary.PREDETERMINADA)
				.setNameProvider(s -> Component.literal(s))
				.setTooltip(Component.literal("Archivos .runa de la carpeta \"runas\" de tu instancia."))
				.setSaveConsumer(s -> RunaConfig.runa = s.equals(RunaLibrary.PREDETERMINADA) ? "" : s)
				.build());

		// --- Tamaño, altura, distancia (en décimas de bloque) ---
		cat.addEntry(e.startIntSlider(Component.literal("Tamaño"), Math.round(RunaConfig.tamano * 10), 5, 100)
				.setDefaultValue(18)
				.setTextGetter(v -> Component.literal(String.format("%.1f bloques de radio", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.tamano = v / 10f)
				.build());

		cat.addEntry(e.startIntSlider(Component.literal("Altura"), Math.round(RunaConfig.altura * 10), -10, 100)
				.setDefaultValue(24)
				.setTextGetter(v -> Component.literal(String.format("%.1f bloques sobre los pies", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.altura = v / 10f)
				.build());

		cat.addEntry(e.startIntSlider(Component.literal("Distancia a la espalda"), Math.round(RunaConfig.distancia * 10), -20, 80)
				.setDefaultValue(10)
				.setTextGetter(v -> Component.literal(String.format("%.1f bloques", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.distancia = v / 10f)
				.build());

		// --- Brillo y color ---
		cat.addEntry(e.startIntSlider(Component.literal("Brillo (jugadores y mobs)"), Math.round(RunaConfig.brillo * 100), 0, 300)
				.setDefaultValue(100)
				.setTextGetter(v -> Component.literal(v + " %"))
				.setSaveConsumer(v -> RunaConfig.brillo = v / 100f)
				.build());

		cat.addEntry(e.startBooleanToggle(Component.literal("Usar un color propio"), RunaConfig.colorPersonalizado)
				.setDefaultValue(false)
				.setTooltip(Component.literal("Apagado: cada runa usa sus colores. Encendido: todas usan el color de abajo."))
				.setSaveConsumer(v -> RunaConfig.colorPersonalizado = v)
				.build());

		cat.addEntry(e.startColorField(Component.literal("Color propio (jugadores y mobs)"), RunaConfig.colorRgb())
				.setDefaultValue(0xFFFFFF)
				.setTooltip(Component.literal("Se aplica solo si \"Usar un color propio\" está encendido."))
				.setSaveConsumer(c -> RunaConfig.setColorRgb(c))
				.build());


		// ===== MOBS CON ARCO (esqueletos, etc.) =====
		ConfigCategory mobs = b.getOrCreateCategory(Component.literal("Mobs con arco"));

		mobs.addEntry(e.startBooleanToggle(Component.literal("Mostrar runa en mobs"), RunaConfig.mobsActivos)
				.setDefaultValue(true)
				.setTooltip(Component.literal("Esqueletos, strays, bogged y cualquier mob que tense un arco."))
				.setSaveConsumer(v -> RunaConfig.mobsActivos = v)
				.build());

		String actualMobs = RunaConfig.runaMobs.isEmpty() || !lista.contains(RunaConfig.runaMobs)
				? RunaLibrary.PREDETERMINADA : RunaConfig.runaMobs;
		mobs.addEntry(e.startSelector(Component.literal("Runa de mobs (archivo .runa)"), valores, actualMobs)
				.setDefaultValue(RunaLibrary.PREDETERMINADA)
				.setNameProvider(s -> Component.literal(s))
				.setSaveConsumer(s -> RunaConfig.runaMobs = s.equals(RunaLibrary.PREDETERMINADA) ? "" : s)
				.build());

		mobs.addEntry(e.startIntSlider(Component.literal("Tamaño"), Math.round(RunaConfig.tamanoMobs * 10), 5, 100)
				.setDefaultValue(14)
				.setTextGetter(v -> Component.literal(String.format("%.1f bloques de radio", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.tamanoMobs = v / 10f)
				.build());

		mobs.addEntry(e.startIntSlider(Component.literal("Altura"), Math.round(RunaConfig.alturaMobs * 10), -10, 100)
				.setDefaultValue(23)
				.setTextGetter(v -> Component.literal(String.format("%.1f bloques sobre los pies", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.alturaMobs = v / 10f)
				.build());

		mobs.addEntry(e.startIntSlider(Component.literal("Distancia a la espalda"), Math.round(RunaConfig.distanciaMobs * 10), -20, 80)
				.setDefaultValue(9)
				.setTextGetter(v -> Component.literal(String.format("%.1f bloques", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.distanciaMobs = v / 10f)
				.build());

		return b.build();
	}
}
