/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.preprocess;

import ir.vsr.HashMapVector;
import java.util.HashMap;
import org.apache.mahout.text.SequenceFilesFromDirectory;
import org.apache.mahout.vectorizer.SparseVectorsFromSequenceFiles;
import thn.research.textutility.io.MahoutFileUtility;

/**
 *
 * @author THNghiep
 */
public class TextVectorizeByMahoutTerminalUtility {

    public static void textVectorizeFiles(String textDir, String sequenceDir, String vectorDir) throws Exception {
        textToSequenceFiles(textDir, sequenceDir);
        sequenceToVectorFiles(sequenceDir, vectorDir);
    }

    /**
     *
     * @param textDir
     * @param sequenceDir
     * @return
     * @throws Exception Using mahout terminal driver.
     */
    public static int textToSequenceFiles(String textDir, String sequenceDir) throws Exception {
        String[] params = {"-i", textDir, "-o", sequenceDir, "-ow", "-xm", "sequential", "-chunk", "12000"};
        int status = new SequenceFilesFromDirectory().run(params);
        return status;
    }

    /**
     * @param sequenceDir
     * @param vectorDir
     * @return
     * @throws Exception
     */
    public static int sequenceToVectorFiles(String sequenceDir, String vectorDir) throws Exception {
        String[] params = {"-i", sequenceDir, "-o", vectorDir, "-ow", "-wt", "tfidf", "-s", "1", "-md", "1", "-x", "100", "-xs", "-1", "-ng", "2", "-ml", "50", "-chunk", "12000", "-n", "2"};
        int status = new SparseVectorsFromSequenceFiles().run(params);
        return status;
    }

    /**
     * Example.
     * 
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        // Prepare input documents in text folder, each document in a .txt file, file name is document id.
        String dir = "";
        textVectorizeFiles(dir + "Test Compute TFIDF\\Removed stopword and stemming text", dir + "Test Compute TFIDF\\sequence", dir + "Test Compute TFIDF\\vector");
        HashMap<Integer, String> dictionary = MahoutFileUtility.readMahoutDictionaryFiles(dir + "Test Compute TFIDF\\vector");
        HashMap<String, HashMapVector> vectorizedDocuments = MahoutFileUtility.readMahoutVectorFiles(dir + "Test Compute TFIDF\\vector");
    }
}
