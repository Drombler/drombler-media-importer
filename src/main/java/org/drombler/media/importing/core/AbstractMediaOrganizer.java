/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.drombler.media.importing.core;

import lombok.extern.slf4j.Slf4j;
import org.drombler.event.core.Event;
import org.drombler.identity.core.DromblerUserId;
import org.drombler.identity.core.PrivateDromblerIdProvider;
import org.drombler.identity.management.DromblerIdentityProviderManager;
import org.drombler.media.core.MediaCategory;
import org.drombler.media.core.MediaCategoryManager;
import org.drombler.media.core.MediaStorage;
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
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.LogManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 *
 * @author Florian
 */
@Slf4j
public abstract class AbstractMediaOrganizer {

    private final EventManager eventManager;
    private final Path mediaRootDir;
    private final Pattern rawDatePattern;
    private final boolean directories;
    private final MediaStorage photoImportStorage;
    private final MediaStorage videoImportStorage;
    private final DromblerUserId defaulCopyrightOwner;
    private final DromblerIdentityProviderManager dromblerIdentityProviderManager = new DromblerIdentityProviderManager();
    private final MediaCategoryManager mediaCategoryManager = new MediaCategoryManager();
    private final MediaStorageManager mediaStorageManager = new MediaStorageManager(mediaCategoryManager);

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
        this.eventManager = new EventManager(dromblerIdentityProviderManager);

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
        this.photoImportStorage = new MediaStorage("photo-import", "importing photo", photoDirPath, Arrays.asList(photoMediaCategory));
        this.videoImportStorage = new MediaStorage("video-import", "importing video", videoDirPath, Arrays.asList(videoMediaCategory));

        this.defaulCopyrightOwner = new DromblerUserId(mediaImportingProperties.getProperty("defaultCopyrightOwner"));
        dromblerIdentityProviderManager.registerDromblerIdentityProvider(PrivateDromblerIdProvider.getInstance());

        mediaStorageManager.getMediaStorages().forEach(eventManager::updateEventMap);
        eventManager.updateEventMap(photoImportStorage);
        eventManager.updateEventMap(videoImportStorage);
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

    private static void moveFile(Path filePath, Path targetDirPath) throws IOException {
        if (!Files.exists(targetDirPath)) {
            Files.createDirectories(targetDirPath);
        }
        // TODO: check the events dirs before enabling the next line. They were not correct the last time
        Files.move(filePath, targetDirPath.resolve(filePath.getFileName()));
    }

    private Event getFirstEvent(Path path) {
        final Matcher matcher = rawDatePattern.matcher(getPathName(path));
        if (matcher.matches()) {
            LocalDate date = getDate(matcher);
            return eventManager.getFirstEvent(date);
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
                            moveFile(filePath, Files.size(filePath) < 1000000);
                        } catch (IOException | FormatException ex) {
                            log.error("Error during moving file!", ex);
                        }
                    });
                }
                if (directories) {
                    deleteEmptySrcDir(path);
                }
            } else {
                moveFile(path, false);
            }
        } catch (IOException | FormatException ex) {
            log.error("Error during moving file!", ex);
        }
    }

    private void moveFile(Path filePath, boolean uncategorized) throws IOException, FormatException {
        Event event = getFirstEvent(directories ? filePath.getParent() : filePath);
        Path photoDir = photoImportStorage.resolveMediaEventDirPath(event, defaulCopyrightOwner, uncategorized);
//        System.out.println("dst: "+photoDir);
        Path videoDir = videoImportStorage.resolveMediaEventDirPath(event, defaulCopyrightOwner, uncategorized);
//        System.out.println("dst: "+videoDir);
        log.debug("src: " + filePath);
        try {
            if (photoImportStorage.isSupportedByFileExtension(filePath.getFileName().toString())) {
                moveFile(filePath, photoDir);
            } else {
                moveFile(filePath, videoDir);
            }
        } catch (IOException ex) {
            log.error("Error during moving file!", ex);
        }
    }

    protected abstract LocalDate getDate(Matcher matcher);

}
