package lancet_.paxifix.mixin.compat.moonlight;

import net.mehvahdjukaar.moonlight.api.resources.pack.DynamicResourcePack;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(DynamicResourcePack.class)
public interface DynamicResourcePackAccessor {
    @Accessor("metadata")
    Supplier<PackMetadataSection> metadata();
}
