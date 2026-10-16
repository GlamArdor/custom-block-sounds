package com.reign.customblocksounds.mixin;

import com.reign.customblocksounds.SoundMapManager;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {
	@Inject(method = "getSoundType", at = @At("HEAD"), cancellable = true)
	private void customblocksounds$overrideSoundType(BlockState state, CallbackInfoReturnable<SoundType> cir) {
		if (SoundMapManager.BYPASS.get()) {
			return;
		}
		SoundType override = SoundMapManager.INSTANCE.getGroupOverride(state, () -> {
			SoundMapManager.BYPASS.set(Boolean.TRUE);
			try {
				return state.getSoundType();
			} finally {
				SoundMapManager.BYPASS.set(Boolean.FALSE);
			}
		});
		if (override != null) {
			cir.setReturnValue(override);
		}
	}
}
