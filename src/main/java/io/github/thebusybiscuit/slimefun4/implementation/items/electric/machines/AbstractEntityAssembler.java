package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines;

import io.github.thebusybiscuit.cscorelib2.item.CustomItem;
import io.github.thebusybiscuit.cscorelib2.math.DoubleHandler;
import io.github.thebusybiscuit.cscorelib2.protection.ProtectableAction;
import io.github.thebusybiscuit.slimefun4.api.events.BlockPlacerPlaceEvent;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunPlugin;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Lists.RecipeType;
import me.mrCookieSlime.Slimefun.Objects.Category;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.SlimefunItem;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.UnregisterReason;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.SlimefunItemStack;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * This is an abstract super class for Entity Assemblers.
 *
 * @author TheBusyBiscuit
 * @see WitherAssembler
 * @see IronGolemAssembler
 */
public abstract class AbstractEntityAssembler<T extends Entity> extends SimpleSlimefunItem<BlockTicker> implements EnergyNetComponent {

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_OFFSET = "offset";

    private final int[] border = {0, 2, 3, 4, 5, 6, 8, 12, 14, 21, 23, 30, 32, 39, 40, 41};
    private final int[] inputSlots = {19, 28, 25, 34};

    private final int[] headSlots = {19, 28};
    private final int[] headBorder = {9, 10, 11, 18, 20, 27, 29, 36, 37, 38};

    private final int[] bodySlots = {25, 34};
    private final int[] bodyBorder = {15, 16, 17, 24, 26, 33, 35, 42, 43, 44};

    private int lifetime = 0;

    public AbstractEntityAssembler(Category category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);

        new BlockMenuPreset(getId(), item.getImmutableMeta().getDisplayName().orElse("Entity Assembler")) {

            @Override
            public void init() {
                for (int i : border) {
                    addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
                }

                for (int i : headBorder) {
                    addItem(i, new CustomItem(getHeadBorder(), " "), ChestMenuUtils.getEmptyClickHandler());
                }

                for (int i : bodyBorder) {
                    addItem(i, new CustomItem(getBodyBorder(), " "), ChestMenuUtils.getEmptyClickHandler());
                }

                constructMenu(this);
            }

            @Override
            public void newInstance(BlockMenu menu, Block b) {
                updateBlockInventory(menu, b);
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                return p.hasPermission("slimefun.inventory.bypass") || SlimefunPlugin.getProtectionManager().hasPermission(p, b.getLocation(), ProtectableAction.ACCESS_INVENTORIES);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                if (flow == ItemTransportFlow.INSERT) {
                    return inputSlots;
                } else {
                    return new int[0];
                }
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(DirtyChestMenu menu, ItemTransportFlow flow, ItemStack item) {
                if (flow == ItemTransportFlow.INSERT && item != null) {
                    if (item.getType() == getBody().getType()) {
                        return bodySlots;
                    }

                    if (item.getType() == getHead().getType()) {
                        return headSlots;
                    }
                }

                return new int[0];
            }
        };

        addItemHandler(onPlace());
        registerBlockHandler(getId(), (p, b, stack, reason) -> {
            if (reason == UnregisterReason.EXPLODE) {
                return false;
            }

            BlockMenu inv = BlockStorage.getInventory(b);

            if (inv != null) {
                inv.dropItems(b.getLocation(), headSlots);
                inv.dropItems(b.getLocation(), bodySlots);
            }

            return true;
        });
    }

    private BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(true) {

            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                onPlace(e);
            }

            @Override
            public void onBlockPlacerPlace(BlockPlacerPlaceEvent e) {
                onPlace(e);
            }

