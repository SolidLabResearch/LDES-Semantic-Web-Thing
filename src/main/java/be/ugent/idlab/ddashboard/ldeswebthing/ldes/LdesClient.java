/****************************************************************************
 * be.ugent.idlab.ddashboard.ldeswebthing.LdesClient                        *
 ****************************************************************************/
package be.ugent.idlab.ddashboard.ldeswebthing.ldes;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.json.JSONArray;

import be.ugent.idlab.ddashboard.ldeswebthing.ldes.models.LDESException;

/**
 * Component that contains all the logic to access the LDES in SOLID (e.g. Authentication).
 * Solid Documentation: https://solidproject.org/TR/oidc#concepts
 * 
 * @author Stijn Verstichel (adaptation from original obeliskwebthing)
 * @date 2023-03-30
 * @version 0.1.0
 */
public class LdesClient {

    private final Logger LOGGER = Logger.getLogger(LdesConsumer.class.getName());

    protected String token = null;

    // The buffer added to the token expires date in ms for veryfing token
    protected static final long TOKEN_EXPIRES_BUFFER = 20000;
    // The retry period in ms
    protected static final long RETRY_PERIOD = 2000;
    // How many items may be got from graphql
    protected static final long ITEM_LIMIT = 50000;


    /**
     * Initialize client.
     * Currently, authentication from this Semantic Web Thing to a SolidPod is NOT supported.
     * 
     * @param rootUrl Root Obelisk API url e.g. 'https://example.com/api/v3'
     * @param clientId Obelisk client ID
     * @param clientSecrect Obelisk client secret
     * @throws LDESException
     */
    public LdesClient(String rootUrl, String clientId, String clientSecrect) throws LDESException {      
        // Currently, authentication from this Semantic Web Thing to a SolidPod is not supported.
    }
    
    public LdesClient() throws LDESException {
        // Currently, authentication from this Semantic Web Thing to a SolidPod is not supported.
    }

    /**
     * Get an access token from Obelisk.
     * 
     * @throws LDESException
     * @throws InterruptedException
     */
    private void getToken() {
        /*
        while(true) {
            try {
                HttpResponse<String> response;
                String authenticationString = Base64.getEncoder().encodeToString((this.clientId + ":" + this.clientSecret).getBytes(StandardCharsets.UTF_8.toString()));
                JSONObject payload = new JSONObject().put("grant_type", "client_credentials");
                URI uri = URI.create(this.getTokenUrl()).normalize();
    
                HttpClient client = HttpClient.newBuilder().build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .header("Authorization", "Basic " + authenticationString)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();
    
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
    
                if (response.statusCode()/100 != 2) {
                    // ERROR
                    LOGGER.log(Level.WARNING, "Got an unsuccessful status code during authentication with Obelisk");
                    LOGGER.log(Level.WARNING, "Status code: {0}", response.statusCode());
                    String errorMessage = LdesClient.extractObeliskError(response, "<Could not get error message>");
                    throw new LDESException(String.format("Could not get authentication token: (%s) %s", response.statusCode(), errorMessage));
                }
                else {
                    // OK
                    JSONObject responseObject = new JSONObject((String) response.body());
                    this.token = responseObject.getString("token");
                    // The max_valid_time and max_idle_time are in seconds, Java Date requires ms
                    // Use max_idle_time for expiring token, requesting a token is cheap, tracking idle time
                    // is a lot harder, especially when SSE streams are involved
                    schedluleReauthenticationTask(responseObject.getLong("max_idle_time")*1000);
                    return;
                }
            } catch (Exception e1) {
                // Unsuccesfull authentication
                LOGGER.log(Level.WARNING, "An error occurred during authentication with Obelisk");
                LOGGER.log(Level.WARNING, e1.getMessage());

                // Wait before retry
                try {
                    TimeUnit.MILLISECONDS.sleep(LdesClient.RETRY_PERIOD);
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(Level.WARNING, "Retry authentication task interrupted");
                    LOGGER.log(Level.WARNING, e2.getMessage());
                }
            }
        }
        */
    }

    /**
     * Perform an authenticated http POST JSON request with optional JSON payload and optional url parameters.
     * 
     * @param uri API Endpoint
     * @param payload JSONObject payload, can be null
     * @param urlParams URL parameters, can be null
     * @return JSON response
     * @throws LDESException
     */
    protected HttpResponse<String> httpPost(String url, JSONObject payload, Map<String,String> urlParams) throws LDESException {
        return _httpPost(url, (Object) payload, urlParams);
    }

