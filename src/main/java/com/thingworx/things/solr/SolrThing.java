/*
 * Copyright (c) 2018.  PTC Inc. and/or Its Subsidiary Companies. All Rights Reserved.
 * Copyright for PTC software products is with PTC Inc. and its subsidiary companies (collectively “PTC”), and their respective licensors. This software is provided under written license agreement, contains valuable trade secrets and proprietary information, and is protected by the copyright laws of the United States and other countries. It may not be copied or distributed in any form or medium, disclosed to third parties, or used in any manner not provided for in the software license agreement except with written prior approval from PTC.
 *
 */

package com.thingworx.things.solr;

import com.thingworx.common.utils.HttpUtilities;
import com.thingworx.data.util.InfoTableInstanceFactory;
import com.thingworx.datashape.DataShape;
import com.thingworx.entities.utils.EntityUtilities;
import com.thingworx.metadata.DataShapeDefinition;
import com.thingworx.metadata.FieldDefinition;
import com.thingworx.metadata.annotations.*;
import com.thingworx.relationships.RelationshipTypes.ThingworxRelationshipTypes;
import com.thingworx.things.Thing;
import com.thingworx.types.BaseTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.data.projections.GenericQuery;
import com.thingworx.types.data.queries.Query;
import com.thingworx.types.data.sorters.ISort;
import com.thingworx.types.data.sorters.SortCollection;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.*;
import java.util.Map.Entry;

import static org.apache.lucene.analysis.en.EnglishAnalyzer.ENGLISH_STOP_WORDS_SET;

/**
 * An implementation of the Apache Solr API v7.5.0 for Thingworx 8.0 using the SolrJ library
 * <p>
 * API Parameters:
 * q.alt - An alternate query to be used in cases where the main query (q) is not specified (or blank).
 * tie - (Tie breaker) float value to use as tiebreaker in DisjunctionMaxQueries (should be something much less than 1)
 * qf - (Query Fields) fields and boosts to use when building DisjunctionMaxQueries from the users query. Format is: "fieldA^1.0 fieldB^2.2".
 * mm - (Minimum Match) this supports a wide variety of complex expressions. read SolrPluginUtils.setMinShouldMatch and mm expression format for details.
 * pf - (Phrase Fields) fields/boosts to make phrase queries out of, to boost the users query for exact matches on the specified fields.
 * qs - (Query Slop) amount of slop on phrase queries explicitly specified in the "q" for qf fields.
 * bq - (Boost Query) a raw lucene query that will be included in the users query to influence the score.
 * bf - (Boost Functions) functions (with optional boosts) that will be included in the users query to influence the score.
 * fq - (Filter Query) a raw lucene query that can be used to restrict the super set of products we are interested in - more efficient then using bq, but doesn't influence score.
 */

@ThingworxConfigurationTableDefinitions(
        tables = {@ThingworxConfigurationTableDefinition(
                name = "ConnectionInfo",
                description = "Connection Settings",
                isMultiRow = false,
                dataShape = @ThingworxDataShapeDefinition(
                        fields = {@ThingworxFieldDefinition(
                                name = "serverName",
                                description = "Solr Server name",
                                baseType = "STRING"
                        ), @ThingworxFieldDefinition(
                                name = "serverPort",
                                description = "Solr Server port",
                                baseType = "NUMBER",
                                aspects = {"defaultValue:80"}
                        ), @ThingworxFieldDefinition(
                                name = "useSSL",
                                description = "Use an SSL connection",
                                baseType = "BOOLEAN",
                                aspects = {"defaultValue:false"}
                        ), @ThingworxFieldDefinition(
                                name = "userName",
                                description = "User name",
                                baseType = "STRING",
                                aspects = {"defaultValue:su"}
                        ), @ThingworxFieldDefinition(
                                name = "password",
                                description = "Password",
                                baseType = "PASSWORD"
                        ), @ThingworxFieldDefinition(
                                name = "timeout",
                                description = "Timeout (milliseconds) to execute a request",
                                baseType = "NUMBER",
                                aspects = {"defaultValue:60000"}
                        )}
                )
        )}
)
public class SolrThing extends Thing {
    public static final String SOLR_WEBAPP = "solr";
    private String _serverName;
    private int _serverPort = 80;
    private Boolean _useSSL = false;
    private String _username = "";
    private String _password = "";
    private int _timeout = 60000;

    public SolrThing() {
    }

