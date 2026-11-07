package com.runaarco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja tu runa animada (12 cuadros) a la espalda de todo jugador que tense un arco.
 * Se ve en ti y en los demás jugadores (solo si tú tienes el mod instalado).
 */
public class RunaArcoClient implements ClientModInitializer {
	public static final String MOD_ID = "runaarco";

	// ===== AJUSTES (cámbialos aquí) =====
	static final float RADIO = 1.8f;          // media anchura de la runa en bloques (3.6 de ancho)
	static final float ALTURA = 2.4f;         // altura del centro sobre los pies
	static final float DETRAS = 1.0f;         // distancia detrás de la espalda
	static final float TICKS_APARICION = 12f; // ticks que tarda en aparecer del todo
	static final int FRAMES = 12;             // cuadros de tu animación
	static final float TICKS_POR_FRAME = 2f;  // igual que tu frametime: 2
	// =====================================

	private static final Identifier[] FRAME = new Identifier[FRAMES];
	private static final Identifier TEX_GLOW = id("textures/rune/glow.png");

	static {
		for (int i = 0; i < FRAMES; i++) {
			FRAME[i] = id(String.format("textures/rune/frame_%02d.png", i));
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private record Rune(Vec3 center, float yaw, float charge, float time) {}

	private static final List<Rune> RUNES = new ArrayList<>();
	private static Vec3 cameraPos = Vec3.ZERO;

	@Override
	public void onInitializeClient() {
		// 1) Recoger qué jugadores están tensando el arco
		LevelRenderEvents.END_EXTRACTION.register(ctx -> {
			RUNES.clear();
			cameraPos = ctx.camera().position();
			float pt = ctx.deltaTracker().getGameTimeDeltaPartialTick(false);

			for (AbstractClientPlayer p : ctx.level().players()) {
				if (!p.isUsingItem()) continue;
				ItemStack stack = p.getUseItem();
				if (!stack.is(Items.BOW)) continue;

				int used = stack.getUseDuration(p) - p.getUseItemRemainingTicks();
				float charge = Mth.clamp((used + pt) / TICKS_APARICION, 0f, 1f);
				charge = charge * charge * (3f - 2f * charge); // aparición suave

				float yaw = p.getViewYRot(pt); // hacia donde miras (0 = +Z)
				Vec3 pos = p.getPosition(pt);
				double rad = Math.toRadians(yaw);
				double dx = -Math.sin(rad), dz = Math.cos(rad);
				Vec3 center = new Vec3(
						pos.x - dx * DETRAS,
						pos.y + ALTURA,
						pos.z - dz * DETRAS);
				RUNES.add(new Rune(center, yaw, charge, p.tickCount + pt));
			}
		});

		// 2) Dibujarlas
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			if (RUNES.isEmpty()) return;
			PoseStack ps = ctx.poseStack();
			SubmitNodeCollector out = ctx.submitNodeCollector();

			for (Rune r : RUNES) {
				float t = r.time();
				float c = r.charge();

				float bob = 0.08f * Mth.sin(t * 0.12f);                       // flota suavemente
				float pulse = 1f + 0.03f * Mth.sin(t * 0.3f);                  // late
				float size = Mth.lerp(c, 0.3f, 1f) * pulse * RADIO;
				float flicker = 0.88f + 0.12f * Mth.sin(t * 0.8f) * Mth.sin(t * 0.31f);
				int alpha = (int) (255 * c * flicker);

				ps.pushPose();
				ps.translate(r.center().x - cameraPos.x,
						r.center().y - cameraPos.y + bob,
						r.center().z - cameraPos.z);
				ps.mulPose(Axis.YP.rotationDegrees(-r.yaw()));

				// halo que respira detrás
				float halo = 1.15f + 0.1f * Mth.sin(t * 0.2f);
				quad(out, ps, TEX_GLOW, size * halo * 1.1f, size * halo * 1.1f, -0.004f, (int) (alpha * 0.75f));

				// eco mágico: copia que se expande y se desvanece cada 30 ticks
				float phase = (t % 30f) / 30f;
				int echoAlpha = (int) (alpha * 0.45f * (1f - phase));
				if (echoAlpha > 2) {
					animated(out, ps, t, size * (1f + 0.35f * phase), -0.002f, echoAlpha);
				}

				// tu runa, con tu animación de 12 cuadros
				animated(out, ps, t, size, 0f, alpha);
				ps.popPose();
			}
		});
	}

	/** Mezcla el cuadro actual con el siguiente (igual que "interpolate": true en tu .mcmeta). */
	private static void animated(SubmitNodeCollector out, PoseStack ps, float t, float half, float z, int alpha) {
		float f = (t / TICKS_POR_FRAME) % FRAMES;
		int i = (int) f;
		float frac = f - i;
		int next = (i + 1) % FRAMES;
		quad(out, ps, FRAME[i], half, half, z, (int) (alpha * (1f - frac * 0.5f)));
		quad(out, ps, FRAME[next], half, half, z + 0.0005f, (int) (alpha * frac * 0.5f));
	}

	/** Un cuadrado con textura, visible por ambos lados. */
	private static void quad(SubmitNodeCollector out, PoseStack ps, Identifier tex,
							 float halfW, float halfH, float zOff, int alpha) {
		if (alpha <= 0) return;
		out.submitCustomGeometry(ps, RenderTypes.eyes(tex), (pose, vc) -> {
			for (int side = 0; side < 2; side++) {
				float z = zOff + (side == 0 ? 0.001f : -0.001f);
				float n = side == 0 ? 1f : -1f;
				float[][] v = side == 0
						? new float[][]{{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}}
						: new float[][]{{1, -1, 0, 1}, {-1, -1, 1, 1}, {-1, 1, 1, 0}, {1, 1, 0, 0}};
				for (float[] p : v) {
					vc.addVertex(pose, p[0] * halfW, p[1] * halfH, z)
							.setColor(255, 255, 255, alpha)
							.setUv(p[2], p[3])
							.setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(LightTexture.FULL_BRIGHT)
							.setNormal(pose, 0f, 0f, n);
				}
			}
		});
	}
}
