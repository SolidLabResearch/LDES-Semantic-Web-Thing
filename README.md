# LDES-Semantic-Web-Thing

## Name
LDES-Semantic-Web-Thing

## Description
Aggregations and data summaries allow to provide succinct view on larger dataset. However, to be useful for non-technical users, visualisation is needed the easily interpret the summarised data. Aggregations and summaries from the DAHCC dataset are used for purpose, which contains data streams describing the behaviour of various patients. Specifically, a visualisation of the patients activity is used. This visualisation directly connects to the streaming aggregator defined in Challenge #84 while the Semantic Dashboard will be used for the visualisation itself.

## Solution
The generic visualisation components connects to the aggregator service and allows to visualise the results in the semantic dashboard. The latter allows to specify in a generic manner how to present the visualisation itself. To enable this, a mapping of the internal data of the aggregator to the data format used by the semantic dashboard is necessary (which is build around webThing).

## Screencast

## Functionality
The visualisation is able to:

1. Connect with the aggregation service (as defined in Challenge #84)
2. Map the internal results of the aggregator to the format used by the semantic dashboard (which uses webThing)
3. Allow specifying how to visualise the results in the semantic dashboard

## Installation
### Pre-requisites

1. A running Aggregator service, defined by Challenge #84: https://github.com/argahsuknesib/solid-stream-aggregator
2. The Comunica SPARQL Link Traveral Engine: https://github.com/comunica/comunica-feature-link-traversal/tree/master/engines/query-sparql-link-traversal-solid#usage-as-a-sparql-endpoint. 
* Example usage: `comunica-sparql-link-traversal-solid-http -p 8081 --idp void http://localhost:3000/aggregation_pod/data/ --lenient`
* The example runs the Comunica engine locally on port 8081, connects without user credentials to the Solid Pod containing the Aggregator data in a 
SolidEventSourcing (https://github.com/woutslabbinck/SolidEventSourcing/) compatible manner. 
3. Java version: >= openjdk 16.0.1 2021-04-20 

### Binary utilisation
Clone the GIT repository: https://github.com/SolidLabResearch/LDES-Semantic-Web-Thing.git
The binary can be found in the bin-folder, immediately below root.
Running this binary can be accomplished using the command: `java -jar ldeswebthing-v0.0.1.jar`

### Compiling from source
