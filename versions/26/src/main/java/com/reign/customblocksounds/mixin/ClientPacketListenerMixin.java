package com.reign.customblocksounds.mixin;

import com.reign.customblocksounds.ServerSoundReplacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
	private void customblocksounds$replaceServerSound(ClientboundSoundPacket packet, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		// The handler is first invoked on the network thread and re-scheduled onto
		// the render thread by PacketUtils; only act on the render thread.
		if (!client.isSameThread()) {
			return;
		}
		if (ServerSoundReplacer.tryReplace(client, packet)) {
			ci.cancel();
		}
	}
}
