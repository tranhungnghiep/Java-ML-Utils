/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.datasource;

import com.sun.javafx.scene.control.skin.VirtualFlow;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileExistsException;
import org.apache.commons.io.FileUtils;
import thn.research.textutility.general.GeneralUtility;
import thn.research.textutility.general.InternetUtility;
import thn.research.textutility.io.FileUtility;

/**
 * This class will download pdf files based on the url list in MAG dataset. 
 * Note that the url list is not clean: 
 - Link to abstract, pdf, other file types. 
 - Link to pdf may need to be resolved before download: not direct pdf. 
 - Many pdf may be in a server, e.g., citeseerx/arxiv: consecutive download may be blocked:
 check if blocked, put to wait list for 1 thread to handle consecutively. 
 - May need to use selenium for javascript resolve? 
 - 1 more problem: there may be many pdf file of 1 paper: full paper/presentation/other 
 - 1 more problem: may accidentally download pdf from payment source like IEEE, ACM... when
 connected from NII server: banned. Check download using wget on server to avoid (list of domain).

 Note that have to keep track of paper: using paper id by MAG.

 Critical problem: seem a lot of urls are invalid, cannot get pdf. So the
 estimation is far off, real value may be just 1 mil. -> if this is the case:
 need to use data from core as main source.

 Outline of the program: 
 - parallel thread pool. 
 - Read url list file line by line. 
 - for each line, let a thread handle it: check if it is pdf, try
 downloading, save as pdf, or put to wait list and let 1 thread to handle it. 
 - Note that: pdf of 1 paper id is downloaded only 1 time: if a pdf is successfully saved, stop other thread
 with same paper id. 
 - save in hierarchical directory: each folder has 100
 subfolder: A1-> A100, B1-> B100, C1-> C100, each subfolder Ci has 100 pdf.
 Depth first search, number of subfolder A depends on data, may be 10 mil pdf, or 1 mil?

 Important technical problem: 
 - sanity check link. 
 - avoid duplication. 
 - avoid blocking. 
 - sync all these: process in parallel.
 
 But simple work flow first.
 - read url.
 - parse.
 - download not parallel.
 - save in 1 folder.
 *
 * @author THNghiep
 */
