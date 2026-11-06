package com.yogurines.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yogurines.mod.Yogurines;
import com.yogurines.mod.item.NoteTooltip;
import com.yogurines.mod.net.Net;
import com.yogurines.mod.registry.ModItems;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Registro de todo lo del cliente: teclas, capas del HUD, tooltip, poses y eventos de cámara. */
public final class ClientSetup {
    public static final KeyMapping PHONE_KEY = new KeyMapping("key.yogurines.phone", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.yogurines");

    /** Pose de foto normal: los dos brazos delante sujetando el móvil. */
    public static HumanoidModel.ArmPose CAM_POSE;
    /** Pose de selfie: un brazo levantado hacia delante. */
    public static HumanoidModel.ArmPose SELFIE_POSE;

    public static void init(IEventBus modBus) {
        CAM_POSE = HumanoidModel.ArmPose.create("YOGURINES_CAMERA", true, (model, entity, arm) -> {
            float side = arm == HumanoidArm.RIGHT ? -1 : 1;
            var a = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            a.xRot = model.head.xRot - 1.45F;
            a.yRot = model.head.yRot - side * 0.42F;
            a.zRot = 0;
        });
        SELFIE_POSE = HumanoidModel.ArmPose.create("YOGURINES_SELFIE", false, (model, entity, arm) -> {
            float side = arm == HumanoidArm.RIGHT ? -1 : 1;
            var a = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            a.xRot = -2.05F;
            a.yRot = side * 0.3F;
            a.zRot = 0;
        });
        modBus.addListener(ClientSetup::keys);
        modBus.addListener(ClientSetup::overlays);
        modBus.addListener(ClientSetup::tooltips);
        modBus.addListener(ClientSetup::setup);
        MinecraftForge.EVENT_BUS.register(ClientSetup.class);
    }

    private static void keys(RegisterKeyMappingsEvent e) {
        e.register(PHONE_KEY);
    }

    private static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("camera", (gui, g, pt, w, h) -> CameraMode.render(g, w, h));
        e.registerAboveAll("phone_toasts", (gui, g, pt, w, h) -> Toasts.render(g, w, h));
    }

    private static void tooltips(RegisterClientTooltipComponentFactoriesEvent e) {
        e.register(NoteTooltip.class, NoteTooltipComponent::new);
    }

    private static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> ItemProperties.register(ModItems.PHONE.get(), new ResourceLocation(Yogurines.MOD_ID, "on"),
                (stack, level, entity, seed) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (entity != null && entity == mc.player && ClientHooks.phoneInUse()) return 1f;
                    if (entity instanceof Player p && ClientData.poses.containsKey(p.getUUID())) return 1f;
                    return 0f;
                }));
    }

    // ---------- eventos del juego ----------

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (PHONE_KEY.consumeClick()) {
            if (mc.screen == null && ClientHooks.hasPhone()) ClientHooks.openPhone();
        }
        CameraMode.tick();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.END) CameraMode.afterFrame();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void overlay(RenderGuiOverlayEvent.Pre e) {
        if (!CameraMode.active()) return;
        ResourceLocation id = e.getOverlay().id();
        boolean ours = id.getNamespace().equals(Yogurines.MOD_ID);
        if (!ours || CameraMode.capturing()) e.setCanceled(true);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void hand(RenderHandEvent e) {
        if (CameraMode.active()) e.setCanceled(true);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void nameTag(RenderNameTagEvent e) {
        if (CameraMode.active()) { e.setResult(Event.Result.DENY); return; }
        if (e.getEntity() instanceof Player p && ClientData.hiddenTags.contains(p.getUUID())) e.setResult(Event.Result.DENY);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void renderPlayer(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        Integer mode = ClientData.poses.get(p.getUUID());
        if (p == Minecraft.getInstance().player && CameraMode.active()) mode = CameraMode.selfie() ? 2 : 1;
        if (mode == null) return;
        PlayerModel<?> model = e.getRenderer().getModel();
        if (mode == 1) {
            model.rightArmPose = CAM_POSE;
            model.leftArmPose = CAM_POSE;
        } else {
            boolean right = p.getMainArm() == HumanoidArm.RIGHT;
            model.rightArmPose = right ? SELFIE_POSE : HumanoidModel.ArmPose.EMPTY;
            model.leftArmPose = right ? HumanoidModel.ArmPose.EMPTY : SELFIE_POSE;
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!CameraMode.selfie()) return;
        Camera cam = e.getCamera();
        if (cam.getEntity() == null) return;
        Vec3 eye = cam.getEntity().getEyePosition((float) e.getPartialTick());
        double d = cam.getPosition().distanceTo(eye);
        double target = 1.7;
        cam.move(Math.max(0, d - target), CameraMode.offY() * 0.6, CameraMode.offX() * 0.8);
        e.setYaw(e.getYaw() - CameraMode.offX() * 12f);
        e.setPitch(e.getPitch() + CameraMode.offY() * 10f);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (CameraMode.active() && CameraMode.zoom() > 1f) e.setFOV(e.getFOV() / CameraMode.zoom());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        if (!CameraMode.active() || Minecraft.getInstance().screen != null) return;
        if (e.isUseItem()) {
            CameraMode.shoot();
        } else if (e.isAttack()) {
            CameraMode.toggleSelfie();
        } else return;
        e.setSwingHand(false);
        e.setCanceled(true);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (!CameraMode.active() || Minecraft.getInstance().screen != null) return;
        CameraMode.scroll(e.getScrollDelta());
        e.setCanceled(true);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void key(InputEvent.Key e) {
        if (!CameraMode.active() || Minecraft.getInstance().screen != null) return;
        if (e.getAction() == GLFW.GLFW_PRESS && e.getKey() == GLFW.GLFW_KEY_H) CameraMode.toggleGuide();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void screenOpen(ScreenEvent.Opening e) {
        if (!CameraMode.active()) return;
        if (e.getNewScreen() instanceof PhoneScreen) return;
        boolean pause = e.getNewScreen() instanceof PauseScreen;
        if (pause) e.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> CameraMode.exit(pause));
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn e) {
        ClientData.load();
        Net.toServer("sync", new CompoundTag());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        if (CameraMode.active()) CameraMode.exit(false);
        ClientData.clear();
        Minecraft.getInstance().tell(Images::clear);
    }

    private ClientSetup() {}
}
