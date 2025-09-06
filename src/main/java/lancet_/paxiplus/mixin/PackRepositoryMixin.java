package lancet_.paxiplus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.yungnickyoung.minecraft.paxi.PaxiPackSource;
import com.yungnickyoung.minecraft.paxi.PaxiRepositorySource;
import com.yungnickyoung.minecraft.paxi.client.ClientMixinUtil;
import com.yungnickyoung.minecraft.paxi.util.IPaxiSourceProvider;
import lancet_.paxiplus.PaxiPlus;
import lancet_.paxiplus.interfaces.PackRepositoryTricks;
import lancet_.paxiplus.interfaces.PaxiRepositorySourceTricks;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(value = PackRepository.class, priority = 2000)
public abstract class PackRepositoryMixin implements PackRepositoryTricks {

    @Final
    @Shadow
    private Set<RepositorySource> sources;

    @Unique
    private Map<String, Pack> loadingPacksMap = Map.of();

    @Unique
    public Optional<RepositorySource> getPaxiRepositorySource() {
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
    private Map<String, Pack> paxiPlus$deleteDuplicatePacks(Operation<Map<String, Pack>> original) {
        loadingPacksMap = Map.of();
        Map<String, Pack> map = original.call();
        Optional<RepositorySource> repositorySource = getPaxiRepositorySource();
        if (repositorySource.isEmpty()) {
            PaxiPlus.LOGGER.info("Repository could not be found when deleting duplicate Paxi packs");
        } else {
            PaxiRepositorySource paxiRepositorySource = (PaxiRepositorySource) repositorySource.get();
            List<String> orderedPacks = ((PaxiRepositorySourceTricks) paxiRepositorySource).orderedPacks();
            Map<String, Pack> availablePacks = new LinkedHashMap<>(Map.copyOf(map));
            Set<String> keysToRemove = new HashSet<>();
            for (String vanillaPackId : availablePacks.keySet()) {
                for (String paxiPackId : orderedPacks) {
                    String strippedPaxiPackId = paxiPackId.replaceFirst("paxi/", "");
                    if (vanillaPackId.equals("file/" + strippedPaxiPackId) || (vanillaPackId.equals(strippedPaxiPackId) && !availablePacks.get(vanillaPackId).getPackSource().equals(PaxiPackSource.PACK_SOURCE_PAXI))) {
                        keysToRemove.add(vanillaPackId);
                        break;
                    }
                }
            }
            keysToRemove.forEach(availablePacks::remove);
            map = Map.copyOf(availablePacks);
            Map<String, Pack> newMap = new LinkedHashMap<>(Map.of());
            int orderedPacksOccusations = 0;
            for (String packName : map.keySet()) {
                if (orderedPacks.stream().anyMatch(string -> string.equals("paxi/" + packName))) {
                    String newPack = orderedPacks.get(orderedPacksOccusations);
                    newMap.put(newPack, map.get(newPack));
                    orderedPacksOccusations++;
                } else {
                    newMap.put(packName, map.get(packName));
                }
            }
            map = newMap;
        }
        return map;
    }

    @Inject(method = "discoverAvailable", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/RepositorySource;loadPacks(Ljava/util/function/Consumer;)V"))
    private void pullOutPacksInDiscoveringProcess(CallbackInfoReturnable<Map<String, Pack>> cir, @Local LocalRef<Map<String, Pack>> map, @Local RepositorySource repositorySource) {
        this.loadingPacksMap = map.get();

        if (sources.stream().toList().indexOf(repositorySource) == sources.size() - 1) {
            Optional<RepositorySource> repoSource = getPaxiRepositorySource();
            if (repoSource.isEmpty()) {
                PaxiPlus.LOGGER.info("Repository could not be found when loading Paxi packs at last");
            } else {
                PaxiRepositorySource paxiRepoSource = (PaxiRepositorySource) repoSource.get();
                ((PaxiRepositorySourceTricks) paxiRepoSource).loadPacksTrick(pack -> map.get().put(pack.getId(), pack), (PackRepository) (Object) this);
                map.set(map.get());
            }
        }
    }

    @Override
    public Map<String, Pack> getAlreadyAvailablePacks() {
        return this.loadingPacksMap;
    }
}