    public static void print(QueryResponse response) {
        SolrDocumentList docs = response.getResults();
        if (docs != null) {
            _logger.info(docs.getNumFound() + " documents found, " + docs.size() + " returned : ");

            for (int i = 0; i < docs.size(); ++i) {
                SolrDocument doc = docs.get(i);
                _logger.info(doc.toString());
            }
        }

        List<FacetField> fieldFacets = response.getFacetFields();
        Iterator wordsIterator;
        if (fieldFacets != null && !fieldFacets.isEmpty()) {
            System.out.println("\nField Facets : ");

            for (Iterator facetFieldIterator = fieldFacets.iterator(); facetFieldIterator.hasNext(); System.out.println("\u0099")) {
                FacetField fieldFacet = (FacetField) facetFieldIterator.next();
                System.out.print("\t" + fieldFacet.getName() + " :\t");
                if (fieldFacet.getValueCount() > 0) {
                    wordsIterator = fieldFacet.getValues().iterator();

                    while (wordsIterator.hasNext()) {
                        Count count = (Count) wordsIterator.next();
                        System.out.print(count.getName() + "[" + count.getCount() + "]\t");
                    }
                }
            }
        }

        Map<String, Integer> queryFacets = response.getFacetQuery();
        if (queryFacets != null && !queryFacets.isEmpty()) {
            System.out.println("\u0099\nQuery facets : ");
            Iterator stringIterator = queryFacets.keySet().iterator();

            while (stringIterator.hasNext()) {
                String queryFacet = (String) stringIterator.next();
                System.out.println("\t" + queryFacet + "\t[" + queryFacets.get(queryFacet) + "]");
            }

            System.out.println("\u0099");
        }

        NamedList<NamedList<Object>> spellCheckResponse = (NamedList) response.getResponse().get("spellcheck");
        if (spellCheckResponse != null) {
            for (wordsIterator = spellCheckResponse.iterator(); wordsIterator.hasNext(); System.out.println("\u0099")) {
                Entry<String, NamedList<Object>> entry = (Entry) wordsIterator.next();
                String word = entry.getKey();
                NamedList<Object> spellCheckWordResponse = entry.getValue();
                boolean correct = spellCheckWordResponse.get("frequency").equals(1);
                System.out.println("Word: " + word + ",\tCorrect?: " + correct);
                NamedList<Integer> suggestions = (NamedList) spellCheckWordResponse.get("suggestions");
                if (suggestions != null && suggestions.size() > 0) {
                    System.out.println("Suggestions : ");
                    Iterator suggestionsIterator = suggestions.iterator();

                    while (suggestionsIterator.hasNext()) {
                        System.out.println("\t" + ((Entry) suggestionsIterator.next()).getKey());
                    }
                }
            }
        }

    }

    protected void initializeThing() {
        this._serverName = (String) this.getConfigurationSetting("ConnectionInfo", "serverName");
        this._serverPort = ((Number) this.getConfigurationSetting("ConnectionInfo", "serverPort")).intValue();
        this._username = (String) this.getConfigurationSetting("ConnectionInfo", "userName");
        this._password = (String) this.getConfigurationSetting("ConnectionInfo", "password");
        this._timeout = ((Number) this.getConfigurationSetting("ConnectionInfo", "timeout")).intValue();
        this._useSSL = (Boolean) this.getConfigurationSetting("ConnectionInfo", "useSSL");
    }

    @ThingworxServiceDefinition(
            name = "GetNumberOfResults",
            description = "Returns the number of results for the specified query as a Number:Double type"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "NUMBER"
    )
    public Double GetNumberOfResults(@ThingworxServiceParameter(name = "coreName", description = "Core/index name", baseType = "STRING") String coreName,
                                     @ThingworxServiceParameter(name = "query", description = "Solr query string", baseType = "STRING") String query,
                                     @ThingworxServiceParameter(name = "sortExpression", description = "Sort expression", baseType = "QUERY") JSONObject sortExpression,
                                     @ThingworxServiceParameter(name = "filterQuery", description = "fq parameter from Solr API", baseType = "STRING") String filterQuery,
                                     @ThingworxServiceParameter(name = "dataShape", description = "Data shape", baseType = "DATASHAPENAME") String dataShape)
            throws Exception {

        int maxItems = 0;

        DataShape ds = (DataShape) EntityUtilities.findEntity(dataShape, ThingworxRelationshipTypes.DataShape);
        if (ds == null) {
            throw new Exception("Could not execute query because the Datashape does not exist, or a Datashape was not specified [" + dataShape + "]");
        } else {
            InfoTable it = InfoTableInstanceFactory.createInfoTableFromDataShape(ds.getDataShape());
            double timeout = 60000.0D;
            if (this._timeout > 0) {
                timeout = (double) this._timeout;
            }
            boolean ignoreSSLErrors = false;
            boolean useNTLM = false;
            boolean useProxy = false;
            CloseableHttpClient client = HttpUtilities.createHttpClient(this._username, this._password, ignoreSSLErrors, timeout, useNTLM, "", "", useProxy, "", -1, "");
            SolrClient server = new HttpSolrClient.Builder(this.buildBaseURL(coreName).toString()).withHttpClient(client).build();
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(query);
            solrQuery.setStart(0);
            solrQuery.setRows(maxItems);

            if (filterQuery != null) {
                solrQuery.addFilterQuery(String.valueOf(filterQuery));
            }

            if (sortExpression != null) {
                Query sortQuery = new Query(sortExpression);
                SortCollection sorters = (SortCollection) sortQuery.getSorters();
                Iterator sortIterator = sorters.getSorters().iterator();

                while (sortIterator.hasNext()) {
                    ISort sorter = (ISort) sortIterator.next();
                    if (sorter.isAscending()) {
                        solrQuery.addSort(SolrQuery.SortClause.create(sorter.getFieldName(), ORDER.asc));
                    } else {
                        solrQuery.addSort(SolrQuery.SortClause.create(sorter.getFieldName(), ORDER.desc));
                    }
                }
            }
            long timeStart = System.currentTimeMillis();

            QueryResponse qr = null;

            try {
                qr = server.query(solrQuery);
                if (_logger.isInfoEnabled()) {
                    print(qr);
                }

                SolrDocumentList docs = qr.getResults();
                if (docs != null) {
                    long numFound1 = qr.getResults().getNumFound();
                    int numFound = (int) numFound1;

                    ValueCollection values = new ValueCollection();
                    Iterator iteratorNum = it.getDataShape().getFields().values().iterator();

                    FieldDefinition fieldDefinition = (FieldDefinition) iteratorNum.next();
                    values.put(fieldDefinition.getName(), BaseTypes.ConvertToPrimitive(numFound, fieldDefinition.getBaseType()));

                    it.addRow(values);
                }
            } catch (Exception exceptionError) {
                _logger.error("Error Executing Query: " + exceptionError.getMessage());
            }

            _logger.info("query took " + (System.currentTimeMillis() - timeStart) + " ms");
            return new Double(qr.getResults().getNumFound());
        }
    }

