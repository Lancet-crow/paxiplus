package lancet_.paxiplus.mixin;

import com.yungnickyoung.minecraft.paxi.PaxiCommon;
import com.yungnickyoung.minecraft.paxi.PaxiRepositorySource;
import com.yungnickyoung.minecraft.paxi.util.IPaxiSourceProvider;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = ModResourcePackCreator.class, priority = 2000)
public class ModResourcePackCreatorMixin implements IPaxiSourceProvider {
    @Shadow
    @Final
    private PackType type;
    @Unique
    private PaxiRepositorySource paxiRepositorySource;

    public ModResourcePackCreatorMixin() {
        super();
    }

    @Inject(
            method = {"<init>"},
            at = {@At("TAIL")}
    )
    private void paxi_addPaxiRepositorySourceFabric(PackType type, CallbackInfo callback) {
        createPaxiRepositorySource(type);
    }

    @Inject(
            method = {"loadPacks"},
            at = {@At("TAIL")}
    )
    private void paxi_loadPaxiPacksFabric(Consumer<Pack> consumer, CallbackInfo ci) {
        if (this.paxiRepositorySource == null) createPaxiRepositorySource(this.type);
    }

    public PaxiRepositorySource getPaxiSource() {
        if (this.paxiRepositorySource == null) createPaxiRepositorySource(this.type);
        return this.paxiRepositorySource;
    }

    @Unique
    private void createPaxiRepositorySource(PackType type){
        if (type == PackType.SERVER_DATA) {
            this.paxiRepositorySource = new PaxiRepositorySource(PaxiCommon.DATA_PACK_DIRECTORY, type, PaxiCommon.DATAPACK_ORDERING_FILE);
        } else if (type == PackType.CLIENT_RESOURCES) {
            this.paxiRepositorySource = new PaxiRepositorySource(PaxiCommon.RESOURCE_PACK_DIRECTORY, type, PaxiCommon.RESOURCEPACK_ORDERING_FILE);
        }
    }
}
