package io.github.thebusybiscuit.slimefun4.core.services.github;

import io.github.thebusybiscuit.cscorelib2.players.MinecraftAccount;
import io.github.thebusybiscuit.cscorelib2.players.MinecraftAccount.TooManyRequestsException;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunPlugin;
import me.mrCookieSlime.Slimefun.api.Slimefun;
import org.bukkit.Bukkit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * This {@link GitHubTask} represents a {@link Runnable} that is run every X minutes.
 * It retrieves every {@link Contributor} of this project from GitHub.
 *
 * @author TheBusyBiscuit
 *
 * @see GitHubService
 * @see Contributor
 *
 */
class GitHubTask implements Runnable {

    private static final int MAX_REQUESTS_PER_MINUTE = 16;

    private final GitHubService gitHubService;

    GitHubTask(@Nonnull GitHubService github) {
        gitHubService = github;
    }

    @Override
    public void run() {
        gitHubService.getConnectors().forEach(GitHubConnector::pullFile);

        grabTextures();
    }

    private void grabTextures() {
        // Store all queried usernames to prevent 429 responses for pinging the
        // same URL twice in one run.
        Map<String, String> skins = new HashMap<>();
        int requests = 0;

        for (Contributor contributor : gitHubService.getContributors().values()) {
            int newRequests = requestTexture(contributor, skins);
            requests += newRequests;

            if (newRequests < 0 || requests >= MAX_REQUESTS_PER_MINUTE) {
                break;
            }
        }

        if (requests >= MAX_REQUESTS_PER_MINUTE && SlimefunPlugin.instance() != null && SlimefunPlugin.instance().isEnabled()) {
            // Slow down API requests and wait a minute after more than x requests were made
            Bukkit.getScheduler().runTaskLaterAsynchronously(SlimefunPlugin.instance(), this::grabTextures, 2 * 60 * 20L);
        }

        for (GitHubConnector connector : gitHubService.getConnectors()) {
            if (connector instanceof ContributionsConnector && !((ContributionsConnector) connector).hasFinished()) {
                return;
            }
        }

        // We only wanna save this if all Connectors finished already
        // This will run multiple times but thats okay, this way we get as much data as possible stored
        gitHubService.saveCache();
    }

    private int requestTexture(@Nonnull Contributor contributor, @Nonnull Map<String, String> skins) {
        if (!contributor.hasTexture()) {
            try {
                if (skins.containsKey(contributor.getMinecraftName())) {
                    contributor.setTexture(skins.get(contributor.getMinecraftName()));
                } else {
                    contributor.setTexture(pullTexture(contributor, skins));
                    return contributor.getUniqueId().isPresent() ? 1 : 2;
                }
            } catch (IllegalArgumentException x) {
                // There cannot be a texture found because it is not a valid MC username
                contributor.setTexture(null);
            } catch (IOException x) {
                // Too many requests
                Slimefun.getLogger().log(Level.WARNING, "尝试连接至 mojang.com 得到响应: {0}: {1}", new Object[]{x.getClass().getSimpleName(), x.getMessage()});
                Slimefun.getLogger().log(Level.WARNING, "通常是因为 mojang.com 服务不可用或者限制了我们的连接次数, 这不是报错, Slimefun 一切正常!");

                // Retry after 5 minutes if it was rate-limiting
                if (x.getMessage().contains("429")) {
                    Bukkit.getScheduler().runTaskLaterAsynchronously(SlimefunPlugin.instance(), this::grabTextures, 5 * 60 * 20L);
                }

                return -1;
            } catch (TooManyRequestsException x) {
                Slimefun.getLogger().log(Level.WARNING, "收到来自 mojang.com 的限制连接代码, 将在 4 分钟后重试");
                Bukkit.getScheduler().runTaskLaterAsynchronously(SlimefunPlugin.instance(), this::grabTextures, 4 * 60 * 20L);

                return -1;
            }
        }

        return 0;
    }

    @Nullable
    private String pullTexture(@Nonnull Contributor contributor, @Nonnull Map<String, String> skins) throws TooManyRequestsException, IOException {
        Optional<UUID> uuid = contributor.getUniqueId();

        if (!uuid.isPresent()) {
            uuid = MinecraftAccount.getUUID(contributor.getMinecraftName());

            uuid.ifPresent(contributor::setUniqueId);
        }

        if (uuid.isPresent()) {
            Optional<String> skin = MinecraftAccount.getSkin(uuid.get());
            skins.put(contributor.getMinecraftName(), skin.orElse(""));
            return skin.orElse(null);
        } else {
            return null;
        }
    }

}