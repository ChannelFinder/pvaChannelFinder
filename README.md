# pvaChannelFinder
[![Build Status](https://travis-ci.org/ChannelFinder/pvaChannelFinder.svg?branch=master)](https://travis-ci.org/ChannelFinder/pvaChannelFinder)

A pva RPC service for channelfinder

Interface
----------

#### Request:  
The client requests a query as a NTURI pvStructure.

#### Result:  
The service returns the result as an NTTable pvStructure.

#### Query key words:

```_name``` Seach for channels with names matching the search pattern  
```_tag```  Search for channels with tags matching the pattern   
```<propertyName> <propertyValue>``` Search for channels with property ```<propertyName>``` with value matching the pattern ```<propertyValue>```    

```_size``` The number of results that should be returned  
```_from``` The number of initial results that should be skipped  
```_filter``` A list of properties and tags to be returned


#### Example query:  
```
epics:nt/NTURI:1.0 
      string scheme pva 
      string path channels 
      structure query 
          string _name SR*C001* 
          string _tag group8_50 
          string group1 500 
```
Installation
------------

#### requirements   

 **jdk 8 or above**

 **elasticsearch 1.5.X**

Create the elastic indexes and set up their mapping

  * The Mapping_definitions script (which is avaiable under /pvaChannelFinder/src/main/resources) contains the curl commands to setup the 3 elastic indexes associated with channelfinder.  
  * Enable scripting in elastic by adding the following lines to the elasticsearch.yml (on linux /etc/elasticsearch/elasticsearch.yml)
  ```
  ################################# Scripting ###############################

  script.inline: on
  script.indexed: on
  ```
    
  
  * For more information of how Index and mappings can be setup using any rest client as described here [create elastic index](https://www.elastic.co/guide/en/elasticsearch/reference/1.4/_create_an_index.html)

#### build  

``` mvn clean install ```

#### Running the Service

``` java -jar target/epics-channelfinder-<version>-jar-with-dependencies.jar ```
