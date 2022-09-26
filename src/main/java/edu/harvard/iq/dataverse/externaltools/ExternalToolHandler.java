package edu.harvard.iq.dataverse.externaltools;

import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.FileMetadata;
import edu.harvard.iq.dataverse.authorization.users.ApiToken;
import edu.harvard.iq.dataverse.util.SystemConfig;
import edu.harvard.iq.dataverse.util.URLTokenUtil;

import edu.harvard.iq.dataverse.util.UrlSignerUtil;
import edu.harvard.iq.dataverse.util.json.JsonUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.HttpMethod;

import org.apache.commons.codec.binary.StringUtils;

import com.github.scribejava.core.java8.Base64;

/**
 * Handles an operation on a specific file. Requires a file id in order to be
 * instantiated. Applies logic based on an {@link ExternalTool} specification,
 * such as constructing a URL to access that file.
 */
public class ExternalToolHandler extends URLTokenUtil {

    private final ExternalTool externalTool;

    private String requestMethod;
    private String toolContext;

    /**
     * File level tool
     *
     * @param externalTool The database entity.
     * @param dataFile     Required.
     * @param apiToken     The apiToken can be null because "explore" tools can be
     *                     used anonymously.
     */
    public ExternalToolHandler(ExternalTool externalTool, DataFile dataFile, ApiToken apiToken,
            FileMetadata fileMetadata, String localeCode) {
        super(dataFile, apiToken, fileMetadata, localeCode);
        this.externalTool = externalTool;
        toolContext = externalTool.getToolUrl();
    }

    /**
     * Dataset level tool
     *
     * @param externalTool The database entity.
     * @param dataset      Required.
     * @param apiToken     The apiToken can be null because "explore" tools can be
     *                     used anonymously.
     */
    public ExternalToolHandler(ExternalTool externalTool, Dataset dataset, ApiToken apiToken, String localeCode) {
        super(dataset, apiToken, localeCode);
        this.externalTool = externalTool;
    }

    // TODO: rename to handleRequest() to someday handle sending headers as well as
    // query parameters.
    public String handleRequest() {
        return handleRequest(false);
    }

