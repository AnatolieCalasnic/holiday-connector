package nl.innolics.holidayadapter.sync;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import nl.innolics.holidayadapter.config.AdapterProperties;
import nl.innolics.holidayadapter.config.NagerProperties;
import nl.innolics.holidayadapter.domain.Holiday;
import nl.innolics.holidayadapter.domain.Scope;
import nl.innolics.holidayadapter.platform.PlatformClient;
import nl.innolics.holidayadapter.source.HolidaySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * One run: register if needed, then pull, diff and push every scope.
 */
@Component
public class SyncJob {

    private static final Logger log = LoggerFactory.getLogger(SyncJob.class);

    private final HolidaySource source;
    private final ChangeTracker tracker;
    private final PlatformClient platform;
    private final AdapterProperties adapter;
    private final NagerProperties nager;
    private final Clock clock;

    private URI dataApi;

    @Autowired
    public SyncJob(HolidaySource source, ChangeTracker tracker, PlatformClient platform,
                   AdapterProperties adapter, NagerProperties nager) {
        this(source, tracker, platform, adapter, nager, Clock.systemUTC());
    }

    SyncJob(HolidaySource source, ChangeTracker tracker, PlatformClient platform,
            AdapterProperties adapter, NagerProperties nager, Clock clock) {
        this.source = source;
        this.tracker = tracker;
        this.platform = platform;
        this.adapter = adapter;
        this.nager = nager;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (adapter.runOnStartup()) {
            run();
        }
    }

    @Scheduled(cron = "${adapter.cron}")
    public synchronized void run() {
        if (dataApi == null) {
            Optional<URI> registered = platform.register();
            if (registered.isEmpty()) {
                log.warn("No dataApi; nothing pushed this run, registration retried next run");
                return;
            }
            dataApi = registered.get();
        }

        Instant observedAt = clock.instant();
        int sent = 0, removed = 0, unchanged = 0, failed = 0;

        for (Scope scope : scopes()) {
            List<Holiday> pulled;
            try {
                pulled = source.fetch(scope, observedAt);
            } catch (HolidaySource.SourceUnavailableException e) {
                // No removals from a pull that did not complete.
                log.warn("Skipping {}: {}", scope.key(), e.getMessage());
                failed++;
                continue;
            }

            ChangeTracker.Changes changes = tracker.diff(scope, pulled, observedAt);
            unchanged += pulled.size() - changes.upserts().size();

            List<Holiday> toSend = new ArrayList<>(changes.upserts());
            toSend.addAll(changes.removals());
            for (Holiday h : toSend) {
                try {
                    platform.push(dataApi, h);
                    tracker.markSent(scope, h);
                    if (h.status() == Holiday.Status.removed) removed++; else sent++;
                } catch (RestClientException e) {
                    // Leave it unmarked: it is retried next run. Re-register in case the address moved.
                    log.warn("Push failed for {}: {}", h.id(), e.getMessage());
                    failed++;
                    dataApi = null;
                    break;
                }
            }
        }
        log.info("Sync done: {} sent, {} removed, {} unchanged, {} failed", sent, removed, unchanged, failed);
    }

    List<Scope> scopes() {
        int thisYear = Year.now(clock).getValue();
        List<Scope> scopes = new ArrayList<>();
        for (String country : nager.countries()) {
            for (int y = thisYear; y <= thisYear + nager.yearsAhead(); y++) {
                scopes.add(new Scope(country.trim().toUpperCase(), y));
            }
        }
        return scopes;
    }
}
