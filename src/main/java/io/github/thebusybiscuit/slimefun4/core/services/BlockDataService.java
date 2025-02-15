package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import io.papermc.lib.PaperLib;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The {@link BlockDataService} is similar to the {@link CustomItemDataService},
 * it is responsible for storing NBT data inside a {@link TileState}.
 *
 * This is used to speed up performance and prevent
 *
 * @author TheBusyBiscuit
 *
 */
public class BlockDataService implements Keyed {

    private final NamespacedKey namespacedKey;
    private final NamespacedKey universalDataKey;

    /**
     * This creates a new {@link BlockDataService} for the given {@link Plugin}.
     * The {@link Plugin} and key will together form a {@link NamespacedKey} used to store
     * data on a {@link TileState}.
     *
     * @param plugin
     *            The {@link Plugin} responsible for this service
     * @param key
     *            The key under which to store data
     */
    public BlockDataService(@Nonnull Plugin plugin, @Nonnull String key) {
        namespacedKey = new NamespacedKey(plugin, key);
        universalDataKey = new NamespacedKey(plugin, "slimefun_unidata_uuid");
    }

    @Override
    public NamespacedKey getKey() {
        return namespacedKey;
    }

    /**
     * This will store the given {@link String} inside the NBT data of the given {@link Block}
     *
     * @param b
     *            The {@link Block} in which to store the given value
     * @param value
     *            The value to store
     */
    public void setBlockData(@Nonnull Block b, @Nonnull String value) {
        setBlockData(b, namespacedKey, value);
    }

    /**
     * This will store the universal data {@link UUID} inside the NBT data of the given {@link Block}
     *
     * @param b
     *            The {@link Block} in which to store the given value
     * @param uuid
     *            The uuid linked to certain slimefun item
     */
    public void updateUniversalDataUUID(@Nonnull Block b, @Nonnull String uuid) {
        setBlockData(b, universalDataKey, uuid);
    }

    /**
     * This will store the given {@link String} inside the NBT data of the given {@link Block}
     *
     * @param b
     *            The {@link Block} in which to store the given value
     * @param value
     *            The value to store
     */
    public void setBlockData(@Nonnull Block b, @Nonnull NamespacedKey key, @Nonnull String value) {
        Validate.notNull(b, "The block cannot be null!");
        Validate.notNull(value, "The value cannot be null!");

        /**
         * Don't use PaperLib here, it seems to be quite buggy in block-placing scenarios
         * and it would be too tedious to check for individual build versions to circumvent this.
         */
        BlockState state = b.getState();

        if (state instanceof TileState tileState) {
            try {
                PersistentDataContainer container = tileState.getPersistentDataContainer();
                container.set(key, PersistentDataType.STRING, value);
                state.update();
            } catch (Exception x) {
                Slimefun.logger().log(Level.SEVERE, "Please check if your Server Software is up to date!");

                String serverSoftware = PaperLib.isSpigot() && !PaperLib.isPaper() ? "Spigot" : Bukkit.getName();
                Slimefun.logger()
                        .log(
                                Level.SEVERE,
                                () -> serverSoftware + " | " + Bukkit.getVersion() + " | " + Bukkit.getBukkitVersion());

                Slimefun.logger()
                        .log(
                                Level.SEVERE,
                                "An Exception was thrown while trying to set Persistent Data for a Block",
                                x);
            }
        }
    }

    /**
     * This method returns the NBT data previously stored inside this {@link Block}.
     *
     * @param b
     *            The {@link Block} to retrieve data from
     *
     * @return The stored value
     */
    public Optional<String> getBlockData(@Nonnull Block b) {
        return getBlockData(b, namespacedKey);
    }

    public Optional<UUID> getUniversalDataUUID(@Nonnull Block b) {
        var uuid = getBlockData(b, universalDataKey);

        return uuid.map(data -> {
            try {
                var uniId = UUID.fromString(data);

                var uniData =
                        Slimefun.getDatabaseManager().getBlockDataController().getUniversalBlockDataFromCache(uniId);

                // Auto fix missing location
                if (uniData != null && uniData.getLastPresent() == null) {
                    uniData.setLastPresent(b.getLocation());
                }

                return uniId;
            } catch (IllegalArgumentException e) {
                return null;
            }
        });
    }

    public Optional<String> getBlockData(@Nonnull Block b, @Nonnull NamespacedKey key) {
        Validate.notNull(b, "The block cannot be null!");

        BlockState state = PaperLib.getBlockState(b, false).getState();
        PersistentDataContainer container = getPersistentDataContainer(state);

        if (container != null) {
            return Optional.ofNullable(container.get(key, PersistentDataType.STRING));
        } else {
            return Optional.empty();
        }
    }

    @Nullable private PersistentDataContainer getPersistentDataContainer(@Nonnull BlockState state) {
        if (state instanceof TileState tileState) {
            return tileState.getPersistentDataContainer();
        } else {
            return null;
        }
    }

    /**
     * This method checks whether the given {@link Material} is a Tile Entity.
     * This is used to determine whether the {@link Block} produced by this {@link Material}
     * produces a {@link TileState}, making it useable as a {@link PersistentDataHolder}.
     *
     * Due to {@link Block#getState()} being a very expensive call performance-wise though,
     * this simple lookup method is used instead.
     *
     * @param type
     *            The {@link Material} to check for
     *
     * @return Whether the given {@link Material} is considered a Tile Entity
     */
    public boolean isTileEntity(@Nullable Material type) {
        if (type == null || type.isAir()) {
            // Cannot store data on air
            return false;
        }

        return SlimefunTag.TILE_ENTITIES.isTagged(type);
    }
}
