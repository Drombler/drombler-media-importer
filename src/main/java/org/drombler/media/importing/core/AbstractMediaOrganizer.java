/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.drombler.media.importing.core;

import lombok.extern.slf4j.Slf4j;
import org.drombler.event.core.AllDayEventDuration;
import org.drombler.event.core.Event;
import org.drombler.identity.core.DromblerId;
import org.drombler.identity.core.DromblerUserId;
import org.drombler.identity.core.PrivateDromblerIdProvider;
import org.drombler.identity.management.DromblerIdentityProviderManager;
import org.drombler.media.core.*;
import org.drombler.media.core.protocol.json.MediaCategoryType;
import org.drombler.media.importing.EventManager;
import org.drombler.media.management.MediaStorageManager;
import org.softsmithy.lib.text.FormatException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.LogManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Florian
 */
@Slf4j
public abstract class AbstractMediaOrganizer {

    private final Map<MediaStorageContentType, EventManager> eventManagers = new EnumMap<>(MediaStorageContentType.class);
    private final Path mediaRootDir;
    private final Pattern rawDatePattern;
    private final boolean directories;
    private final MediaStorage defaultPhotoImportStorage;
    private final MediaStorage defaultVideoImportStorage;
    private final List<MediaStorage> photoImportStorages = new ArrayList<>();
    private final List<MediaStorage> videoImportStorages = new ArrayList<>();
    private final DromblerUserId defaulCopyrightOwner;
    private final MediaStorageContentType defaultContentType;
    private final DromblerIdentityProviderManager dromblerIdentityProviderManager = new DromblerIdentityProviderManager();
    private final MediaCategoryManager mediaCategoryManager = new MediaCategoryManager();
    private final MediaStorageManager mediaStorageManager = new MediaStorageManager(mediaCategoryManager);
    private final FileMigrationOperation fileMigrationOperation;

    protected final static void initLogger() throws IOException {
        // TODO: extend and use JarFiles (SoftSmithy)
        String userDir = System.getProperty("user.dir");
        Path loggingPropertiesPath = Paths.get(userDir, "src", "main", "conf", "logging.properties");
        System.setProperty("java.util.logging.config.file", loggingPropertiesPath.toString());
        LogManager.getLogManager().readConfiguration(); // seems to be necessary!?
    }

