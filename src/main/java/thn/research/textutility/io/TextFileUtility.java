/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author THNghiep
 */
public class TextFileUtility {

    /**
     * Splitting a text file by number of rows.
     *
     * @param filePathIn
     * @param numRow
     */
    public static void splitTextFile(String filePathIn, long numRow) {
        int count = 0;
        StringBuilder strBuilder = new StringBuilder();
        String line;
        String filePathOut;
        int pos = filePathIn.lastIndexOf(".");
        if (pos > 0) {
            filePathOut = filePathIn.substring(0, pos);
        } else {
            filePathOut = filePathIn;
        }
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(filePathIn), "UTF-8"));
            BufferedWriter writer;

            while ((line = reader.readLine()) != null) {
                strBuilder.append(line).append("\n");
                count++;
                if (count % numRow == 0) {
                    filePathOut += "_" + (count / numRow) + ".txt";
                    writer = new BufferedWriter(
                            new OutputStreamWriter(
                                    new FileOutputStream(filePathOut, true), "UTF-8"));
                    writer.write(strBuilder.toString());
                    writer.close();
                    strBuilder.setLength(0);
                }
            }
            if (strBuilder.length() > 0) {
                filePathOut += "_" + (count / numRow + 1) + ".txt";
                writer = new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(filePathOut, true), "UTF-8"));
                writer.write(strBuilder.toString());
                writer.close();
                strBuilder.setLength(0);
            }

            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * getAllFilePaths.
     *
     * @param dirPath: directory path.
     * @param extensions: extension such as .txt and .dat
     * @return list of full path of files in the directory including
     * subdirectory with extension specified.
     */
    public static List<String> getAllFilePaths(String dirPath, List<String> extensions) throws Exception {
        File[] allFiles = new File(dirPath).listFiles();
        List<String> files = new ArrayList<>();
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
        return files;
    }

    /**
     * Solve exception when writing files to not existing directory using Java
     * writer.
     *
     * @param filePath
     * @throws Exception
     */
    public static void checkAndCreateParentDirs(String filePath) throws Exception {
        File f = new File(filePath);
        if (!(f.getParentFile().exists())) {
            f.getParentFile().mkdirs();
        }
    }

    /**
     * Write text file, default encoding UTF-8, checked to create parent
     * directory.
     *
     * @param filePathOutput
     * @param content
     * @param append
     * @throws Exception
     */
    public static void writeTextFile(String filePathOutput, String content, boolean append) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(filePathOutput, append), "UTF-8"))) {
            checkAndCreateParentDirs(filePathOutput);
            writer.write(content);
        }
    }
}