    /**
     * Perform an authenticated http POST JSON request with optional JSON payload and optional url parameters.
     * 
     * @param uri API Endpoint
     * @param payload JSONArray payload, can be null
     * @param urlParams URL parameters, can be null
     * @return JSON response
     * @throws LDESException
     */
    protected HttpResponse<String> httpPost(String url, JSONArray payload, Map<String,String> urlParams) throws LDESException {
        return _httpPost(url, (Object) payload, urlParams);
    }

    /**
     * Perform an authenticated http POST JSON request with optional JSON payload and optional url parameters.
     * 
     * @param uri API Endpoint
     * @param payload Payload, can be null
     * @param urlParams URL parameters, can be null
     * @return JSON response
     * @throws LDESException
     */
    private HttpResponse<String> _httpPost(String url, Object payload, Map<String,String> urlParams) throws LDESException {
        String paramString = "";
        if (urlParams != null && urlParams.size() > 0) {
            List<String> paramsStrings = new ArrayList<>();
            try {
                for (Map.Entry<String, String> entry : urlParams.entrySet()) {
                    paramsStrings.add(URLEncoder.encode(entry.getKey(), "UTF-8") + "=" + URLEncoder.encode(entry.getValue(), "UTF-8"));
                }
            }
            catch(Exception exception) {
                throw new LDESException("Invalid URL paramters");
            }
            
            paramString = "?" + String.join("&", paramsStrings);
        }

        URI uri = URI.create(url + paramString).normalize();

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + this.token)
                .header("Content-Type", "application/json");

