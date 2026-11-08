package com.runaarco;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja una runa animada (archivo .runa) a la espalda de todo jugador o mob que tense un arco.
 * Se ve en ti, en otros jugadores y en esqueletos, etc. (solo si tú tienes el mod instalado).
 */
public class RunaArcoClient implements ClientModInitializer {
	public static final String MOD_ID = "runaarco";

	static final float TICKS_APARICION = 12f; // ticks que tarda en aparecer del todo
	static final double DISTANCIA_MAX_MOBS = 48.0; // no se dibuja en mobs más lejos que esto
	static final int MAX_RUNAS = 48;

	private static final int FULL_BRIGHT = 0xF000F0; // luz máxima (15,15)

	private static final Identifier TEX_GLOW = id("textures/rune/glow.png");

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private record Rune(Vec3 center, float yaw, float charge, float time, boolean mob) {}

	private static final List<Rune> RUNES = new ArrayList<>();
	private static Vec3 cameraPos = Vec3.ZERO;
	private static KeyMapping abrirLista;

	@Override
	public void onInitializeClient() {
		RunaConfig.cargar();
		RunaLibrary.iniciar();

		// Tecla para abrir la lista de runas (por defecto R; se cambia en Controles)
		KeyMapping.Category categoria = KeyMapping.Category.register(id("principal"));
		abrirLista = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.runaarco.abrir", InputConstants.Type.KEYSYM, 82, categoria));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (abrirLista.consumeClick()) {
				if (mc.screen == null) mc.setScreen(new RunaListaScreen());
			}
		});

		// 1) Recoger quién está tensando un arco (jugadores y mobs)
		LevelRenderEvents.END_EXTRACTION.register(ctx -> {
			RunaConfig.revisar();
			RunaLibrary.sincronizar();
			RUNES.clear();
			cameraPos = ctx.camera().position();
			float pt = ctx.deltaTracker().getGameTimeDeltaPartialTick(false);

			for (Entity e : ctx.level().entitiesForRendering()) {
				if (!(e instanceof LivingEntity le)) continue;
				if (!le.isUsingItem()) continue;
				ItemStack stack = le.getUseItem();
				if (!stack.is(Items.BOW)) continue;

				boolean mob = !(le instanceof Player);
				if (mob) {
					if (!RunaConfig.mobsActivos) continue;
					if (le.position().distanceToSqr(cameraPos) > DISTANCIA_MAX_MOBS * DISTANCIA_MAX_MOBS) continue;
				}
				if (RUNES.size() >= MAX_RUNAS) break;

				float charge = Mth.clamp((le.getTicksUsingItem() + pt) / TICKS_APARICION, 0f, 1f);
				charge = charge * charge * (3f - 2f * charge); // aparición suave

				float yaw = le.getViewYRot(pt); // hacia donde mira (0 = +Z)
				Vec3 pos = le.getPosition(pt);
				double rad = Math.toRadians(yaw);
				double dx = -Math.sin(rad), dz = Math.cos(rad);
				float dist = mob ? RunaConfig.distanciaMobs : RunaConfig.distancia;
				float alt = mob ? RunaConfig.alturaMobs : RunaConfig.altura;
				Vec3 center = new Vec3(pos.x - dx * dist, pos.y + alt, pos.z - dz * dist);
				RUNES.add(new Rune(center, yaw, charge, le.tickCount + pt, mob));
			}
		});

		// 2) Dibujarlas
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			if (RUNES.isEmpty()) return;
			PoseStack ps = ctx.poseStack();
			SubmitNodeCollector out = ctx.submitNodeCollector();

			for (Rune r : RUNES) {
				RunaLibrary.Runa ru = RunaLibrary.obtener(r.mob());
				float t = r.time();
				float c = r.charge();
				float base = r.mob() ? RunaConfig.tamanoMobs : RunaConfig.tamano;

				float bob = 0.08f * Mth.sin(t * 0.12f);                       // flota suavemente
				float pulse = 1f + 0.03f * Mth.sin(t * 0.3f);                  // late
				float size = Mth.lerp(c, 0.3f, 1f) * pulse * base;
				float flicker = 0.88f + 0.12f * Mth.sin(t * 0.8f) * Mth.sin(t * 0.31f);
				int alpha = (int) Math.min(255f, 255 * c * flicker * RunaConfig.brillo);

				ps.pushPose();
				ps.translate(r.center().x - cameraPos.x,
						r.center().y - cameraPos.y + bob,
						r.center().z - cameraPos.z);
				ps.mulPose(Axis.YP.rotationDegrees(-r.yaw()));

				int rgbRuna = RunaConfig.colorPersonalizado ? RunaConfig.colorRgb() : ru.tinte();
				int rgbHalo = RunaConfig.colorPersonalizado ? RunaConfig.colorRgb() : ru.halo();

				// halo que respira detrás
				float halo = 1.15f + 0.1f * Mth.sin(t * 0.2f);
				quad(out, ps, TEX_GLOW, size * halo * 1.1f, size * halo * 1.1f, -0.004f, (int) (alpha * 0.75f), rgbHalo);

				// capas que giran detrás (círculos mágicos, etc.)
				RunaLibrary.Capa[] capas = ru.capas();
				for (int ci = 0; ci < capas.length; ci++) {
					ps.pushPose();
					ps.mulPose(Axis.ZP.rotationDegrees(capas[ci].giro() * t));
					quad(out, ps, capas[ci].id(), size, size * ru.aspecto(), -0.003f - 0.0002f * ci, alpha, rgbRuna);
					ps.popPose();
				}

				// eco mágico: copia que se expande y se desvanece cada 30 ticks
				float phase = (t % 30f) / 30f;
				int echoAlpha = (int) (alpha * 0.45f * (1f - phase));
				if (echoAlpha > 2) {
					animated(out, ps, ru, t, size * (1f + 0.35f * phase), -0.002f, echoAlpha, rgbRuna);
				}

				// la runa, con su animación
				animated(out, ps, ru, t, size, 0f, alpha, rgbRuna);
				ps.popPose();
			}
		});
	}

	/** Dibuja el cuadro actual y, si el .runa lo pide, lo mezcla con el siguiente. */
	private static void animated(SubmitNodeCollector out, PoseStack ps, RunaLibrary.Runa ru,
								 float t, float half, float z, int alpha, int rgb) {
		Identifier[] fr = ru.frames();
		int n = fr.length;
		float f = (t / ru.ticksPorFrame()) % n;
		int i = (int) f;
		float frac = f - i;
		float halfH = half * ru.aspecto();
		if (ru.interpolar() && n > 1) {
			int next = (i + 1) % n;
			quad(out, ps, fr[i], half, halfH, z, (int) (alpha * (1f - frac * 0.5f)), rgb);
			quad(out, ps, fr[next], half, halfH, z + 0.0005f, (int) (alpha * frac * 0.5f), rgb);
		} else {
			quad(out, ps, fr[i], half, halfH, z, alpha, rgb);
		}
	}

	/** Un cuadrado con textura, visible por ambos lados. */
	private static void quad(SubmitNodeCollector out, PoseStack ps, Identifier tex,
							 float halfW, float halfH, float zOff, int alpha, int rgb) {
		if (alpha <= 0) return;
		out.submitCustomGeometry(ps, RenderTypes.eyes(tex), (pose, vc) -> {
			for (int side = 0; side < 2; side++) {
				float z = zOff + (side == 0 ? 0.001f : -0.001f);
				float n = side == 0 ? 1f : -1f;
				float[][] v = side == 0
						? new float[][]{{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}}
						: new float[][]{{1, -1, 0, 1}, {-1, -1, 1, 1}, {-1, 1, 1, 0}, {1, 1, 0, 0}};
				float b = Math.min(RunaConfig.brillo, 1f);
				int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;
				for (float[] p : v) {
					vc.addVertex(pose, p[0] * halfW, p[1] * halfH, z)
							.setColor(
									Math.min(255, (int) (cr * b)),
									Math.min(255, (int) (cg * b)),
									Math.min(255, (int) (cb * b)),
									alpha)
							.setUv(p[2], p[3])
							.setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(FULL_BRIGHT)
							.setNormal(pose, 0f, 0f, n);
				}
			}
		});
	}
}