            private void onPlace(BlockEvent e) {
                BlockStorage.addBlockInfo(e.getBlock(), KEY_OFFSET, "3.0");
                BlockStorage.addBlockInfo(e.getBlock(), KEY_ENABLED, String.valueOf(false));
            }
        };
    }

    private void updateBlockInventory(BlockMenu menu, Block b) {
        if (!BlockStorage.hasBlockInfo(b) || BlockStorage.getLocationInfo(b.getLocation(), "enabled") == null || BlockStorage.getLocationInfo(b.getLocation(), "enabled").equals(String.valueOf(false))) {
            menu.replaceExistingItem(22, new CustomItem(Material.GUNPOWDER, "&7是否可用: &4\u2718", "", "&e> 单击开启机器"));
            menu.addMenuClickHandler(22, (p, slot, item, action) -> {
                BlockStorage.addBlockInfo(b, "enabled", String.valueOf(true));
                updateBlockInventory(menu, b);
                return false;
            });
        } else {
            menu.replaceExistingItem(22, new CustomItem(Material.REDSTONE, "&7是否可用: &2\u2714", "", "&e> 单击关闭机器"));
            menu.addMenuClickHandler(22, (p, slot, item, action) -> {
                BlockStorage.addBlockInfo(b, "enabled", String.valueOf(false));
                updateBlockInventory(menu, b);
                return false;
            });
        }

        double offset = (!BlockStorage.hasBlockInfo(b) || BlockStorage.getLocationInfo(b.getLocation(), "offset") == null) ? 3.0F : Double.parseDouble(BlockStorage.getLocationInfo(b.getLocation(), "offset"));

        menu.replaceExistingItem(31, new CustomItem(Material.PISTON, "&7生成高度: &3" + offset + " 格方块高", "", "&f左键: &7+0.1", "&f右键: &7-0.1"));
        menu.addMenuClickHandler(31, (p, slot, item, action) -> {
            double offsetv = DoubleHandler.fixDouble(Double.parseDouble(BlockStorage.getLocationInfo(b.getLocation(), "offset")) + (action.isRightClicked() ? -0.1F : 0.1F));
            BlockStorage.addBlockInfo(b, "offset", String.valueOf(offsetv));
            updateBlockInventory(menu, b);
            return false;
        });
    }

    @Override
    public BlockTicker getItemHandler() {
        return new BlockTicker() {

            @Override
            public void tick(Block b, SlimefunItem sf, Config data) {
                if ("false".equals(BlockStorage.getLocationInfo(b.getLocation(), KEY_ENABLED))) {
                    return;
                }

                if (lifetime % 60 == 0 && getCharge(b.getLocation()) >= getEnergyConsumption()) {
                    BlockMenu menu = BlockStorage.getInventory(b);

                    boolean hasBody = findResource(menu, getBody(), bodySlots);
                    boolean hasHead = findResource(menu, getHead(), headSlots);

                    if (hasBody && hasHead) {
                        consumeResources(menu);

                        removeCharge(b.getLocation(), getEnergyConsumption());
                        double offset = Double.parseDouble(BlockStorage.getLocationInfo(b.getLocation(), KEY_OFFSET));

                        SlimefunPlugin.runSync(() -> {
                            Location loc = new Location(b.getWorld(), b.getX() + 0.5D, b.getY() + offset, b.getZ() + 0.5D);
                            spawnEntity(loc);

                            b.getWorld().playEffect(b.getLocation(), Effect.STEP_SOUND, getHead().getType());
                        });
                    }
                }
            }

            @Override
            public void uniqueTick() {
                lifetime++;
            }

            @Override
            public boolean isSynchronized() {
                return false;
            }
        };
    }

    private boolean findResource(BlockMenu menu, ItemStack item, int[] slots) {
        int found = 0;

        for (int slot : slots) {
            if (SlimefunUtils.isItemSimilar(menu.getItemInSlot(slot), item, true, false)) {
                found += menu.getItemInSlot(slot).getAmount();

                if (found >= item.getAmount()) {
                    return true;
                }
            }
        }

        return false;
    }

    private void consumeResources(BlockMenu inv) {
        int bodyCount = getBody().getAmount();
        int headCount = getHead().getAmount();

        for (int slot : bodySlots) {
            if (SlimefunUtils.isItemSimilar(inv.getItemInSlot(slot), getBody(), true, false)) {
                int amount = inv.getItemInSlot(slot).getAmount();

                if (amount >= bodyCount) {
                    inv.consumeItem(slot, bodyCount);
                    break;
                } else {
                    bodyCount -= amount;
                    inv.replaceExistingItem(slot, null);
                }
            }
        }

        for (int slot : headSlots) {
            if (SlimefunUtils.isItemSimilar(inv.getItemInSlot(slot), getHead(), true, false)) {
                int amount = inv.getItemInSlot(slot).getAmount();

                if (amount >= headCount) {
                    inv.consumeItem(slot, headCount);
                    break;
                } else {
                    headCount -= amount;
                    inv.replaceExistingItem(slot, null);
                }
            }
        }
    }

    protected void constructMenu(BlockMenuPreset preset) {
        preset.addItem(1, new CustomItem(getHead(), "&7在此处放入头颅", "", "&f此处可以放入作为生成实体头颅的物品"), ChestMenuUtils.getEmptyClickHandler());
        preset.addItem(7, new CustomItem(getBody(), "&7在此处放入组装原料", "", "&f此处可以放入作为生成实体躯干的物品"), ChestMenuUtils.getEmptyClickHandler());
        preset.addItem(13, new CustomItem(Material.CLOCK, "&7冷却时间: &b30 秒", "", "&f这个机器需要半分钟的时间装配", "&f所以耐心等等吧!"), ChestMenuUtils.getEmptyClickHandler());
    }

    @Override
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    public abstract int getEnergyConsumption();

    public abstract ItemStack getHead();

    public abstract ItemStack getBody();

    public abstract Material getHeadBorder();

    public abstract Material getBodyBorder();

    public abstract T spawnEntity(Location l);

}