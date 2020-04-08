/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.drombler.media.importing.panasonic.hdwriterae;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.drombler.identity.core.DromblerId;
import org.drombler.identity.core.DromblerUserId;
import org.drombler.media.importing.core.AbstractMediaOrganizer;

/**
 * Organizes Files from Panasonic import for Synology import.
 *
 * @author Florian
 */
public class PanasonicMediaOrganizer extends AbstractMediaOrganizer {

    public static void main(String[] args) throws IOException {
        initLogger();
        
        Path mediaRootDir = Paths.get("D:\\hd-writer-ae-tmp");

        PanasonicMediaOrganizer organizer = new PanasonicMediaOrganizer(mediaRootDir);
        organizer.organize();
    }

    private static final Pattern RAW_DATE_PATTERN = Pattern.compile("\\d{2}-\\d{2}-\\d{4}");
    private static final DateTimeFormatter RAW_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    public PanasonicMediaOrganizer(Path mediaRootDir) throws IOException {
        super(mediaRootDir, RAW_DATE_PATTERN, true);
    }

    @Override
    protected LocalDate getDate(Matcher matcher) {
        return RAW_DATE_FORMATTER.parse(matcher.group(), LocalDate::from);
    }    
    
}
