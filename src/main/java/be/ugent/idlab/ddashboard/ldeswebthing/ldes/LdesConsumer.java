/****************************************************************************
 * be.ugent.idlab.ddashboard.ldeswebthing.LdesConsumer                        *
 ****************************************************************************/
package be.ugent.idlab.ddashboard.ldeswebthing.ldes;

import be.ugent.idlab.ddashboard.ldeswebthing.ldes.models.EventField;
import be.ugent.idlab.ddashboard.ldeswebthing.ldes.models.EventOrdering;
import be.ugent.idlab.ddashboard.ldeswebthing.ldes.models.LDESException;
import be.ugent.idlab.ddashboard.ldeswebthing.ldes.models.TimestampPrecision;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Component that contains all the logic to consume data from a LDES in SOLID
 * pod.
 * <p>
 * LDES in SOLID documentation:
 * https://github.com/woutslabbinck/SolidEventSourcing
 * 
 * @author Stijn Verstichel (adaptation from original obeliskwebthing)
 * @date 2023-04-03
 * @version 0.1.0
 */
public class LdesConsumer extends LdesClient {

    private final Logger LOGGER = Logger.getLogger(LdesConsumer.class.getName());

    // Stream id, source and handler
    private Map<String, EventSource> streamSources = new HashMap<>();
    private Map<String, EventHandler> streamHandlers = new HashMap<>();

    public LdesConsumer(String rootUrl, String clientId, String clientSecrect) throws LDESException {
        super(rootUrl, clientId, clientSecrect);
    }

    /**
     * Get all historical events, keep getting pages until all events got.If
 limit is set will only get until limit reached, this should only be once
 unless very large value.
     *
     * @param datasets Set of dataset IDs
     * @param metrics Set of metric IDs or wildcards (e.G. "*::number"), null
     * defaults to all metrics
     * @param fromTimestamp Limit output to events after (and including) this
     * UTC millisecond timestamp, null defaults to no limit
     * @param toTimestamp Limit output to events before (and excluding) this UTC
     * millisecond timestamp, null defaults to no limit
     * @param fields Set of fields to return in the result set, null defaults to
     * [metric, source, value]
     * @param precision Defines the timestamp precision for the returned
     * results, null defaults to milliseconds
     * @param orderByFields Linked set specifying the ordering of the output,
     * null defaults to timestamp
     * @param orderByOrdering Specifies the ordering of the output, null
     * defaults to ascending
     * @param filter Limit output to events matching the specified filter
     * expression, null defaults to no filtering
     * @param limit Limit output to a maximum number of events, can be null,
     * will be unlimited
     * @param limitByFields Limit the combination of a specific set of Index
     * fields to a specified maximum number, can be null, only applies if
     * limitByLimit not null
     * @param limitByLimit Limit the combination of a specific set of Index
     * fields to a specified maximum number, can be null, only applies if
     * limitByFields not null
     * @return List of Event JSONObjects
     * @throws be.ugent.idlab.ddashboard.ldeswebthing.ldes.models.LDESException
     */
    public List<JSONObject> getEvents(Set<String> datasets, Set<String> metrics, Long fromTimestamp, Long toTimestamp, Set<EventField> fields, TimestampPrecision precision,
            LinkedHashSet<EventField> orderByFields, EventOrdering orderByOrdering, JSONObject filter, Integer limit, Set<EventField> limitByFields, Integer limitByLimit)
            throws LDESException {

        List<JSONObject> eventsList = new ArrayList<>();
        JSONArray eventsArray = new JSONArray();
        String cursor = null;
        boolean finished = false;
        while (!finished) {
            // Get response and add events to list
            JSONObject response = this.getEvents(datasets, metrics, fromTimestamp, toTimestamp, fields, precision, orderByFields, orderByOrdering, filter, limit, limitByFields, limitByLimit, cursor);

            JSONObject observationObject = null;
            // System.out.println(response);
            JSONObject resultsJSONObject = response.getJSONObject("results");
            JSONArray bindingsJSONArray = resultsJSONObject.getJSONArray("bindings");
            // System.out.println(bindingsJSONArray);
            if (!bindingsJSONArray.isEmpty()) {
                for (int i = 0; i < bindingsJSONArray.length(); i++) {
                    observationObject = new JSONObject();
                    JSONObject bindingJSONObject = bindingsJSONArray.getJSONObject(i);
                    System.out.println(bindingJSONObject);
                    observationObject.put("timestamp", bindingJSONObject.getJSONObject("timestamp"));
                    observationObject.put("value", bindingJSONObject.getJSONObject("value").getFloat("value"));

                    //System.out.println(observationObject);
                    eventsArray.put(observationObject);

                    for (Object event_ : eventsArray) {
                        eventsList.add((JSONObject) event_);
                    }
                    // Check if cursor
                    cursor = response.optString("cursor", null);
                    // All events got when there is no (more) cursor
                    // OR more or equal events got than limit
                }
            }
            finished = cursor == null || (limit != null && eventsList.size() >= limit);
            // Because limit is actually page size for large limits the amount of events got may be greater than limit?
            if (limit != null && eventsList.size() > limit) {
                eventsList = eventsList.subList(0, limit);
            }
        }
        return eventsList;
    }

