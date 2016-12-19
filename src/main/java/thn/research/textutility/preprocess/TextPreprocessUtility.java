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
     * @param isCleanToken
     * @param isLower
     * @param isNoStop
     * @param isStem
     * @param isLemma
     * @throws Exception
     */
    public static void preprocess(String filePathInput, String filePathOutput,
            String heading, boolean isCleanToken, boolean isLower, boolean isNoStop, boolean isStem, boolean isLemma) throws Exception {
        ArrayList<String> wordList;
        StringBuilder preprocessedLine = new StringBuilder();

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
//            reader.readLine(); // Bypass first line, use when preprocess again.
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                if (isLemma) {
                    // Stanford Lemma use case information, punctuation, stopwords to segment sentence and POS tag: Do lemma first and return the line.
                    // This also tokenizes, but not clean. Lemma keep case, keep punctuation mark, keep stopwords. Need to check to process these later.
                    line = lemmaLine(line);
                }

                if (isLower) {
                    line = line.toLowerCase();
                }

                // Always tokenize, even when not lemma, lower, nostop, stem.
                wordList = tokenize(line, isCleanToken);

                if (isNoStop) {
                    wordList = removeStopword(wordList);
                }

                if (isStem) {
                    wordList = stem(wordList);
                }

                for (String word : wordList) {
                    preprocessedLine.append(word).append(" ");
                }
                preprocessedLine.append("\n");

                // Write each line with buffered writer, to avoid writing very large file at once.
                writer.write(preprocessedLine.toString());
                preprocessedLine.setLength(0);
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
     * @param isCleanToken
     * @param isLower
     * @param isNoStop
     * @param isStem
     * @param isLemma
     * @throws Exception
     */
    public static void parallelPreprocess(String rootPathInput, String rootPathOutput, List<String> extensions, boolean overwrite,
            final String heading, final boolean isCleanToken, final boolean isLower, final boolean isNoStop, final boolean isStem, final boolean isLemma) throws Exception {
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
                    preprocess(filePathInput, filePathOutput, heading, isCleanToken, isLower, isNoStop, isStem, isLemma);
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
     * @param isCleanToken: true: clean all punctuation, false: only split by
     * visible space.
     * @return
     * @throws Exception
     */
    public static ArrayList<String> tokenize(String input, boolean isCleanToken) throws Exception {
        ArrayList<String> result = new ArrayList();

        WordTokenizer wordTokenizer = new WordTokenizer();
        if (isCleanToken) {
            String cleanDelimiters = " \r\t\n.,;:\'\"()?!-><#$\\%&*+/@^_=[]{}|`~0123456789·‘’“”\\«ª©¯¬£¢§™•ϵϕ­ ´";
            wordTokenizer.setDelimiters(cleanDelimiters);
        } else {
            String simpleDelimiters = " \r\t\n";
            wordTokenizer.setDelimiters(simpleDelimiters);
        }
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
     * Lemmatizing using Stanford CORE NLP. For CoreNLP manual, see
     * http://stanfordnlp.github.io/CoreNLP/download.html
     *
     * @param input
     * @return A list of lemmatized tokens.
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
     * Lemmatizing using Stanford CORE NLP. For CoreNLP manual, see
     * http://stanfordnlp.github.io/CoreNLP/download.html
     *
     * @param input
     * @return A lemmatized string.
     * @throws Exception
     */
    public static String lemmaLine(String input) throws Exception {
        ArrayList<String> wordList = new ArrayList();

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
                wordList.add(token.get(LemmaAnnotation.class));
            }
        }

        StringBuilder lemmaLine = new StringBuilder();
        for (String word : wordList) {
            lemmaLine.append(word);
        }
        
        return lemmaLine.toString();
    }

    /**
     * Test. Lemmatization is very slow (˜hours). Others are very fast (˜minutes).
     *
     * @param args
     */
    public static void main(String[] args) {
        String dirPath = "E:\\NghiepTH Working\\Data\\PTM\\MAS";
        String heading = "1182744\n";
        try {
            TextPreprocessUtility.preprocess(dirPath + File.separator + "MAS_doc.txt",
                    dirPath + File.separator + "MAS_doc_cleanToken_lowercase_removedSW.txt", heading, true, true, true, false, false);
            TextPreprocessUtility.preprocess(dirPath + File.separator + "MAS_doc.txt",
                    dirPath + File.separator + "MAS_doc_cleanToken_lowercase_removedSW_stem.txt", heading, true, true, true, true, false);
            TextPreprocessUtility.preprocess(dirPath + File.separator + "MAS_doc.txt",
                    dirPath + File.separator + "MAS_doc_cleanToken_lowercase_removedSW_lemma.txt", heading, true, true, true, false, true);
//            TextPreprocessUtility.preprocess(dirPath + File.separator + "MAS_doc_simpleToken_lowercase_removedSW_lemma.txt",
//                    dirPath + File.separator + "MAS_doc_cleanToken_lowercase_removedSW_lemma.txt", heading, true, false, false, false, false); // Process again.
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
