package com.yogurines.mod.client;

import com.yogurines.mod.item.PhoneItem;
import com.yogurines.mod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Puntos de entrada del lado cliente que llaman los items y bloques. */
public final class ClientHooks {
    public static void openPhone() {
        openPhoneAt(PhoneScreen.Page.HOME);
    }

    public static void openPhoneAt(PhoneScreen.Page page) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (CameraMode.active()) CameraMode.exit(false);
        ClientData.load();
        mc.setScreen(new PhoneScreen(page));
        if (page == PhoneScreen.Page.HOME) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.PHONE_OPEN.get(), 1f, 0.8f));
        }
    }

    public static void openWallet(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new WalletScreen(hand));
    }

    public static void openAtm() {
        Minecraft.getInstance().setScreen(new AtmScreen());
    }

    public static boolean phoneInUse() {
        return Minecraft.getInstance().screen instanceof PhoneScreen || CameraMode.active();
    }

    public static boolean hasPhone() {
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof PhoneItem) return true;
        return false;
    }

    public static IClientItemExtensions phoneExtensions() {
        return new IClientItemExtensions() {};
    }

    private ClientHooks() {}
}
