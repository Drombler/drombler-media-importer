/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.drombler.media.importing.core;

import lombok.extern.slf4j.Slf4j;
import org.drombler.event.core.AllDayEventDuration;
import org.drombler.event.core.Event;
import org.drombler.identity.core.DromblerUserId;
import org.drombler.identity.core.PrivateDromblerIdProvider;
import org.drombler.identity.management.DromblerIdentityProviderManager;
import org.drombler.media.core.*;
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

    private final Map<MediaStorageType, EventManager> eventManagers = new EnumMap<>(MediaStorageType.class);
    private final Path mediaRootDir;
    private final Pattern rawDatePattern;
    private final boolean directories;
    private final MediaStorage defaultPhotoImportStorage;
    private final MediaStorage defaultVideoImportStorage;
    private final List<MediaStorage> photoImportStorages = new ArrayList<>();
    private final List<MediaStorage> videoImportStorages = new ArrayList<>();
    private final DromblerUserId defaulCopyrightOwner;
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
        Arrays.stream(MediaStorageType.values()).forEach(type -> eventManagers.put(type, new EventManager()));

        Properties mediaImportingProperties = new Properties();
        try (InputStream is = AbstractMediaOrganizer.class.getResourceAsStream("media-importing.properties")) {
            mediaImportingProperties.load(is);
        }
        this.mediaRootDir = mediaRootDir;
        Path photoDirPath = mediaRootDir.resolve(mediaImportingProperties.getProperty("photoDir"));
        Path videoDirPath = mediaRootDir.resolve(mediaImportingProperties.getProperty("videoDir"));
        final MediaCategory photoMediaCategory = mediaCategoryManager.getMediaCategory("photo");
        if (photoMediaCategory == null) {
            throw new IllegalArgumentException("photo media category not configured correctly!");
        }
        final MediaCategory videoMediaCategory = mediaCategoryManager.getMediaCategory("video");
        if (videoMediaCategory == null) {
            throw new IllegalArgumentException("video media category not configured correctly!");
        }
        this.defaultPhotoImportStorage = new MediaStorage("photo-import-owner-events", "importing owner event photos", photoDirPath, MediaStorageType.OWNER_EVENTS, false, Arrays.asList(photoMediaCategory));
        photoImportStorages.add(defaultPhotoImportStorage);
        photoImportStorages.add(new MediaStorage("photo-import-private-events", "importing private event photos", photoDirPath, MediaStorageType.PRIVATE_EVENTS, false, Arrays.asList(photoMediaCategory)));
        photoImportStorages.add(new MediaStorage("photo-import-business-events", "importing business event photos", photoDirPath, MediaStorageType.BUSINESS_EVENTS, false, Arrays.asList(photoMediaCategory)));
        photoImportStorages.add(new MediaStorage("photo-import-other-events", "importing other event photos", photoDirPath, MediaStorageType.OTHER_EVENTS, false, Arrays.asList(photoMediaCategory)));
        photoImportStorages.add(new MediaStorage("photo-import-things", "importing photos of things", photoDirPath, MediaStorageType.THINGS, false, Arrays.asList(photoMediaCategory)));

        this.defaultVideoImportStorage = new MediaStorage("video-import-owner-events", "importing owner event video", videoDirPath, MediaStorageType.OWNER_EVENTS, false, Arrays.asList(videoMediaCategory));
        videoImportStorages.add(defaultVideoImportStorage);

        this.defaulCopyrightOwner = new DromblerUserId(mediaImportingProperties.getProperty("defaultCopyrightOwner"));
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
        try {
            eventManagers.get(mediaStorage.getType()).updateEventMap(mediaStorage.parseEvents());
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    private void importNamedEventCandidates(MediaStorage importStorage) throws IOException {
        List<Event> events = importStorage.parseEvents();
        List<Event> namedEvents = events.stream()
                .filter(event -> !event.isUnnamed())
                .collect(Collectors.toList());
        eventManagers.get(importStorage.getType()).updateEventMap(namedEvents);
    }

    private void importUnamedEventCandidates(MediaStorage importStorage) throws IOException {
        List<Event> events = importStorage.parseEvents();
        List<Event> namedEvents = events.stream()
                .filter(event -> !event.isUnnamed())
                .collect(Collectors.toList());
        eventManagers.get(importStorage.getType()).updateEventMap(namedEvents);
    }

    private void reorganizeImportStorage(MediaStorage importStorage) throws IOException {
        List<Event> events = importStorage.parseEvents();
        List<Event> unnamedEvents = events.stream()
                .filter(Event::isUnnamed)
                .collect(Collectors.toList());
        unnamedEvents.stream()
                .filter(event -> event.getDuration() instanceof AllDayEventDuration)
                .forEach(unnamedEvent -> mergeUnnamedEvent(unnamedEvent, importStorage));
    }

    private void mergeUnnamedEvent(Event unnamedEvent, MediaStorage importStorage) {
        try {
            if (!mergeEventDirs(importStorage, unnamedEvent)) {
                eventManagers.get(importStorage.getType()).updateEventMap(unnamedEvent);
            }
        } catch (FormatException | IOException | RuntimeException e) {
            log.error("Could not merge event dirs for event: " + unnamedEvent, e);
        }
    }

    private boolean mergeEventDirs(MediaStorage importStorage, Event unnamedEvent) throws FormatException, IOException {
        AllDayEventDuration duration = (AllDayEventDuration) unnamedEvent.getDuration();
        for (LocalDate date : duration) {
            if (eventManagers.get(importStorage.getType()).hasEvent(date)) {
                Optional<Event> namedEvent = eventManagers.get(importStorage.getType())
                        .getEvents(date).stream()
                        .filter(event -> !event.isUnnamed())
                        .findFirst();
                if (namedEvent.isPresent()) {
                    importStorage.mergeEventDirs(unnamedEvent, namedEvent.get());
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
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

    private Path importFile(Path filePath, MediaStorage mediaStorage, boolean uncategorized) throws IOException, FormatException {
        Path path = directories ? filePath.getParent() : filePath;
        Event event = getFirstEvent(path, eventManagers.get(mediaStorage.getType()));
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
