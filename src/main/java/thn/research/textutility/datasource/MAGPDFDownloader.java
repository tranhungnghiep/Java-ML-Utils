/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.datasource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.apache.commons.io.FileExistsException;
import org.apache.commons.io.FileUtils;
import thn.research.textutility.general.GeneralUtility;
import thn.research.textutility.general.InternetUtility;
import thn.research.textutility.io.FileUtility;
import thn.research.textutility.general.NumericUtility;

/**
 * This class will download pdf files based on the url list in MAG dataset. 
 * Note that the url list is not clean: 
 * - Link to abstract, pdf, other file types. 
 * - Link to pdf may need to be resolved before download: not direct pdf. 
 * - Many pdf may be in a server, e.g., citeseerx/arxiv/researchgate: consecutive download may be blocked:
 * check if blocked, put to wait list for 1 thread to handle consecutively. 
 * -> better avoid crawling academic database, find bulk download.
 * - May need to use selenium for javascript resolve? 
 * - 1 more problem: there may be many pdf file of 1 paper: full paper/presentation/other 
 * - 1 more problem: may accidentally download pdf from payment source like IEEE, ACM... when
 * connected from NII server: banned. Check download using wget on server to avoid (list of domain).
 *
 * Note that have to keep track of paper: using paper id by MAG as file name.
 *
 * Critical problem: seem a lot of urls are just abstract, or html not pdf, or pointing to payment server, cannot get pdf. 
 * So the estimation is far off, real value may be just 1 mil. 
 * -> if this is the case: need to use data from core as main source.
 * -> turns out underestimated. Check more. But need to start with bulky source core, arxiv, citeseerx.
 *
 * Outline of the program: 
 * - parallel thread pool. 
 * - Read url list file line by line. 
 * - for each line, let a thread handle it: check if it is pdf, try
 * downloading, save as pdf, or put to wait list and let 1 thread to handle it. 
 * - Note that: pdf of 1 paper id is downloaded only 1 time: if a pdf is successfully saved, stop other thread
 * with same paper id. 
 * - save in hierarchical directory: each folder has 100
 * subfolder: A1-> A100, B1-> B100, C1-> C100, each subfolder Ci has 100 pdf.
 * Depth first search, number of subfolder A depends on data, may be 10 mil pdf, or 1 mil?
 *
 * Important technical problem: 
 * - sanity check link. 
 * - avoid duplication. 
 * - avoid blocking.  
 * - sync all these: process in parallel.
 * 
 * But simple work flow first.
 * - read url.
 * - parse.
 * - download not parallel.
 * - save in 1 folder.
 *
 * @author THNghiep
 */
public class MAGPDFDownloader {

    private static final Logger LOGGER = Logger.getLogger(MAGPDFDownloader.class.getName());
    private static final Logger LOGGER_MISC = Logger.getLogger(MAGPDFDownloader.class.getName() + "_misc");
    
    private static Set<String> downloadedPaperId = ConcurrentHashMap.newKeySet();
    