    /*For the standard request handler, "boost" the clause on the title field: q=title:superman^2 subject:superman
    Using the dismax request handler, one can specify boosts on fields in parameters such as
    qf: q=superman&qf=title^2 subject If no other sort order is specified, the default is by relevancy score.*/

    @ThingworxServiceDefinition(
            name = "ExecuteBoostedQuery",
            description = "Return more relevant documents by specifying important fields, i.e. q=superman&qf=title^2 subject (uses DisMax Query Parser)"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "INFOTABLE"
    )
    public InfoTable ExecuteBoostedQuery(@ThingworxServiceParameter(name = "coreName", description = "Core/index name", baseType = "STRING") String coreName,
                                         @ThingworxServiceParameter(name = "query", description = "Solr query string", baseType = "STRING") String query,
                                         @ThingworxServiceParameter(name = "sortExpression", description = "Sort expression", baseType = "QUERY") JSONObject sortExpression,
                                         @ThingworxServiceParameter(name = "filterQuery", description = "fq parameter from Solr API", baseType = "STRING") String filterQuery,
                                         @ThingworxServiceParameter(name = "dataShape", description = "Data shape", baseType = "DATASHAPENAME") String dataShape,
                                         //boosted query specific parameters
                                         @ThingworxServiceParameter(name = "enableMoreLikeThis", description = "enables the use of similarity functions via Solr API", baseType = "BOOLEAN") Boolean enableMoreLikeThis,
                                         @ThingworxServiceParameter(name = "queryFields", description = "qf: specify boosts on fields in parameters", baseType = "STRING") String queryFields)
            throws Exception {
        Double maxItems = new Double("500");
        DataShape ds = (DataShape) EntityUtilities.findEntity(dataShape, ThingworxRelationshipTypes.DataShape);

        if (ds == null) {
            throw new Exception("Could not execute query because the Datashape does not exist, or a Datashape was not specified [" + dataShape + "]");
        } else {
            InfoTable it = InfoTableInstanceFactory.createInfoTableFromDataShape(ds.getDataShape());
            double timeout = 60000.0D;
            if (this._timeout > 0) {
                timeout = (double) this._timeout;
            }
            boolean ignoreSSLErrors = false;
            boolean useNTLM = false;
            boolean useProxy = false;
            CloseableHttpClient client = HttpUtilities.createHttpClient(this._username, this._password, ignoreSSLErrors, timeout, useNTLM, "", "", useProxy, "", -1, "");
            SolrClient server = new HttpSolrClient.Builder(this.buildBaseURL(coreName).toString()).withHttpClient(client).build();
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(query);
            solrQuery.setStart(0);
            solrQuery.setRows(maxItems.intValue());
            if (sortExpression != null) {
                Query sortQuery = new Query(sortExpression);
                SortCollection sorters = (SortCollection) sortQuery.getSorters();
                Iterator sortIterator = sorters.getSorters().iterator();

                while (sortIterator.hasNext()) {
                    ISort sorter = (ISort) sortIterator.next();
                    if (sorter.isAscending()) {
                        solrQuery.addSort(SolrQuery.SortClause.create(sorter.getFieldName(), ORDER.asc));
                    } else {
                        solrQuery.addSort(SolrQuery.SortClause.create(sorter.getFieldName(), ORDER.desc));
                    }
                }
            }
            if (filterQuery != null) {
                solrQuery.addFilterQuery(String.valueOf(filterQuery));
            }

            long timeStart = System.currentTimeMillis();
            if (enableMoreLikeThis == true) {
                solrQuery.setMoreLikeThis(true);
                solrQuery.setIncludeScore(true);
            }
            solrQuery.setMoreLikeThisQF(queryFields);
            solrQuery.addMoreLikeThisField(queryFields); //doc These fields must also be added using addMoreLikeThisField(String).

            try {
                QueryResponse qr = server.query(solrQuery);
                if (_logger.isInfoEnabled()) {
                    print(qr);
                }

                SolrDocumentList docs = qr.getResults();
                if (docs != null) {
                    for (int i = 0; i < docs.size() && (double) i <= maxItems; ++i) {
                        SolrDocument doc = docs.get(i);
                        ValueCollection values = new ValueCollection();
                        Iterator fieldDefinitionIterator = it.getDataShape().getFields().values().iterator();

                        while (fieldDefinitionIterator.hasNext()) {
                            FieldDefinition fieldDefinition = (FieldDefinition) fieldDefinitionIterator.next();
                            Object value = doc.get(fieldDefinition.getName());
                            if (value != null) {
                                values.put(fieldDefinition.getName(), BaseTypes.ConvertToPrimitive(value, fieldDefinition.getBaseType()));
                            }
                        }

                        it.addRow(values);
                    }
                }
            } catch (Exception exceptionErrorQ) {
                _logger.error("Error Executing Query: " + exceptionErrorQ.getMessage());
            }

            _logger.info("query took " + (System.currentTimeMillis() - timeStart) + " ms");
            return it;
        }
    }


