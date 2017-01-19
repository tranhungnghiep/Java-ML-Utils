/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.io;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author mac
 */
public class FileUtility {

    /**
     * Solve exception when writing files to not existing directory using Java
     * writer.
     *
     * @param filePath
     * @throws Exception
     */
    public static void checkToCreateParentDir(String filePath) throws Exception {
        File file = new File(filePath);
        if ((file.getParentFile() != null) && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
    }

    /**
     * Just create dir if not exists.
     * 
     * @param dirPath
     * @throws Exception 
     */
    public static void checkToCreateDir(String dirPath) throws Exception {
        if (!new File(dirPath).exists()) {
            new File(dirPath).mkdirs();
        }
    }

    /**
     * Get All File Paths, including in subfolder, filtered by extensions.
     *
     * @param dirPath: directory path.
     * @param extensions: extension such as .txt and .dat
     * @return list of full path of files in the directory including
     * subdirectory with extension specified.
     */
    public static List<String> getAllFilePaths(String dirPath, List<String> extensions) throws Exception {
        File[] allFiles = new File(dirPath).listFiles();
        List<String> files = new ArrayList<>();
        if (allFiles != null) {
            for (File file : allFiles) {
                if (file.isFile()) {
                    for (String ex : extensions) {
                        if (file.getName().endsWith(ex)) {
                            files.add(file.getAbsolutePath());
                            break;
                        }
                    }
                } else {
                    files.addAll(getAllFilePaths(file.getPath(), extensions));
                }
            }
        }
        return files;
    }
    
}
