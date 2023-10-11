package com.adobe.aem.guides.wknd.core.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;

import javax.servlet.Servlet;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.keystore.KeyStoreService;

@Component(service = { Servlet.class }, property = {
        "sling.servlet.paths=/bin/tutorial/api/invoker/self-signed-cert",
        "sling.servlet.methods=GET"
})
public class SelfSignedCertAPIInvoker extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelfSignedCertAPIInvoker.class);

    private static String API_ENDPOINT = "https://127.0.0.1:3000/now";

    @Reference
    private KeyStoreService keyStoreService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {

        APIResponseDetails apiResponseDetails = null;

        try {

            // Call API by loading AEM TrustStore material
            CloseableHttpResponse closeableHttpResponse = invokeSelfSignedCertAPI(request.getResourceResolver());

            apiResponseDetails = toAPIResponseDetails(closeableHttpResponse);

        } catch (Exception e) {
            LOGGER.error("Error while invoking API, details are:", e);

            apiResponseDetails = new APIResponseDetails(500, e.getMessage());
        }

        LOGGER.info("API Invocation response details are:" + apiResponseDetails.toString());

        // Rely the API Invocation response
        relyAPIResponse(response, apiResponseDetails);

    }

    /**
     * 
     * Invokes API by loading the AEM TrustStore material into the SSL Context
     * before triggering the call.
     * 
     * @param resourceResolver
     * @return CloseableHttpResponse
     * @throws Exception
     */
    private CloseableHttpResponse invokeSelfSignedCertAPI(ResourceResolver resourceResolver)
            throws Exception {

        CloseableHttpResponse closeableHttpResponse = null;

        // Get AEM TrustStore
        KeyStore aemTrustStore = getAEMTrustStore(keyStoreService, resourceResolver);

        if (aemTrustStore != null) {

            // Create SSL Context
            SSLContextBuilder sslbuilder = new SSLContextBuilder();

            // Load AEM TrustStore material into above SSL Context
            sslbuilder.loadTrustMaterial(aemTrustStore, null);

            // Create SSL Connection Socket using above SSL Context
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                    sslbuilder.build(), NoopHostnameVerifier.INSTANCE);

            // Create HttpClientBuilder
            HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
            httpClientBuilder.setSSLSocketFactory(sslsf);

            // Create HttpClient
            CloseableHttpClient httpClient = httpClientBuilder.build();

            // Invoke API
            closeableHttpResponse = httpClient.execute(new HttpGet(API_ENDPOINT));

        } else {
            LOGGER.warn("Could not find AEM TrustStore, API Invocation cancelled");
        }

        return closeableHttpResponse;
    }

    /**
     * 
     * Returns the global AEM TrustStore
     * 
     * @param keyStoreService  OOTB OSGi service that makes AEM based KeyStore
     *                         operations easy.
     * @param resourceResolver
     * @return KeyStore
     */
    private KeyStore getAEMTrustStore(KeyStoreService keyStoreService, ResourceResolver resourceResolver) {

        // get AEM TrustStore from the KeyStoreService and ResourceResolver
        KeyStore aemTrustStore = keyStoreService.getTrustStore(resourceResolver);

        return aemTrustStore;
    }

    /**
     * Extracts the response code and body from the given CloseableHttpResponse into
     * APIResponseDetails POJO
     * 
     * @param CloseableHttpResponse
     * @return APIResponseDetails POJO containing response code and body
     * @throws Exception
     */
    private APIResponseDetails toAPIResponseDetails(CloseableHttpResponse closeableHttpResponse) throws Exception {

        if (closeableHttpResponse != null) {
            // Get Response Code and Body
            int responseCode = closeableHttpResponse.getStatusLine().getStatusCode();

            HttpEntity entity = closeableHttpResponse.getEntity();
            InputStream inputStream = entity.getContent();

            String responseBody = IOUtils.toString(inputStream, "UTF-8");

            return new APIResponseDetails(responseCode, responseBody);

        } else {
            return new APIResponseDetails(404, "Cloud not invoke API, please see error logs");
        }
    }

    /**
     * 
     * Sends the API Invocation response to the caller in HTML format containing
     * Reponse Code and Response Body
     * 
     * @param response
     * @param apiResponseDetails
     * @throws IOException
     */
    private void relyAPIResponse(SlingHttpServletResponse response, APIResponseDetails apiResponseDetails)
            throws IOException {

        response.setContentType("text/html");
        response.getWriter().write("<html><body>");
        response.getWriter().write("<h1>AEM Tutorial - Sample HTTPS API Invoker!</h1>");
        response.getWriter().write("<br/>");
        response.getWriter().write("<h3>The API invocation response details are:</h3>");
        response.getWriter().write("<br/>");
        response.getWriter().write("<strong>API Endpoint:</strong> " + API_ENDPOINT + "<br/><br/>");

        response.getWriter().write("<strong>Response Code:</strong> " + apiResponseDetails.getResponseCode() + "</br><br/>");
        response.getWriter().write("<strong>Response Details: </strong>" + apiResponseDetails.getResponseBody() + "</br><br/>");

        response.getWriter().write("</body></html>");

    }
}
