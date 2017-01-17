/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.datasource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
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
     * @param skipLineNum
     * @throws Exception 
     */
    public static void downloadPDFMAGParallel(String urlListFilePath, String dirPathOutput, String tempDirPathOutput, boolean overwrite, List<String> forbiddenDomain, List<String> rateLimitDomain, int waitingSecond, int maxConsecutiveCheck, int connectionTimeout, int readTimeout, int threadPoolSize, long skipLineNum) throws Exception {
        LOGGER.log(Level.INFO, "Download Start.");
        
        // Thread pool.
        ExecutorService executor;
        if (threadPoolSize == 0) {
            executor = Executors.newFixedThreadPool(GeneralUtility.getNumOfCore() - 1);
        } else {
            executor = Executors.newFixedThreadPool(threadPoolSize);
        }

        List<String> mimeType = Arrays.asList("application/pdf", "application/x-pdf");

        // Get downloaded paper id.
        if (overwrite) {
            FileUtils.deleteQuietly(new File(dirPathOutput));
        } else {
            // Get downloaded paper id.
            List<String> listFilePaths = FileUtility.getAllFilePaths(dirPathOutput, Arrays.asList(".pdf"));
            for (String path : listFilePaths) {
                String paperId = path.substring(path.length() - 12, path.length() - 4);
                downloadedPaperId.add(paperId);
            }
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
                if (count % 1000000 == 0) {
//                    System.out.println("");
//                    System.out.println("Processed line: " + count);
                    LOGGER.log(Level.INFO, "Processed line: " + count);
                }
                if (count <= skipLineNum) {
                    continue;
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
//                    System.out.println("");
//                    System.out.println("Exception: new URL() failed.");
//                    System.out.println("URL: " + url);
//                    System.out.println("Paper ID: " + paperId);
//                    e.printStackTrace();
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
                executor.submit(() -> {
                    try {
                        downloadFile(url, paperId, filePath, tempFilePath, mimeType, connectionTimeout, readTimeout, forbiddenDomain);
                    } catch (Exception ex) {
//                        System.out.println("");
//                        System.out.println("Exception in executor submit.");
//                        ex.printStackTrace();
                        LOGGER.log(Level.SEVERE, "Exception in executor submit. " + ex.toString(), ex);
                    }
                });
            }

            executor.shutdown();
            while (!executor.isTerminated()) {
            }
        }

//        System.out.println("");
//        System.out.println("Download Finish.");
        LOGGER.log(Level.INFO, "Download Finish. Processed line: " + count);
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
     * @param skipLineNum
     * @throws Exception 
     */
    public static void downloadPDFMAG(String urlListFilePath, String dirPathOutput, String tempDirPathOutput, boolean overwrite, List<String> forbiddenDomain, List<String> rateLimitDomain, int waitingSecond, int maxConsecutiveCheck, int connectionTimeout, int readTimeout, long skipLineNum) throws Exception {
        LOGGER.log(Level.INFO, "Download Start.");
        
        List<String> mimeType = Arrays.asList("application/pdf", "application/x-pdf");

        // Get downloaded paper id.
        if (overwrite) {
            FileUtils.deleteQuietly(new File(dirPathOutput));
        } else {
            // Get downloaded paper id.
            List<String> listFilePaths = FileUtility.getAllFilePaths(dirPathOutput, Arrays.asList(".pdf"));
            for (String path : listFilePaths) {
                String paperId = path.substring(path.length() - 12, path.length() - 4);
                downloadedPaperId.add(paperId);
            }
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
                if (count % 1000000 == 0) {
//                    System.out.println("");
//                    System.out.println("Processed line: " + count);
                    LOGGER.log(Level.INFO, "Processed line: " + count);
                }
                if (count <= skipLineNum) {
                    continue;
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
//                    System.out.println("");
//                    System.out.println("Exception: new URL() failed.");
//                    System.out.println("URL: " + url);
//                    System.out.println("Paper ID: " + paperId);
//                    e.printStackTrace();
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
                downloadFile(url, paperId, filePath, tempFilePath, mimeType, connectionTimeout, readTimeout, forbiddenDomain);
            }
        }
            
//        System.out.println("");
//        System.out.println("Download Finish.");
        LOGGER.log(Level.INFO, "Download Finish. Processed line: " + count);
    }
    
    /**
     * Downloads a file from a URL using java HttpURLConnection.
     * @param url HTTP URL of the file to be downloaded
     * @param id the paper id corresponding to this url, use to check downloaded paper.
     * @param filePath path to save the file
     * @param tempFilePath
     * @param fileType
     * @param connectionTimeout millisecond connect timeout.
     * @param readTimeout millisecond read data from source timeout.
     * @param forbiddenDomain
     * @throws IOException
     */
    public static void downloadFile(String url, String id, String filePath, String tempFilePath, List<String> fileType, int connectionTimeout, int readTimeout, List<String> forbiddenDomain) throws Exception {
        HttpURLConnection httpConn = null;
        try {
            if (downloadedPaperId.contains(id)) {
                return;
            }
            url = InternetUtility.getFinalRedirectURL(url, connectionTimeout, readTimeout, 10, 0, LOGGER_MISC);
            // Final url may be null.
            if (url == null) {
                return;
            }
            String parallelUrl = url;
            // If url is in forbidden list, pass.
            if (forbiddenDomain != null && forbiddenDomain.stream().anyMatch(s -> parallelUrl.contains(s))) {
                return;
            }

            URL u = new URL(url);
            httpConn = (HttpURLConnection) u.openConnection();
            httpConn.setConnectTimeout(connectionTimeout);
            httpConn.setReadTimeout(readTimeout);
            // Avoid some 403 forbidden.
            httpConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36");
            
            // Check HTTP response code first, note that this is final redirect url, but may be blocked 403 or not found 404.
            // * Also check response code for blocking.
            int responseCode = httpConn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
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
                            // The logic ensure that parent dirs are created.
//                            FileUtility.checkToCreateParentDir(filePath);
//                            FileUtility.checkToCreateParentDir(tempFilePath);
                            try (FileOutputStream outputStream = new FileOutputStream(tempFilePath)) {
                                int bytesRead = -1;
                                byte[] buffer = new byte[4096];
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                            }
                            FileUtils.moveFile(new File(tempFilePath), new File(filePath));
                        }
                    }
                } else {
//                    System.out.println("");
//                    System.out.println("Error: No matching mime-type. Server replied mime-type: " + contentType);
//                    System.out.println("URL: " + url);
//                    System.out.println("File Path: " + filePath);
                }
            } else {
//                System.out.println("");
//                System.out.println("Error: Server replied HTTP code: " + responseCode);
//                System.out.println("URL: " + url);
//                System.out.println("File Path: " + filePath);
            }
        } catch (FileExistsException e) {   
            // Apache io move file failed.
//            System.out.println("");
//            System.out.println("Exception: FileExistsException: Apache io move file failed.");
//            System.out.println("URL: " + url);
//            System.out.println("File Path: " + filePath);
            LOGGER.log(Level.SEVERE, "Exception: FileExistsException: Apache io move file failed.");
            LOGGER.log(Level.SEVERE, "URL: " + url);
            LOGGER.log(Level.SEVERE, "File Path: " + filePath);
            FileUtils.deleteQuietly(new File(tempFilePath));
//            e.printStackTrace();
        } catch (MalformedURLException e) { 
            // new URL() failed
//            System.out.println("");
//            System.out.println("Exception: MalformedURLException: new URL() failed.");
//            System.out.println("URL: " + url);
//            System.out.println("File Path: " + filePath);
//            e.printStackTrace();
        } catch (IOException e) {   
            // openConnection() failed
//            System.out.println("");
//            System.out.println("Exception: IOException: openConnection() failed.");
//            System.out.println("URL: " + url);
//            System.out.println("File Path: " + filePath);
//            e.printStackTrace();
        } catch (Exception e) {   
            // other exception
//            System.out.println("");
//            System.out.println("Exception: Other.");
//            System.out.println("URL: " + url);
//            System.out.println("File Path: " + filePath);
//            e.printStackTrace();
            LOGGER.log(Level.SEVERE, "Exception: Other.");
            LOGGER.log(Level.SEVERE, "URL: " + url);
            LOGGER.log(Level.SEVERE, "File Path: " + filePath);
            LOGGER.log(Level.SEVERE, "Exception in executor submit. " + e.toString(), e);
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
        // Get downloaded paper id.
        HashSet<String> hashSet = new HashSet<>();
        List<String> listFilePaths = FileUtility.getAllFilePaths(dirPathOutput, Arrays.asList(".pdf"));
        for (String path : listFilePaths) {
            String paperId = path.substring(path.length() - 12, path.length() - 4);
            hashSet.add(paperId);
        }
        long count = 0;
        long skipLine = 0;

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
        LOGGER.log(Level.INFO, "Skip line: " + skipLine);
        return skipLine;
    }
    
    /**
     * args[0] int, thread pool size, >= 0. Default: 500, 0 means auto specify.
     * args[1] long, number of skip line, >= 0. Default: auto count.
     * 
     * @param args 
     */
    public static void main(String[] args) {
        // Local.
//        String urlListFilePath = "/Users/mac/Downloads/PaperUrls.txt";
//        String dirPathOutput = "/Users/mac/Downloads/PDF";
//        String tempDirPathOutput = "/Users/mac/Downloads/PDFTemp";
        // 125.
//        String urlListFilePath = "E:\\NghiepTH Working\\Data\\MAG\\TestPDF\\PaperUrls.txt";
//        String dirPathOutput = "E:\\NghiepTH Working\\Data\\MAG\\TestPDF\\PDF";
//        String tempDirPathOutput = "E:\\NghiepTH Working\\Data\\MAG\\TestPDF\\PDFTemp";
        // CPS.
        String urlListFilePath = "/mnt/storage/private/nghiep/Data/MAG/Unzip/PaperUrls.txt";
        String dirPathOutput = "/mnt/storage/private/nghiep/Data/MAG/PDF";
        String tempDirPathOutput = "/mnt/storage/private/nghiep/Data/MAG/PDFTemp";
        boolean overwrite = false;

        // Try to filter out all databases, only download from author's homepage.
        List<String> forbiddenDomain = Arrays.asList("acm.org", "ieee.org", "springer.com", 
                "elsevier.com", "scopus.com", "cell.com", "elsevierhealth.com", "sciencedirect.com", 
                "sagepub.com", "wiley.com", "tandfonline.com", "thomsonreuters.com", 
                "nature.com", "sciencemag.org", 
                "core.ac.uk", "arxiv.org", "citeseerx.ist.psu.edu", 
                "researchgate.net", "academia.edu", 
                "ci.nii.ac.jp", "dbpia.co.kr", 
                "worldcat.org", "dx.doi.org", "google.com", "harvard.edu/abs", 
                "jstor.org", "iopscience.iop.org", "freepatentsonline.com", "cambridge.org", "pnas.org", "deepdyve.com", 
                "scielo.br", "cyberleninka.ru", "jamanetwork.com", "redalyc.org", "europepmc.org", 
                ".jp");

        List<String> rateLimitDomain = null;
        int waitingSecond = 60;
        int maxConsecutiveCheck = 10;
        
        int connectionTimeout = 10000;
        int readTimeout = 10000;
        
        int threadPoolSize;
        if (args != null && args.length >= 1 && NumericUtility.isInteger(args[0]) && Integer.parseInt(args[0]) >= 0) {
            threadPoolSize = Integer.parseInt(args[0]);
        } else {
            // Local.
//            threadPoolSize = 10;
            // CPS, 125.
            threadPoolSize = 500;
        }
        
        try {
            Handler handler = new FileHandler(new File(dirPathOutput).getParent() + File.separator + MAGPDFDownloader.class.getName() + ".log");
            Logger.getLogger(MAGPDFDownloader.class.getName()).addHandler(handler);
            handler = new FileHandler(new File(dirPathOutput).getParent() + File.separator + MAGPDFDownloader.class.getName() + "_misc.log");
            Logger.getLogger(MAGPDFDownloader.class.getName() + "_misc").addHandler(handler);
            
            long skipLineNum;
            if (args != null && args.length >= 2 && NumericUtility.isInteger(args[1]) && Integer.parseInt(args[1]) >= 0) {
                skipLineNum = Integer.parseInt(args[1]);
            } else {
                skipLineNum = getSkipLineNum(urlListFilePath, dirPathOutput);
            }

//            downloadPDFMAG(urlListFilePath, dirPathOutput, tempDirPathOutput, overwrite, forbiddenDomain, rateLimitDomain, waitingSecond, maxConsecutiveCheck, connectionTimeout, readTimeout, skipLineNum);
            downloadPDFMAGParallel(urlListFilePath, dirPathOutput, tempDirPathOutput, overwrite, forbiddenDomain, rateLimitDomain, waitingSecond, maxConsecutiveCheck, connectionTimeout, readTimeout, threadPoolSize, skipLineNum);
        }
        catch (Exception e) {
            System.out.println("");
            System.out.println("Exception: main(.).");
            e.printStackTrace();
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
 *                  -> avoid using getfinalurl().
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
 *          already blocked major domains, for any missed domain, it has to block this downloader. OK.
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
 *          Auto maintain: delete /temp when rerun, guarantee finished download and clean partial download.
 * - Many url from https://www.ncbi.nlm.nih.gov is missing host. Check url start withs "/pmc/articles/" and add. OK
 * - listFilePaths.contains(): Linear may be very slow. Change to Set. OK.
 * - Log to file, not print to system output. sout slow down the program.
 * - Rerun skip urls that are tried before: skip all line bofore the last downloaded paper.
 *      -> later, now the main problem is that the pdf collection is too big, cannot do it anyway. 
 *          May need to process gradually, or integrate core data first.
 *  => cannot rerun: maybe need to get the last paper id one time from the downloaded pdf.
 * 
 * ok ok, there are too many issues. This is just toy code, not scalable.
 *      => Now just do some simple line counting and log the progress, then tag it and do a rewrite with the new architecture.
 *          => New architecture: 
 *              1. only crawl from distributed source (author homepage, avoid database site)
 *              2. synced hashset check while download, replace file system check.
 */