    @ThingworxServiceDefinition(
            name = "ExecuteQuery",
            description = "Execute an Solr query and return an Infotable"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "INFOTABLE"
    )
    public InfoTable ExecuteQuery(@ThingworxServiceParameter(name = "coreName", description = "Core/index name", baseType = "STRING") String coreName,
                                  @ThingworxServiceParameter(name = "query", description = "Solr query string", baseType = "STRING") String query,
                                  @ThingworxServiceParameter(name = "sortExpression", description = "Sort expression", baseType = "QUERY") JSONObject sortExpression,
                                  @ThingworxServiceParameter(name = "filterExpression", description = "Post-query sort/filter expression", baseType = "QUERY") JSONObject filterExpression,
                                  @ThingworxServiceParameter(name = "dataShape", description = "Data shape", baseType = "DATASHAPENAME") String dataShape,
                                  @ThingworxServiceParameter(name = "maxItems", description = "Max items to return", baseType = "NUMBER") Double maxItems) throws Exception {
        if (maxItems == null) {
            maxItems = new Double(500.0D);
        }

        DataShape ds = (DataShape) EntityUtilities.findEntity(dataShape, ThingworxRelationshipTypes.DataShape);
        if (ds == null) {
            throw new Exception("Could not execute query because the Datashape does not exist, or a Datashape was not specified [" + dataShape + "]");
        } else {
            InfoTable it = InfoTableInstanceFactory.createInfoTableFromDataShape(ds.getDataShape());
            double timeout = 60000.0D;
            if (this._timeout > 0) {
                timeout = (double) this._timeout;
            }
            boolean ignoreSSLErrors = false;
            boolean useNTLM = false;
            boolean useProxy = false;
            CloseableHttpClient client = HttpUtilities.createHttpClient(this._username, this._password, ignoreSSLErrors, timeout, useNTLM, "", "", useProxy, "", -1, "");
            SolrClient server = new HttpSolrClient.Builder(this.buildBaseURL(coreName).toString()).withHttpClient(client).build();
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(query);
            solrQuery.setStart(0);
            solrQuery.setRows(maxItems.intValue());
            if (sortExpression != null) {
                Query sortQuery = new Query(sortExpression);
                SortCollection sorters = (SortCollection) sortQuery.getSorters();
                Iterator sortIterator = sorters.getSorters().iterator();

                while (sortIterator.hasNext()) {
                    ISort sorter = (ISort) sortIterator.next();
                    if (sorter.isAscending()) {
                        solrQuery.addSort(SolrQuery.SortClause.create(sorter.getFieldName(), ORDER.asc));
                    } else {
                        solrQuery.addSort(SolrQuery.SortClause.create(sorter.getFieldName(), ORDER.desc));
                    }
                }
            }
            long timeStart = System.currentTimeMillis();

            try {
                QueryResponse qr = server.query(solrQuery);
                if (_logger.isInfoEnabled()) {
                    print(qr);
                }

                SolrDocumentList docs = qr.getResults();
                if (docs != null) {
                    for (int i = 0; i < docs.size() && (double) i <= maxItems; ++i) {
                        SolrDocument doc = docs.get(i);
                        ValueCollection values = new ValueCollection();
                        Iterator fieldDefinitionIterator = it.getDataShape().getFields().values().iterator();

                        while (fieldDefinitionIterator.hasNext()) {
                            FieldDefinition fieldDefinition = (FieldDefinition) fieldDefinitionIterator.next();
                            Object value = doc.get(fieldDefinition.getName());
                            if (value != null) {
                                values.put(fieldDefinition.getName(), BaseTypes.ConvertToPrimitive(value, fieldDefinition.getBaseType()));
                            }
                        }

                        it.addRow(values);
                    }
                }
            } catch (Exception exceptionErrorQ) {
                _logger.error("Error Executing Query: " + exceptionErrorQ.getMessage());
            }

            if (filterExpression != null) {
                it = GenericQuery.query(it, filterExpression);
            }

            _logger.info("query took " + (System.currentTimeMillis() - timeStart) + " ms");
            return it;
        }
    }