    protected AbstractMediaOrganizer(Path mediaRootDir, Pattern rawDatePattern, boolean directories) throws IOException {
        this.rawDatePattern = rawDatePattern;
        this.directories = directories;
        try (InputStream is = AbstractMediaOrganizer.class.getResourceAsStream("media-storages.json")) {
            mediaStorageManager.loadJsonConfig(is);
        }
        Arrays.stream(MediaStorageContentType.values()).forEach(type -> eventManagers.put(type, new EventManager()));

        Properties mediaImportingProperties = new Properties();
        try (InputStream is = AbstractMediaOrganizer.class.getResourceAsStream("media-importing.properties")) {
            mediaImportingProperties.load(is);
        }
        this.mediaRootDir = mediaRootDir;
        Path photoDirPath = mediaRootDir.resolve(mediaImportingProperties.getProperty("photoDir"));
        Path videoDirPath = mediaRootDir.resolve(mediaImportingProperties.getProperty("videoDir"));
        final MediaCategory photoMediaCategory = mediaCategoryManager.getMediaCategory(MediaCategoryType.PHOTO);
        if (photoMediaCategory == null) {
            throw new IllegalArgumentException("photo media category not configured correctly!");
        }
        final MediaCategory videoMediaCategory = mediaCategoryManager.getMediaCategory(MediaCategoryType.VIDEO);
        if (videoMediaCategory == null) {
            throw new IllegalArgumentException("video media category not configured correctly!");
        }
        this.defaulCopyrightOwner = new DromblerUserId(mediaImportingProperties.getProperty("defaultCopyrightOwner"));
        this.defaultContentType = MediaStorageContentType.valueOf(mediaImportingProperties.getProperty("defaultContentType"));
        Set<DromblerId> defaulCopyrightOwners = new HashSet<>();
        defaulCopyrightOwners.add(defaulCopyrightOwner);

        this.defaultPhotoImportStorage = new MediaStorage(null, "importing owner event photos", photoDirPath, EnumSet.of(MediaStorageContentType.SHARED_EVENTS), false, Arrays.asList(photoMediaCategory), defaulCopyrightOwners);
        photoImportStorages.add(defaultPhotoImportStorage);
        photoImportStorages.add(new MediaStorage(null, "importing private event photos", photoDirPath, EnumSet.of(MediaStorageContentType.PRIVATE_EVENTS), false, Arrays.asList(photoMediaCategory), defaulCopyrightOwners));
        photoImportStorages.add(new MediaStorage(null, "importing business event photos", photoDirPath, EnumSet.of(MediaStorageContentType.BUSINESS), false, Arrays.asList(photoMediaCategory), defaulCopyrightOwners));
        photoImportStorages.add(new MediaStorage(null, "importing other event photos", photoDirPath, EnumSet.of(MediaStorageContentType.OTHER_EVENTS), false, Arrays.asList(photoMediaCategory), defaulCopyrightOwners));
        photoImportStorages.add(new MediaStorage(null, "importing photos of things", photoDirPath, EnumSet.of(MediaStorageContentType.THINGS), false, Arrays.asList(photoMediaCategory), defaulCopyrightOwners));

        this.defaultVideoImportStorage = new MediaStorage(null, "importing owner event video", videoDirPath, EnumSet.of(MediaStorageContentType.SHARED_EVENTS), false, Arrays.asList(videoMediaCategory), defaulCopyrightOwners);
        videoImportStorages.add(defaultVideoImportStorage);

        this.fileMigrationOperation = FileMigrationOperation.valueOf(mediaImportingProperties.getProperty("fileMigrationOperation"));
        dromblerIdentityProviderManager.registerDromblerIdentityProvider(PrivateDromblerIdProvider.getInstance());

        mediaStorageManager.getMediaStorages().forEach(this::updateEventMap);

        for (MediaStorage photoImportStorage : photoImportStorages) {
            importNamedEventCandidates(photoImportStorage);
        }
        for (MediaStorage videoImportStorage : videoImportStorages) {
            importNamedEventCandidates(videoImportStorage);
        }

        reorganizeImportStorage(defaultPhotoImportStorage);
        reorganizeImportStorage(defaultVideoImportStorage);

        for (MediaStorage photoImportStorage : photoImportStorages) {
            importUnamedEventCandidates(photoImportStorage);
        }
        for (MediaStorage videoImportStorage : videoImportStorages) {
            importUnamedEventCandidates(videoImportStorage);
        }
    }


    private void updateEventMap(MediaStorage mediaStorage) {
        mediaStorage.getSupportedContentTypes().stream()
                .map(eventManagers::get)
                .forEach(eventManager -> updateEventMap(eventManager, mediaStorage));
    }

