/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.drombler.media.importing.iphone;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.drombler.media.importing.core.AbstractMediaOrganizer;

/**
 * Organizes Files from iPhone import for Synology import.
 *
 * @author Florian
 */
public class IPhoneMobileMediaOrganizer extends AbstractMediaOrganizer {

    public static void main(String[] args) throws IOException {
        initLogger();

        Path mediaRootDir = Paths.get("\\\\diskstation\\photo\\Puce-Mobile");

        IPhoneMobileMediaOrganizer organizer = new IPhoneMobileMediaOrganizer(mediaRootDir);
        organizer.organize();
    }

    private static final Pattern RAW_DATE_PATTERN = Pattern.compile("IMG_(\\d{8}_\\d{6})\\..*");
    private static final DateTimeFormatter RAW_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public IPhoneMobileMediaOrganizer(Path mediaRootDir) throws IOException {
        super(mediaRootDir, RAW_DATE_PATTERN, false);
    }

    @Override
    protected LocalDate getDate(Matcher matcher) {
        return RAW_DATE_FORMATTER.parse(matcher.group(1), LocalDate::from);
    }
}
