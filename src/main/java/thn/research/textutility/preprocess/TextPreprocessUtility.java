/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.preprocess;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import thn.research.textutility.general.GeneralUtility;
import thn.research.textutility.io.TextFileUtility;
import weka.core.Stopwords;
import weka.core.stemmers.IteratedLovinsStemmer;
import weka.core.tokenizers.WordTokenizer;

/**
 *
 * @author THNghiep
 */
public class TextPreprocessUtility {

    public static StanfordCoreNLP pipelineStanfordCoreNLP = null;

    /**
     * Remove stop-words and do stemming, and then out to TXT file
     *
     * @param filePathInput
     * @param filePathOutput
     * @param heading
     * @param isLower
     * @param isNoStop
     * @param isStem
     * @param isLemma
     * @throws Exception
     */
    private static void preprocess(String filePathInput, String filePathOutput,
            String heading, boolean isLower, boolean isNoStop, boolean isStem, boolean isLemma) throws Exception {
        ArrayList<String> wordList;
        StringBuilder strBuilder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(filePathInput), "UTF-8"));
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(filePathOutput, true), "UTF-8"));) {

            TextFileUtility.checkAndCreateParentDirs(filePathOutput);

            if (heading != null && !heading.isEmpty()) {
                writer.write(heading);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                if (isLemma) {
                    // Stanford Lemma may use case information to segment sentence and POS tag: 
                    // Do lemma first, before lower case. This also tokenizes.
                    wordList = lemma(line);
                } else {
                    wordList = tokenize(line);
                }

                if (isLower) {
                    ArrayList<String> lowerWordList = new ArrayList();
                    for (String word : wordList) {
                        lowerWordList.add(word.toLowerCase());
                    }
                    wordList = lowerWordList;
                }
                if (isNoStop) {
                    wordList = removeStopword(wordList);
                }
                if (isStem) {
                    wordList = stem(wordList);
                }

                for (String word : wordList) {
                    strBuilder.append(word).append(" ");
                }
                strBuilder.append("\n");

                // Write each line with buffered writer, to avoid writing very large file at once.
                writer.write(strBuilder.toString());
            }
        }
    }

    /**
     * Preprocess all files in a directory.
     *
     * @param rootPathInput
     * @param rootPathOutput
     * @param extensions: e.g., Arrays.asList(".txt", ".dat").
     * @param overwrite
     * @param heading
     * @param isLower
     * @param isNoStop
     * @param isStem
     * @param isLemma
     * @throws Exception
     */
    public static void parallelPreprocess(String rootPathInput, String rootPathOutput, List<String> extensions, boolean overwrite,
            final String heading, final boolean isLower, final boolean isNoStop, final boolean isStem, final boolean isLemma) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(GeneralUtility.getNumOfCore() - 1);

        File rootOutput = new File(rootPathOutput);
        if (rootOutput.exists()) {
            if (overwrite) {
                FileUtils.deleteQuietly(rootOutput);
            } else {
                throw new Exception("The output folder is already existing.");
            }
        }

        List<String> listFilePaths = TextFileUtility.getAllFilePaths(rootPathInput, extensions);

        for (int i = 0; i < listFilePaths.size(); i++) {
            final String filePathInput = listFilePaths.get(i);
            final String filePathOutput = filePathInput.replace(rootPathInput, rootPathOutput);
            executor.submit(() -> {
                try {
                    preprocess(filePathInput, filePathOutput, heading, isLower, isNoStop, isStem, isLemma);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
        }
    }

    /**
     * Tokenize.
     *
     * @param input
     * @return
     * @throws Exception
     */
    public static ArrayList<String> tokenize(String input) throws Exception {
        ArrayList<String> result = new ArrayList();

        WordTokenizer wordTokenizer = new WordTokenizer();
        String delimiters = " \r\t\n.,;:\'\"()?!-><#$\\%&*+/@^_=[]{}|`~0123456789·‘’“”\\«ª©¯¬£¢§™•ϵϕ­ ´";
        wordTokenizer.setDelimiters(delimiters);
        wordTokenizer.tokenize(input);
        String token;
        while (wordTokenizer.hasMoreElements()) {
            token = wordTokenizer.nextElement();
            if (token.length() > 1) {
                result.add(token);
            }
        }
        return result;
    }

    /**
     * Remove stopword using Weka stopword list.
     *
     * @param input
     * @return
     * @throws Exception
     */
    public static ArrayList<String> removeStopword(ArrayList<String> input) throws Exception {
        ArrayList<String> result = new ArrayList();
        for (String token : input) {
            if (!Stopwords.isStopword(token.toLowerCase())) {
                result.add(token);
            }
        }
        return result;
    }

    /**
     * Stemming each word using Weka's Lovin Stemmer.
     *
     * @param input
     * @return
     * @throws java.lang.Exception
     */
    public static ArrayList<String> stem(ArrayList<String> input) throws Exception {
        ArrayList<String> result = new ArrayList();
        IteratedLovinsStemmer stemmerLovin = new IteratedLovinsStemmer();
        for (String token : input) {
            token = stemmerLovin.stem(token);
            result.add(token);
        }
        return result;
    }

    /**
     * Lemmatizing using Stanford CORE NLP.
     *
     * @param input
     * @return
     * @throws Exception
     */
    public static ArrayList<String> lemma(String input) throws Exception {
        ArrayList<String> result = new ArrayList();

        // StanfordCoreNLP loads a lot of models, so you probably only want to do this once per execution
        if (pipelineStanfordCoreNLP == null) {
            // Create StanfordCoreNLP object properties, with POS tagging (required for lemmatization), and lemmatization
            Properties props = new Properties();
            props.put("annotators", "tokenize, ssplit, pos, lemma");
            pipelineStanfordCoreNLP = new StanfordCoreNLP(props);
        }

        // create an empty Annotation just with the given text
        Annotation document = new Annotation(input);

        // run all Annotators on this text
        pipelineStanfordCoreNLP.annotate(document);

        // Iterate over all of the sentences found
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            // Iterate over all tokens in a sentence
            for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
                // Retrieve and add the lemma for each word into the list of lemmas
                result.add(token.get(LemmaAnnotation.class));
            }
        }

        return result;
    }

    /**
     * Test.
     *
     * @param args
     */
    public static void main(String[] args) {
        String dirPath = "E:\\NghiepTH Working\\Data\\PTM\\MAS";
        String heading = "1182744\n";
        try {
            TextPreprocessUtility.preprocess(dirPath + File.separator + "MAS_doc.txt",
                    dirPath + File.separator + "MAS_doc_lowercase_removedSW_lemma.txt", heading, true, true, false, true);
            TextPreprocessUtility.preprocess(dirPath + File.separator + "MAS_doc.txt",
                    dirPath + File.separator + "MAS_doc_lowercase_removedSW_stem.txt", heading, true, true, true, false);
            TextPreprocessUtility.preprocess(dirPath + File.separator + "MAS_doc.txt",
                    dirPath + File.separator + "MAS_doc_lowercase_removedSW.txt", heading, true, true, false, false);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
