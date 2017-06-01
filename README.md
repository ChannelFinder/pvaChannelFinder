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

```mvn clean install```
