package com.reign.customblocksounds.mixin;

import com.reign.customblocksounds.SoundMapManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.sound.BlockSoundGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public abstract class AbstractBlockMixin {
	@Inject(method = "getSoundGroup", at = @At("HEAD"), cancellable = true)
	private void customblocksounds$overrideSoundGroup(BlockState state, CallbackInfoReturnable<BlockSoundGroup> cir) {
		if (SoundMapManager.BYPASS.get()) {
			return;
		}
		BlockSoundGroup override = SoundMapManager.INSTANCE.getGroupOverride(state, () -> {
			SoundMapManager.BYPASS.set(Boolean.TRUE);
			try {
				return state.getSoundGroup();
			} finally {
				SoundMapManager.BYPASS.set(Boolean.FALSE);
			}
		});
		if (override != null) {
			cir.setReturnValue(override);
		}
	}
}