    @ThingworxServiceDefinition(
            name = "ExecutePagedQuery",
            description = "Execute an Solr query with a specified document interval and return an Infotable"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "INFOTABLE"
    )
    public InfoTable ExecutePagedQuery(@ThingworxServiceParameter(name = "coreName", description = "Core/index name", baseType = "STRING") String coreName,
                                       @ThingworxServiceParameter(name = "query", description = "Solr query string", baseType = "STRING") String query,
                                       @ThingworxServiceParameter(name = "sortExpression", description = "Sort expression", baseType = "QUERY") JSONObject sortExpression,
                                       @ThingworxServiceParameter(name = "filterExpression", description = "Query-based filter expression", baseType = "STRING") String filterExpression,
                                       @ThingworxServiceParameter(name = "dataShape", description = "Data shape", baseType = "DATASHAPENAME") String dataShape,
                                       @ThingworxServiceParameter(name = "startAtIndex", description = "Lower Limit", baseType = "NUMBER") Double startAtIndex,
                                       @ThingworxServiceParameter(name = "stopAtIndex", description = "Upper Limit", baseType = "NUMBER") Double stopAtIndex)
            throws Exception {

        DataShape ds = (DataShape) EntityUtilities.findEntity(dataShape, ThingworxRelationshipTypes.DataShape);
        if (ds == null) {
            throw new Exception("Could not execute query because the Datashape does not exist, or a Datashape was not specified [" + dataShape + "]");
        } else {
            InfoTable it = InfoTableInstanceFactory.createInfoTableFromDataShape(ds.getDataShape());
            double timeout = 60000.0D;
            if (this._timeout > 0) {
                timeout = (double) this._timeout;
            }
            boolean ignoreSSLErrors = false;
            boolean useNTLM = false;
            boolean useProxy = false;
            CloseableHttpClient client = HttpUtilities.createHttpClient(this._username, this._password, ignoreSSLErrors, timeout, useNTLM, "", "", useProxy, "", -1, "");
            SolrClient server = new HttpSolrClient.Builder(this.buildBaseURL(coreName).toString()).withHttpClient(client).build();
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(query);

            if (filterExpression != null) {
                solrQuery.addFilterQuery(String.valueOf(filterExpression));
            }

            solrQuery.setStart(Integer.valueOf(startAtIndex.intValue()));
            solrQuery.setRows(Integer.valueOf(stopAtIndex.intValue()) - Integer.valueOf(startAtIndex.intValue()));

            if (sortExpression != null) {
                Query sortQuery = new Query(sortExpression);
                SortCollection sorters = (SortCollection) sortQuery.getSorters();
                Iterator sortIterator = sorters.getSorters().iterator();

                while (sortIterator.hasNext()) {
                    ISort sorter = (ISort) sortIterator.next();
                    if (sorter.isAscending()) {
                        solrQuery.addSort(SolrQuery.SortClause.create(sorter.getFieldName(), ORDER.asc));
                    } else {
                        solrQuery.addSort(SolrQuery.SortClause.create(sorter.getFieldName(), ORDER.desc));
                    }
                }
            }
            long timeStart = System.currentTimeMillis();

            try {
                QueryResponse qr = server.query(solrQuery);
                if (_logger.isInfoEnabled()) {
                    print(qr);
                }

                SolrDocumentList docs = qr.getResults();
                if (docs != null) {
                    for (int i = 0; i < docs.size(); ++i) {
                        SolrDocument doc = docs.get(i);
                        ValueCollection values = new ValueCollection();
                        Iterator fieldDefinitionIterator = it.getDataShape().getFields().values().iterator();

                        while (fieldDefinitionIterator.hasNext()) {
                            FieldDefinition fieldDefinition = (FieldDefinition) fieldDefinitionIterator.next();
                            Object value = doc.get(fieldDefinition.getName());
                            if (value != null) {
                                values.put(fieldDefinition.getName(), BaseTypes.ConvertToPrimitive(value, fieldDefinition.getBaseType()));
                            }
                        }

                        it.addRow(values);
                    }
                }
            } catch (Exception exceptionError) {
                _logger.error("Error Executing Query: " + exceptionError.getMessage());
            }


            _logger.info("query took " + (System.currentTimeMillis() - timeStart) + " ms");
            return it;
        }
    }

    @ThingworxServiceDefinition(
            name = "ExecutePHQuery",
            description = "Execute an Solr query with a specified document interval and return an Infotable"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "INFOTABLE"
    )

    /*
     *This is the same as running a paged query (P) with the additional feature of returning the highlighted (H)
     * search term with html tags. This makes it so you can display the infotable contents within an html text area
     * widget.
     */

