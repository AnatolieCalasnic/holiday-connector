package nl.innolics.holidayadapter.domain;

/**
 * One slice of the source that is pulled completely: one country, one year.
 * Delete-by-absence only ever applies inside a scope that was pulled in full.
 */
public record Scope(String countryCode, int year) {

    public String key() {
        return countryCode + ":" + year;
    }
}
