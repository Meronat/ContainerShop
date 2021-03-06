/*
 * This file is part of ContainerShop, licensed under the MIT License (MIT).
 *
 * Copyright (c) Meronat <http://www.meronat.com>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.meronat.containershop.listeners;

import com.meronat.containershop.ContainerShop;
import com.meronat.containershop.Util;
import com.meronat.containershop.entities.ShopSign;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.entity.spawn.EntitySpawnCause;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.filter.type.Include;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

public class InteractListener {

    @Listener
    public void onRightClick(InteractBlockEvent.Secondary event, @Root Player player) {
        BlockSnapshot bs = event.getTargetBlock();

        if (!bs.getState().getType().equals(BlockTypes.WALL_SIGN)) {
            return;
        }

        Optional<Location<World>> optionalLocation = bs.getLocation();

        if (!optionalLocation.isPresent()) {
            return;
        }

        Location<World> location = optionalLocation.get();

        Optional<ShopSign> optionalShop = ContainerShop.getSignCollection().getSign(location);

        if (!optionalShop.isPresent()) {
            return;
        }

        ShopSign shopSign = optionalShop.get();

        ItemStack itemStack = shopSign.getItem();

        if (player.get(Keys.IS_SNEAKING).isPresent() && player.get(Keys.IS_SNEAKING).get()) {
            final String buy;

            if (shopSign.getBuyPrice().isPresent()) {
                buy = shopSign.getBuyPrice().get().toPlainString();
            } else {
                buy = "not available";
            }

            final String sell;

            if (shopSign.getSellPrice().isPresent()) {
                sell = shopSign.getSellPrice().get().toPlainString();
            } else {
                sell = "not available";
            }

            Text info = Text.builder()
                .append(Text.of(TextColors.DARK_GREEN, "Id: ", TextColors.GRAY, itemStack.getItem().getId()), Text.NEW_LINE)
                .append(Text.of(TextColors.DARK_GREEN, "Name: ", TextColors.GRAY,  itemStack.getItem().getTranslation().get(player.getLocale())), Text.NEW_LINE)
                .append(Text.of(TextColors.DARK_GREEN, "Amount: ", TextColors.GRAY, itemStack.getQuantity(), Text.NEW_LINE))
                .append(Text.of(TextColors.DARK_GREEN, "Buy price: ", TextColors.GRAY, buy, Text.NEW_LINE))
                .append(Text.of(TextColors.DARK_GREEN, "Sell price: ", TextColors.GRAY, sell, Text.NEW_LINE))
                .append(Text.of(TextColors.DARK_GREEN, "Enchantments: ", TextColors.GRAY, Util.getEnchantments(itemStack)))
                .build();

            player.sendMessage(Text.of(TextColors.DARK_GREEN, "You are buying ", TextColors.LIGHT_PURPLE, itemStack.getQuantity() + " " +
                itemStack.getItem().getTranslation().get(player.getLocale()), TextColors.GRAY, " - Hover for more information.").toBuilder()
                .onHover(TextActions.showText(info))
                .onClick(TextActions.suggestCommand("/cs help"))
                .build());
        } else {
            if (player.get(Keys.GAME_MODE).isPresent() && player.get(Keys.GAME_MODE).get().equals(GameModes.CREATIVE)) {
                player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "You cannot be in creative mode and use shops."));

                return;
            }

            if (player.getUniqueId().equals(shopSign.getOwner())) {
                player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "You cannot buy from your own shop."));

                return;
            }

            if (shopSign.isAccessor(player.getUniqueId())) {
                player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "You cannot buy from a shop you have access to."));
                return;
            }

            Optional<BigDecimal> optionalBuyPrice = shopSign.getBuyPrice();

            if (!optionalBuyPrice.isPresent()) {
                player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "You cannot buy from this shop."));

                return;
            }

            //noinspection ConstantConditions
            PluginContainer pluginContainer = Sponge.getPluginManager().getPlugin("containershop").get();

            EconomyService economyService = ContainerShop.getEconomyService();

            //noinspection ConstantConditions
            UniqueAccount account = economyService.getOrCreateAccount(player.getUniqueId()).get();

            final ResultType econResult;

            if (shopSign.isAdminShop()) {
                econResult = account.withdraw(economyService.getDefaultCurrency(), optionalBuyPrice.get(), Cause.source(pluginContainer).build()).getResult();
            } else {
                //noinspection ConstantConditions
                econResult = account.transfer(
                    economyService.getOrCreateAccount(shopSign.getOwner()).get(),
                    economyService.getDefaultCurrency(),
                    optionalBuyPrice.get(),
                    Cause.source(pluginContainer).build()).getResult();
            }

            if (!econResult.equals(ResultType.SUCCESS)) {
                if (econResult.equals(ResultType.ACCOUNT_NO_FUNDS)) {
                    player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "You do not have enough available funds."));
                } else {
                    player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "There was a problem transferring funds."));
                }

                return;
            }

            ItemStack stack = null;

            if (shopSign.isAdminShop()) {
                stack = shopSign.getItem().copy();
            } else {
                for (Inventory i : Util.getConnectedContainers(location, shopSign)) {
                    if (!i.contains(itemStack)) {
                        continue;
                    }

                    Optional<ItemStack> optionalStack = i.query(itemStack).poll(itemStack.getQuantity());

                    if (!optionalStack.isPresent()) {
                        continue;
                    }

                    if (!(optionalStack.get().getQuantity() == itemStack.getQuantity())) {
                        continue;
                    }

                    stack = optionalStack.get();

                    break;
                }

            }

            if (stack == null) {
                player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "This shop does not have enough stock."));

                return;
            }

            InventoryTransactionResult result = player.getInventory().offer(stack);

            player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.DARK_GREEN, "Successfully purchased items."));

            Collection<ItemStackSnapshot> rejectedItems = result.getRejectedItems();

            if (rejectedItems.isEmpty()) {
                return;
            }

            Location<World> playerLocation = player.getLocation();

            World world = playerLocation.getExtent();

            for (ItemStackSnapshot i : rejectedItems) {
                Item rejected = (Item) world.createEntity(EntityTypes.ITEM, playerLocation.getPosition());

                rejected.offer(Keys.REPRESENTED_ITEM, i);

                world.spawnEntity(rejected,
                    Cause.source(EntitySpawnCause.builder()
                        .entity(rejected)
                        .type(SpawnTypes.PLUGIN)
                        .build()).owner(pluginContainer).build());
            }

            player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "Some items you bought were placed on the ground."));
        }

    }

    @Listener
    public void onLeftClick(InteractBlockEvent.Primary event, @Root Player player) {
        BlockSnapshot bs = event.getTargetBlock();

        if (!bs.getState().getType().equals(BlockTypes.WALL_SIGN)) {
            return;
        }

        Optional<Location<World>> optionalLocation = bs.getLocation();

        if (!optionalLocation.isPresent()) {
            return;
        }

        Location<World> location = optionalLocation.get();

        Optional<ShopSign> optionalShop = ContainerShop.getSignCollection().getSign(location);

        if (!optionalShop.isPresent()) {
            return;
        }

        ShopSign shopSign = optionalShop.get();

        if (player.get(Keys.GAME_MODE).isPresent() && player.get(Keys.GAME_MODE).get().equals(GameModes.CREATIVE)) {
            player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "You cannot be in creative mode and use shops."));
            return;
        }

        if (player.getUniqueId().equals(shopSign.getOwner())) {
            player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "You cannot sell to your own shop."));
            return;
        }

        if (shopSign.isAccessor(player.getUniqueId())) {
            player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "You cannot sell to a shop you have access to."));
            return;
        }

        ItemStack itemStack = shopSign.getItem();

        Optional<BigDecimal> optionalSellPrice = shopSign.getSellPrice();

        if (!optionalSellPrice.isPresent()) {
            player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "You cannot sell to this shop."));

            return;
        }

        Optional<ItemStack> optionalTempStack = player.getInventory().query(itemStack).poll();

        if (!optionalTempStack.isPresent() || optionalTempStack.get().getQuantity() != itemStack.getQuantity()) {
            player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "You do not have enough of this item to sell."));

            return;
        }

        Inventory inventoryToOffer = null;

        if (!shopSign.isAdminShop()) {
            for (Inventory i : Util.getConnectedContainers(location, shopSign)) {
                ItemStack clone = itemStack.copy();

                i.offer(clone);

                if (clone.getQuantity() > 0) {
                    i.query(itemStack).poll(itemStack.getQuantity() - clone.getQuantity());
                    continue;
                }

                i.query(itemStack).poll(itemStack.getQuantity());

                inventoryToOffer = i;

                break;
            }

            if (inventoryToOffer == null) {
                player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "This shop does not have space for your sale."));

                // TODO Send message to owner of shop

                return;
            }
        }

        //noinspection ConstantConditions
        PluginContainer pluginContainer = Sponge.getPluginManager().getPlugin("containershop").get();

        EconomyService economyService = ContainerShop.getEconomyService();

        //noinspection ConstantConditions
        UniqueAccount account = economyService.getOrCreateAccount(player.getUniqueId()).get();

        final ResultType econResult;

        if (shopSign.isAdminShop()) {
            econResult = account.deposit(economyService.getDefaultCurrency(), optionalSellPrice.get(), Cause.source(pluginContainer).build()).getResult();
        } else {
            //noinspection ConstantConditions
            econResult = economyService.getOrCreateAccount(shopSign.getOwner()).get().transfer(
                account,
                economyService.getDefaultCurrency(),
                optionalSellPrice.get(),
                Cause.source(pluginContainer).build()).getResult();
        }

        if (!econResult.equals(ResultType.SUCCESS)) {
            if (econResult.equals(ResultType.ACCOUNT_NO_FUNDS)) {
                player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "The shop owner does not have enough money."));
            } else {
                player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "There was a problem transferring funds."));
            }

            return;
        }

        if (inventoryToOffer == null) {
            player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "There was some extraneous issue. Try again."));

            player.getInventory().offer(optionalTempStack.get());

            return;
        }

        if (!shopSign.isAdminShop()) {
            inventoryToOffer.offer(optionalTempStack.get());
        }

        player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.DARK_GREEN, "Successfully sold items!"));
    }

    @Listener
    @Include({InteractBlockEvent.Primary.class, InteractBlockEvent.Secondary.class})
    public void onPlayerClick(InteractBlockEvent event, @Root Player player) {
        //Special code to handle shift secondary clicking (placing a block)
        if(event instanceof InteractBlockEvent.Secondary && player.get(Keys.IS_SNEAKING).orElse(false)) {
            if(player.getItemInHand(HandTypes.MAIN_HAND).isPresent() && player.getItemInHand(HandTypes.MAIN_HAND).get().getItem().getBlock().isPresent()) {
                if(event.getTargetBlock().getLocation().isPresent() && event.getTargetBlock().getLocation().get().getBlockRelative(event.getTargetSide()).getBlockType() == BlockTypes.AIR) {
                    //If they're sneaking and have an item(block) in their hand, and are clicking to replace air... let the block place handle it
                    return;
                }
            }
        }

        Optional<Location<World>> optionalLocation = event.getTargetBlock().getLocation();

        //Ignore air and invalid locations, and non-lockable blocks
        if(event.getTargetBlock().equals(BlockSnapshot.NONE) || !optionalLocation.isPresent() || !ContainerShop.getConfig().getContainers().contains(event.getTargetBlock().getState().getType().getId())) {
            return;
        }

        Optional<ShopSign> optionalShopSign = Util.getAttachedSign(event.getTargetBlock());

        if (!optionalShopSign.isPresent()) {
            return;
        }

        if (!optionalShopSign.get().isAccessor(player.getUniqueId())) {
            player.sendMessage(ChatTypes.ACTION_BAR, Text.of(TextColors.RED, "You cannot access someone else's shop!"));
            event.setCancelled(true);
        }

    }

}
