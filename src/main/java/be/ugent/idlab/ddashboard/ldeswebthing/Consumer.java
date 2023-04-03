/****************************************************************************
 * be.ugent.idlab.ddashboard.ldeswebthing.Consumer                          *
 ****************************************************************************/
package be.ugent.idlab.ddashboard.ldeswebthing;

import be.ugent.idlab.ddashboard.ldeswebthing.ldes.LdesConsumer;
import be.ugent.idlab.ddashboard.ldeswebthing.ldes.models.EventField;
import be.ugent.idlab.ddashboard.ldeswebthing.ldes.models.EventOrdering;
import be.ugent.idlab.ddashboard.ldeswebthing.ldes.models.LDESException;
import be.ugent.idlab.ddashboard.ldeswebthing.ldes.models.TimestampPrecision;
import be.ugent.idlab.ddashboard.semanticwebthing.domain.*;
import be.ugent.idlab.ddashboard.semanticwebthing.spring.RealtimeProviderInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Based on/Adapted from the original from obeliskwebthing. 
 * Obelisk functionality replaced by LDES functionality.
 * Stripped to the required functionality for Challenge 85.
 * 
 * @author Stijn Verstichel (adaptation from original obeliskwebthing)
 * @date 2023-03-09
 * @version 0.1.0
 */
