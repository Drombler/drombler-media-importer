/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.drombler.media.importing;

import java.io.IOException;
import java.nio.file.Path;
import org.drombler.identity.core.DromblerId;
import org.drombler.media.importing.core.AbstractMediaOrganizer;

/**
 *
 * @author Florian
 */
public class MediaImportJob {

    private final AbstractMediaOrganizer mediaOrganizer;

    public MediaImportJob(AbstractMediaOrganizer mediaOrganizer) {
        this.mediaOrganizer = mediaOrganizer;
    }

    /**
     * @return the mediaOrganizer
     */
    public AbstractMediaOrganizer getMediaOrganizer() {
        return mediaOrganizer;
    }

    public void run() throws IOException {
        mediaOrganizer.organize();
    }

}
