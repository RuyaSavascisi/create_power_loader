package com.hlysine.create_power_loader.config;

import com.hlysine.create_power_loader.content.LoaderMode;
import com.hlysine.create_power_loader.content.LoaderType;
import net.createmod.catnip.config.ConfigBase;

public class CLoader extends ConfigBase {

    public final ConfigInt chunkUpdateInterval = i(10, 0, 200, "chunkUpdateInterval", Comments.chunkUpdateInterval);

    public final ConfigInt unloadGracePeriod = i(20, 0, 20 * 60, "unloadGracePeriod", Comments.unloadGracePeriod);

    public final ConfigFloat speedMultiplier = f(1, 0, 128, "speedMultiplier", Comments.speedMultiplier);

    public final ConfigFloat stressImpact = f(16, 0, Float.MAX_VALUE, "stressImpact", Comments.stressImpact);

    public final ConfigBool enableStatic = b(true, "enableStatic", Comments.enableStatic);

    public final ConfigBool enableContraption;
    public final ConfigInt rangeOnContraption = i(2, 1, 10, "rangeOnContraption", Comments.rangeOnContraption);

    public final ConfigBool enableTrain;
    public final ConfigInt rangeOnTrain = i(2, 1, 10, "rangeOnTrain", Comments.rangeOnTrain);

    public final ConfigBool enableStation;
    public final ConfigInt rangeOnStation = i(2, 1, 10, "rangeOnStation", Comments.rangeOnStation);

    private final LoaderType type;

    public CLoader(LoaderType type) {
        this.type = type;
        enableContraption = b(type == LoaderType.BRASS, "enableContraption", Comments.enableContraption);
        enableTrain = b(type == LoaderType.BRASS, "enableTrain", Comments.enableTrain);
        enableStation = b(type == LoaderType.BRASS, "enableStation", Comments.enableStation);
    }

    public boolean modeEnabled(LoaderMode mode) {
        return switch (mode) {
            case STATIC -> enableStatic.get();
            case CONTRAPTION -> enableContraption.get();
            case TRAIN -> enableTrain.get();
            case STATION -> enableStation.get();
        };
    }

    @Override
    public String getName() {
        return type.getSerializedName();
    }

    private static class Comments {
        static String chunkUpdateInterval = "Number of ticks between chunk loading checks. Does not affect contraptions";
        static String unloadGracePeriod = "Minimum number of ticks between loss of power and chunk unloading. Rounds up to multiples of update interval";
        static String speedMultiplier = "A multiplier for the minimum speed requirement of a chunk loader";
        static String stressImpact = "The stress impact of a chunk loader when it is at 1 RPM";
        static String enableStatic = "Whether the chunk loader functions on the ground when given rotational power";
        static String enableContraption = "Whether the chunk loader functions on contraptions other than trains. WARNING: does not update contraptions that are currently unloaded";
        static String rangeOnContraption = "Chunk loading radius on contraptions. 1 = 1 chunk, 2 = 9 chunks. Contraptions require a minimum radius of 2 for reliable loading";
        static String enableTrain = "Whether the chunk loader functions on trains";
        static String rangeOnTrain = "Chunk loading radius on trains. 1 = 1 chunk, 2 = 9 chunks";
        static String enableStation = "Whether the chunk loader functions when attached to train stations.";
        static String rangeOnStation = "Chunk loading radius on train stations. 1 = 1 chunk, 2 = 9 chunks";
    }
}
