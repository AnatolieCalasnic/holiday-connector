package nl.innolics.holidayprocessor.messaging;

/**
 * The message can never be stored, however often it is retried. Goes straight to the DLQ.
 */
public class InvalidMessageException extends Exception {
    public InvalidMessageException(String reason) {
        super(reason);
    }
}