    private void updateEventMap(EventManager eventManager, MediaStorage mediaStorage) {
        try {
            eventManager.updateEventMap(mediaStorage.parseEvents());
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    private void importNamedEventCandidates(MediaStorage importStorage) throws IOException {
        List<Event> events = importStorage.parseEvents();
        List<Event> namedEvents = events.stream()
                .filter(event -> !event.isUnnamed())
                .collect(Collectors.toList());
        importStorage.getSupportedContentTypes().stream()
                .map(eventManagers::get)
                .forEach(eventManager -> eventManager.updateEventMap(namedEvents));
    }

    private void importUnamedEventCandidates(MediaStorage importStorage) throws IOException {
        List<Event> events = importStorage.parseEvents();
        List<Event> namedEvents = events.stream()
                .filter(event -> !event.isUnnamed())
                .collect(Collectors.toList());
        importStorage.getSupportedContentTypes().stream()
                .map(eventManagers::get)
                .forEach(eventManager -> eventManager.updateEventMap(namedEvents));
    }

    private void reorganizeImportStorage(MediaStorage importStorage) throws IOException {
        List<Event> events = importStorage.parseEvents();
        List<Event> unnamedEvents = events.stream()
                .filter(Event::isUnnamed)
                .collect(Collectors.toList());
        unnamedEvents.stream()
                .filter(event -> event.getDuration() instanceof AllDayEventDuration)
                .forEach(unnamedEvent -> mergeOrUpdateUnnamedEvent(unnamedEvent, importStorage));
    }

    private void mergeOrUpdateUnnamedEvent(Event unnamedEvent, MediaStorage importStorage) {
        importStorage.getSupportedContentTypes()
                .forEach(contentType -> mergeOrUpdateUnnamedEvent(unnamedEvent, importStorage, contentType));
    }

    private void mergeOrUpdateUnnamedEvent(Event unnamedEvent, MediaStorage importStorage, MediaStorageContentType contentType) {
        AllDayEventDuration duration = (AllDayEventDuration) unnamedEvent.getDuration();
        Optional<Event> namedEvent = findNamedEvent(duration, contentType);
        if (namedEvent.isPresent()) {
            mergeEventDirs(importStorage, unnamedEvent, namedEvent.get());
        } else {
            eventManagers.get(contentType).updateEventMap(unnamedEvent);
        }
    }

    private void mergeEventDirs(MediaStorage importStorage, Event unnamedEvent, Event namedEvent) {
        try {
            importStorage.mergeEventDirs(unnamedEvent, namedEvent);
        } catch (FormatException | IOException | RuntimeException e) {
            log.error("Could not merge event dirs for event: " + unnamedEvent, e);
        }
    }

    private Optional<Event> findNamedEvent(AllDayEventDuration duration, MediaStorageContentType contentType) {
        for (LocalDate date : duration) {
            if (eventManagers.get(contentType).hasEvent(date)) {
                return findNamedEvent(date, contentType);
            }
        }
        return Optional.empty();
    }

    private Optional<Event> findNamedEvent(LocalDate date, MediaStorageContentType contentType) {
        return eventManagers.get(contentType)
                .getEvents(date).stream()
                .filter(event -> !event.isUnnamed())
                .findFirst();
    }

    private static String getPathName(Path filePath) {
        return filePath.getFileName().toString();
    }

    protected static void deleteEmptySrcDir(Path path) throws IOException {
        if (isDirEmpty(path)) {
            Files.delete(path);
        }
    }

    private static boolean isDirEmpty(Path path) throws IOException {
        try (final DirectoryStream<Path> dirStream = Files.newDirectoryStream(path)) {
            return !dirStream.iterator().hasNext();
        }
    }

    private Path importFile(Path filePath, MediaStorage mediaStorage, boolean uncategorized) throws
            IOException, FormatException {
        Path path = directories ? filePath.getParent() : filePath;
        Event event = getFirstEvent(path, eventManagers.get(defaultContentType));
        return mediaStorage.importFile(filePath, event, defaulCopyrightOwner, uncategorized, fileMigrationOperation);
    }

    private Event getFirstEvent(Path path, EventManager eventManager) {
        final Matcher matcher = rawDatePattern.matcher(getPathName(path));
        if (matcher.matches()) {
            LocalDate date = getDate(matcher);
            return eventManager.getAndUpdateFirstEvent(date);
        } else {
            throw new IllegalStateException("Should be matching here: " + getPathName(path));
        }
    }

    public void organize() throws IOException {
        try (final Stream<Path> paths = Files.list(mediaRootDir)) {
            paths.filter(path -> ((directories && Files.isDirectory(path)) || (!directories && !Files.isDirectory(path)))
                    && rawDatePattern.matcher(getPathName(path)).matches())
                    .forEach(this::organize);
        }
    }

    private void organize(Path path) {
        try {
            if (directories) {
                try (final Stream<Path> paths = Files.list(path)) {
                    paths.forEach(filePath -> {
                        try {
                            importFile(filePath, Files.size(filePath) < 1000000);
                        } catch (IOException | FormatException ex) {
                            log.error("Error during moving file!", ex);
                        }
                    });
                }
                if (directories) {
                    deleteEmptySrcDir(path);
                }
            } else {
                importFile(path, false);
            }
        } catch (IOException | FormatException ex) {
            log.error("Error during moving file!", ex);
        }
    }

    private void importFile(Path filePath, boolean uncategorized) throws IOException, FormatException {
        log.debug("src: " + filePath);
        try {
            if (defaultPhotoImportStorage.isSupportedByFileExtension(filePath.getFileName().toString())) {
                importFile(filePath, defaultPhotoImportStorage, uncategorized);
            } else {
                importFile(filePath, defaultVideoImportStorage, uncategorized);
            }
        } catch (IOException ex) {
            log.error("Error during moving file!", ex);
        }
    }

    protected abstract LocalDate getDate(Matcher matcher);

}
