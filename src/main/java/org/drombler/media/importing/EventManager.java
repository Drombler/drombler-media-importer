package org.drombler.media.importing;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.drombler.event.core.Event;
import org.drombler.event.core.EventDuration;
import org.drombler.event.core.AllDayEventDuration;
import org.drombler.event.core.format.EventDirNameFormatter;
import org.drombler.identity.management.DromblerIdentityProviderManager;
import org.drombler.media.core.MediaSource;
import org.drombler.media.core.MediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.softsmithy.lib.text.FormatException;

/**
 *
 * @author Florian
 */
public class EventManager {

    private static final Logger LOG = LoggerFactory.getLogger(EventManager.class);

    private final Map<LocalDate, SortedSet<Event>> events = new HashMap<>();
    private final Comparator<EventDuration> eventDurationComparator = new ImportEventDurationComparator();
    private final Comparator<Event> eventComparator = Comparator.comparing(Event::getDuration, eventDurationComparator)
            .thenComparing(Event::getName);
    private final DromblerIdentityProviderManager dromblerIdentityProviderManager;

    public EventManager(DromblerIdentityProviderManager dromblerIdentityProviderManager) throws IOException {
        this.dromblerIdentityProviderManager = dromblerIdentityProviderManager;
    }

    public void updateEventMap(MediaStorage mediaStorage) {
        try {
            List<MediaSource> mediaSources = mediaStorage.readMediaSources(dromblerIdentityProviderManager);
            mediaSources.stream()
                    .map(MediaSource::getEvent)
                    .filter(Objects::nonNull)
                    .filter(event -> event.getDuration() instanceof AllDayEventDuration)
                    .forEach(this::updateEventMap);
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private void updateEventMap(Event event) {
        final AllDayEventDuration duration = (AllDayEventDuration) event.getDuration();
        for (LocalDate date = duration.getStartDateInclusive(); date.isBefore(duration.getEndDateInclusive()) || date.equals(duration.getEndDateInclusive()); date = date.plusDays(1)) {
            if (!events.containsKey(date)) {
                events.put(date, new TreeSet<>(eventComparator));
            }
            if (!events.get(date).contains(event)) {
                events.get(date).add(event);
                String eventDirName = getFormattedEventDirName(event);
                LOG.debug(event.getName() + " - " + eventDirName);
            }
        }
    }

    private String getFormattedEventDirName(Event event) {
        try {
            EventDirNameFormatter formatter = new EventDirNameFormatter();
            return formatter.format(event);
        } catch (FormatException ex) {
            LOG.error(ex.getMessage(), ex);
            return null;
        }
    }

    public Event getFirstEvent(LocalDate date) {
        updateEventMap(createEvent(date));
        return events.get(date).first();
    }

    private Event createEvent(LocalDate date) {
        return new Event(null, "", new AllDayEventDuration(date, date));
    }

    public SortedSet<Event> getAllEvents() {
        return events.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(() -> new TreeSet<>(eventComparator)));
    }
}
