/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.drombler.media.importing;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.drombler.media.importing.iphone.IPhoneMobileMediaOrganizer;

/**
 *
 * @author Florian
 */
public class MediaImporter {

    public static void main(String... args) throws IOException {
        List<MediaImportJob> puceMediaImportJobs = createPuceMediaImportJobs();
        puceMediaImportJobs.forEach(job -> {
            try {
                job.run();
            } catch (IOException ex) {
                Logger.getLogger(MediaImporter.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    private static List<MediaImportJob> createPuceMediaImportJobs() throws IOException {
        Path iPhoneMediaRootDir = Paths.get("\\\\diskstation\\photo\\Puce-Mobile");

        return Arrays.asList(
//                new MediaImportJob(puceMobileDirPath, puceDromblerId, new SamsungMobileMediaOrganizer(mediaEventDirPathsFilePath)),
//                new MediaImportJob(puceMobileDirPath, defaultDromblerId, new ThreemaMediaOrganizer(mediaEventDirPathsFilePath)),
                new MediaImportJob(new IPhoneMobileMediaOrganizer(iPhoneMediaRootDir))
        );

    }
}
