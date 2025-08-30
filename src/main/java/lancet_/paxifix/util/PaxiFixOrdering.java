package lancet_.paxifix.util;

import com.google.gson.annotations.SerializedName;

public record PaxiFixOrdering(@SerializedName("loadOrder") String[] orderedPackNames) {

    @Override
    public String[] orderedPackNames() {
        return this.orderedPackNames;
    }
}
