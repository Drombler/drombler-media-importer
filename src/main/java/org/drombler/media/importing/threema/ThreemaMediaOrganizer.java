/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.drombler.media.importing.threema;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
public class ThreemaMediaOrganizer extends AbstractMediaOrganizer {

    private static final Pattern RAW_DATE_PATTERN = Pattern.compile("\\d+(\\d{13}).*");

    public static void main(String[] args) throws IOException {
        initLogger();

        Path baseDirPath = Paths.get("\\\\diskstation\\photo\\Puce-Mobile");
        DromblerId defaultDromblerId = new DromblerUserId("unknown");

        ThreemaMediaOrganizer organizer = new ThreemaMediaOrganizer();
        organizer.organize(baseDirPath, defaultDromblerId);
    }

    public ThreemaMediaOrganizer() throws IOException {
        super(RAW_DATE_PATTERN, false);
    }

    @Override
    protected LocalDate getDate(final Matcher matcher) throws NumberFormatException {
        Instant instant = Instant.ofEpochMilli(Long.parseLong(matcher.group(1)));
        ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
        LocalDate date = zdt.toLocalDate();
        return date;
    }
}
