package dev.misieur.iasync.utils;

import java.io.File;
import java.util.List;

public class FileUtils {

    public static void deleteDirectoryContent(File directory, List<String> excludes) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (excludes != null && excludes.contains(file.getName())) {
                continue;
            }

            if (file.isDirectory()) {
                deleteDirectoryContent(file, null);
                file.delete();
            } else {
                file.delete();
            }
        }
    }

    public static void deleteDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

}
