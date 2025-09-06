package lancet_.paxifix.interfaces;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.RepositorySource;

import java.util.Map;
import java.util.Optional;

public interface PackRepositoryTricks {
    Optional<RepositorySource> getPaxiRepositorySource();
    Map<String, Pack> getAlreadyAvailablePacks();
}
