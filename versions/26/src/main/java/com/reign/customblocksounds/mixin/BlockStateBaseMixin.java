package com.reign.customblocksounds.mixin;

import com.reign.customblocksounds.SoundMapManager;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
	@Inject(method = "getSoundType", at = @At("HEAD"), cancellable = true)
	private void customblocksounds$overrideSoundType(CallbackInfoReturnable<SoundType> cir) {
		if (SoundMapManager.BYPASS.get()) {
			return;
		}
		Object self = this;
		if (!(self instanceof BlockState state)) {
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
