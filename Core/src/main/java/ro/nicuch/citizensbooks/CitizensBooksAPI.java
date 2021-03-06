/*

   CitizensBooks
   Copyright (c) 2018 @ Drăghiciu 'nicuch' Nicolae

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/

package ro.nicuch.citizensbooks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import me.clip.placeholderapi.PlaceholderAPI;
import net.citizensnpcs.api.npc.NPC;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.milkbowl.vault.permission.Permission;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import ro.nicuch.citizensbooks.dist.Distribution;
import ro.nicuch.citizensbooks.utils.BookLink;
import ro.nicuch.citizensbooks.utils.UpdateChecker;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CitizensBooksAPI {
    private final CitizensBooksPlugin plugin;
    private Distribution distribution = null;
    private final Map<String, BookLink> filters = new HashMap<>();
    private final File filtersDirectory;
    private final Pattern filterNamePattern = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public CitizensBooksAPI(CitizensBooksPlugin plugin) {
        this.plugin = plugin;
        this.filtersDirectory = new File(this.plugin.getDataFolder() + File.separator + "filters");
    }

    public boolean loadDistribution() {
        // Copyright (c) mbax - Thank you for the great 'modular project' tutorial!
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf('.') + 1);

        this.plugin.getLogger().info("Your server is running version " + version + "!");
        try {
            final Class<?> clazz = Class.forName("ro.nicuch.citizensbooks.dist." + version + ".DistributionHandler");
            if (Distribution.class.isAssignableFrom(clazz)) {
                this.plugin.getLogger().info("Loading support for version " + version + "...");
                this.distribution = (Distribution) clazz.getConstructor().newInstance();
                return true;
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
            this.plugin.getLogger().warning("Well, this version of CitizensBooks is incompatible with your server version " + version + "... ");
            if (UpdateChecker.updateAvailable())
                this.plugin.getLogger().info("Oh look! An update is available! Go to Spigot page and download it! It might fix the error!");
            else
                this.plugin.getLogger().warning("Please don't report this error! We try hard to update it as fast as possible!");
            return false;
        }
        return false;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void reloadFilters(Logger logger) {
        logger.info("Loading filters...");
        this.filters.clear();
        if (!this.filtersDirectory.exists())
            this.filtersDirectory.mkdirs();
        AtomicInteger successfulFile = new AtomicInteger();
        FileVisitor<Path> fileVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                File jsonFile = path.toFile();
                if (!jsonFile.getName().toLowerCase().endsWith(".json")) return FileVisitResult.CONTINUE; // don't log non json files
                try (FileReader fileReader = new FileReader(jsonFile)) {
                    JsonObject jsonObject = CitizensBooksAPI.this.gson.fromJson(fileReader, JsonObject.class);
                    JsonPrimitive jsonFilterName = jsonObject.getAsJsonPrimitive("filter_name");
                    if (!jsonFilterName.isString())  {
                        logger.warning("Failed to load " + jsonFile.getName() + " because it doesn't have a filter name!");
                        return FileVisitResult.CONTINUE;
                    }
                    String filterName = jsonFilterName.getAsString();
                    if (!isValidName(filterName)) {
                        logger.warning("Failed to load " + jsonFile.getName() + " because it doesn't have a valid filter name!");
                        return FileVisitResult.CONTINUE;
                    }
                    JsonObject jsonBookContent = jsonObject.getAsJsonObject("book_content");
                    ItemStack book = CitizensBooksAPI.this.distribution.convertJsonToBook(jsonBookContent);
                    CitizensBooksAPI.this.filters.put(filterName, new BookLink(book, jsonFile.toPath()));
                    successfulFile.incrementAndGet();
                } catch (Exception ex) {
                    logger.warning("Failed to load " + jsonFile.getName());
                    return FileVisitResult.CONTINUE;
                }
                return FileVisitResult.CONTINUE;
            }
        };
        try {
            Files.walkFileTree(this.filtersDirectory.toPath(), fileVisitor);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int successful = successfulFile.get();
        if (successful == 0)
            logger.info("No filter was loaded!");
        else
            logger.info("Loaded " + successfulFile.get() + " filters!");
    }

    public boolean isValidName(String filterName) {
        if (filterName == null)
            return false;
        if (filterName.isEmpty())
            return false;
        return this.filterNamePattern.matcher(filterName).matches();
    }

    public Distribution getDistribution() {
        return this.distribution;
    }

    /**
     * Get the book from filter
     *
     * @param filterName filter name/id
     * @return the book
     */
    public ItemStack getFilter(String filterName) {
        Validate.notNull(filterName, "The filter name is null! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        Validate.notEmpty(filterName, "The filter name is empty! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        Validate.isTrue(this.isValidName(filterName), "Invalid characters found in filterName!");
        return this.filters.getOrDefault(filterName, new BookLink(new ItemStack(Material.WRITTEN_BOOK), null)).getBook();
    }

    /**
     * Check if filter has the book
     *
     * @param filterName filter name/id
     * @return if the filter has the book
     */
    public boolean hasFilter(String filterName) {
        Validate.notNull(filterName, "The filter name is null! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        Validate.isTrue(!filterName.isEmpty(), "The filter name is empty! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        Validate.isTrue(this.isValidName(filterName), "Invalid characters found in filterName!");
        return this.filters.containsKey(filterName);
    }

    /**
     * Create a filter from a book
     *
     * @param filterName filter name/id
     * @param book       the book
     * @throws NullPointerException     if the book is null
     * @throws IllegalArgumentException if the book is not really a book
     */
    public void createFilter(String filterName, ItemStack book) {
        Validate.notNull(filterName, "The filter name is null! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        Validate.notEmpty(filterName, "The filter name is empty! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        Validate.notNull(book, "The ItemStack is null! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        Validate.isTrue(book.getType() == Material.WRITTEN_BOOK, "The ItemStack is not a written book! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        Validate.isTrue(this.isValidName(filterName), "Invalid characters found in filterName!");
        File jsonFile = new File(this.filtersDirectory + File.separator + filterName + ".json");
        try (FileWriter fileWriter = new FileWriter(jsonFile)) {
            JsonPrimitive jsonFilterName = new JsonPrimitive(filterName);
            JsonObject jsonBookContent = this.distribution.convertBookToJson(book);
            JsonObject jsonFileObject = new JsonObject();
            jsonFileObject.add("filter_name", jsonFilterName);
            jsonFileObject.add("book_content", jsonBookContent);
            this.gson.toJson(jsonFileObject, fileWriter);
            this.filters.put(filterName, new BookLink(book, jsonFile.toPath()));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public Set<String> getFilters() {
        return this.filters.keySet();
    }

    /**
     * Remove the filter
     *
     * @param filterName filter name/id
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void removeFilter(String filterName) {
        if (this.filters.containsKey(filterName)) {
            BookLink link = this.filters.remove(filterName);
            File jsonFile = link.getLink().toFile();
            if (jsonFile.exists())
                jsonFile.delete();
        }
    }

    protected void rightClick(Player player) {
        this.distribution.sendRightClick(player);
    }

    /**
     * Open a book for player
     *
     * @param player the player
     * @param book   the book
     * @throws NullPointerException     if the book is null
     * @throws IllegalArgumentException if the book is not really a book
     */
    public void openBook(Player player, ItemStack book) {
        player.closeInventory();
        Validate.notNull(book, "The ItemStack is null! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        Validate.isTrue(book.getType() == Material.WRITTEN_BOOK, "The ItemStack is not a written book! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack old = player.getInventory().getItem(slot);
        PlayerInventory pi = player.getInventory();
        pi.setItem(slot, book);
        this.rightClick(player);
        pi.setItem(slot, old);
    }

    public ItemStack placeholderHook(Player player, ItemStack book, NPC npc) {
        Validate.notNull(book, "The ItemStack is null! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        Validate.isTrue(book.getType() == Material.WRITTEN_BOOK, "The ItemStack is not a written book! This is not an error with CitizensBooks," +
                " so please don't report it. Make sure the plugins that uses CitizensBooks as dependency are correctly configured.");
        if (!this.plugin.isPlaceHolderEnabled())
            return book;
        if (book.hasItemMeta()) {
            BookMeta bookMeta = (BookMeta) book.getItemMeta();
            if (bookMeta.hasTitle()) {
                if (npc == null) {
                    bookMeta.setTitle(PlaceholderAPI.setPlaceholders(player, bookMeta.getTitle()));
                } else {
                    Location loc = npc.getStoredLocation();
                    bookMeta.setTitle(PlaceholderAPI.setPlaceholders(player, bookMeta.getTitle())
                            .replace("%npc_name%", npc.getName())
                            .replace("%npc_id%", npc.getId() + "")
                            .replace("%npc_loc_x%", loc.getX() + "")
                            .replace("%npc_loc_y%", loc.getY() + "")
                            .replace("%npc_loc_z%", loc.getZ() + "")
                            .replace("%npc_loc_world%", loc.getWorld().getName()));
                }
            }
            if (bookMeta.hasAuthor()) {
                if (npc == null) {
                    bookMeta.setAuthor(PlaceholderAPI.setPlaceholders(player, bookMeta.getAuthor()));
                } else {
                    Location loc = npc.getStoredLocation();
                    bookMeta.setAuthor(PlaceholderAPI.setPlaceholders(player, bookMeta.getAuthor())
                            .replace("%npc_name%", npc.getName())
                            .replace("%npc_id%", npc.getId() + "")
                            .replace("%npc_loc_x%", loc.getX() + "")
                            .replace("%npc_loc_y%", loc.getY() + "")
                            .replace("%npc_loc_z%", loc.getZ() + "")
                            .replace("%npc_loc_world%", loc.getWorld().getName()));
                }
            }
            if (bookMeta.hasPages()) {
                if (npc == null) {
                    bookMeta.setPages(PlaceholderAPI.setPlaceholders(player, bookMeta.getPages()));
                } else {
                    Location loc = npc.getStoredLocation();
                    bookMeta.setPages(PlaceholderAPI.setPlaceholders(player, bookMeta.getPages()).stream().map(str -> str
                            .replace("%npc_name%", npc.getName())
                            .replace("%npc_id%", npc.getId() + "")
                            .replace("%npc_loc_x%", loc.getX() + "")
                            .replace("%npc_loc_y%", loc.getY() + "")
                            .replace("%npc_loc_z%", loc.getZ() + "")
                            .replace("%npc_loc_world%", loc.getWorld().getName())).collect(Collectors.toList()));
                }
            }
            book.setItemMeta(bookMeta);
        }
        return book;
    }

    public boolean hasPermission(CommandSender sender, String permission) {
        try {
            if (sender.isOp()) return true;
            Optional<LuckPerms> luckPerms = this.plugin.isLuckPermsEnabled() ? Optional.of(this.plugin.getLuckPermissions()) : Optional.empty(); //If LuckPerms not enabled, this will return empty
            Optional<Permission> vaultPerms = this.plugin.isVaultEnabled() ? Optional.of(this.plugin.getVaultPermissions()) : Optional.empty(); //If vault not enabled or luckperms is used, this will return empty

            return (luckPerms.isPresent() && this.hasLuckPermission(luckPerms.get().getUserManager().getUser(sender.getName()), permission)) ||
                    (vaultPerms.isPresent() && vaultPerms.get().has(sender, permission)) || sender.hasPermission(permission);
        } catch (NullPointerException ex) {
            return false;
        }
    }

    protected boolean hasLuckPermission(User user, String permission) {
        if (user == null)
            throw new NullPointerException();
        ContextManager contextManager = this.plugin.getLuckPermissions().getContextManager();
        return user.getCachedData().getPermissionData(
                QueryOptions.contextual(contextManager.getContext(user).orElseGet(contextManager::getStaticContext))
        ).checkPermission(permission).asBoolean();
    }

    @SuppressWarnings("deprecation")
    protected Optional<Player> getPlayer(String name) {
        Player player = Bukkit.getPlayer(name);
        if (player == null)
            return Optional.empty();
        return Optional.of(player);
    }

    protected List<String> getPlayers() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }
}
