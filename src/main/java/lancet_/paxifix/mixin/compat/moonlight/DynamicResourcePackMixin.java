package lancet_.paxifix.mixin.compat.moonlight;

import com.llamalad7.mixinextras.sugar.Local;
import net.mehvahdjukaar.moonlight.api.resources.pack.DynamicResourcePack;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Pack.class)
public class DynamicResourcePackMixin {
    @Inject(method = "readPackInfo",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/PackResources;getMetadataSection(Lnet/minecraft/server/packs/metadata/MetadataSectionSerializer;)Ljava/lang/Object;",
            shift = At.Shift.BEFORE),
            cancellable = true)
    private static void checkIfMoonlightDynamicPack(String string, Pack.ResourcesSupplier resourcesSupplier, CallbackInfoReturnable<Pack.Info> cir, @Local PackResources packResources){
        if (packResources.packId().equals("Moonlight Mods Dynamic Assets")){
            DynamicResourcePack dynamicResourcePack = (DynamicResourcePack) packResources;
            DynamicResourcePackAccessor dynamicResourcePackAccessor = (DynamicResourcePackAccessor) dynamicResourcePack;
            cir.setReturnValue(new Pack.Info(dynamicResourcePackAccessor.metadata().get().getDescription(),
                    dynamicResourcePackAccessor.metadata().get().getPackFormat(), FeatureFlagSet.of()));
        }

    }
}
