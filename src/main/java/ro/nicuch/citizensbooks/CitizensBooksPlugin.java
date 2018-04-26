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

import java.io.File;

import org.bukkit.ChatColor;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.permission.Permission;
import ro.nicuch.citizensbooks.bstats.Metrics;

public class CitizensBooksPlugin extends JavaPlugin {
    private Permission PERMISSION;
    private boolean placeholder;
    private CitizensBooksAPI api;

    @Override
    public void onEnable() {
        this.reloadSettings();
        //bStats Metrics, by default enabled
        if (this.getConfig().getBoolean("metrics", true)) {
            this.getLogger().info("bStats Metrics starting...");
            new Metrics(this);
        }
        PluginManager manager = this.getServer().getPluginManager();
        if (!manager.isPluginEnabled("Vault")) {
            this.getLogger().warning("Vault not enabled, plugin disabled!");
            this.setEnabled(false);
            return;
        }
        this.api = new CitizensBooksAPI(this);
        if (!manager.isPluginEnabled("PlaceholderAPI")) {
            this.getLogger().info("PlaceholderAPI not found!");
        } else {
            this.getLogger().info("PlaceholderAPI found, try hooking!");
            this.placeholder = true;
        }
        this.PERMISSION = this.getServer().getServicesManager().getRegistration(Permission.class).getProvider();
        TabExecutor te;
        manager.registerEvents(new PlayerActions(this), this);
        if (!manager.isPluginEnabled("Citizens")) {
            this.getLogger().info("Citizens not found!");
            te = new PlayerCommands(this);
        } else {
            this.getLogger().info("Citizens found, try hooking!");
            manager.registerEvents(new CitizensActions(this), this);
            te = new CitizensCommands(this);
        }
        this.getCommand("npcbook").setExecutor(te);
        this.getCommand("npcbook").setTabCompleter(te);
        //Update checker, by default enabled
        if (this.getConfig().getBoolean("update_check", true))
            manager.registerEvent(new UpdateChecker(this), this);
    }

    public CitizensBooksAPI getAPI() {
        return this.api;
    }

    @Override
    public void onDisable() {
        this.saveSettings();
    }

    public void reloadSettings() {
        File config = new File(this.getDataFolder() + File.separator + "config.yml");
        if (!config.exists()) {
            this.saveResource("config.yml", false);
            this.getLogger().info("A new config.yml was created!");
        }
        if (this.getConfig().isInt("version") && this.getConfig().getInt("version") != 6) {
            File copy = new File(
                    this.getDataFolder() + File.separator + "config_" + System.currentTimeMillis() + ".yml");
            config.renameTo(copy);
            this.getLogger().info("A new config.yml was generated!");
            this.saveResource("config.yml", true);
        }
        this.reloadConfig();
    }

    public void saveSettings() {
        this.saveConfig();
    }

    public Permission getPermission() {
        return this.PERMISSION;
    }

    public boolean isPlaceHolderEnabled() {
        return this.placeholder;
    }

    public String getMessage(String path, String def) {
        return ChatColor.translateAlternateColorCodes('&',
                this.getConfig().getString("lang.header", ConfigDefaults.header))
                + ChatColor.translateAlternateColorCodes('&', this.getConfig().getString(path, def));
    }

    public String getMessageNoHeader(String path, String def) {
        return ChatColor.translateAlternateColorCodes('&', this.getConfig().getString(path, def));
    }
}