    /**
     * Parallel version of downloadPDFMAG(.).
     * Download pdf files from MAG url list.
     * Write all files to 1 directory.
     * Avoiding forbidden payment domains.
     * 
     * @param urlListFilePath
     * @param dirPathOutput
     * @param tempDirPathOutput
     * @param overwrite
     * @param forbiddenDomain
     * @param rateLimitDomain
     * @param waitingSecond
     * @param maxConsecutiveCheck
     * @param connectionTimeout
     * @param readTimeout
     * @param threadPoolSize
     * @param fromLineNum
     * @param toLineNum
     * @throws Exception 
     */
    public static void downloadPDFMAGParallel(String urlListFilePath, String dirPathOutput, String tempDirPathOutput, boolean overwrite, List<String> forbiddenDomain, List<String> rateLimitDomain, int waitingSecond, int maxConsecutiveCheck, int connectionTimeout, int readTimeout, int threadPoolSize, long fromLineNum, long toLineNum) throws Exception {
        LOGGER.log(Level.INFO, "Download Start.");
        
        // Thread pool.
        ExecutorService executor;
        if (threadPoolSize == 0) {
            executor = Executors.newFixedThreadPool(GeneralUtility.getNumOfCore() - 1);
        } else {
            executor = Executors.newFixedThreadPool(threadPoolSize);
        }

        List<String> mimeType = Arrays.asList("application/pdf", "application/x-pdf");

        if (overwrite) {
            FileUtils.deleteQuietly(new File(dirPathOutput));
            // Reset downloaded paper id.
            downloadedPaperId.clear();
            readDonePaperIds(dirPathOutput, downloadedPaperId);
        } else {
            // Already get downloaded paper id.
        }
        // Always delete temp dir.
        FileUtils.deleteQuietly(new File(tempDirPathOutput));

        FileUtility.checkToCreateDir(dirPathOutput);
        FileUtility.checkToCreateDir(tempDirPathOutput);

        long count = 0;
        // Read URL list.
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(urlListFilePath), "UTF-8"))) {
            
            List<String> recentDomains = new LinkedList<>();
            
            String[] parsedLine;
            String tryDomain;
            // Read line.
            String line;
            while ((line = reader.readLine()) != null) {
                count++;
                if ((count - 1) % 1000000 == 0) {
                    LOGGER.log(Level.INFO, "Processed line: " + (count - 1));
                }
                if (count < fromLineNum) {
                    continue;
                }
                if (toLineNum > 0 && count > toLineNum) {
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                // Parse line, get paper id and url.
                parsedLine = line.split("\t");
                String paperId = parsedLine[0];
                
                // If the paper has been downloaded, pass.
                if (!overwrite && downloadedPaperId.contains(paperId)) {
                    continue;
                }

                String url = fixUrl(parsedLine[1]);
                // If url is in forbidden list, pass.
                if (forbiddenDomain != null && forbiddenDomain.stream().anyMatch(s -> url.contains(s))) {
                    continue;
                }

                // If near consecutive rate limit domain, wait.
                try {
                    tryDomain = new URL(url).getHost();
                } 
                catch (Exception e) {
                    // Also precheck malformed url before download.
//                    LOGGER.log(Level.SEVERE, "MalformedURLException in precheck: " + e.toString() + "\n" + "\t" + "Paper ID: " + paperId + ". URL: " + url);
                    continue;
                }
                String domain = tryDomain;
                if (rateLimitDomain != null && rateLimitDomain.stream().anyMatch(s -> url.contains(s)) 
                        && domain != null && recentDomains.stream().anyMatch(s -> domain.equals(s))) {
                    Thread.sleep(waitingSecond * 1000);
                }
                if (maxConsecutiveCheck > 0 && recentDomains.size() == maxConsecutiveCheck) {
                    recentDomains.remove(0);
                }
                recentDomains.add(domain);

                String filePath = dirPathOutput + File.separator + paperId + ".pdf";
                String tempFilePath = tempDirPathOutput + File.separator + paperId + ".pdf";

                // Download in parallel.
                // Note that normally, ExecutorService does not block when thread pool is full.
                // So the number of processed line while in this loop is not real.
                // Moreover, after submitting all url to executor, the loop will finish 
                // and executor will be shutdowned although all urls are not executed.
                executor.submit(() -> {
                    try {
                        downloadFile(paperId, url, filePath, tempFilePath, mimeType, connectionTimeout, readTimeout, forbiddenDomain);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Exception in executor submit. " + e.toString() + "!", e);
                    }
                });
            }

            executor.shutdown();
            // This while loop may wait forever, e.g., some urls take too long to process, or forever?
            while (!executor.isTerminated()) {
            }
            // Only wait some time, do not use this.
//            executor.awaitTermination(600, TimeUnit.SECONDS);
//            if (!executor.isTerminated()) {
//                LOGGER.log(Level.SEVERE, "Waiting for too long: Have to cancel unfinished task.");
//            }
//            executor.shutdownNow();
        }

        LOGGER.log(Level.INFO, "Download Finish. Processed line: " + (count - 1));
    }
    
    /**
     * Download pdf files from MAG url list.
     * Write all files to 1 directory.
     * Avoiding forbidden payment domains.
     * 
     * @param urlListFilePath
     * @param dirPathOutput
     * @param tempDirPathOutput
     * @param overwrite
     * @param forbiddenDomain
     * @param rateLimitDomain
     * @param waitingSecond
     * @param maxConsecutiveCheck
     * @param connectionTimeout
     * @param readTimeout
     * @param fromLineNum
     * @param toLineNum
     * @throws Exception 
     */
    public static void downloadPDFMAG(String urlListFilePath, String dirPathOutput, String tempDirPathOutput, boolean overwrite, List<String> forbiddenDomain, List<String> rateLimitDomain, int waitingSecond, int maxConsecutiveCheck, int connectionTimeout, int readTimeout, long fromLineNum, long toLineNum) throws Exception {
        LOGGER.log(Level.INFO, "Download Start.");
        
        List<String> mimeType = Arrays.asList("application/pdf", "application/x-pdf");

        if (overwrite) {
            FileUtils.deleteQuietly(new File(dirPathOutput));
            // Reset downloaded paper id.
            downloadedPaperId.clear();
            readDonePaperIds(dirPathOutput, downloadedPaperId);
        } else {
            // Already get downloaded paper id.
        }
        // Always delete temp dir.
        FileUtils.deleteQuietly(new File(tempDirPathOutput));

        FileUtility.checkToCreateDir(dirPathOutput);
        FileUtility.checkToCreateDir(tempDirPathOutput);

        long count = 0;
        // Read URL list.
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(urlListFilePath), "UTF-8"))) {
            
            List<String> recentDomains = new LinkedList<>();
            
            String[] parsedLine;
            String tryDomain;
            // Read line.
            String line;
            while ((line = reader.readLine()) != null) {
                count++;
                if ((count - 1) % 1000000 == 0) {
                    LOGGER.log(Level.INFO, "Processed line: " + (count - 1));
                }
                if (count < fromLineNum) {
                    continue;
                }
                if (toLineNum > 0 && count > toLineNum) {
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                // Parse line, get paper id and url.
                parsedLine = line.split("\t");
                String paperId = parsedLine[0];
                                
                // If the paper has been downloaded, pass.
                if (!overwrite && downloadedPaperId.contains(paperId)) {
                    continue;
                }
                
                String url = fixUrl(parsedLine[1]);
                // If url is in forbidden list, pass.
                if (forbiddenDomain != null && forbiddenDomain.stream().anyMatch(s -> url.contains(s))) {
                    continue;
                }
                
                // If near consecutive rate limit domain, wait.
                try {
                    tryDomain = new URL(url).getHost();
                } 
                catch (Exception e) {
                    // Also precheck malformed url before download.
//                    LOGGER.log(Level.SEVERE, "MalformedURLException in precheck: " + e.toString() + "\n" + "\t" + "Paper ID: " + paperId + ". URL: " + url);
                    continue;
                }
                String domain = tryDomain;
                if (rateLimitDomain != null && rateLimitDomain.stream().anyMatch(s -> url.contains(s)) 
                        && domain != null && recentDomains.stream().anyMatch(s -> domain.equals(s))) {
                    Thread.sleep(waitingSecond * 1000);
                }
                if (maxConsecutiveCheck > 0 && recentDomains.size() == maxConsecutiveCheck) {
                    recentDomains.remove(0);
                }
                recentDomains.add(domain);
                
                String filePath = dirPathOutput + File.separator + paperId + ".pdf";
                String tempFilePath = tempDirPathOutput + File.separator + paperId + ".pdf";

                // Download.
                downloadFile(paperId, url, filePath, tempFilePath, mimeType, connectionTimeout, readTimeout, forbiddenDomain);
            }
        }
            
        LOGGER.log(Level.INFO, "Download Finish. Processed line: " + (count - 1));
    }
    
    /**
     * Downloads a file from a URL using java HttpURLConnection.
     * 
     * @param id the paper id corresponding to this url, use to check while downloaded paper because of parallel.
     * @param url HTTP URL of the file to be downloaded
     * @param filePath path to save the file
     * @param tempFilePath
     * @param fileType
     * @param connectionTimeout millisecond connect timeout.
     * @param readTimeout millisecond read data from source timeout.
     * @param forbiddenDomain
     */
    public static void downloadFile(String id, String url, String filePath, String tempFilePath, List<String> fileType, int connectionTimeout, int readTimeout, List<String> forbiddenDomain) {
        // Check , before doing anything like opening connection.
        if (downloadedPaperId.contains(id)) {
            return;
        }
            
        HttpURLConnection httpConn = null;
        try {
            List<Integer> errorCodeToWatch = Arrays.asList(403, 429);
            httpConn = InternetUtility.getRedirectOKHttpConn(url, forbiddenDomain, connectionTimeout, readTimeout, 3, 0, LOGGER_MISC, errorCodeToWatch);
            
            // Connection not null means OK.
            if (httpConn != null) {
                // Check content type: pdf.
                String contentType = httpConn.getContentType();
                if (contentType == null) {
                    return;
                }
                if (fileType == null || fileType.stream().anyMatch(s -> contentType.startsWith(s))) {
                    // Manually download, reuse Connection to reduce blocking.
                    try (InputStream inputStream = httpConn.getInputStream()) {
                        // FIFO: Before writing, always check avoid conflict between different download process. If exists, skip.
                        if (!downloadedPaperId.contains(id)) {
                            downloadedPaperId.add(id);
                            // The logic ensure that parent dirs have been created.
                            try (FileOutputStream outputStream = new FileOutputStream(tempFilePath)) {
                                int bytesRead = -1;
                                byte[] buffer = new byte[4096];
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                                FileUtils.moveFile(new File(tempFilePath), new File(filePath));
                            }
                        }
                    }
                } else {
//                    LOGGER.log(Level.SEVERE, "Error: No matching mime-type. Server replied mime-type: " + contentType + "!");
//                    LOGGER.log(Level.SEVERE, "Paper ID: " + id + ". URL: " + url);
                }
            }
        } catch (FileExistsException e) {   
            // Apache io move file failed.
            LOGGER.log(Level.SEVERE, "Exception: FileExistsException: Apache io move file failed." + "\n" + "\t" + "Paper ID: " + id + ". URL: " + url);
            FileUtils.deleteQuietly(new File(tempFilePath));
//            e.printStackTrace();
        } catch (MalformedURLException e) { 
            // new URL() failed
//            LOGGER.log(Level.SEVERE, "MalformedURLException in downloadFile: " + e.toString() + "\n" + "\t" + "Paper ID: " + id + ". URL: " + url);
        } catch (IOException e) {   
            // openConnection() failed
            LOGGER.log(Level.SEVERE, "IOException in downloadFile: " + e.toString() + "\n" + "\t" + "Paper ID: " + id + ". URL: " + url);
        } catch (Exception e) {   
            // other exception
            LOGGER.log(Level.SEVERE, "Exception (Other) in downloadFile: " + e.toString() + "\n" + "\t" + "Paper ID: " + id + ". URL: " + url, e);
        } finally {
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
    }

    /**
     * Fix some common url error.
     * 
     * @param url
     * @return 
     */
    public static String fixUrl(String url) {
        if (url.startsWith("/pmc/articles/")) {
            return "https://www.ncbi.nlm.nih.gov" + url;
        }
        if (!url.startsWith("http") && !url.startsWith("/")) {
            return "http://" + url;
        }
        if (url.startsWith("//")) {
            return "http:" + url;
        }
        if (url.startsWith("://")) {
            return "http" + url;
        }
        if (url.startsWith("http:/cyberleninka.ru/")) {
            return url.replaceFirst("/", "//");
        }
        return url;
    }

    /**
     * Find the last line number of the downloaded pdf.
     * 
     * @param urlListFilePath
     * @param dirPathOutput
     * @return
     * @throws Exception 
     */
    private static long getSkipLineNum(String urlListFilePath, String dirPathOutput) throws Exception {
        long count = 0;
        long skipLine = 0;
        
        // Independent hash set.
        HashSet<String> hashSet = new HashSet();
        getDownloadedPaperIds(dirPathOutput, hashSet);

        // Read URL list.
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(urlListFilePath), "UTF-8"))) {
            // Read line.
            String line;
            while ((line = reader.readLine()) != null) {
                count++;
                if (line.isEmpty()) {
                    continue;
                }
                // Parse line, get paper id.
                String paperId = line.split("\t")[0];
                                
                // If the paper has been downloaded, skip.
                if (hashSet.contains(paperId)) {
                    skipLine = count;
                }
            }
        }
        LOGGER.log(Level.INFO, "Auto skip line: " + skipLine);
        return skipLine;
    }

    /**
     * Get a set of paper id of pdf files in a dir.
     * 
     * @param dirPathOutput
     * @param set
     * @throws Exception 
     */
    private static void getDownloadedPaperIds(String dirPathOutput, Set<String> set) throws Exception {
        List<String> listFilePaths = FileUtility.getAllFilePaths(dirPathOutput, Arrays.asList(".pdf"));
        for (String path : listFilePaths) {
            String paperId = path.substring(path.length() - 12, path.length() - 4);
            set.add(paperId);
        }
    }
    
    /**
     * For future use.
     * 
     * @param doneIdFilePath: also accept base dir path.
     * @param set
     * @throws Exception 
     */
    private static void readDonePaperIds(String doneIdFilePath, Set<String> set) throws Exception {
        if (new File(doneIdFilePath).isDirectory()) { // baseDir.
            doneIdFilePath = doneIdFilePath + File.separator + "DonePaperIds.txt";
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(doneIdFilePath), "UTF-8"))) {
            // Read line.
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                set.add(line);
            }
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.WARNING, "File not found: DonePaperIds.txt");
        }
    }

    /**
     * For future use.
     * 
     * @param set
     * @param doneIdFilePath: also accept base dir path.
     * @throws Exception 
     */
    private static void writeDonePaperIds(Set<String> set, String doneIdFilePath) throws Exception {
        if (new File(doneIdFilePath).isDirectory()) { // baseDir.
            doneIdFilePath = doneIdFilePath + File.separator + "DonePaperIds.txt";
        }
        FileUtility.checkToCreateParentDir(doneIdFilePath);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(doneIdFilePath, false), "UTF-8"))) {
            for (String s : set) {
                writer.write(s + "\n");
            }
        }
    }

    /**
     * args[0] String, base dir. Default: empty string "", means using the value in code, which defaults to local.
     * args[1] int, switch functionality. Default: 1, means only update done list.
     * 1: update done paper id list. 
     * 2: continue downloading. 
     * 3: delete pdf based on done paper id list file. Be careful! Do not delete after updating done paper id list file.
     * args[2] int, thread pool size, >= 0. Default: 10, 0 means auto specify.
     * args[3] long, from line, >= 0. Default: 1, 0 means auto skip line + 1.
     * args[4] long, to line, >= 0. Default: 0, 0 means to the end.
     * 
     * @param args 
     */
    public static void main(String[] args) {
        String baseDir;
        if (args != null && args.length >= 1 && !args[0].isEmpty()) {
            baseDir = args[0];
        } else {
            // Local.
            baseDir = "/Users/mac/Downloads";
            // 125.
//            baseDir = "E:\\NghiepTHWorking\\Data\\MAG\\TestPDF";
            // CPS.
//            baseDir = "/mnt/storage/private/nghiep/Data/MAG";
        }
        String urlListFilePath = baseDir + File.separator + "PaperUrls.txt";
        String dirPathOutput = baseDir + File.separator + "PDF";
        String tempDirPathOutput = baseDir + File.separator + "PDFTemp";

        boolean overwrite = false;

        // Try to filter out all databases, only download from author's homepage.
        List<String> forbiddenDomain = Arrays.asList(
                // Supscription.
                "acm.org", "ieee.org", "springer.com", 
                "elsevier.com", "scopus.com", "cell.com", "elsevierhealth.com", "sciencedirect.com", 
                "sagepub.com", "wiley.com", "tandfonline.com", "thelancet.com", "pnas.org", 
                "nature.com", "sciencemag.org", 
                "thomsonreuters.com", 
                // Database.
                "core.ac.uk", "arxiv.org", "citeseerx.ist.psu.edu", "nlm.nih.gov", 
                "nii.ac.jp", "jst.go.jp", "researchmap.jp", "ndl.go.jp", "nichigai.co.jp",
                "dbpia.co.kr", 
                // Open.
                "plos.org", "cambridge.org", "deepdyve.com", 
                // Other database.
                "researchgate.net", "academia.edu", 
                // Other service.
                "worldcat.org", "dx.doi.org", "jstor.org", "google.com", "harvard.edu/abs", 
                // 403: uninvited.
                "iopscience.iop.org", "freepatentsonline.com", "jvascsurg.org", "eblue.org", "www.jaad.org", 
                "scielo.br", "cyberleninka.ru", "jamanetwork.com", "redalyc.org", "europepmc.org", 
                // Broad.
                ".jp"
        );
        // Also check these: 
        // http://guides.library.yale.edu/ealdatabases/japan-databases
        // http://guides.is.uwa.edu.au/japanese
        // http://subjects.library.manchester.ac.uk/japanesestudies/databases/
        // http://www.wul.waseda.ac.jp/research-navi/find_journals-articles-e.html
        // http://www.sophia.ac.jp/eng/research/library/search/Database-Search

        List<String> rateLimitDomain = null;
        int waitingSecond = 60;
        int maxConsecutiveCheck = 10;
        
        // Millisecond.
        int connectionTimeout = 60000;
        int readTimeout = 60000;
        
        try {
            // Format log on 1 line.
            System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %2$s %5$s%6$s%n");
            // LOGGER to console and file, 1 line.
            Handler handler = new FileHandler(baseDir + File.separator + MAGPDFDownloader.class.getName() + ".log");
            handler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(handler);
            // LOGGER to file only, 1 line.
            LOGGER_MISC.setUseParentHandlers(false);
            handler = new FileHandler(baseDir + File.separator + MAGPDFDownloader.class.getName() + "_misc.log");
            handler.setFormatter(new SimpleFormatter());
            LOGGER_MISC.addHandler(handler);
            
            LOGGER.log(Level.INFO, "Start main(.)");

            int functionality;
            if (args != null && args.length >= 2 && NumericUtility.isInteger(args[1]) && Integer.parseInt(args[1]) >= 1) {
                functionality = Integer.parseInt(args[1]);
            } else {
//                functionality = 1;
                functionality = 1;
            }
            
            // Update done paper id list.
            if (functionality == 1) {
                downloadedPaperId.clear();
                readDonePaperIds(baseDir, downloadedPaperId);
                getDownloadedPaperIds(dirPathOutput, downloadedPaperId);
                writeDonePaperIds(downloadedPaperId, baseDir);
                return;
            }

            // Continue downloading.
            if (functionality == 2) {
                int threadPoolSize;
                if (args != null && args.length >= 3 && NumericUtility.isInteger(args[2]) && Integer.parseInt(args[2]) >= 0) {
                    threadPoolSize = Integer.parseInt(args[2]);
                } else {
                    // Local.
                    threadPoolSize = 10;
                    // CPS, 125.
//                    threadPoolSize = 1000;
                }
                long fromLineNum;
                if (args != null && args.length >= 4 && NumericUtility.isInteger(args[3]) && Integer.parseInt(args[3]) >= 0) {
                    fromLineNum = Integer.parseInt(args[3]);
                } else {
//                    fromLineNum = 1;
                    fromLineNum = 1;
                }
                if (fromLineNum == 0) {
                    fromLineNum = getSkipLineNum(urlListFilePath, dirPathOutput) + 1;
                }
                long toLineNum;
                if (args != null && args.length >= 5 && NumericUtility.isInteger(args[4]) && Integer.parseInt(args[4]) >= 0) {
                    toLineNum = Integer.parseInt(args[4]);
                } else {
//                    toLineNum = 7000;
                    toLineNum = 0;
                }

                LOGGER.log(Level.INFO, "Thread pool size: " + threadPoolSize);
                LOGGER.log(Level.INFO, "From line: " + fromLineNum);
                LOGGER.log(Level.INFO, "To line: " + toLineNum);

                // Read downloaded paper id.
                downloadedPaperId.clear();
                readDonePaperIds(baseDir, downloadedPaperId);
                getDownloadedPaperIds(dirPathOutput, downloadedPaperId);

//                downloadPDFMAG(urlListFilePath, dirPathOutput, tempDirPathOutput, overwrite, forbiddenDomain, rateLimitDomain, waitingSecond, maxConsecutiveCheck, connectionTimeout, readTimeout, fromLineNum, toLineNum);
                downloadPDFMAGParallel(urlListFilePath, dirPathOutput, tempDirPathOutput, overwrite, forbiddenDomain, rateLimitDomain, waitingSecond, maxConsecutiveCheck, connectionTimeout, readTimeout, threadPoolSize, fromLineNum, toLineNum);

                // Output done paper id list.
                downloadedPaperId.clear();
                readDonePaperIds(baseDir, downloadedPaperId);
                getDownloadedPaperIds(dirPathOutput, downloadedPaperId);
                writeDonePaperIds(downloadedPaperId, baseDir);
                
                return;
            }
            
            // Delete done pdf. Be careful to run only 1 time with done paper id list from other machine.
            if (functionality == 3) {
                downloadedPaperId.clear();
                readDonePaperIds(baseDir, downloadedPaperId);
                downloadedPaperId.stream().forEach((paperId) -> {
                    FileUtils.deleteQuietly(new File(dirPathOutput + File.separator + paperId + ".pdf"));
                });
                getDownloadedPaperIds(dirPathOutput, downloadedPaperId);
                writeDonePaperIds(downloadedPaperId, baseDir);
                return;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception in main(.): " + e.toString(), e);
        } finally {
            LOGGER.log(Level.INFO, "End main(.)");
        }
    }
}

