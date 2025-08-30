package lancet_.paxifix.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yungnickyoung.minecraft.paxi.PaxiRepositorySource;
import com.yungnickyoung.minecraft.paxi.client.ClientMixinUtil;
import com.yungnickyoung.minecraft.paxi.util.IPaxiSourceProvider;
import lancet_.paxifix.PaxiFix;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.*;

import java.util.*;

@Mixin(value = PackRepository.class, priority = 2000)
public abstract class PackRepositoryMixin {

    @Final
    @Shadow
    private Set<RepositorySource> sources;

    @Unique
    private Optional<RepositorySource> getPaxiRepositorySource() {
        boolean isClient = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        Optional<RepositorySource> paxiRepositorySource = Optional.empty();

        // Data-pack only
        Optional<ModResourcePackCreator> moddedPackRepositorySource = this.sources.stream()
                .filter(provider -> provider instanceof ModResourcePackCreator)
                .findFirst()
                .map(repositorySource -> (ModResourcePackCreator) repositorySource);
        if (moddedPackRepositorySource.isPresent()) {
            paxiRepositorySource = Optional.of(((IPaxiSourceProvider) moddedPackRepositorySource.get()).getPaxiSource());
        }

        // Resource-pack only.
        // Uses separate util method to avoid classloading client-only
        // classes when using Paxi on a dedicated server.
        if (paxiRepositorySource.isEmpty() && isClient) {
            paxiRepositorySource = ClientMixinUtil.getClientRepositorySource(this.sources);
        }

        return paxiRepositorySource;
    }

    @WrapMethod(method = "discoverAvailable")
    private Map<String, Pack> paxiFix$deleteDuplicatePacks(Operation<Map<String, Pack>> original){
        Map<String, Pack> map = original.call();
        Optional<RepositorySource> repositorySource = getPaxiRepositorySource();
        if (repositorySource.isEmpty()){
            PaxiFix.LOGGER.info("Repository could not be found when deleting duplicate Paxi packs");
        }
        else{
            PaxiRepositorySource paxiRepositorySource = (PaxiRepositorySource) repositorySource.get();
            List<String> orderedPaxiPacks = paxiRepositorySource.orderedPaxiPacks;
            Map<String, Pack> availablePacks = new TreeMap<>(map);
            Set<String> keysToRemove = new HashSet<>();
            for (String vanillaPackId : availablePacks.keySet()) {
                for (String paxiPackId : orderedPaxiPacks) {
                    if (vanillaPackId.equals("file/" + paxiPackId)) {
                        keysToRemove.add(vanillaPackId);
                        break;
                    }
                }
            }
            keysToRemove.forEach(availablePacks::remove);
            map = new TreeMap<>(availablePacks);
        }
        return map;
    }
}