    public InfoTable ExecutePHQuery(@ThingworxServiceParameter(name = "coreName", description = "Core/index name", baseType = "STRING") String coreName,
                                    @ThingworxServiceParameter(name = "query", description = "Solr query string", baseType = "STRING") String query,
                                    @ThingworxServiceParameter(name = "sortExpression", description = "Sort expression", baseType = "QUERY") JSONObject sortExpression,
                                    @ThingworxServiceParameter(name = "filterExpression", description = "Query-based filter expression", baseType = "STRING") String filterExpression,
                                    @ThingworxServiceParameter(name = "dataShape", description = "Data shape", baseType = "DATASHAPENAME") String dataShape,
                                    @ThingworxServiceParameter(name = "startAtIndex", description = "Lower Limit", baseType = "NUMBER") Double startAtIndex,
                                    @ThingworxServiceParameter(name = "stopAtIndex", description = "Upper Limit", baseType = "NUMBER") Double stopAtIndex)
            throws Exception {

        DataShape ds = (DataShape) EntityUtilities.findEntity(dataShape, ThingworxRelationshipTypes.DataShape);
        if (ds == null) {
            throw new Exception("Could not execute query because the Datashape does not exist, or a Datashape was not specified [" + dataShape + "]");
        } else {
            InfoTable it = InfoTableInstanceFactory.createInfoTableFromDataShape(ds.getDataShape());
            double timeout = 60000.0D;
            if (this._timeout > 0) {
                timeout = (double) this._timeout;
            }
            boolean ignoreSSLErrors = false;
            boolean useNTLM = false;
            boolean useProxy = false;
            CloseableHttpClient client = HttpUtilities.createHttpClient(this._username, this._password, ignoreSSLErrors, timeout, useNTLM, "", "", useProxy, "", -1, "");
            SolrClient server = new HttpSolrClient.Builder(this.buildBaseURL(coreName).toString()).withHttpClient(client).build();

            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(query);

            //set solr highlighter parameters
            solrQuery.addHighlightField("*");
            solrQuery.setHighlight(true);
            solrQuery.setHighlightFragsize(0);
            solrQuery.setHighlightSimplePre("<span style=\"background-color: #FFFF00\">");
            solrQuery.setHighlightSimplePost("</span>");

            if (filterExpression != null) {
                solrQuery.addFilterQuery(String.valueOf(filterExpression));
            }

            solrQuery.setStart(Integer.valueOf(startAtIndex.intValue()));
            solrQuery.setRows(Integer.valueOf(stopAtIndex.intValue()) - Integer.valueOf(startAtIndex.intValue()));

            if (sortExpression != null) {
                Query sortQuery = new Query(sortExpression);
                SortCollection sorters = (SortCollection) sortQuery.getSorters();
                Iterator sortIterator = sorters.getSorters().iterator();

                while (sortIterator.hasNext()) {
                    ISort sorter = (ISort) sortIterator.next();
                    if (sorter.isAscending()) {
                        solrQuery.addSort(SolrQuery.SortClause.create(sorter.getFieldName(), ORDER.asc));
                    } else {
                        solrQuery.addSort(SolrQuery.SortClause.create(sorter.getFieldName(), ORDER.desc));
                    }
                }
            }
            long timeStart = System.currentTimeMillis();

            try {
                QueryResponse qr = server.query(solrQuery);
                if (_logger.isInfoEnabled()) {
                    print(qr);
                }

                Map<String, Map<String, List<String>>> highlighting = qr.getHighlighting();

                SolrDocumentList docs = qr.getResults();
                if (docs != null) {
                    for (int i = 0; i < docs.size() && (double) i <= new Integer(500); ++i) {
                        SolrDocument doc = docs.get(i);
                        ValueCollection values = new ValueCollection();
                        Iterator fieldDefinitionIterator = it.getDataShape().getFields().values().iterator();

                        while (fieldDefinitionIterator.hasNext()) {
                            FieldDefinition fieldDefinition = (FieldDefinition) fieldDefinitionIterator.next();
                            Object value = doc.get(fieldDefinition.getName());
                            String rowId = (String) doc.get("id");
                            if (highlighting.containsKey(rowId) && highlighting.get(rowId).containsKey(fieldDefinition.getName())) {
                                value = highlighting.get(rowId).get(fieldDefinition.getName());
                            }
                            if (value != null) {
                                values.put(fieldDefinition.getName(), BaseTypes.ConvertToPrimitive(value, fieldDefinition.getBaseType()));
                            }
                        }

                        it.addRow(values);
                    }
                }
            } catch (Exception exceptionError) {
                _logger.error("Error Executing Query: " + exceptionError.getMessage());
            }


            _logger.info("query took " + (System.currentTimeMillis() - timeStart) + " ms");
            return it;
        }
    }


    @ThingworxServiceDefinition(
            name = "ExecuteFuzzyQuery",
            description = "Execute an Solr query with a specified document interval and return an Infotable"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "INFOTABLE"
    )

    /*
     *An implementation of the FuzzyQuery methods from org.apache.lucene.search.FuzzyQuery
     * term - the term to search for
     * maxEdits - must be >= 0 and <= LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE.
     * prefixLength - length of common (non-fuzzy) prefix
     * maxExpansions - the maximum number of terms to match.
     * transpositions - true if transpositions should be treated as a primitive edit operation.
     */