/**
 * Issues:
 * 
 * Note. There are something weird in this code??? Sometimes save bad, sometimes save without running, or after stop.
 * -> seem there are orphaned thread/leaked thread.
 *  -> many java processes found in the OS. !!!
 *      -> maybe error of maven/netbeans?
 *          => Yes, netbeans error. Solution: 
 *              1. Run debug mode in netbeans, stop using stop debug.
 *              2. Run command line. e.g.: java -cp /Users/mac/NetBeansProjects/TextUtility/target/TextUtility-1.0-SNAPSHOT.jar thn.research.textutility.datasource.MAGPDFDownloader
 * 
 * - Parallel, based on file check. OK.
 * 
 * - Reuse connection: => manually download: OK.
 * - Why not folder: => FileUtils error. OK.
 * 
 * - Check each exception: avoid mistake. 
 *      Esp: application/pdf;charset=UTF-8: OK.
 *      http response.
 *          301, 302, 303: redirect.
 *              java auto follow redirect but not when switch to https: need to handle explicitly: OK.
 *          403: forbidden.
 *              1 reason for 403: user agent: OK.
 *          429: too many request.
 *              researchgate.net: seem because of request to get redirect url.
 *                  -> avoid using getfinalhttpconn().
 *              other: try waiting before access. Note that have to wait sequentially. So it also blocks other sites.
 *              -> at this time, forbid researchgate (about 40 mil url).
 *          503: (temporary) unavailable service.
 * 
 * - Check download from payment server.
 *      Yes: Most of the time cannot download pdf from payment server in cps01. But occasionally it can: 
 *      http://link.springer.com/content/pdf/10.1007%2F978-3-662-45136-6_8.pdf
 *      http://onlinelibrary.wiley.com/doi/10.1034/j.1601-0825.2003.02920.x/full
 *          Some fulltext are in html, not pdf.
 *      -> use a forbidden list of domain, if url contain the item in this list: stop. 
 *          already blocked major domains. => need a complete list.
 * - Fail to download from arxiv. 301: move not follow. 403: forbidden.
 *      -> fixed 301, fix 403: OK.
 * - Download paper vs slide. !!!
 * 
 * 
 * New issues:
 * - Parallel: halt.
 *      seem error because error in array index when computing consecutive domain. => change to using URL#getHost(). OK.
 * - redirect url: relative url. (ref: http://stackoverflow.com/questions/1884230/urlconnection-doesnt-follow-redirect)
 *      check and reconstruct using URL#getHost(). OK.
 * 
 * Todo:
 * - 2 stages download: all threads download to /temp, when finish move file to /PDF. 
 *      So 
 *          When check before download: all file in /PDF and /temp.
 *          When check while download: all file in /temp.
 *      Pro: when interrupted: only need to delete /temp to delete partially downloaded files.
 *      Con: 1 case of error: 
 *          thread1 check before download ok
 *          thread2 check before download ok
 *          thread2 check while download in /temp ok
 *          thread2 save file to /temp
 *          thread2 move file to /pdf
 *          thread1 check while download in /temp ok
 *          thread1 save file to /temp
 *          thread1 move file to /pdf
 *      : conflict.
 *      -> check both dir while download.
 *      Caution: may leak in /temp if error while moving. Need to check to reset /temp.
 *      OK.
 *          Auto maintenance: delete /temp when rerun, guarantee finished download and clean partial download.
 * - Many url from https://www.ncbi.nlm.nih.gov is missing host. Check url start withs "/pmc/articles/" and add. OK
 * - listFilePaths.contains(): Linear may be very slow. Change to Set. OK.
 * - Log to file, not print to system output. sout slow down the program. OK.
 * - Rerun skip urls that are tried before: skip all line bofore the last downloaded paper.
 *      -> later, now the main problem is that the pdf collection is too big, cannot do it anyway. 
 *          May need to process gradually, or integrate core data first.
 *  => cannot just rerun: maybe need to get the last paper id one time from the downloaded pdf???
 * 
 * ok ok, there are too many issues. This is just toy code, not scalable.
 *      => Now just do some simple line counting and log the progress, then tag it and do a rewrite with the new architecture.
 *          => New architecture: 
 *              1. only crawl from distributed source (author homepage, avoid database site). Most major.
 *              2. synced hashset check while download, replace file system check. OK
 * 
 * Toward a complete MAG dataset:
 * Because papers are from many sources and at many machine, need to maintain a file containing list of donePaperId. OK.
 * 
 * Note:
 * => Had better getting fulltext from core, arxiv, citeseer before downloading pdf.
 * - Using other machine is bad, transferring is very bad.
 *      -> better solution is acquiring the accurate list of subscription sites to avoid, and run only on cps.
 *          => next week ask for the list.
 *          => also update forbidden list based on 403 and 429 log.
 *          => then may rerun from start for a clean data, it's pretty quick.
 */