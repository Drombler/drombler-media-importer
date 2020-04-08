package org.drombler.media.importing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.drombler.event.core.Event;
import org.drombler.event.core.EventDuration;
import org.drombler.event.core.FullTimeEventDuration;
import org.drombler.identity.core.DromblerIdentityProviderManager;
import org.drombler.media.core.MediaSource;
import org.drombler.media.core.MediaStorage;
import org.drombler.media.core.photo.PhotoStorage;
import org.drombler.media.core.video.VideoStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        Properties mediaStorageProperties = loadMediaStorageProperties("photo-storages.properties");
        mediaStorageProperties.stringPropertyNames().stream()
                .map(name -> new PhotoStorage(name, Paths.get(mediaStorageProperties.getProperty(name))))
                .forEach(photoStorage -> updateEventMap(photoStorage));
        
        Properties videoStorageProperties = loadMediaStorageProperties("video-storages.properties");
        videoStorageProperties.stringPropertyNames().stream()
                .map(name -> new VideoStorage(name, Paths.get(videoStorageProperties.getProperty(name))))
                .forEach(videoStorage -> updateEventMap(videoStorage));
    }

    private Properties loadMediaStorageProperties(final String mediaStoragesPropertiesFile) throws IOException {
        Properties mediaStorageProperties = new Properties();
        try (InputStream is = EventManager.class.getResourceAsStream(mediaStoragesPropertiesFile)) {
            mediaStorageProperties.load(is);
        }
        return mediaStorageProperties;
    }

    public <M extends MediaSource<M>> void updateEventMap(MediaStorage<M> mediaStorage) {
        try  {
            List<M> mediaSources = mediaStorage.readMediaSources(dromblerIdentityProviderManager);
            mediaSources.stream()
                    .map(MediaSource::getEvent)
                    .filter(Objects::nonNull)
                    .forEach(this::updateEventMap);
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private void updateEventMap(Event event) {
        final FullTimeEventDuration duration = (FullTimeEventDuration) event.getDuration();
        for (LocalDate date = duration.getStartDateInclusive(); date.isBefore(duration.getEndDateInclusive()) || date.equals(duration.getEndDateInclusive()); date = date.plusDays(1)) {
            if (!events.containsKey(date)) {
                events.put(date, new TreeSet<>(eventComparator));
            }
            if (!events.get(date).contains(event)) {
                events.get(date).add(event);
                LOG.debug(event.getName() + " - " + event.getDirName());
            }
        }
    }

    public Event getFirstEvent(LocalDate date) {
        updateEventMap(createEvent(date));
        return events.get(date).first();
    }

    private Event createEvent(LocalDate date) {
        return new Event(null, "", new FullTimeEventDuration(date, date));
    }

    public SortedSet<Event> getAllEvents() {
        return events.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(() -> new TreeSet<>(eventComparator)));
    }
}
