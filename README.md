# SolrThing

**Disclaimer**

This repository is provided "AS-IS" with **no warranty or support** given. This is not an official or supported product/use case. 

Download the extension package from

https://github.com/ptc-iot-sharing/SolrThing/releases/download/v6.0.137/Solr.zip

**Description**

Apache Lucene is a high-performance, full featured text search engine library
written in Java.

Apache Solr is an enterprise search platform written using Apache Lucene.
Major features include full-text search, index replication and sharding, and
result faceting and highlighting.

This project is an extension for TWX that allows it to interface with Solr.

**Services**

`ExecuteQuery(coreName, query, sortExpression, filterExpression, dataShape, maxItems)`: Returns matching records from the Solr server taking into account the name of the data core (coreName), the user input from the search box, as a string (query), according to the data model specified in the Thingworx DataShape (datashape). The maximum number of results can be limited by specifying a integer (maxItems). This is the default search method, implements this [class](https://lucene.apache.org/solr/6_0_0/solr-core/org/apache/solr/query/package-tree.html).

`ExecutePagedQuery(coreName, query, sortExpression, filterQuery, dataShape, startAtIndex, stopAtIndex)`:

Same as ExecuteQuery with the option to specify the index number, so within the entire result set you can start and stop at an arbitrary point. This enables the Next Page/Previous Page functionality within the search mashup CollectionView widget.

`ExecutePHQuery(coreName, query, sortExpression, filterQuery, dataShape, startAtIndex, stopAtIndex)`:

Same as ExecutePagedQuery but returns the data with html tags highlighting the search term. This highlighting can be viewed in a html text area widget.

`saveDatashape(coreName,dataShapeName)`:

Calls the getDatashape service to automatically generate the Thingworx DataShape from the Solr data model. This is run before the query and the result is used as a parameter on the Solr query services.

`GetNumberOfResults(coreName,query, sortExpression, filterQuery, dataShape)`

Returns the number of records matching a query, without the associated records. This is used to update the mashup labels describing how many results have been found.

`IndexDocument(coreName, document-JSON) and IndexMultipleDocuments`

This provides a facility to import data into the Solr server, if its formatted as JSON. This is not used in the current implementation.

`ExecuteFuzzyQuery(currentTerm, maxEdits, prefixLen, maxExpansions, transpositions)`

An implementation of the Apache Lucene Query Parser and Lucene Search Fuzzy Query class that allows to configure fuzzy querying parameters. Experimental, implements this [class](https://lucene.apache.org/core/6_4_2/core/org/apache/lucene/search/FuzzyQuery.html).



**Configuration**

*password* - the password if it is configured

*serverName* - the IP address or hostname of the Solr server

*serverPort* - the port that is configured to receive queries on the Solr server

*userName* - the username that will be used together with the password to authenticate, can be blank

*timeout* - after this interval if a response is not received the connection will time out, in miliseconds

*useSSL* - a boolean that indicates whether or not the connection is secured with SSL



## Online Documentation

This README file only contains basic setup instructions.  For more
comprehensive documentation, visit:

- Lucene: <http://lucene.apache.org/core/documentation.html>
- Solr: <http://lucene.apache.org/solr/guide/>

  # Disclaimer
By downloading this software, the user acknowledges that it is unsupported, not reviewed for security purposes, and that the user assumes all risk for running it.

Users accept all risk whatsoever regarding the security of the code they download.

This software is not an official PTC product and is not officially supported by PTC.

PTC is not responsible for any maintenance for this software.

PTC will not accept technical support cases logged related to this Software.

This source code is offered freely and AS IS without any warranty.

The author of this code cannot be held accountable for the well-functioning of it.

The author shared the code that worked at a specific moment in time using specific versions of PTC products at that time, without the intention to make the code compliant with past, current or future versions of those PTC products.

The author has not committed to maintain this code and he may not be bound to maintain or fix it.


# License
I accept the MIT License (https://opensource.org/licenses/MIT) and agree that any software downloaded/utilized will be in compliance with that Agreement. However, despite anything to the contrary in the License Agreement, I agree as follows:

I acknowledge that I am not entitled to support assistance with respect to the software, and PTC will have no obligation to maintain the software or provide bug fixes or security patches or new releases.

The software is provided “As Is” and with no warranty, indemnitees or guarantees whatsoever, and PTC will have no liability whatsoever with respect to the software, including with respect to any intellectual property infringement claims or security incidents or data loss.

