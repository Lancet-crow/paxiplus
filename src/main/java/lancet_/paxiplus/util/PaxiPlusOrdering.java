package lancet_.paxiplus.util;

import com.google.gson.annotations.SerializedName;

public record PaxiPlusOrdering(@SerializedName("loadOrder") String[] orderedPackNames) {

    @Override
    public String[] orderedPackNames() {
        return this.orderedPackNames;
    }
}
