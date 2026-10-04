package nl.innolics.holidayadapter.source;

import java.time.Instant;
import java.util.List;

import nl.innolics.holidayadapter.domain.Holiday;
import nl.innolics.holidayadapter.domain.Scope;

/**
 * The only thing in this adapter that knows where holidays come from.
 */
public interface HolidaySource {

    /**
     * Returns every holiday in the scope, or throws. Never returns a partial list:
     * the caller treats whatever comes back as complete and infers removals from it.
     */
    List<Holiday> fetch(Scope scope, Instant observedAt) throws SourceUnavailableException;

    class SourceUnavailableException extends Exception {
        public SourceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
