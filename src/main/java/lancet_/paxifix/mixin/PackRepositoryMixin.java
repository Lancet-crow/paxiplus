package lancet_.paxifix.mixin;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yungnickyoung.minecraft.paxi.PaxiCommon;
import com.yungnickyoung.minecraft.paxi.PaxiRepositorySource;
import com.yungnickyoung.minecraft.paxi.client.ClientMixinUtil;
import com.yungnickyoung.minecraft.paxi.util.IPaxiSourceProvider;
import com.yungnickyoung.minecraft.yungsapi.io.JSON;
import lancet_.paxifix.PaxiFix;
import lancet_.paxifix.access.PackInterface;
import lancet_.paxifix.util.PaxiFixOrdering;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(value = PackRepository.class, priority = 2000)
public abstract class PackRepositoryMixin implements PackInterface {

    @Final
    @Shadow
    private Set<RepositorySource> sources;

    @Shadow
    private Stream<Pack> getAvailablePacks(Collection<String> names) {
        PaxiFix.LOGGER.info("Logging getAvailablePacks");
        throw new AssertionError();
    }

    @Shadow private Map<String, Pack> available;

    @Shadow public abstract boolean removePack(String string);

    @Shadow private List<Pack> selected;

    @Unique
    private ArrayList<String> earlierPaxiPacks = new ArrayList<>();


    @Inject(at = @At("RETURN"), method = "discoverAvailable", cancellable = true)
    private void paxi_removeDuplicatesInAvailableList(CallbackInfoReturnable<Map<String, Pack>> cir) {
        // Paxi repo source. Will be fetched differently depending if we're loading data or resource packs.
        Optional<RepositorySource> repositorySource = getPaxiRepositorySource();
        if (repositorySource.isEmpty()) {
            PaxiCommon.LOGGER.error("Unable to find Paxi repository source when removing duplicates from available packs. You may see duplicate pack entries in your list of available packs on the Resource Packs screen.");
            return;
        }
        // Get a list of all ordered Paxi packs
        PaxiRepositorySource paxiRepositorySource = (PaxiRepositorySource) repositorySource.get();
        List<String> orderedPaxiPacks = paxiRepositorySource.orderedPaxiPacks;

        // Remove duplicates from the available packs list and return the new list
        Map<String, Pack> availablePacks = new TreeMap<>(cir.getReturnValue());
        Set<String> keysToRemove = new HashSet<>();

        for (String vanillaPackId : availablePacks.keySet()) {
            /*PaxiFix.LOGGER.info("Found pack: {}, required: {}", vanillaPackId, availablePacks.get(vanillaPackId).isRequired());
            String strippedVanillaPackId = vanillaPackId.replaceFirst("^file/", "");
            if (earlierPaxiPacks.contains(strippedVanillaPackId) && !orderedPaxiPacks.contains(strippedVanillaPackId)){
                ((PackInterface)availablePacks.get(vanillaPackId)).setRequired(false);

            }*/            // Vanilla pack IDs are stored as "file/" + fileName,
            // but Paxi packs are stored as just the fileName.
            for (String paxiPackId : orderedPaxiPacks) {
                if (vanillaPackId.equals("file/" + paxiPackId)) {
                    //PaxiFix.LOGGER.info("Deleting duplicate file: {}", paxiPackId);
                    keysToRemove.add(vanillaPackId);

                    break;
                }
            }
        }

        keysToRemove.forEach(availablePacks::remove);
        cir.setReturnValue(availablePacks);
    }

    /*@Inject(method = "rebuildSelected", at = @At("HEAD"))
    private void paxiFix$testLog(Collection<String> collection, CallbackInfoReturnable<List<Pack>> cir){
        PaxiFix.LOGGER.info("Logging rebuildSelected");
        PaxiCommon.LOGGER.info("logging rebuildSelected");
    }*/

