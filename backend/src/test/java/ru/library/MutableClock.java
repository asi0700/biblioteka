package ru.library;

import java.time.*;

final class MutableClock extends Clock {
    private volatile Instant instant;
    private final ZoneId zone;
    MutableClock(Instant instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
    void advance(Duration duration) { instant = instant.plus(duration); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId value) { return new MutableClock(instant, value); }
    @Override public Instant instant() { return instant; }
}
