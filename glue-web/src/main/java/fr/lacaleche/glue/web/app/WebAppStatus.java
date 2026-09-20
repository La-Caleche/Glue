package fr.lacaleche.glue.web.app;

/** An immutable snapshot. Availability is independent of update activity; ready and error may be null. */
public record WebAppStatus(Release selected, Release ready, Phase phase, Selection selection, String error) {

    public enum Phase { RESTORING, IDLE, CHECKING, DOWNLOADING, VERIFYING, READY }

    /** Persistent selection policy. PINNED is set by returning to the previous release. */
    public enum Selection { AUTOMATIC, EMBEDDED, PINNED }

    public enum Source { EMBEDDED, DOWNLOADED, DEVELOPMENT }

    /** sha256 is the archive digest for a downloaded release, otherwise null. */
    public record Release(String version, Source source, String sha256) { }
}
