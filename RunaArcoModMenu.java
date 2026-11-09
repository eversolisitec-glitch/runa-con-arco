package com.runaarco;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Conecta el botón de configuración de Mod Menu con nuestro menú. */
public class RunaArcoModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> RunaMenu.crear(parent);
	}
}