class Consumer implements HistoricalProviderInterface,
        RealtimeProviderInterface, PostInterface {

    // LDES in SOLID pod URL
    private String ldesInSolidEndpoint;
    // Holds the authentication information to be used with LDES for this client
    // -> stored in the app.properties file
    private String authId;
    private String authSecret;
    // Holds the ID of the dataset (might not be needed) to be used with LDES for this client
    // -> stored in the app.properties file  
    private String datasetId;
    // Metric reference for LDES where all events are stored as url encoded
    private String eventId;
    // LDES in SOLID consumer and producer
    private LdesConsumer consumer = null;

    // Roots needed
    private Thing thingRoot = ThingRegistry.getInstance().getThing();
    private Event eventRoot = EventRegistry.getInstance().getEvent();

    // Reference to the static LOGGER
    private final static Logger LOGGER = Logger.getLogger(Consumer.class.getName());


    // Constructor
    public Consumer(String ldesInSolidEndpoint, String datasetId, String eventId) {
        this.ldesInSolidEndpoint = ldesInSolidEndpoint;
        this.datasetId = datasetId;
        this.eventId = eventId;
    }

    /*
     * Implements the sequence of actions to be performed to initialise and
     * setup a WebThing Server instance.
     */
    @Override
    public void start() {
        LOGGER.info("Starting Web Thing");

        LOGGER.info("Initializing LDES consumer");
        this.initializeLDESCommunication();

        LOGGER.info("Discovering all Web Things");
        this.populateThings();

        LOGGER.info("Starting Web Thing completed");
    }

    /**
     * Create LDES consumer.
     */
    private void initializeLDESCommunication() {
        try {
            this.consumer = new LdesConsumer(this.ldesInSolidEndpoint, this.authId, this.authSecret);
        } catch (LDESException e) {
            LOGGER.warning("Error creating LDES consumer!");
            LOGGER.warning(e.toString());
            throw new RuntimeException(e);
        }
    }

    /**
     * Populate the webthing with all known things and properties in the LDES.
     * Create thing with given id and optional propertyIds and put in root.
     * If thing exists will add given properties (if they don't exist).
     */
    private void populateThings() {
        Map<String, List<String>> thingsMetrics;
        try {
            thingsMetrics = this.consumer.getThingsMetrics(this.datasetId);
        } catch (LDESException e) {
            LOGGER.warning("Error populating things!");
            LOGGER.warning(e.toString());
            throw new RuntimeException(e);
        }
        LOGGER.info(String.format("Populating %s things", thingsMetrics.size()));
        for (Map.Entry<String, List<String>> thingMetrics : thingsMetrics.entrySet()) {
            // Do use unencoded for name though
            String thingLDESId = thingMetrics.getKey();
            List<String> propertyLDESIds = thingMetrics.getValue();

            String thingId = SemanticModel.urlEncode(thingLDESId);
            Thing thing = this.thingRoot.getThing(thingId);
            if (thing == null) {
                thing = new Thing(thingId, thingLDESId, null);
                this.thingRoot.addThing(thing);
            }
            if (propertyLDESIds != null) {
                for (String propertyLDESId : propertyLDESIds) {
                    String propertyId = SemanticModel.urlEncode(propertyLDESId);
                    if (thing.getProperty(propertyId) == null) {
                        thing.addProperty(new Property(propertyId, propertyLDESId, null, this.getDashboardMetric(propertyLDESId)));
                    }
                }
            }
        }
    }

    /**
     * Get the metric id part from ldes metrics/property as valid URI part (RFC3986).
     * @param metric
     * @return String
     */
    private String getDashboardMetric(String metric) {
        return SemanticModel.urlEncode(metric.substring(metric.lastIndexOf("::")+2));
    }

    /**
     * Returns an array with all historical events, starting from begin and
     * ending at end in milliseconds.
     * If both begin and end are null will get last event.
     * @param begin may be null
     * @param end may be null
     * @return List<Observation>
     */
    @Override
    public List<Observation> getHistoricalEvents(Long begin, Long end) {
        if (begin != null || end != null) {
            return this.getUpdateableObservations(null, this.eventId, begin, end, null, null, null);
        }
        else {
            return this.getPreviousUpdateableObservations(null, this.eventId, null, 1);
        }
    }

    /**
     * Getting all observations for a given thingID between begin and end (Long
     * UNIX timestamp).
     * If both begin and end are null will get last observation.
     * @param begin      Unix timestamp, may be null
     * @param end        Unix timestamp, may be null
     * @param thingId    The Web Thing (Thing) to be considered, may be null.
     * @param propertyId The Property (Metric) to be considered.
     * @param fillWindow If the window doesn't start with an observation get the observation before begin (null defaults to false)
     * @return List<Observation> with all observation between begin and end.
     */
    @Override
    public List<Observation> getHistoricalObservations(Long begin, Long end, String thingId, String propertyId, Boolean fillWindow) {
        if (begin != null || end != null) {
            // Get from  LDES
            List<Observation> observations = this.getObservations(thingId, propertyId, begin, end, null, null, null);
            // Check if window filled
            if (fillWindow != null && fillWindow && begin != null) {
                if (observations.isEmpty() || !observations.get(0).getTimestamp().equals(Instant.ofEpochMilli(begin))) {
                    observations.addAll(0, this.getPreviousObservations(thingId, propertyId, begin, 1));
                }
            }
            return observations;
        }
        else {
            return this.getPreviousObservations(thingId, propertyId, null, 1);
        }
    }


    /**
     * Get previous updateable observations, if timestamp is null will get latest observations.
     * Returns empty array when no observations found. (Does not return "deleted" observations)
     * @param thingId may be null
     * @param propertyId
     * @param timestamp Non inclusive, may be null
     * @param count Maximum observations to return, may be null defaults to 1
     * @return List<Observation>
     */
    private List<Observation> getPreviousUpdateableObservations(String thingId, String propertyId, Long timestamp, int limit) {
        Set<String> metrics = new HashSet<>();
        metrics.add(SemanticModel.urlDecode(propertyId));

        // Filter on not deleted
        JSONObject filter = new JSONObject().put("_and", 
            new JSONArray().put(new JSONObject().put("_not", 
            new JSONObject().put("_withTag", "deleted=true"))));
        if (thingId != null) {
            filter.getJSONArray("_and").put(new JSONObject().put("source", new JSONObject().put("_eq", SemanticModel.urlDecode(thingId))));
        }

        Set<EventField> fields = new HashSet<>();
        fields.add(EventField.TIMESTAMP);
        fields.add(EventField.VALUE);

        LinkedHashSet<EventField> orderByFields = new LinkedHashSet<>();
        orderByFields.add(EventField.TIMESTAMP);
        EventOrdering orderByOrdering = EventOrdering.DESCENDING;

        return Consumer.createObservations(this.getSWTEvents(metrics, null, timestamp, fields, TimestampPrecision.MILLISECONDS, orderByFields, orderByOrdering, filter, limit, null, null));
    }

    /**
     * Get updateable Observations, this will check tags on LDES for removal/add.
     * @param thingId may be null
     * @param propertyId
     * @param fromTimestamp may be null
     * @param toTimestamp may be null
     * @param orderByFields may be null
     * @param orderByOrdering may be null
     * @param limit may be null
     * @return List<Observation>
     */
    private List<Observation> getUpdateableObservations(String thingId, String propertyId, Long fromTimestamp, Long toTimestamp, LinkedHashSet<EventField> orderByFields, EventOrdering orderByOrdering, Integer limit) {
        Set<String> metrics = new HashSet<>();
        metrics.add(SemanticModel.urlDecode(propertyId));

        JSONObject filter = null;
        if (thingId != null) {
            filter = new JSONObject().put("source", new JSONObject().put("_eq", SemanticModel.urlDecode(thingId)));
        }
        
        // Set the fields needed by default
        Set<EventField> fields = new HashSet<>();
        fields.add(EventField.TIMESTAMP);
        fields.add(EventField.VALUE);
        fields.add(EventField.TAGS);

        List<JSONObject> ldesEvents = this.getSWTEvents(metrics, fromTimestamp, toTimestamp, fields, null, orderByFields, orderByOrdering, filter, limit, null, null);

        // Bin by id, so we can get the latest and drop removed
        // Performance?
        Map<String, Observation> observations = new HashMap<>();
        Map<String, Integer> ldesEventsUpdate = new HashMap<>();
        for (JSONObject ldesEvent : ldesEvents) {
            Map<String, String> tags = this.parseLDESEventTags(ldesEvent);
            String id = this.getLDESEventIdTag(tags);
            Integer updateCount = this.getLDESEventUpdateTag(tags);
            // Skip invalid events (without id tag)
            if (id != null) {
                // Set if newer
                if (!ldesEventsUpdate.containsKey(id) || ldesEventsUpdate.get(id) < updateCount) {
                    ldesEventsUpdate.put(id, updateCount);
                    if (this.getLDESEventDeletedTag(tags)) {
                        // Remove value from map
                        observations.remove(id);
                    }
                    else {
                        observations.put(id, Consumer.createObservation(ldesEvent));
                    }
                }
            }
        }
        return new ArrayList<>(observations.values());
    }
    
    /**
     * Parse the LDES JSONArray string tags to a Map.
     * @param event JSONObject LDES event
     * @return Map<String, String>
     */
    private Map<String, String> parseLDESEventTags(JSONObject event) {
        JSONArray tags = event.getJSONArray("tags");
        Map<String, String> parsedTags = new HashMap<>();
        for (Object tag_ : tags) {
            String tag = (String) tag_;
            String[] parts = tag.split("=");
            parsedTags.put(parts[0], parts[1]);
        }
        return parsedTags;
    }

    /**
     * Get update counter from tags, if update tag could not be found assume 0 (first).
     * @param tags Parsed tags
     * @return int update counter, default 0
     */
    private int getLDESEventUpdateTag(Map<String, String> tags) {
        // No update tag -> default 0
        String value = tags.getOrDefault("update", null);
        return (value != null) ? Integer.parseInt(value) : 0;
    }

    /**
     * Check if the tags contain the deleted tag.
     * @param tags Parsed tags
     * @return Value of deleted, default false
     */
    private boolean getLDESEventDeletedTag(Map<String, String> tags) {
        // No update tag -> default false
        String value = tags.getOrDefault("deleted", null);
        return (value != null) ? Boolean.parseBoolean(value) : false;
    }

    /**
     * Get the id tag of LDES event.
     * @param tags Parsed tags
     * @return String id, default null
     */
    private String getLDESEventIdTag(Map<String, String> tags) {
        // No id tag -> default null
        return tags.getOrDefault("id", null);
    }

    /**
     * Get previous observations, if timestamp is null will get latest observations.
     * Returns empty array when no observations found.
     * @param thingId may be null
     * @param propertyId
     * @param timestamp Non inclusive, may be null
     * @param count Maximum observations to return
     * @return List<Observation>
     */
    private List<Observation> getPreviousObservations(String thingId, String propertyId, Long timestamp, int limit) {
        LinkedHashSet<EventField> orderByFields = new LinkedHashSet<>();
        orderByFields.add(EventField.TIMESTAMP);
        EventOrdering orderByOrdering = EventOrdering.DESCENDING;
        return this.getObservations(thingId, propertyId, null, timestamp, orderByFields, orderByOrdering, limit);
    }

    /**
     * Get Observations from LDES.
     * @param thingId may be null, unencoded
     * @param propertyId unencoded
     * @param fromTimestamp may be null
     * @param toTimestamp may be null
     * @param orderByFields may be null
     * @param orderByOrdering may be null
     * @param limit may be null
     * @return List<Observation>
     */
    private List<Observation> getObservations(String thingId, String propertyId, Long fromTimestamp, Long toTimestamp, LinkedHashSet<EventField> orderByFields, EventOrdering orderByOrdering, Integer limit) {
        Set<String> metrics = new HashSet<>();
        metrics.add(SemanticModel.urlDecode(propertyId));

        JSONObject filter = null;
        if (thingId != null) {
            filter = new JSONObject().put("source", new JSONObject().put("_eq", SemanticModel.urlDecode(thingId)));
        }
        
        // Set the fields needed by default
        Set<EventField> fields = new HashSet<>();
        fields.add(EventField.TIMESTAMP);
        fields.add(EventField.VALUE);

        return Consumer.createObservations(this.getSWTEvents(metrics, fromTimestamp, toTimestamp, fields, TimestampPrecision.MILLISECONDS, orderByFields, orderByOrdering, filter, limit, null, null));
    }

    /**
     * Get all historical events.
     * @param metrics Set of metric IDs or wildcards (e.G. "*::number"), null defaults to all metrics
     * @param fromTimestamp Limit output to events after (and including) this UTC millisecond timestamp, null defaults to no limit
     * @param toTimestamp Limit output to events before (and excluding) this UTC millisecond timestamp, null defaults to no limit
     * @param fields Set of fields to return in the result set, null defaults to [metric, source, value]
     * @param precision Defines the timestamp precision for the returned results, null defaults to milliseconds
     * @param orderByFields Linked set specifying the ordering of the output, null defaults to timestamp
     * @param orderByOrdering Specifies the ordering of the output, null defaults to ascending
     * @param filter Limit output to events matching the specified filter expression, null defaults to no filtering
     * @param limit Limit output to a maximum number of events, also determines the page size, null defaults to 2500, can be null
     * @param limitByFields Limit the combination of a specific set of Index fields to a specified maximum number, can be null, only applies if limitByLimit not null
     * @param limitByLimit Limit the combination of a specific set of Index fields to a specified maximum number, can be null, only applies if limitByFields not null
     * @return List of Event JSONObjects
     */
    private List<JSONObject> getSWTEvents(Set<String> metrics, Long fromTimestamp, Long toTimestamp, Set<EventField> fields, TimestampPrecision precision,
            LinkedHashSet<EventField> orderByFields, EventOrdering orderByOrdering, JSONObject filter, Integer limit, Set<EventField> limitByFields, Integer limitByLimit) {

        Set<String> datasets = new HashSet<>();
        datasets.add(this.datasetId);
        try {
            return this.consumer.getEvents(datasets, metrics, fromTimestamp, toTimestamp, fields, precision, orderByFields, orderByOrdering, filter, limit, limitByFields, limitByLimit);
        } catch (LDESException e) {
            LOGGER.warning("Error getting Semantic Web Thing Events!");
            LOGGER.warning(e.toString());
            throw new RuntimeException(e);
        }
    }

    /**
     * Create an Observation from an LDES Event.
     * @param ldesEvent
     * @return Observation
     */
    private static Observation createObservation(JSONObject ldesEvent) {
        // 2023-03-06T12:54:01.915Z
        Instant timestamp = Instant.parse(ldesEvent.getJSONObject("timestamp").getString("value"));
        return new Observation(timestamp, ldesEvent.get("value"));
    }

    /**
     * Create Observations from a LDES events.
     * @param ldesEvents
     * @return List<Observation>
     */
    private static List<Observation> createObservations(List<JSONObject> ldesEvents) {
        List<Observation> observations = new ArrayList<>();
        for (JSONObject ldesEvent : ldesEvents) {
            observations.add(Consumer.createObservation(ldesEvent));
        }
        return observations;
    }

    @Override
    public List<Observation> getHistoricalActions(Long begin, Long end) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void post(JSONObject actions) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Observation getHistoricalEventById(String id) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
