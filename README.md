# LDES-Semantic-Web-Thing

## Name
LDES-Semantic-Web-Thing

## Description
Aggregations and data summaries allow to provide succinct view on larger dataset. However, to be useful for non-technical users, visualisation is needed the easily interpret the summarised data. Aggregations and summaries from the DAHCC dataset are used for purpose, which contains data streams describing the behaviour of various patients. Specifically, a visualisation of the patients activity is used. This visualisation directly connects to the streaming aggregator defined in Challenge #84 while the Semantic Dashboard will be used for the visualisation itself.

## Solution
The generic visualisation components connects to the aggregator service and allows to visualise the results in the semantic dashboard. The latter allows to specify in a generic manner how to present the visualisation itself. To enable this, a mapping of the internal data of the aggregator to the data format used by the semantic dashboard is necessary (which is build around webThing).
