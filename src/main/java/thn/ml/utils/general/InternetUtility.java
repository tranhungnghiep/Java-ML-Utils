/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.general;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mac
 */
public class InternetUtility {

    /**
     * Follow url redirect, return the final url. Also work with http/https
     * switch. Consecutive redirect supported.
     *
     * @param url
     * @param forbiddenDomain
     * @param connectionTimeout
     * @param readTimeout
     * @param maxRedirectNumber
     * @param currentRedirectNumber
     * @param logger
     * @param errorCodeToWatch: null means log all error codes.
     * @return
     * @throws Exception
     */
    public static String getRedirectOKURL(String url, List<String> forbiddenDomain, int connectionTimeout, int readTimeout, int maxRedirectNumber, int currentRedirectNumber, Logger logger, List<Integer> errorCodeToWatch) throws Exception {
        if (currentRedirectNumber == maxRedirectNumber) {
            return null; // Not OK.
        }

        // If redirected url is in forbidden list, not OK.
        if (forbiddenDomain != null && forbiddenDomain.stream().anyMatch(s -> url.contains(s))) {
            return null;
        }

        List<Integer> redirectCode = Arrays.asList(301, 302, 303, 304, 307, 308);
        
        HttpURLConnection httpConn = null;
        try {
            URL u = new URL(url);
            httpConn = (HttpURLConnection) u.openConnection();
            httpConn.setConnectTimeout(connectionTimeout);
            httpConn.setReadTimeout(readTimeout);
            // Avoid some 403 forbidden.
            httpConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36");

            httpConn.setInstanceFollowRedirects(false); // Handle redirect explicitly.
            
            int status = httpConn.getResponseCode();
            if (status == 200) {
                return url; // OK.
            } else if (redirectCode.contains(status)) {
                String redirectUrl = httpConn.getHeaderField("Location");
                // redirectUrl may be null, if there is no header field Location.
                if (redirectUrl == null) {
                    return null;
                }
                // Solve relative url.
                if (redirectUrl.startsWith("/")) {
                    if (u.getPort() == -1) {
                        redirectUrl = u.getProtocol() + "://" + u.getHost() + redirectUrl;
                    } else {
                        redirectUrl = u.getProtocol() + "://" + u.getHost() + ":" + u.getPort() + redirectUrl;
                    }
                }
                return getRedirectOKURL(redirectUrl, forbiddenDomain, connectionTimeout, readTimeout, maxRedirectNumber, currentRedirectNumber + 1, logger, errorCodeToWatch);
            } else {
                if (errorCodeToWatch == null || errorCodeToWatch.contains(status)) {
                    logger.log(Level.SEVERE, "Code " + status + " at url: " + url);
                }
                return null;
            }
        } finally {
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
    }

    /**
     * The idea is reduce and reuse connection, by outsourcing all processing from downloadFile() to this method, and return the OK HttpConn.
     * So, if OK: stop, return the OK connection for reuse in downloadFile().
     * If redirect: open next connection for every redirect, then stop when OK and reuse.
     * If bad: stop return null.
     * 
     * @param url
     * @param forbiddenDomain
     * @param connectionTimeout
     * @param readTimeout
     * @param maxRedirectNumber
     * @param currentRedirectNumber
     * @param logger
     * @param errorCodeToWatch: null means log all error codes.
     * @return httpConn: do not close finally.
     * @throws Exception 
     */
    public static HttpURLConnection getRedirectOKHttpConn(String url, List<String> forbiddenDomain, int connectionTimeout, int readTimeout, int maxRedirectNumber, int currentRedirectNumber, Logger logger, List<Integer> errorCodeToWatch) throws Exception {
        if (currentRedirectNumber == maxRedirectNumber) {
            return null; // Not OK.
        }

        // If redirected url is in forbidden list, not OK.
        if (forbiddenDomain != null && forbiddenDomain.stream().anyMatch(s -> url.contains(s))) {
            return null;
        }
        
        List<Integer> redirectCode = Arrays.asList(301, 302, 303, 304, 307, 308);
        
        URL u = new URL(url);
        HttpURLConnection httpConn = (HttpURLConnection) u.openConnection();
        httpConn.setConnectTimeout(connectionTimeout);
        httpConn.setReadTimeout(readTimeout);
        // Avoid some 403 forbidden.
        httpConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36");

        httpConn.setInstanceFollowRedirects(false); // Handle redirect explicitly.

        int status = httpConn.getResponseCode();
        if (status == 200) {
            return httpConn; // OK.
        } else if (redirectCode.contains(status)) {
            String redirectUrl = httpConn.getHeaderField("Location");
            // redirectUrl may be null, if there is no header field Location.
            if (redirectUrl == null) {
                httpConn.disconnect();
                return null;
            }
            // Solve relative url.
            if (redirectUrl.startsWith("/")) {
                if (u.getPort() == -1) {
                    redirectUrl = u.getProtocol() + "://" + u.getHost() + redirectUrl;
                } else {
                    redirectUrl = u.getProtocol() + "://" + u.getHost() + ":" + u.getPort() + redirectUrl;
                }
            }
            return getRedirectOKHttpConn(redirectUrl, forbiddenDomain, connectionTimeout, readTimeout, maxRedirectNumber, currentRedirectNumber + 1, logger, errorCodeToWatch);
        } else {
            if (errorCodeToWatch == null || errorCodeToWatch.contains(status)) {
                logger.log(Level.SEVERE, "Code " + status + " at url: " + url);
            }
            httpConn.disconnect();
            return null;
        }
    }
}
