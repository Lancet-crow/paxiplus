package lancet_.paxifix.util;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class PaxiFixOrdering {
    @SerializedName("loadOrder")
    private final ArrayList<String> orderedPackNames;

    public PaxiFixOrdering(ArrayList<String> orderedPackNames) {
        this.orderedPackNames = orderedPackNames;
    }

    public ArrayList<String> getOrderedPackNames() {
        return this.orderedPackNames;
    }
}