    //@Inject(at = @At("HEAD"), method = "rebuildSelected", cancellable = true)
    @WrapMethod(method = "rebuildSelected")
    private List<Pack> paxiFix$fixPacksDistortion(Collection<String> collection, Operation<List<Pack>> original) {
        //PaxiFix.LOGGER.info("Logging rebuildSelected2");
        //PaxiCommon.LOGGER.info("logging rebuildSelected2");
        Optional<RepositorySource> paxiRepositorySource = getPaxiRepositorySource();

        List<Pack> sortedEnabledPacks = this.getAvailablePacks(collection).collect(Collectors.toList());

        ArrayList<Pack> orderedPaxiPacks = new ArrayList<>();

        /*sortedEnabledPacks.forEach((pack) -> {
            PaxiFix.LOGGER.info("Pack, set required: {}, {}", pack.getId(), pack.isRequired());
        });*/

        // Grab a list of all Paxi packs from the Paxi repo source, if it exists.
        // We must gather Paxi packs separately because vanilla uses a TreeMap to store all packs, so they are
        // stored lexicographically, but for Paxi we need them to be enabled in a specific order
        // (determined by the user's load order json)
        if (paxiRepositorySource.isPresent() && ((PaxiRepositorySource) paxiRepositorySource.get()).hasPacks()) {
            orderedPaxiPacks = new ArrayList<>(this.getAvailablePacks(((PaxiRepositorySource) paxiRepositorySource.get()).orderedPaxiPacks).toList());
            sortedEnabledPacks.removeAll(orderedPaxiPacks); // Ordered packs should always load after all other packs, so remove them for now
        }

        try {
            File file = Paths.get(PaxiCommon.BASE_PACK_DIRECTORY.toString(), "past_paxi_packs.json").toFile();
            if (!file.exists()){
                file = new File(file.toURI());
            }
            earlierPaxiPacks = JSON.loadObjectFromJsonFile(file.toPath(), PaxiFixOrdering.class).getOrderedPackNames();
            //PaxiFix.LOGGER.info("Found earlier Paxi Packs: {}", earlierPaxiPacks);
        }
        catch (IOException ex){
            PaxiFix.LOGGER.error(ex.getMessage());
        }

        sortedEnabledPacks.removeIf(pack ->{
            //PaxiFix.LOGGER.info("Deleting pack {} from selected ENABLED", pack.getId());
            return earlierPaxiPacks.contains(pack.getId().replaceFirst("^file/", ""));
        });

        for(Pack pack : this.available.values()) {
            if (pack.isRequired() && !sortedEnabledPacks.contains(pack) && !orderedPaxiPacks.contains(pack)) {
                pack.getDefaultPosition().insert(sortedEnabledPacks, pack, Functions.identity(), false);
            }
        }

        // Add all Paxi packs
        orderedPaxiPacks.forEach(pack -> {
            if (pack.isRequired() && !sortedEnabledPacks.contains(pack)) {
                pack.getDefaultPosition().insert(sortedEnabledPacks, pack, Functions.identity(), false);
            }
        });

        return ImmutableList.copyOf(sortedEnabledPacks);
        //cir.cancel();
    }

    @Inject(method = "reload", at = @At("TAIL"))
    private void deletePaxiUnloads(CallbackInfo ci){
        Optional<RepositorySource> paxiRepositorySource = getPaxiRepositorySource();

        ArrayList<Pack> orderedPaxiPacks = new ArrayList<>();

        if (paxiRepositorySource.isPresent() && ((PaxiRepositorySource) paxiRepositorySource.get()).hasPacks()) {
            orderedPaxiPacks = new ArrayList<>(this.getAvailablePacks(((PaxiRepositorySource) paxiRepositorySource.get()).orderedPaxiPacks).toList());
        }

        ArrayList<Pack> finalOrderedPaxiPacks = orderedPaxiPacks;
        this.selected.forEach(pack ->{
            if (!finalOrderedPaxiPacks.contains(pack) && earlierPaxiPacks.contains(pack.getId().replaceFirst("^file/", ""))){
                removePack(pack.getId());
            }
        });
        try {
            if (!earlierPaxiPacks.containsAll(orderedPaxiPacks.stream().map(Pack::getId).toList())){
                earlierPaxiPacks.removeIf((pack) -> finalOrderedPaxiPacks.stream().anyMatch(((x) -> x.getId().equals(pack))));
                orderedPaxiPacks.forEach((pack) -> {if (!earlierPaxiPacks.contains(pack.getId())){
                    earlierPaxiPacks.add(pack.getId());
                }});
                File file = Paths.get(PaxiCommon.BASE_PACK_DIRECTORY.toString(), "past_paxi_packs.json").toFile();
                if (!file.exists()){
                    file = new File(file.toURI());
                }
                JSON.createJsonFileFromObject(file.toPath(), new PaxiFixOrdering(earlierPaxiPacks));
            }
        }
        catch (IOException ex){
            PaxiFix.LOGGER.error(ex.getMessage());
        }
    }

    @Unique
    private Optional<RepositorySource> getPaxiRepositorySource() {
        //PaxiFix.LOGGER.info("Getting Paxi repo");
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

    /*@TargetHandler(
            mixin = "com.yungnickyoung.minecraft.paxi.mixin.MixinPackRepositoryFabric",
            name = "paxi_buildEnabledProfilesFabric",
            prefix = "handler"
    )
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"))
    private void doSomething(Collection<String> enabledNames, CallbackInfoReturnable<List<Pack>> cir, CallbackInfo ci) {
        PaxiFix.LOGGER.info("Injecting into handler$######$modid$targetMethod");
    }*/
}