    // TODO: rename to handleRequest() to someday handle sending headers as well as
    // query parameters.
    public String handleRequest(boolean preview) {
        JsonObject toolParameters = JsonUtil.getJsonObject(externalTool.getToolParameters());
        JsonString method = toolParameters.getJsonString("httpMethod");
        requestMethod = method != null ? method.getString() : HttpMethod.GET;
        JsonObject params = getParams(toolParameters);
        logger.fine("Found params: " + JsonUtil.prettyPrint(params));
        if (requestMethod.equals(HttpMethod.GET)) {
            String paramsString = "";
            if (externalTool.getAllowedApiCalls() == null) {
                // Legacy, using apiKey
                logger.fine("Legacy Case");

                for (Entry<String, JsonValue> entry : params.entrySet()) {
                    paramsString = paramsString + (paramsString.isEmpty() ? "?" : "&") + entry.getKey() + "=";
                    JsonValue val = entry.getValue();
                    if (val.getValueType().equals(JsonValue.ValueType.NUMBER)) {
                        paramsString += ((JsonNumber) val).intValue();
                    } else {
                        paramsString += ((JsonString) val).getString();
                    }
                }
            } else {
                //Send a signed callback to get params and signedURLs 
                String callback = null;
                switch (externalTool.getScope()) {
                case DATASET:
                    callback=SystemConfig.getDataverseSiteUrlStatic() + "/api/v1/datasets/"
                            + dataset.getId() + "/versions/:latest/toolparams/" + externalTool.getId();
                case FILE:
                    callback= SystemConfig.getDataverseSiteUrlStatic() + "/api/v1/files/"
                            + dataFile.getId() + "/metadata/" + fileMetadata.getId() + "/toolparams/"
                            + externalTool.getId();
                }
                if (apiToken != null) {
                    callback = UrlSignerUtil.signUrl(callback, 5, apiToken.getAuthenticatedUser().getUserIdentifier(), HttpMethod.GET,
                            System.getProperty(SystemConfig.API_SIGNING_SECRET, "") + apiToken.getTokenString());
                }
                paramsString= "?callback=" + Base64.getEncoder().encodeToString(StringUtils.getBytesUtf8(callback));
                if (getLocaleCode() != null) {
                    paramsString += "&locale=" + getLocaleCode();
                }
            }
            if (preview) {
                paramsString += "&preview=true";
            }
            logger.fine("GET return is: " + paramsString);
            return paramsString;

        } else {
            // ToDo - if the allowedApiCalls() are defined, could/should we send them to
            // tools using GET as well?

            if (requestMethod.equals(HttpMethod.POST)) {
                String body = JsonUtil.prettyPrint(createPostBody(params).build());
                try {
                    logger.info("POST Body: " + body);
                    return postFormData(body);
                } catch (IOException | InterruptedException ex) {
                    Logger.getLogger(ExternalToolHandler.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return null;
    }

    public JsonObject getParams(JsonObject toolParameters) {
        JsonArray queryParams = toolParameters.getJsonArray("queryParameters");

        // ToDo return json and print later
        JsonObjectBuilder paramsBuilder = Json.createObjectBuilder();
        if (!(queryParams == null) && !queryParams.isEmpty()) {
            queryParams.getValuesAs(JsonObject.class).forEach((queryParam) -> {
                queryParam.keySet().forEach((key) -> {
                    String value = queryParam.getString(key);
                    JsonValue param = getParam(value);
                    if (param != null) {
                        paramsBuilder.add(key, param);
                    }
                });
            });
        }
        return paramsBuilder.build();
    }

    public JsonObjectBuilder createPostBody(JsonObject params) {
        JsonObjectBuilder bodyBuilder = Json.createObjectBuilder();
        bodyBuilder.add("queryParameters", params);

        JsonArray apiArray = JsonUtil.getJsonArray(externalTool.getAllowedApiCalls());
        JsonArrayBuilder apisBuilder = Json.createArrayBuilder();
        
        apiArray.getValuesAs(JsonObject.class).forEach(((apiObj) -> {
            logger.info(JsonUtil.prettyPrint(apiObj));
            String name = apiObj.getJsonString("name").getString();
            String httpmethod = apiObj.getJsonString("method").getString();
            int timeout = apiObj.getInt("timeOut");
            String urlTemplate = apiObj.getJsonString("urlTemplate").getString();
            logger.fine("URL Template: " + urlTemplate);
            urlTemplate = SystemConfig.getDataverseSiteUrlStatic() + urlTemplate;
            String apiPath = replaceTokensWithValues(urlTemplate);
            logger.fine("URL WithTokens: " + apiPath);
            String url = apiPath;
            // Sign if apiToken exists, otherwise send unsigned URL (i.e. for guest users)
            ApiToken apiToken = getApiToken();
            logger.info("Fullkey create: " + System.getProperty(SystemConfig.API_SIGNING_SECRET, "") + getApiToken().getTokenString());
            if (apiToken != null) {
                url = UrlSignerUtil.signUrl(apiPath, timeout, apiToken.getAuthenticatedUser().getUserIdentifier(), httpmethod,
                        System.getProperty(SystemConfig.API_SIGNING_SECRET, "") + getApiToken().getTokenString());
            }
            logger.fine("Signed URL: " + url);
            apisBuilder.add(Json.createObjectBuilder().add("name", name).add("httpMethod", httpmethod)
                    .add("signedUrl", url).add("timeOut", timeout));
        }));
        bodyBuilder.add("signedUrls", apisBuilder);
        return bodyBuilder;
    }

    private String postFormData(String allowedApis) throws IOException, InterruptedException {
        String url = null;
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.ofString(allowedApis))
                .uri(URI.create(externalTool.getToolUrl())).header("Content-Type", "application/json").build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        boolean redirect = false;
        int status = response.statusCode();
        if (status != HttpURLConnection.HTTP_OK) {
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER) {
                redirect = true;
            }
        }
        if (redirect == true) {
            String newUrl = response.headers().firstValue("location").get();
//            toolContext = "http://" + response.uri().getAuthority();

            url = newUrl;
        }
        return url;
    }

    public String getToolUrlWithQueryParams() {
        String params = ExternalToolHandler.this.handleRequest();
        return toolContext + params;
    }

    public String getToolUrlForPreviewMode() {
        return externalTool.getToolUrl() + handleRequest(true);
    }

    public ExternalTool getExternalTool() {
        return externalTool;
    }

    public void setApiToken(ApiToken apiToken) {
        this.apiToken = apiToken;
    }

    /**
     * @return Returns Javascript that opens the explore tool in a new browser tab
     *         if the browser allows it.If not, it shows an alert that popups must
     *         be enabled in the browser.
     */
    public String getExploreScript() {
        String toolUrl = this.getToolUrlWithQueryParams();
        logger.fine("Exploring with " + toolUrl);
        return getScriptForUrl(toolUrl);
    }
}
