/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.general;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 *
 * @author mac
 */
public class InternetUtility {

    /**
     * Follow url redirect, return the final url. Also work with http/https switch. Consecutive redirect supported.
     * 
     * @param url
     * @param connectionTimeout
     * @param readTimeout
     * @param maxRedirectNumber
     * @param currentRedirectNumber
     * @return
     * @throws Exception 
     */
    public static String getFinalRedirectURL(String url, int connectionTimeout, int readTimeout, int maxRedirectNumber, int currentRedirectNumber) throws Exception {
        if (currentRedirectNumber == maxRedirectNumber) {
            return url;
        }
        URL u = new URL(url);
        HttpURLConnection httpConn = (HttpURLConnection) u.openConnection();
        httpConn.setConnectTimeout(connectionTimeout);
        httpConn.setReadTimeout(readTimeout);
        httpConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36");

        httpConn.setInstanceFollowRedirects(false);
        int status = httpConn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_PERM
			|| status == HttpURLConnection.HTTP_MOVED_TEMP
				|| status == HttpURLConnection.HTTP_SEE_OTHER) {
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
            return getFinalRedirectURL(redirectUrl, connectionTimeout, readTimeout, maxRedirectNumber, currentRedirectNumber + 1);
        }
        return url;
    }
}