        if (payload != null) {
            request.POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
        }
        else {
            request.POST(HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response;
        try {
            HttpClient client = HttpClient.newBuilder().build();
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        catch(Exception exception) {
            throw new LDESException(exception);
        }

        return response;
    }

    /**
     * Extract error message from standard error template (if possible).
     * 
     * @param response HttpResponse
     * @param defaultError Message to return if actual error message could not be extracted (can be null)
     * @return Error message, default message
     */
    protected static String extractObeliskError(HttpResponse<String> response, String defaultError) {
        if (response.body() != null) {
            JSONObject responseObject = new JSONObject((String) response.body());
            if (responseObject.has("error")) {
                JSONObject errorObject = responseObject.getJSONObject("error");
                if (errorObject.has("message")) {
                    return errorObject.getString("message");
                }
            }
        }
        return defaultError;
    }

    /**
     * Execute GraphQL query.
     * 
     * @param query Query string
     * @return Result as JSON object
     * @throws LDESException
     */
    public JSONObject queryGraphql(String query) throws LDESException {
        /**
        JSONObject payload = new JSONObject().put("query", query);
        HttpResponse<String> response = httpPost(this.getGraphqlUrl(), payload, null);

        if (response.statusCode()/100 != 2 || graphqlErrorResponse(response)) {
            // ERROR
            LOGGER.log(Level.WARNING, "An error occurred during GraphQL query");
            LOGGER.log(Level.WARNING, "Status code: {0}", response.statusCode());
            String errorMessage = LdesClient.extractGraphqlError(response, "<Could not get error message>");
            throw new LDESException(String.format("Could not execute GraphQL query: (%s) %s", response.statusCode(), errorMessage));
        }
        else {
            return new JSONObject((String) response.body());
        }
        * */
        return null;
    }

    /**
     * Check if the graphql response is an error response.
     * 
     * @param response
     * @return True if error response
     */
    protected static boolean graphqlErrorResponse(HttpResponse<String> response) {
        return new JSONObject((String) response.body()).has("errors");
    }

    /**
     * Extract error message from GraphQl error response.
     * 
     * @param response HttpResponse
     * @param defaultError Message to return if actual error message could not be extracted (can be null)
     * @return Error message, default message
     */
    protected static String extractGraphqlError(HttpResponse<String> response, String defaultError) {
        if (response.body() != null) {
            JSONObject responseObject = new JSONObject((String) response.body());
            if (responseObject.has("errors")) {
                List<String> errorList = new ArrayList<String>();
                responseObject.getJSONArray("errors").forEach(error_ -> {
                    JSONObject error = (JSONObject) error_;
                    errorList.add(error.getString("message"));
                });
                return String.join(", ", errorList);
            }
        }
        return defaultError;
    }

    /**
     * List scopes accesible from current authenticated session.
     * 
     * @return Map of dataset <ID, name> pairs
     * @throws LDESException
     */
    public Map<String, String> getDatasets() throws LDESException {
        String query = String.format("{ me { datasets(limit: %s) { items { id name } } } }", LdesClient.ITEM_LIMIT);
        JSONObject response = this.queryGraphql(query);
        Map<String, String> datasets = new HashMap<String, String>();
        response.getJSONObject("data").getJSONObject("me").getJSONObject("datasets").getJSONArray("items").forEach(item_ -> {
            JSONObject item = (JSONObject) item_;
            datasets.put(item.getString("id"), item.getString("name"));
        });
        return datasets;
    }

    /**
     * Retrieve available metrics for a scope.
     * 
     * @param dataset Dataset ID
     * @return List of metric IDs
     * @throws LDESException
     */
    public List<String> getMetrics(String dataset) throws LDESException {
        String query = String.format("{ dataset(id:\"%s\") { metrics(limit: %s) { items { id } } } }", dataset, LdesClient.ITEM_LIMIT);
        JSONObject response = this.queryGraphql(query);
        List<String> metrics = new ArrayList<String>();
        response.getJSONObject("data").getJSONObject("dataset").getJSONObject("metrics").getJSONArray("items").forEach(item_ -> {
            JSONObject item = (JSONObject) item_;
            metrics.add(item.getString("id"));
        });
        return metrics;
    }

    /**
     * Retrieve the things using this metric.
     * 
     * @param dataset Dataset ID
     * @param metric Metric ID
     * @return List of thing IDs
     * @throws LDESException
     */
    public List<String> getMetricThings(String dataset, String metric) throws LDESException {
        String query = String.format("{ dataset(id:\"%s\") { metric(id:\"%s\") { id things(limit: %s) { items { id } } } } }", dataset, metric, LdesClient.ITEM_LIMIT);
        JSONObject response = this.queryGraphql(query);
        List<String> things = new ArrayList<String>();
        response.getJSONObject("data").getJSONObject("dataset").getJSONObject("metric").getJSONObject("things").getJSONArray("items").forEach(item_ -> {
            JSONObject item = (JSONObject) item_;
            things.add(item.getString("id"));
        });
        return things;
    }
    

    /**
     * Retrieve available things for a scope.
     * 
     * @param dataset Dataset ID
     * @return List of thing IDs
     * @throws LDESException
     */
    public List<String> getThings(String dataset) throws LDESException {
        String query = String.format("{ dataset(id:\"%s\") { things(limit: %s) { items { id } } } }", dataset, LdesClient.ITEM_LIMIT);
        JSONObject response = this.queryGraphql(query);
        List<String> things = new ArrayList<String>();
        LOGGER.info(""+things);
        response.getJSONObject("data").getJSONObject("dataset").getJSONObject("things").getJSONArray("items").forEach(item_ -> {
            JSONObject item = (JSONObject) item_;
            things.add(item.getString("id"));
        });
        return things;
    }

    /**
     * Retrieve the metrics for a thing.
     * 
     * @param dataset Dataset ID
     * @param thing Thing ID
     * @return List of metric IDs
     * @throws LDESException
     */
    public List<String> getThingMetrics(String dataset, String thing) throws LDESException {
        String query = String.format("{ dataset(id:\"%s\") { thing(id:\"%s\") { id metrics(limit: %s) { items { id } } } } }", dataset, thing, LdesClient.ITEM_LIMIT);
        JSONObject response = this.queryGraphql(query);
        List<String> metrics = new ArrayList<String>();
        response.getJSONObject("data").getJSONObject("dataset").getJSONObject("thing").getJSONObject("metrics").getJSONArray("items").forEach(item_ -> {
            JSONObject item = (JSONObject) item_;
            metrics.add(item.getString("id"));
        });
        return metrics;
    }
    
    /**
     * Retrieve available things and its metrics for a scope.
     * 
     * @param dataset Dataset ID (can this be omitted?
     * @return Map of thing IDs and its metrics IDs.
     * @throws LDESException
     * 
     * !!ATTENTION!! Hardcoded implementation - testing with Dashboard, 
     * !!ATTENTION!! needs to be refactored for use with configurable queries.
     */
    public Map<String, List<String>> getThingsMetrics(String dataset) throws LDESException {
        Map<String, List<String>> thingsMetrics = new HashMap<>();
        List<String> metrics = new ArrayList<>();
        metrics.add("average");
        thingsMetrics.put("aggregation", metrics);
        
        return thingsMetrics;
    }
}