    public InfoTable ExecuteFuzzyQuery(
            @ThingworxServiceParameter(name = "coreName", description = "Core/index name", baseType = "STRING") String coreName,
            @ThingworxServiceParameter(name = "dataShape", description = "Data shape", baseType = "DATASHAPENAME") String dataShape,
            //fuzzy parameters
            @ThingworxServiceParameter(name = "String field name", description = "at least one field must be defined as string", baseType = "STRING") String strField,
            @ThingworxServiceParameter(name = "Default field name", description = "field assumed to be the default field and omitted from the query", baseType = "STRING") String ommitField,
            @ThingworxServiceParameter(name = "Term", description = "the term to search for", baseType = "STRING") String strTerm,
            @ThingworxServiceParameter(name = "Prefix Length", description = "the non-fuzzy prefix length", baseType = "STRING") String prefixLen,
            @ThingworxServiceParameter(name = "maxEdits", description = "minimum similarity as int", baseType = "STRING") String sMaxEdits,
            @ThingworxServiceParameter(name = "maxExpansions", description = "the maximum number of terms to match", baseType = "STRING") String maxExpansions,
            @ThingworxServiceParameter(name = "transpositions", description = "true if transpositions should be treated as a primitive edit operation", baseType = "BOOLEAN") Boolean transpositions)
            throws Exception {

        DataShape ds = (DataShape) EntityUtilities.findEntity(dataShape, ThingworxRelationshipTypes.DataShape);
        if (ds == null) {
            throw new Exception("Could not execute query because the Datashape does not exist, or a Datashape was not specified [" + dataShape + "]");
        } else {
            InfoTable it = InfoTableInstanceFactory.createInfoTableFromDataShape(ds.getDataShape());
            double timeout = 60000.0D;
            if (this._timeout > 0) {
                timeout = (double) this._timeout;
            }
            boolean ignoreSSLErrors = false;
            boolean useNTLM = false;
            boolean useProxy = false;
            CloseableHttpClient client = HttpUtilities.createHttpClient(this._username, this._password, ignoreSSLErrors, timeout, useNTLM, "", "", useProxy, "", -1, "");
            SolrClient server = new HttpSolrClient.Builder(this.buildBaseURL(coreName).toString()).withHttpClient(client).build();

            long timeStart = System.currentTimeMillis();

            //build fuzzy query
            int maxEdits = Integer.parseInt(sMaxEdits);
            Analyzer standardAnalyzer = new StandardAnalyzer(ENGLISH_STOP_WORDS_SET);

            StandardQueryParser standardQueryParser = new StandardQueryParser(standardAnalyzer);

            TokenStream term = standardQueryParser.getAnalyzer().tokenStream(strField, strTerm);
            Term currentTerm = new Term(strField, String.valueOf(term));
            FuzzyQuery queryFz = new FuzzyQuery(currentTerm, maxEdits, Integer.valueOf(prefixLen), Integer.valueOf(maxExpansions), Boolean.valueOf(transpositions));
            SolrQuery solrQuery = new SolrQuery(queryFz.toString());

            try {
                QueryResponse qr = server.query(solrQuery);
                if (_logger.isInfoEnabled()) {
                    print(qr);
                }

                SolrDocumentList docs = qr.getResults();
                if (docs != null) {
                    for (int i = 0; i < docs.size() && (double) i <= new Integer(500); ++i) {
                        SolrDocument doc = docs.get(i);
                        ValueCollection values = new ValueCollection();
                        Iterator fieldDefinitionIterator = it.getDataShape().getFields().values().iterator();

                        while (fieldDefinitionIterator.hasNext()) {
                            FieldDefinition fieldDefinition = (FieldDefinition) fieldDefinitionIterator.next();
                            Object value = doc.get(fieldDefinition.getName());
                            if (value != null) {
                                values.put(fieldDefinition.getName(), BaseTypes.ConvertToPrimitive(value, fieldDefinition.getBaseType()));
                            }
                        }

                        it.addRow(values);
                    }
                }
            } catch (Exception exceptionError) {
                _logger.error("Error Executing Query: " + exceptionError.getMessage());
            }


            _logger.info("query took " + (System.currentTimeMillis() - timeStart) + " ms");
            return it;
        }
    }


    @ThingworxServiceDefinition(
            name = "IndexDocument",
            description = "Add a document to Solr"
    )

    public void IndexDocument(@ThingworxServiceParameter(name = "coreName", description = "Core/index name", baseType = "STRING") String coreName, @ThingworxServiceParameter(name = "document", description = "Document to index, as a JSON object", baseType = "JSON") JSONObject document) throws Exception {
        double timeout = 60000.0D;
        if (this._timeout > 0) {
            timeout = (double) this._timeout;
        }

        boolean ignoreSSLErrors = false;
        boolean useNTLM = false;
        boolean useProxy = false;
        CloseableHttpClient client = HttpUtilities.createHttpClient(this._username, this._password, ignoreSSLErrors, timeout, useNTLM, "", "", useProxy, "", -1, "");
        SolrClient server = new HttpSolrClient.Builder(this.buildBaseURL(coreName).toString()).withHttpClient(client).build();
        // SolrClient server = new HttpSolrClient(this.buildBaseURL(coreName).toString(), client);
        SolrInputDocument doc = new SolrInputDocument();
        Iterator fieldNames = document.keys();

        while (fieldNames.hasNext()) {
            String fieldName = (String) fieldNames.next();
            doc.addField(fieldName, document.get(fieldName));
        }

        Collection<SolrInputDocument> docs = new ArrayList();
        docs.add(doc);
        server.add(docs);
        server.commit();
    }

    @ThingworxServiceDefinition(
            name = "IndexMultipleDocuments",
            description = "Add a document to Solr"
    )
    public InfoTable IndexMultipleDocuments(@ThingworxServiceParameter(name = "coreName", description = "Core/index name", baseType = "STRING") String coreName, @ThingworxServiceParameter(name = "documents", description = "Documents to index", baseType = "INFOTABLE") InfoTable documents) throws Exception {
        double timeout = 60000.0D;
        if (this._timeout > 0) {
            timeout = (double) this._timeout;
        }

        boolean ignoreSSLErrors = false;
        boolean useNTLM = false;
        boolean useProxy = false;
        CloseableHttpClient client = HttpUtilities.createHttpClient(this._username, this._password, ignoreSSLErrors, timeout, useNTLM, "", "", useProxy, "", -1, "");
        SolrClient server = new HttpSolrClient.Builder(this.buildBaseURL(coreName).toString()).withHttpClient(client).build();

        Collection<SolrInputDocument> docs = new ArrayList();
        Iterator valueCollectionIterator = documents.getRows().iterator();

        while (valueCollectionIterator.hasNext()) {
            ValueCollection row = (ValueCollection) valueCollectionIterator.next();
            SolrInputDocument doc = new SolrInputDocument();
            Iterator stringIterator = documents.getDataShape().getFields().keySet().iterator();

            while (stringIterator.hasNext()) {
                String fieldName = (String) stringIterator.next();
                doc.addField(fieldName, row.getValue(fieldName));
            }

            docs.add(doc);
        }

        server.add(docs);
        server.commit();
        return new InfoTable();
    }

