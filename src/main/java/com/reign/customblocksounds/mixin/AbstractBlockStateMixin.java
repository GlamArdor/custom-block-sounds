package com.reign.customblocksounds.mixin;

import com.reign.customblocksounds.SoundMapManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.sound.BlockSoundGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
	@Inject(method = "getSoundGroup", at = @At("HEAD"), cancellable = true)
	private void customblocksounds$overrideSoundGroup(CallbackInfoReturnable<BlockSoundGroup> cir) {
		if (SoundMapManager.BYPASS.get()) {
			return;
		}
		Object self = this;
		if (!(self instanceof BlockState state)) {
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
