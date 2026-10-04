package nl.innolics.holidayadapter.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import nl.innolics.holidayadapter.domain.Holiday;
import nl.innolics.holidayadapter.domain.Scope;
import org.springframework.stereotype.Component;

/**
 * Remembers what was last sent per scope, so a run only sends what changed.
 * In memory: after a restart everything is sent again, which the processor absorbs (ADR-001).
 */
@Component
public class ChangeTracker {

    /** scope key -> (holiday id -> fingerprint last sent) */
    private final Map<String, Map<String, String>> sent = new ConcurrentHashMap<>();

    public record Changes(List<Holiday> upserts, List<Holiday> removals) {
        public boolean isEmpty() {
            return upserts.isEmpty() && removals.isEmpty();
        }
    }

    /**
     * Compares a <b>complete</b> pull of one scope with what was last sent for it.
     * Never call this with a partial pull: anything missing is reported as removed.
     */
    public Changes diff(Scope scope, List<Holiday> completePull, Instant observedAt) {
        Map<String, String> last = sent.getOrDefault(scope.key(), Map.of());

        Map<String, Holiday> current = new LinkedHashMap<>();
        for (Holiday h : completePull) {
            current.putIfAbsent(h.id(), h);
        }

        List<Holiday> upserts = new ArrayList<>();
        for (Holiday h : current.values()) {
            if (!h.fingerprint().equals(last.get(h.id()))) {
                upserts.add(h);
            }
        }

        List<Holiday> removals = new ArrayList<>();
        for (String id : last.keySet()) {
            if (!current.containsKey(id)) {
                removals.add(Holiday.removed(id, scope, observedAt));
            }
        }
        return new Changes(upserts, removals);
    }

    /** Record a successful push, so the next run does not send it again. */
    public void markSent(Scope scope, Holiday holiday) {
        Map<String, String> ids = sent.computeIfAbsent(scope.key(), k -> new HashMap<>());
        if (holiday.status() == Holiday.Status.removed) {
            ids.remove(holiday.id());
        } else {
            ids.put(holiday.id(), holiday.fingerprint());
        }
    }
}
