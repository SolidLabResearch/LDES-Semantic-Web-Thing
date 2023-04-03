/****************************************************************************
 * be.ugent.idlab.ddashboard.ldeswebthing.Main                              *
 ****************************************************************************/
package be.ugent.idlab.ddashboard.ldeswebthing;

import be.ugent.idlab.ddashboard.semanticwebthing.domain.Event;
import be.ugent.idlab.ddashboard.semanticwebthing.domain.EventRegistry;
import be.ugent.idlab.ddashboard.semanticwebthing.domain.HistoricalProviderInterface;
import be.ugent.idlab.ddashboard.semanticwebthing.domain.SemanticModel;
import be.ugent.idlab.ddashboard.semanticwebthing.domain.Thing;
import be.ugent.idlab.ddashboard.semanticwebthing.domain.ThingRegistry;
import be.ugent.idlab.ddashboard.semanticwebthing.spring.WebThingServer;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.rdf4j.rio.RDFFormat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * Based on/Adapted from the original from obeliskwebthing.
 * 
 * @author Stijn Verstichel (adaptation from original obeliskwebthing)
 * @date 2023-03-09
 * @version 0.1.0
 */
public class Main {

    private final static Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final String CONFIG_PATH = "./src/main/resources/";
    private static final String GIT_CONFIG_PATH = "./git-config/";
    private static final String APP_PROPERTIES = "app.properties";
    private static final String SEMANTIC_DATA = "semantic-data.ttl";

    public static void main(String[] args) {
        WebThingServer server = new WebThingServer();
        server.start();

        Consumer consumer = Main.getInstance();
        consumer.start();
    }

    /**
     * Reading out the properties-file with configuration information
     */
    private static Consumer getInstance() {
        // First get the remote configuration (app.properties and/or semantic-data.ttl) if git url is set in env
        // The url should contain username and password/token if private
        // https://<username>:<deploy_token>@gitlab.ilabt.imec.be/predict/dynamic-dashboard/web-thing/ldes-web-thing-configs/project.git
        if (System.getenv("GIT_CONFIG_URL") != null) {
            String git_url = System.getenv("GIT_CONFIG_URL");
            LOGGER.log(Level.INFO, "Getting configuration from git: {0}", git_url);
            Main.getConfigFromGit(git_url);
        }

        // Creating the Java Properties Reading object.
        Properties appProps = new Properties();
        try {
            String appConfigPath = new File(CONFIG_PATH + APP_PROPERTIES).getCanonicalPath();
            appProps.load(new FileInputStream(appConfigPath));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load properties file", e);
            throw new RuntimeException(e);
        }

        // Get all settings either from env variables or properties file, env have priority
        // Configuring webthing id
        String id = getEnvOrProperties("WEB_THING_ID", appProps, "LDES_in_SOLID");
        String name = getEnvOrProperties("WEB_THING_NAME", appProps, "Challenge85: LDES in Solid Pods");
        // Configuring the Dataset to be used
        String datasetId = getEnvOrProperties("DATASET_ID", appProps, null);
        // Configuring the LDES API endpoint
        String ldesEndpoint = getEnvOrProperties("LDES_ENDPOINT", appProps, null);
        // Configuring the EVENT ID
        String eventId = SemanticModel.urlEncode(getEnvOrProperties("EVENT_ID", appProps, null));
        // Configuring the ROOT URL
        String rootUrl = getEnvOrProperties("ROOT_URL", appProps, null);
        // Import Semantic data from file
        SemanticModel semanticData = new SemanticModel(CONFIG_PATH + SEMANTIC_DATA, RDFFormat.TURTLE, rootUrl);

        // Create things + consumer
        Thing root = new Thing(id, name, null, semanticData, true);
        root.setTags(new ArrayList<>(Arrays.asList("LDES_in_SOLID", "webthing")));
        ThingRegistry.getInstance().setThing(root);
        
        Event rootEvent = new Event(UUID.randomUUID().toString());
        EventRegistry.getInstance().setEvent(rootEvent);

        // Initialise the LDES Consumer.
        Consumer consumer = new Consumer(ldesEndpoint, datasetId, eventId);

        // We're for this use case only working with Historical Event
        // No Push-support for the latest event. No Actions.
        List<HistoricalProviderInterface> providers = new ArrayList<>();
        providers.add(consumer);
        ThingRegistry.getInstance().setHistoricalProviders(providers);
        
        return consumer;
    }

    /**
     * Returns the environment value if it exists else from properties file.
     * @param key
     * @param appProps
     * @param defaultVal May be null, if null will throw exception if not found in env or prop
     * @return
     */
    private static String getEnvOrProperties(String key, Properties appProps, String defaultVal) {
        if (System.getenv(key) != null) {
            return System.getenv(key);
        }
        else if (appProps.getProperty(key) != null) {
            return appProps.getProperty(key);
        }
        else if (defaultVal != null) {
            return defaultVal;
        }
        else {
            throw new RuntimeException("Could not get configuration for key: " + key);
        }
    }

    /**
     * Place config from git into config path
     * @param gitUri Uri wit credentials if private
     */
    private static void getConfigFromGit(String gitUri) {
        try {
            File gitConfig = new File(GIT_CONFIG_PATH);
            File config = new File(CONFIG_PATH);

            // Remove git config if already exists
            FileUtils.deleteDirectory(gitConfig);

            // Copy files from git into temp git config folder
            Git.cloneRepository()
            .setURI(gitUri)
            //.setDirectory(new File(GIT_CONFIG_PATH))
            .setDirectory(gitConfig)
            // .setGitDir(new File(GIT_CONFIG_PATH+"test"))
            .setCloneAllBranches(false)
            .call().close();

            // Discard git data
            FileUtils.deleteDirectory(new File(gitConfig, ".git"));

            // Copy to config folder
            FileUtils.copyDirectory(gitConfig, config);
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Could not get git configuration for: " + gitUri, e);
        }
    }
}