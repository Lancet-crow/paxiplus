package lancet_.paxiplus.interfaces;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.List;
import java.util.function.Consumer;

public interface PaxiRepositorySourceTricks {
    List<String> orderedPacks();

    void loadPacksTrick(Consumer<Pack> packAdder, PackRepository packRepository);
}
