package lancet_.paxifix.mixin;

import lancet_.paxifix.access.PackInterface;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Pack.class)
public class PackMixin implements PackInterface {
    @Mutable
    @Shadow @Final private boolean required;

    public void setRequired(boolean required){
        this.required = false;
    }
}
