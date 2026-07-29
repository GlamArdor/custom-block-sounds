package com.reign.customblocksounds.mixin;

import com.reign.customblocksounds.ServerSoundReplacer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
	@Inject(method = "onPlaySound", at = @At("HEAD"), cancellable = true)
	private void customblocksounds$replaceServerSound(PlaySoundS2CPacket packet, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		// The handler is first invoked on the network thread and re-scheduled onto
		// the render thread by NetworkThreadUtils; only act on the render thread.
		if (!client.isOnThread()) {
			return;
		}
		if (ServerSoundReplacer.tryReplace(client, packet)) {
			ci.cancel();
		}
	}
}
