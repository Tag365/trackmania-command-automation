package stream.support.command.api.repositories;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import stream.support.command.api.models.Competition;
import stream.support.command.api.models.Cotd;
import stream.support.command.api.models.PlayerResult;
import stream.support.command.api.models.RecentCotdCompetitions;
import stream.support.command.api.network.HTTPRequests;
import stream.support.command.api.util.Cache;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CotdResultRepository {
    private final HTTPRequests httpRequests;
    private final Cache cache;

    public CotdResultRepository(HTTPRequests requests, Cache cache) {
        this.httpRequests = requests;
        this.cache = cache;
    }


    public Optional<Integer> getLastPlayerPosition(String playerId) {
        LocalDateTime timeNow = LocalDateTime.now();
        final LocalDateTime cotdTime = getTodayCotdTime(timeNow);
        log.info("CotdTime: {}", cotdTime);
        log.info("CachedTime: {}", cache.getLastUpdated());

        LocalDateTime fromCache = cache.getLastUpdated();
        Optional<Integer> relevantPosition = Optional.empty();
        if ((timeNow.getHour() >= 19 && fromCache.isBefore(cotdTime)) || fromCache.isBefore(cotdTime.minusDays(1))) {
            log.info("Get new cotd results");
            log.info(timeNow.toString());
            getLastCotdResults(timeNow, timeNow.getHour() < 19);
            final Optional<PlayerResult> optional = findPlayerByPlayerId(cache.getLastCotdDiv1Results(), playerId);
            if (optional.isPresent()) {
                relevantPosition = Optional.of(optional.get().getPosition());
            }
        } else {
            final Optional<PlayerResult> positionOptional = findPlayerByPlayerId(cache.getLastCotdDiv1Results(), playerId);
            if (positionOptional.isPresent()) {
                log.info("Use cached cotd results");
                PlayerResult position = positionOptional.get();
                relevantPosition = Optional.of(position.getPosition());
            }
        }
        return relevantPosition;
    }

    private Optional<PlayerResult> findPlayerByPlayerId(List<PlayerResult> result, String playerId) {
        return result.stream().filter(player -> player.getPlayer().getId().trim().equalsIgnoreCase(playerId.trim())).findFirst();
    }

    private void getLastCotdResults(LocalDateTime time, boolean yesterdayCotd) {
        RecentCotdCompetitions cotdRecentHistory = httpRequests.getRecentCotdCompetitions();
        if (yesterdayCotd) time = time.minusDays(1);
        String dayString = String.format("%d-%02d-%02d", time.getYear(), time.getMonthValue(), time.getDayOfMonth());

        Optional<Competition> optional = cotdRecentHistory.getCompetitions().stream().filter(c -> c.getName().contains("#1") && c.getName().contains(dayString)).findFirst();
        if (optional.isPresent()) {
            Cotd cotd = httpRequests.getCotdByCompId(optional.get().getId());
            if (!cotd.isInvalid()) {
                List<PlayerResult> cotdResult = httpRequests.getCotdResultsForMatch(optional.get().getId(), cotd.getRounds().get(0).getMatches().get(0).getId());
                if (!cotdResult.isEmpty()) {
                    cache.setLastCotdDiv1Results(cotdResult);
                    cache.setLastUpdated(LocalDateTime.now());
                }
            }
        }
    }

    private LocalDateTime getTodayCotdTime(LocalDateTime now) {
        return LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 18, 45);
    }

    @PostConstruct
    private void postConstruct() {
        log.info("Get cotd results after startup");
        LocalDateTime timeNow = LocalDateTime.now();
        getLastCotdResults(timeNow, timeNow.getHour() < 19);
    }

}