package org.drombler.media.importing;

import lombok.extern.slf4j.Slf4j;
import org.drombler.event.core.AllDayEventDuration;
import org.drombler.event.core.Event;
import org.drombler.event.core.EventDuration;
import org.drombler.event.core.format.EventDirNameFormatter;
import org.softsmithy.lib.text.FormatException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Florian
 */
@Slf4j
public class EventManager {

    private final Map<LocalDate, SortedSet<Event>> events = new HashMap<>();
    private final Comparator<EventDuration> eventDurationComparator = new ImportEventDurationComparator();
    private final Comparator<Event> eventComparator = Comparator.comparing(Event::getDuration, eventDurationComparator)
            .thenComparing(Event::getName);


    public void updateEventMap(List<Event> events) {
        events.stream()
                .filter(event -> event.getDuration() instanceof AllDayEventDuration)
                .forEach(this::updateEventMap);
    }

    public void updateEventMap(Event event) {
        final AllDayEventDuration duration = (AllDayEventDuration) event.getDuration();
        duration.iterator().forEachRemaining(date -> {
            if (!events.containsKey(date)) {
                events.put(date, new TreeSet<>(eventComparator));
            }
            if (!events.get(date).contains(event)) {
                events.get(date).add(event);
                String eventDirName = getFormattedEventDirName(event);
                log.debug(date + ": " + event.getName() + " - " + eventDirName);
            }
        });
    }

    private String getFormattedEventDirName(Event event) {
        try {
            EventDirNameFormatter formatter = new EventDirNameFormatter();
            return formatter.format(event);
        } catch (FormatException ex) {
            log.error(ex.getMessage(), ex);
            return null;
        }
    }

    public Event getAndUpdateFirstEvent(LocalDate date) {
        if (!hasEvent(date)) {
            updateEventMap(createUnnamedEvent(date));
        }
        return getFirstEvent(date);
    }

    public boolean hasEvent(LocalDate date) {
        return events.containsKey(date) && !events.get(date).isEmpty();
    }

    public Event getFirstEvent(LocalDate date) {
        return events.get(date).first();
    }

    public SortedSet<Event> getEvents(LocalDate date) {
        return events.get(date);
    }

    private Event createUnnamedEvent(LocalDate date) {
        return Event.builder()
                .name("")
                .duration(new AllDayEventDuration(date, date))
                .build();
    }

    public SortedSet<Event> getAllEvents() {
        return events.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(() -> new TreeSet<>(eventComparator)));
    }
}