public class MAGPDFDownloader {

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
     * @throws Exception 
     */
    public static void downloadPDFMAGParallel(String urlListFilePath, String dirPathOutput, String tempDirPathOutput, boolean overwrite, List<String> forbiddenDomain, List<String> rateLimitDomain, int waitingSecond, int maxConsecutiveCheck, int connectionTimeout, int readTimeout, int threadPoolSize) throws Exception {
        // Thread pool.
        ExecutorService executor;
        if (threadPoolSize == 0) {
            executor = Executors.newFixedThreadPool(GeneralUtility.getNumOfCore() - 1);
        } else {
            executor = Executors.newFixedThreadPool(threadPoolSize);
        }

        List<String> mimeType = Arrays.asList("application/pdf", "application/x-pdf");

        List<String> listFilePaths = null;
        if (overwrite) {
            FileUtils.deleteQuietly(new File(dirPathOutput));
        } else {
            // Get downloaded paper id.
            listFilePaths = FileUtility.getAllFilePaths(dirPathOutput, Arrays.asList(".pdf"));
        }
        // Always delete temp dir.
        FileUtils.deleteQuietly(new File(tempDirPathOutput));

        FileUtility.checkToCreateDir(dirPathOutput);
        FileUtility.checkToCreateDir(tempDirPathOutput);

        // Read URL list.
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(urlListFilePath), "UTF-8"))) {
            
            List<String> recentDomains = new ArrayList<>();
            
            // Read line.
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                // Parse line, get paper id and url.
                String[] parsedLine = line.split("\t");
                String paperId = parsedLine[0];
                String rawUrl = parsedLine[1];
                
                String url = fixUrl(rawUrl);
                
                // If url is in forbidden list, pass.
                if (forbiddenDomain != null && forbiddenDomain.stream().anyMatch(s -> url.contains(s))) {
                    continue;
                }
                
                // If the paper has been downloaded, pass.
                String filePath = dirPathOutput + File.separator + paperId + ".pdf";
                String tempFilePath = tempDirPathOutput + File.separator + paperId + ".pdf";
                if (!overwrite && listFilePaths.contains(filePath)) {
                    continue;
                }

                // If near consecutive rate limit domain, wait.
                String tempDomain = null;
                try {
                    tempDomain = new URL(url).getHost();
                } 
                catch (Exception e) {
                    // Also precheck malformed url before download.
                    System.out.println("");
                    System.out.println("Exception: new URL() failed.");
                    System.out.println("URL: " + url);
                    System.out.println("Paper ID: " + paperId);
//                    e.printStackTrace();
                    continue;
                }
                String domain = tempDomain;
                if (rateLimitDomain != null && rateLimitDomain.stream().anyMatch(s -> url.contains(s)) 
                        && domain != null && recentDomains.stream().anyMatch(s -> domain.equals(s))) {
                    Thread.sleep(waitingSecond * 1000);
                }
                if (maxConsecutiveCheck > 0 && recentDomains.size() == maxConsecutiveCheck) {
                    recentDomains.remove(0);
                }
                recentDomains.add(domain);

                // Download in parallel.
                executor.submit(() -> {
                    try {
                        downloadFile(url, filePath, tempFilePath, mimeType, connectionTimeout, readTimeout);
                    } catch (Exception ex) {
                        System.out.println("");
                        System.out.println("Exception in executor submit.");
                        ex.printStackTrace();
                    }
                });
            }

            executor.shutdown();
            while (!executor.isTerminated()) {
            }
        }
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
     * @throws Exception 
     */
    public static void downloadPDFMAG(String urlListFilePath, String dirPathOutput, String tempDirPathOutput, boolean overwrite, List<String> forbiddenDomain, List<String> rateLimitDomain, int waitingSecond, int maxConsecutiveCheck, int connectionTimeout, int readTimeout) throws Exception {
        List<String> mimeType = Arrays.asList("application/pdf", "application/x-pdf");

        List<String> listFilePaths = null;
        if (overwrite) {
            FileUtils.deleteQuietly(new File(dirPathOutput));
        } else {
            // Get downloaded paper id.
            listFilePaths = FileUtility.getAllFilePaths(dirPathOutput, Arrays.asList(".pdf"));
        }
        // Always delete temp dir.
        FileUtils.deleteQuietly(new File(tempDirPathOutput));

        FileUtility.checkToCreateDir(dirPathOutput);
        FileUtility.checkToCreateDir(tempDirPathOutput);

        // Read URL list.
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(urlListFilePath), "UTF-8"))) {
            
            List<String> recentDomains = new ArrayList<>();
            
            // Read line.
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                // Parse line, get paper id and url.
                String[] parsedLine = line.split("\t");
                String paperId = parsedLine[0];
                String rawUrl = parsedLine[1];
                
                String url = fixUrl(rawUrl);
                
                // If the paper has been downloaded, pass.
                String filePath = dirPathOutput + File.separator + paperId + ".pdf";
                String tempFilePath = tempDirPathOutput + File.separator + paperId + ".pdf";
                if (!overwrite && listFilePaths.contains(filePath)) {
                    continue;
                }
                
                // If url is in forbidden list, pass.
                if (forbiddenDomain != null && forbiddenDomain.stream().anyMatch(s -> url.contains(s))) {
                    continue;
                }
                
                // If near consecutive rate limit domain, wait.
                String tempDomain = null;
                try {
                    tempDomain = new URL(url).getHost();
                } 
                catch (Exception e) {
                    // Also precheck malformed url before download.
                    System.out.println("");
                    System.out.println("Exception: new URL() failed.");
                    System.out.println("URL: " + url);
                    System.out.println("Paper ID: " + paperId);
//                    e.printStackTrace();
                    continue;
                }
                String domain = tempDomain;
                if (rateLimitDomain != null && rateLimitDomain.stream().anyMatch(s -> url.contains(s)) 
                        && domain != null && recentDomains.stream().anyMatch(s -> domain.equals(s))) {
                    Thread.sleep(waitingSecond * 1000);
                }
                if (maxConsecutiveCheck > 0 && recentDomains.size() == maxConsecutiveCheck) {
                    recentDomains.remove(0);
                }
                recentDomains.add(domain);
                
                // Download.
                downloadFile(url, filePath, tempFilePath, mimeType, connectionTimeout, readTimeout);
            }
        }
    }
    
    /**
     * Downloads a file from a URL using java HttpURLConnection.
     * @param url HTTP URL of the file to be downloaded
     * @param filePath path to save the file
     * @param tempFilePath
     * @param fileType
     * @param connectionTimeout millisecond connect timeout.
     * @param readTimeout millisecond read data from source timeout.
     * @throws IOException
     */
    public static void downloadFile(String url, String filePath, String tempFilePath, List<String> fileType, int connectionTimeout, int readTimeout) throws Exception {
        try {
            url = InternetUtility.getFinalRedirectURL(url, connectionTimeout, readTimeout);

            URL u = new URL(url);
            HttpURLConnection httpConn = (HttpURLConnection) u.openConnection();
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
                if (fileType == null || fileType.stream().anyMatch(s -> contentType.startsWith(s))) {
                    
                    // Manually download, reuse Connection to reduce blocking.
                    try (InputStream inputStream = httpConn.getInputStream()) {
                        // FIFO: Before writing, always check avoid conflict between different download process. If exists, skip.
                        if (new File(filePath).exists() || new File(tempFilePath).exists()) {
                        } else {
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
                    
                    // Using Apache common io. Auto create parent dir. Need to manually check overwrite, pdf... before downloadPDF.
                    // Error: currently not recognizing separator between dir PDF and file name.
//                    if (!new File(filePath).exists()) { 
//                        // Always check overwrite: FIFO: To avoid conflict between different download process.
//                        FileUtils.copyURLToFile(new URL(url), new File(filePath), connectionTimeout, readTimeout);
//                    }
//                } else {
//                    System.out.println("");
//                    System.out.println("Error: No matching mime-type. Server replied mime-type: " + contentType);
//                    System.out.println("URL: " + url);
//                    System.out.println("File Path: " + filePath);
                }

            } else {
                System.out.println("");
                System.out.println("Error: Server replied HTTP code: " + responseCode);
                System.out.println("URL: " + url);
                System.out.println("File Path: " + filePath);
            }
            httpConn.disconnect();
        }
        catch (FileExistsException e) {   
            // Apache io move file failed.
            System.out.println("");
            System.out.println("Exception: FileExistsException: Apache io move file failed.");
            System.out.println("URL: " + url);
            System.out.println("File Path: " + filePath);
            FileUtils.deleteQuietly(new File(tempFilePath));
//            e.printStackTrace();
        }
        catch (MalformedURLException e) { 
            // new URL() failed
            System.out.println("");
            System.out.println("Exception: MalformedURLException: new URL() failed.");
            System.out.println("URL: " + url);
            System.out.println("File Path: " + filePath);
//            e.printStackTrace();
        } 
        catch (IOException e) {   
            // openConnection() failed
            System.out.println("");
            System.out.println("Exception: IOException: openConnection() failed.");
            System.out.println("URL: " + url);
            System.out.println("File Path: " + filePath);
//            e.printStackTrace();
        }
        catch (Exception e) {   
            // other exception
            System.out.println("");
            System.out.println("Exception: other.");
            System.out.println("URL: " + url);
            System.out.println("File Path: " + filePath);
//            e.printStackTrace();
        }
    }

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
        return url;
    }
    
    public static void main(String[] args) {
//        String urlListFilePath = "/Users/mac/Downloads/PaperUrls.txt";
//        String dirPathOutput = "/Users/mac/Downloads/PDF";
//        String tempDirPathOutput = "/Users/mac/Downloads/PDFTemp";
        String urlListFilePath = "/mnt/storage/private/nghiep/Data/MAG/Unzip/PaperUrls.txt";
        String dirPathOutput = "/mnt/storage/private/nghiep/Data/MAG/PDF";
        String tempDirPathOutput = "/mnt/storage/private/nghiep/Data/MAG/PDFTemp";
        boolean overwrite = false;

        List<String> forbiddenDomain = Arrays.asList("acm.org", "ieee.org", "springer.com", "wiley.com", "sciencedirect.com", "elsevier.com", "scopus.com", "thomsonreuters.com", "nature.com", "sciencemag.org", "tandfonline.com", "researchgate.net", "arxiv.org/abs", "harvard.edu/abs");

        List<String> rateLimitDomain = null;
        int waitingSecond = 60;
        int maxConsecutiveCheck = 10;
        
        int connectionTimeout = 10000;
        int readTimeout = 10000;
        
//        int threadPoolSize = 10;
        int threadPoolSize = 500;
        
        try {
//            downloadPDFMAG(urlListFilePath, dirPathOutput, tempDirPathOutput, overwrite, forbiddenDomain, rateLimitDomain, waitingSecond, maxConsecutiveCheck, connectionTimeout, readTimeout);
            downloadPDFMAGParallel(urlListFilePath, dirPathOutput, tempDirPathOutput, overwrite, forbiddenDomain, rateLimitDomain, waitingSecond, maxConsecutiveCheck, connectionTimeout, readTimeout, threadPoolSize);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/**
 * Issues:
 * 
 * Note. There are something weird in this code??? Sometimes save bad, sometimes save without running, or after stop.
 * -> seem there are orphaned thread/leaked thread.
 * -> many java threads found in the OS. !!!
 *  -> maybe error of maven/netbeans?
 *      => Yes, netbeans error. Solution: 
 *          1. Run debug mode in netbeans, stop using stop debug.
 *          2. Run command line. e.g.: java -cp /Users/mac/NetBeansProjects/TextUtility/target/TextUtility-1.0-SNAPSHOT.jar thn.research.textutility.datasource.MAGPDFDownloader
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
 */