    @ThingworxServiceDefinition(
            name = "GetDatashape",
            description = "Get the datashape"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "INFOTABLE"
    )
    public InfoTable GetDatashape(@ThingworxServiceParameter(name = "coreName", description = "Core/index name", baseType = "STRING") String coreName, @ThingworxServiceParameter(name = "dataShape", description = "Data shape", baseType = "DATASHAPENAME") String dataShape) throws Exception {
        DataShape ds = (DataShape) EntityUtilities.findEntity(dataShape, ThingworxRelationshipTypes.DataShape);
        if (ds == null) {
            throw new Exception("Unable to process queries without a field definition");
        } else {
            DataShapeDefinition dsDef = new DataShapeDefinition();

            double timeout = 60000.0D;
            if (this._timeout > 0) {
                timeout = (double) this._timeout;
            }

            boolean ignoreSSLErrors = false;
            boolean useNTLM = false;
            boolean useProxy = false;
            CloseableHttpClient client = HttpUtilities.createHttpClient(this._username, this._password, ignoreSSLErrors, timeout, useNTLM, "", "", useProxy, "", -1, "");
            SolrClient server = new HttpSolrClient.Builder(this.buildBaseURL(coreName).toString()).withHttpClient(client).build();


            //SS
            SolrQuery query = new SolrQuery();
            query.add(CommonParams.QT, "/schema/fields");
            QueryResponse response = server.query(query);
            NamedList responseHeader = response.getResponseHeader();
            ArrayList<SimpleOrderedMap> fields = (ArrayList<SimpleOrderedMap>) response.getResponse().get("fields");
            for (SimpleOrderedMap field : fields) {
                Object fieldName = field.get("name");
                Object fieldType = field.get("type");
                Object isIndexed = field.get("indexed");
                Object isStored = field.get("stored");

                if (dsDef.hasField(field.get("name").toString())) {
                    //(dsDef.getFieldDefinition(fieldName)).setDescription("solr-schema");
                    //(dsDef.getFieldDefinition(fieldName)).setBaseType(BaseTypes.fromFriendlyName(fieldInfo.getType()));
                    //update base type if it changes
                } else {
                    dsDef.addFieldDefinition(new FieldDefinition(fieldName.toString(), "solr-schema", convertBaseType(fieldType.toString())));
                    //add the data field to datashape definition
                }

                System.out.println(field.toString());
            }

            long timeStart = System.currentTimeMillis();

            ds.setFields(dsDef.getFields());
            InfoTable it = InfoTableInstanceFactory.createInfoTableFromDataShape(ds.getDataShape());

            _logger.info("query took " + (System.currentTimeMillis() - timeStart) + " ms");
            return it;
        }
    }


    protected BaseTypes convertBaseType(String solrBaseType) {
        switch (solrBaseType) {
            case "boolean":
                return BaseTypes.BOOLEAN;
            case "pdouble":
                return BaseTypes.NUMBER;
            case "pfloat":
                return BaseTypes.NUMBER;
            case "plong":
                return BaseTypes.NUMBER;
            case "string":
                return BaseTypes.STRING;
            case "pdate":
                return BaseTypes.DATETIME;
            case "text_general":
                return BaseTypes.STRING;
            default:
                return BaseTypes.STRING;
        }
    }

    protected String addParametersToURL(JSONObject values) throws Exception {
        StringBuffer sbParams = new StringBuffer();
        Iterator itr = values.keys();

        try {
            while (itr.hasNext()) {
                String strKey = (String) itr.next();
                String paramValue = (String) values.get(strKey);
                sbParams.append("&");
                sbParams.append(strKey);
                sbParams.append("=");
                sbParams.append(URLEncoder.encode(paramValue, "UTF8"));
            }
        } catch (Exception exceptionError) {
            throw new Exception("Unable to process VALUES collection: " + exceptionError.getMessage());
        }

        return sbParams.toString();
    }

    protected StringBuffer buildBaseURL(String core) {
        StringBuffer sbURL = new StringBuffer();
        if (this._useSSL) {
            sbURL.append("https");
        } else {
            sbURL.append("http");
        }
        sbURL.append(':');
        sbURL.append("/");
        sbURL.append("/");
        sbURL.append(this._serverName);
        sbURL.append(':');
        sbURL.append(this._serverPort);
        sbURL.append("/");
        sbURL.append("solr");
        if (core != null && core.length() > 0) {
            sbURL.append("/");
            sbURL.append(core);
        }
        return sbURL;
    }

    protected static class ConfigConstants {
        public static final String ConnectionInfo = "ConnectionInfo";
        public static final String ServerName = "serverName";
        public static final String ServerPort = "serverPort";
        public static final String UseSSL = "useSSL";
        public static final String UserName = "userName";
        public static final String Password = "password";
        public static final String Timeout = "timeout";

        protected ConfigConstants() {
        }
    }
}