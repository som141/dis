package discordgateway.application;

import discordgateway.infrastructure.audio.PlaybackGateway;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayAutocompleteService {

    private static final Duration AUTOCOMPLETE_TTL = Duration.ofSeconds(30);

    private final PlaybackGateway playbackGateway;
    private final ConcurrentHashMap<String, CachedChoices> cache = new ConcurrentHashMap<>();

    private record CachedChoices(long createdAtMillis, List<Command.Choice> choices) {}

    public PlayAutocompleteService(PlaybackGateway playbackGateway) {
        this.playbackGateway = playbackGateway;
    }

    public CompletableFuture<List<Command.Choice>> complete(String query) {
        String typed = Objects.toString(query, "").trim();
        if (typed.length() < 3) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String cacheKey = typed.toLowerCase();
        CachedChoices cached = cache.get(cacheKey);
        long now = System.currentTimeMillis();

        if (cached != null && (now - cached.createdAtMillis()) <= AUTOCOMPLETE_TTL.toMillis()) {
            return CompletableFuture.completedFuture(cached.choices());
        }

        return playbackGateway.searchChoices(typed, 15)
                .orTimeout(2500, TimeUnit.MILLISECONDS)
                .handle((choices, err) -> {
                    if (err != null || choices == null) {
                        return Collections.<Command.Choice>emptyList();
                    }

                    List<Command.Choice> sanitized = new ArrayList<>();
                    for (Command.Choice choice : choices) {
                        String name = trimToMax(choice.getName(), 100);
                        String value = trimToMax(choice.getAsString(), 100);
                        sanitized.add(new Command.Choice(name, value));
                        if (sanitized.size() >= 25) {
                            break;
                        }
                    }

                    cache.put(cacheKey, new CachedChoices(System.currentTimeMillis(), sanitized));
                    return sanitized;
                });
    }

    private String trimToMax(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}