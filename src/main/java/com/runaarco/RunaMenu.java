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


		// ===== EFECTOS EXTRA =====
		ConfigCategory fx = b.getOrCreateCategory(Component.literal("Efectos"));

		fx.addEntry(e.startBooleanToggle(Component.literal("Runa en la flecha (arco con Poder)"), RunaConfig.flechaPoder)
				.setDefaultValue(true)
				.setTooltip(Component.literal("La flecha de un arco con Poder pasa por una runa al salir."))
				.setSaveConsumer(v -> RunaConfig.flechaPoder = v)
				.build());

		fx.addEntry(e.startBooleanToggle(Component.literal("Rayos en objetos encantados (suelo)"), RunaConfig.rayosObjetos)
				.setDefaultValue(true)
				.setSaveConsumer(v -> RunaConfig.rayosObjetos = v)
				.build());

		fx.addEntry(e.startBooleanToggle(Component.literal("Rayos en objetos encantados (en la mano)"), RunaConfig.rayosEnMano)
				.setDefaultValue(true)
				.setTooltip(Component.literal("La posición en la mano es aproximada."))
				.setSaveConsumer(v -> RunaConfig.rayosEnMano = v)
				.build());

		fx.addEntry(e.startColorField(Component.literal("Color de los rayos"), RunaConfig.rayoRgb)
				.setDefaultValue(0xA8E0FF)
				.setSaveConsumer(c -> RunaConfig.rayoRgb = c)
				.build());


		fx.addEntry(e.startBooleanToggle(Component.literal("Anillos de runas en el tridente"), RunaConfig.anillosTridente)
				.setDefaultValue(true)
				.setSaveConsumer(v -> RunaConfig.anillosTridente = v)
				.build());

		fx.addEntry(e.startColorField(Component.literal("Color de los anillos"), RunaConfig.anillosRgb)
				.setDefaultValue(0xBFFFEF)
				.setSaveConsumer(c -> RunaConfig.anillosRgb = c)
				.build());


		// ===== TAMAÑO DE LOS EFECTOS =====
		ConfigCategory tam = b.getOrCreateCategory(Component.literal("Tamaño de efectos"));

		tam.addEntry(e.startIntSlider(Component.literal("Rayos"), Math.round(RunaConfig.escalaRayos * 100), 20, 500)
				.setDefaultValue(100)
				.setTextGetter(v -> Component.literal(v + " %"))
				.setSaveConsumer(v -> RunaConfig.escalaRayos = v / 100f)
				.build());
		tam.addEntry(e.startIntSlider(Component.literal("Anillos del tridente"), Math.round(RunaConfig.escalaAnillos * 100), 20, 500)
				.setDefaultValue(100)
				.setTextGetter(v -> Component.literal(v + " %"))
				.setSaveConsumer(v -> RunaConfig.escalaAnillos = v / 100f)
				.build());
		tam.addEntry(e.startIntSlider(Component.literal("Runa de la flecha"), Math.round(RunaConfig.escalaFlecha * 100), 20, 500)
				.setDefaultValue(100)
				.setTextGetter(v -> Component.literal(v + " %"))
				.setSaveConsumer(v -> RunaConfig.escalaFlecha = v / 100f)
				.build());
		tam.addEntry(e.startIntSlider(Component.literal("Grosor de la estela de luz"), Math.round(RunaConfig.grosorEstela * 100), 1, 100)
				.setDefaultValue(12)
				.setTextGetter(v -> Component.literal(String.format("%.2f bloques", v / 100f)))
				.setSaveConsumer(v -> RunaConfig.grosorEstela = v / 100f)
				.build());

		// ===== POSICIÓN DE LOS EFECTOS =====
		ConfigCategory pos = b.getOrCreateCategory(Component.literal("Posición de efectos"));

		pos.addEntry(e.startIntSlider(Component.literal("Runa de la flecha: distancia delante"), Math.round(RunaConfig.delanteFlecha * 10), 0, 100)
				.setDefaultValue(18)
				.setTextGetter(v -> Component.literal(String.format("%.1f bloques", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.delanteFlecha = v / 10f)
				.build());
		pos.addEntry(e.startIntSlider(Component.literal("Runa de la flecha: altura"), Math.round(RunaConfig.alturaFlecha * 10), -30, 30)
				.setDefaultValue(0)
				.setTextGetter(v -> Component.literal(String.format("%+.1f bloques", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.alturaFlecha = v / 10f)
				.build());
		pos.addEntry(e.startIntSlider(Component.literal("Rayos: altura"), Math.round(RunaConfig.alturaRayos * 10), -30, 30)
				.setDefaultValue(0)
				.setTextGetter(v -> Component.literal(String.format("%+.1f bloques", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.alturaRayos = v / 10f)
				.build());
		pos.addEntry(e.startIntSlider(Component.literal("Anillos del tridente: a lo largo"), Math.round(RunaConfig.anillosAdelante * 10), -30, 30)
				.setDefaultValue(0)
				.setTextGetter(v -> Component.literal(String.format("%+.1f bloques", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.anillosAdelante = v / 10f)
				.build());
		pos.addEntry(e.startIntSlider(Component.literal("Objeto en la mano: lado"), Math.round(RunaConfig.manoLado * 10), -30, 30)
				.setDefaultValue(4)
				.setTextGetter(v -> Component.literal(String.format("%+.1f bloques", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.manoLado = v / 10f)
				.build());
		pos.addEntry(e.startIntSlider(Component.literal("Objeto en la mano: delante"), Math.round(RunaConfig.manoAdelante * 10), -30, 30)
				.setDefaultValue(5)
				.setTextGetter(v -> Component.literal(String.format("%+.1f bloques", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.manoAdelante = v / 10f)
				.build());
		pos.addEntry(e.startIntSlider(Component.literal("Objeto en la mano: altura"), Math.round(RunaConfig.manoAltura * 10), -30, 30)
				.setDefaultValue(0)
				.setTextGetter(v -> Component.literal(String.format("%+.1f bloques", v / 10f)))
				.setSaveConsumer(v -> RunaConfig.manoAltura = v / 10f)
				.build());

		// ===== ESTELA DE LUZ DE LA FLECHA =====
		ConfigCategory est = b.getOrCreateCategory(Component.literal("Estela de la flecha"));

		est.addEntry(e.startBooleanToggle(Component.literal("Estela de luz en la trayectoria"), RunaConfig.estela)
				.setDefaultValue(true)
				.setSaveConsumer(v -> RunaConfig.estela = v)
				.build());
		est.addEntry(e.startBooleanToggle(Component.literal("Solo flechas de un arco con Poder"), RunaConfig.estelaSoloPoder)
				.setDefaultValue(false)
				.setSaveConsumer(v -> RunaConfig.estelaSoloPoder = v)
				.build());
		est.addEntry(e.startIntSlider(Component.literal("Duración"), Math.round(RunaConfig.vidaEstela), 5, 100)
				.setDefaultValue(25)
				.setTextGetter(v -> Component.literal(String.format("%.1f segundos", v / 20f)))
				.setSaveConsumer(v -> RunaConfig.vidaEstela = v)
				.build());
		est.addEntry(e.startColorField(Component.literal("Color de la estela"), RunaConfig.estelaRgb)
				.setDefaultValue(0xBFE9FF)
				.setSaveConsumer(c -> RunaConfig.estelaRgb = c)
				.build());


		// ===== TRIDENTE Y OBJETO EN TU MANO (PRIMERA PERSONA) =====
		ConfigCategory tri = b.getOrCreateCategory(Component.literal("Tridente (ajustes)"));

		tri.addEntry(e.startIntSlider(Component.literal("Primera persona: tamaño de los rayos"), Math.round(RunaConfig.fpEscala * 100), 5, 300)
				.setDefaultValue(60)
				.setTextGetter(v -> Component.literal(v + " %"))
				.setSaveConsumer(v -> RunaConfig.fpEscala = v / 100f)
				.build());
		tri.addEntry(e.startIntSlider(Component.literal("Primera persona: tamaño de los anillos"), Math.round(RunaConfig.fpEscalaAnillos * 100), 10, 400)
				.setDefaultValue(100)
				.setTextGetter(v -> Component.literal(v + " %"))
				.setSaveConsumer(v -> RunaConfig.fpEscalaAnillos = v / 100f)
				.build());
		tri.addEntry(e.startIntSlider(Component.literal("Primera persona: lado"), Math.round(RunaConfig.fpLado * 100), -150, 150)
				.setDefaultValue(50)
				.setTextGetter(v -> Component.literal(String.format("%+.2f bloques", v / 100f)))
				.setSaveConsumer(v -> RunaConfig.fpLado = v / 100f)
				.build());
		tri.addEntry(e.startIntSlider(Component.literal("Primera persona: altura"), Math.round(RunaConfig.fpAltura * 100), -150, 150)
				.setDefaultValue(-30)
				.setTextGetter(v -> Component.literal(String.format("%+.2f bloques", v / 100f)))
				.setSaveConsumer(v -> RunaConfig.fpAltura = v / 100f)
				.build());
		tri.addEntry(e.startIntSlider(Component.literal("Primera persona: distancia delante"), Math.round(RunaConfig.fpAdelante * 100), 0, 200)
				.setDefaultValue(85)
				.setTextGetter(v -> Component.literal(String.format("%.2f bloques", v / 100f)))
				.setSaveConsumer(v -> RunaConfig.fpAdelante = v / 100f)
				.build());
		tri.addEntry(e.startIntSlider(Component.literal("Primera persona: inclinación"), Math.round(RunaConfig.fpInclinacion), -90, 90)
				.setDefaultValue(45)
				.setTextGetter(v -> Component.literal(v + " grados"))
				.setSaveConsumer(v -> RunaConfig.fpInclinacion = v)
				.build());
		tri.addEntry(e.startIntSlider(Component.literal("Tridente lanzado: tamaño"), Math.round(RunaConfig.escalaLanzado * 100), 10, 400)
				.setDefaultValue(100)
				.setTextGetter(v -> Component.literal(v + " %"))
				.setSaveConsumer(v -> RunaConfig.escalaLanzado = v / 100f)
				.build());
		tri.addEntry(e.startIntSlider(Component.literal("Separación entre los 3 anillos"), Math.round(RunaConfig.separacionAnillos * 100), 5, 300)
				.setDefaultValue(45)
				.setTextGetter(v -> Component.literal(String.format("%.2f bloques", v / 100f)))
				.setSaveConsumer(v -> RunaConfig.separacionAnillos = v / 100f)
				.build());
		tri.addEntry(e.startIntSlider(Component.literal("Alto de cada anillo"), Math.round(RunaConfig.altoAnillos * 100), 2, 100)
				.setDefaultValue(11)
				.setTextGetter(v -> Component.literal(String.format("%.2f bloques", v / 100f)))
				.setSaveConsumer(v -> RunaConfig.altoAnillos = v / 100f)
				.build());

		return b.build();
	}
}
