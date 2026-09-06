package pl.example.billboardrefresher;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.URLImageMap;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class BillboardRefresherPlugin extends JavaPlugin {

    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<String, Integer> imageIndexes = new HashMap<>();

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("ImageFrame") == null) {
            getLogger().severe("ImageFrame was not found on this server. Disabling BillboardRefresher.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        ReloadCommand executor = new ReloadCommand(this);
        getCommand("billboardrefresher").setExecutor(executor);

        startTasks();
        getLogger().info("BillboardRefresher enabled - " + tasks.size() + " billboard task(s) scheduled.");
    }

    @Override
    public void onDisable() {
        stopTasks();
    }

    /**
     * Cancels all running tasks, reloads config.yml from disk, and re-schedules everything.
     */
    public void reload() {
        stopTasks();
        reloadConfig();
        startTasks();
    }

    private void stopTasks() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        imageIndexes.clear();
    }

    @SuppressWarnings("unchecked")
    private void startTasks() {
        List<Map<?, ?>> billboards = getConfig().getMapList("billboards");
        boolean logRefresh = getConfig().getBoolean("settings.log-refreshes", false);

        if (billboards.isEmpty()) {
            getLogger().warning("No billboards configured in config.yml - nothing to do.");
            return;
        }

        for (Map<?, ?> raw : billboards) {
            Object nameObj = raw.get("name");
            if (nameObj == null) {
                getLogger().warning("Skipping a billboard entry with no 'name' set.");
                continue;
            }
            String name = String.valueOf(nameObj);

            String ownerRaw = raw.get("owner") == null ? "console" : String.valueOf(raw.get("owner"));
            UUID owner = resolveOwner(ownerRaw);
            if (owner == null) {
                getLogger().warning("Billboard '" + name + "': could not resolve owner '" + ownerRaw + "' (unknown player). Skipping.");
                continue;
            }

            long intervalSeconds;
            try {
                intervalSeconds = raw.get("interval-seconds") == null ? 30L : Long.parseLong(String.valueOf(raw.get("interval-seconds")));
            } catch (NumberFormatException e) {
                getLogger().warning("Billboard '" + name + "': invalid interval-seconds, defaulting to 30.");
                intervalSeconds = 30L;
            }
            if (intervalSeconds < 1) {
                intervalSeconds = 1;
            }

            boolean random = raw.get("random") != null && Boolean.parseBoolean(String.valueOf(raw.get("random")));

            Object imagesObj = raw.get("images");
            List<String> images = new ArrayList<>();
            if (imagesObj instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null) {
                        images.add(String.valueOf(o));
                    }
                }
            }
            if (images.isEmpty()) {
                getLogger().warning("Billboard '" + name + "' has no images configured, skipping.");
                continue;
            }

            long periodTicks = intervalSeconds * 20L;
            String key = owner + ":" + name;
            imageIndexes.put(key, -1);

            List<String> finalImages = images;
            long finalIntervalSeconds = intervalSeconds;
            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    this,
                    () -> refreshBillboard(name, owner, finalImages, random, key, logRefresh),
                    periodTicks,
                    periodTicks
            );
            tasks.add(task);

            getLogger().info("Scheduled billboard '" + name + "' (owner: " + ownerRaw + ") every " + finalIntervalSeconds + "s with " + finalImages.size() + " image(s).");
        }
    }

    private UUID resolveOwner(String ownerRaw) {
        if (ownerRaw.equalsIgnoreCase("console")) {
            return ImageMap.CONSOLE_CREATOR;
        }
        try {
            return UUID.fromString(ownerRaw);
        } catch (IllegalArgumentException ignored) {
            // not a UUID, fall through to name lookup
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(ownerRaw);
        if (player.hasPlayedBefore() || player.isOnline()) {
            return player.getUniqueId();
        }
        return null;
    }

    private void refreshBillboard(String name, UUID owner, List<String> images, boolean random, String key, boolean logRefresh) {
        try {
            ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(owner, name);
            if (imageMap == null) {
                getLogger().warning("Billboard '" + name + "' was not found in ImageFrame (owner: " + owner + "). Check the name/owner in config.yml.");
                return;
            }
            if (!(imageMap instanceof URLImageMap urlImageMap)) {
                getLogger().warning("Billboard '" + name + "' is not a URL-based image map, it cannot be swapped automatically.");
                return;
            }

            int index;
            if (random) {
                index = ThreadLocalRandom.current().nextInt(images.size());
            } else {
                int current = imageIndexes.getOrDefault(key, -1);
                index = (current + 1) % images.size();
                imageIndexes.put(key, index);
            }

            String newUrl = images.get(index);
            String oldUrl = urlImageMap.getUrl();

            if (newUrl.equals(oldUrl) && images.size() > 1) {
                // Extremely unlikely with sequential cycling, but guards against
                // pointless refreshes if two consecutive entries are identical.
            }

            urlImageMap.setUrl(newUrl);
            imageMap.update();

            if (logRefresh) {
                getLogger().info("Refreshed billboard '" + name + "' -> " + newUrl);
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Failed to refresh billboard '" + name + "'", t);
        }
    }
}