    /**
     * Get historical events.
     *
     * @param datasets Set of dataset IDs
     * @param metrics Set of metric IDs or wildcards (e.G. "*::number"), null
     * defaults to all metrics
     * @param fromTimestamp Limit output to events after (and including) this
     * UTC millisecond timestamp, null defaults to no limit
     * @param toTimestamp Limit output to events before (and excluding) this UTC
     * millisecond timestamp, null defaults to no limit
     * @param fields Set of fields to return in the result set, null defaults to
     * [metric, source, value]
     * @param precision Defines the timestamp precision for the returned
     * results, null defaults to milliseconds
     * @param orderByFields Linked set specifying the ordering of the output,
     * null defaults to timestamp
     * @param orderByOrdering Specifies the ordering of the output, null
     * defaults to ascending
     * @param filter Limit output to events matching the specified filter
     * expression, null defaults to no filtering
     * @param limit Limit output to a maximum number of events, also determines
     * the page size, null defaults to 2500, can be null
     * @param limitByFields Limit the combination of a specific set of Index
     * fields to a specified maximum number, can be null, only applies if
     * limitByLimit not null
     * @param limitByLimit Limit the combination of a specific set of Index
     * fields to a specified maximum number, can be null, only applies if
     * limitByFields not null
     * @param cursor Specifies the next cursor, used when paging through large
     * result sets, can be null
     * 
     * CAVEAT: It needs to be defined/taken care of how static these
     * SPARQL queries used to trigger Comunica are. !!
     * 
     * @return Obelisk response
     * @throws be.ugent.idlab.ddashboard.ldeswebthing.ldes.models.LDESException
     */
    public JSONObject getEvents(Set<String> datasets, Set<String> metrics, Long fromTimestamp, Long toTimestamp, Set<EventField> fields, TimestampPrecision precision,
            LinkedHashSet<EventField> orderByFields, EventOrdering orderByOrdering, JSONObject filter, Integer limit, Set<EventField> limitByFields, Integer limitByLimit,
            String cursor) throws LDESException {

        JSONObject jsonObject = null;
        String toTimestampString = null;
        String fromTimestampString = null;

        if (toTimestamp != null) {
            ZonedDateTime toZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(toTimestamp),
                    ZoneId.systemDefault());
            Instant toDateTime = Instant.ofEpochMilli(toTimestamp);
            //toTimestampString = "\"" + toZonedDateTime.format(DateTimeFormatter.ISO_DATE_TIME) + "\"^^xsd:dateTime";
            toTimestampString = "\"" + toDateTime.toString() + "\"^^<http://www.w3.org/2001/XMLSchema#dateTime>";
        }

        if (fromTimestamp != null) {
            ZonedDateTime fromZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromTimestamp),
                    ZoneId.systemDefault());
            Instant fromDateTime = Instant.ofEpochMilli(fromTimestamp);
            //fromTimestampString = "\"" + fromZonedDateTime.format(DateTimeFormatter.ISO_DATE_TIME) + "\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)";
            fromTimestampString = "\"" + fromDateTime.toString() + "\"^^<http://www.w3.org/2001/XMLSchema#dateTime>";
        }

        try {
            HttpClient client = HttpClient.newHttpClient();

            String query = null;
            if (fromTimestamp == null) {
                if (toTimestamp == null) {
                    String plainQuery = "SELECT ?aggregation ?timestamp ?value WHERE "
                            + "{?aggregation <https://saref.etsi.org/core/hasTimestamp> ?timestamp . "
                            + "?aggregation <https://saref.etsi.org/core/hasValue> ?value } "
                            + "ORDER BY DESC(?timestamp) "
                            + "LIMIT 1";
                    //System.out.println(plainQuery);
                    query = URLEncoder.encode(plainQuery, StandardCharsets.UTF_8.toString());
                } else {
                    String plainQuery = "SELECT ?aggregation ?timestamp ?value WHERE "
                            + "{?aggregation <https://saref.etsi.org/core/hasTimestamp> ?timestamp . "
                            + "?aggregation <https://saref.etsi.org/core/hasValue> ?value ."
                            + "FILTER (?timestamp < " + toTimestampString + ") } "
                            + "ORDER BY DESC(?timestamp) ";
                    //System.out.println(plainQuery);
                    query = URLEncoder.encode(plainQuery, StandardCharsets.UTF_8.toString());
                }
            } else {
                if (toTimestamp == null) {
                    String plainQuery = "SELECT ?aggregation ?timestamp ?value WHERE "
                            + "{?aggregation <https://saref.etsi.org/core/hasTimestamp> ?timestamp . "
                            + "?aggregation <https://saref.etsi.org/core/hasValue> ?value ."
                            + "FILTER (?timestamp > " + fromTimestampString + ") } "
                            + "ORDER BY DESC(?timestamp) ";
                    //System.out.println(plainQuery);
                    query = URLEncoder.encode(plainQuery, StandardCharsets.UTF_8.toString());
                } else {
                    String plainQuery = "SELECT ?aggregation ?timestamp ?value WHERE "
                            + "{?aggregation <https://saref.etsi.org/core/hasTimestamp> ?timestamp . "
                            + "?aggregation <https://saref.etsi.org/core/hasValue> ?value ."
                            + "FILTER (?timestamp > " + fromTimestampString + " && "
                            + "?timestamp < " + toTimestampString + ") } "
                            + "ORDER BY DESC(?timestamp) ";
                    //System.out.println(plainQuery);
                    query = URLEncoder.encode(plainQuery, StandardCharsets.UTF_8.toString());

                }
            }

            //System.out.println(query);
            // Should probably be configurable, in case the Comunica Endpoint is 
            // hosted on a separate server!!
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8081/sparql?query=" + query))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            //System.out.println(response);
            jsonObject = new JSONObject(response.body());
            //System.out.println(jsonObject);

        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(LdesConsumer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return jsonObject;
    }
}
