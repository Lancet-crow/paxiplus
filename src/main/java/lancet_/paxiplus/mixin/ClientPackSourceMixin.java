package lancet_.paxiplus.mixin;

import com.yungnickyoung.minecraft.paxi.PaxiCommon;
import com.yungnickyoung.minecraft.paxi.PaxiRepositorySource;
import com.yungnickyoung.minecraft.paxi.util.IPaxiSourceProvider;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = BuiltInPackSource.class, priority = 2000)
public class ClientPackSourceMixin implements IPaxiSourceProvider {
    @Shadow @Final private PackType packType;
    @Unique
    private PaxiRepositorySource paxiRepositorySource;

    @Inject(method = "loadPacks", at = @At("TAIL"))
    private void createPaxiSource(Consumer<Pack> consumer, CallbackInfo ci){
        if (this.thisIsClientPackSource() && this.paxiRepositorySource == null) {
            this.paxiRepositorySource = new PaxiRepositorySource(PaxiCommon.RESOURCE_PACK_DIRECTORY, PackType.CLIENT_RESOURCES, PaxiCommon.RESOURCEPACK_ORDERING_FILE);
        }
    }

    @Override
    public PaxiRepositorySource getPaxiSource() {
        if (this.thisIsClientPackSource() && this.paxiRepositorySource == null){
            this.paxiRepositorySource = new PaxiRepositorySource(PaxiCommon.RESOURCE_PACK_DIRECTORY, PackType.CLIENT_RESOURCES, PaxiCommon.RESOURCEPACK_ORDERING_FILE);
        }
        return this.paxiRepositorySource;
    }

    @Unique
    private boolean thisIsClientPackSource() {
        return this.packType.equals(PackType.CLIENT_RESOURCES);
    }
}
