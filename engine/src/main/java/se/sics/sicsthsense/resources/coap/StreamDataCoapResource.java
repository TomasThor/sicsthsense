/*
 * Copyright (c) 2013, Swedish Institute of Computer Science
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *		 * Redistributions of source code must retain the above copyright
 *			 notice, this list of conditions and the following disclaimer.
 *		 * Redistributions in binary form must reproduce the above copyright
 *			 notice, this list of conditions and the following disclaimer in the
 *			 documentation and/or other materials provided with the distribution.
 *		 * Neither the name of The Swedish Institute of Computer Science nor the
 *			 names of its contributors may be used to endorse or promote products
 *			 derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE SWEDISH INSTITUTE OF COMPUTER SCIENCE BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

/* Description: Description: Coap Resource for SicsthSense Stream Data.
 * TODO:
 * */
package se.sics.sicsthsense.resources.coap;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import javax.ws.rs.PathParam;
import org.atmosphere.cpr.Broadcaster;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;

import se.sics.sicsthsense.Utils;
import se.sics.sicsthsense.core.*;
import se.sics.sicsthsense.jdbi.*;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;

public class StreamDataCoapResource extends CoapResource{
    private StorageDAO storage;
    private final AtomicLong counter;
    private final Logger logger = LoggerFactory.getLogger(StreamDataCoapResource.class);
    private ObjectMapper mapper;
    private @PathParam("resourceId") Broadcaster topic;

    public StreamDataCoapResource() {
        this("data");  
    }
    
    public StreamDataCoapResource(String name) {
        super(name);
        getAttributes().setTitle("Sicsth Sense Streams Data");
        this.storage = DAOFactory.getInstance();
        this.counter = new AtomicLong();
        this.mapper = new ObjectMapper();
    }
    
    private static long getLongParameter(Map<String, String> parameters, String parameter){
        long number = -1;
        if(parameters.containsKey(parameter)){
            try{
                number = Long.parseLong(parameters.get(parameter));
            } catch (Exception e) {}
        }
        return number;
    }
    
    private static int getIntParameter(Map<String, String> parameters, String parameter){
        int number = -1;
        if(parameters.containsKey(parameter)){
            try{
                number = Integer.parseInt(parameters.get(parameter));
            } catch (Exception e) {}
        }
        return number;
    }
    
    @Override
    public void handleGET(CoapExchange exchange) {
        Map<String, String> parameters = StreamCoapResource.getFourStreamParameters(exchange, false);
        if(parameters == null) return;
        long userId = Long.parseLong(parameters.get("user"));
        String resourceName = parameters.get("resource");
        String streamName = parameters.get("stream");
        String key = parameters.get("key");
    
        int limit = getIntParameter(parameters, "limit");
        long from = getLongParameter(parameters, "from");
        long until = getLongParameter(parameters, "until"); 
        String format = "json";
        if(parameters.containsKey("format")) format = parameters.get("format");
        
        List<DataPoint> rv; // return value before conversion
        User user         = storage.findUserById(userId);
        Resource resource = Utils.findResourceByIdName(storage,resourceName);
        Stream stream     = Utils.findStreamByIdName(storage,streamName);
        Utils.checkHierarchy(storage,user,resource,stream);
        if (!stream.getPublic_access()) { // need to authenticate
            //logger.warn("Stream isnt public access!");
            if (!user.isAuthorised(key) && !stream.isAuthorised(key) && !resource.isAuthorised(key)) {
                exchange.respond(ResponseCode.FORBIDDEN, "Error: Not authorised to POST to stream");
            }
        }
        //logger.info("Getting stream: "+streamId);
        boolean limitSet=true;
        int limitValue = limit;
        if (limit == -1) {
            limitSet=false;
            limitValue=50; // give default value
        }

        if (from != -1) { // from is set
            if (until != -1) { // until is set
                rv = storage.findPointsByStreamIdSince(stream.getId(), from, until);
            } else { // until is not set
                if (limitSet) { // limit was set
                    rv = storage.findPointsByStreamIdSinceLimit(stream.getId(), from, limitValue);
                } else { // limit was not set, only from
                    rv = storage.findPointsByStreamIdSince(stream.getId(), from);
                }
            }
        } else { // just get the most recent LIMIT points
            rv = storage.findPointsByStreamId(stream.getId(), limitValue);
            Collections.reverse(rv);
        }

        try {
            if ("csv".equals(format)) {
                //return Utils.resp(Status.OK, csvmapper.writer(schema).writeValueAsString(rv), null);
                exchange.respond(ResponseCode.BAD_REQUEST, "Error: Can't parse data!");
            } else { // default dump to JSON
                exchange.respond(ResponseCode.CONTENT, mapper.writeValueAsString(rv));
            }
        } catch (Exception e) {
            exchange.respond(ResponseCode.BAD_REQUEST, "Error: Can't parse data!");
        }
    }
    
    @Override
    public void handlePOST(CoapExchange exchange) {
        Map<String, String> parameters = StreamCoapResource.getFourStreamParameters(exchange, true);
        if(parameters == null) return;
        long userId = Long.parseLong(parameters.get("user"));
        String resourceName = parameters.get("resource");
        String streamName = parameters.get("stream");
        String key = parameters.get("key");
        
        String data = exchange.getRequestText();
        DataPoint datapoint = null;
        try {
            datapoint = mapper.readValue(data, DataPoint.class);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(StreamDataCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
            return;
        }
        
        User user         = storage.findUserById(userId);
        Resource resource = Utils.findResourceByIdName(storage,resourceName);
        Stream stream     = Utils.findStreamByIdName(storage,streamName);
        Utils.checkHierarchy(storage, user, resource, stream);
        if (!user.isAuthorised(key) && !resource.isAuthorised(key) && !stream.isAuthorised(key)) {
                exchange.respond(ResponseCode.FORBIDDEN, "Error: User is not owner and has incorrect key on resource/stream!");
        }
        logger.info("Inserting data into stream: "+streamName);
        datapoint.setStreamId(stream.getId()); // keep consistency
        try {
            Utils.insertDataPoint(storage, datapoint); // insert first to fail early
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(StreamDataCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
            return;
        }
        //topic.broadcast(datapoint.toString());
        exchange.respond(ResponseCode.CREATED, "Data successfully posted");  
    }
    
    @Override
    public void handlePUT(CoapExchange exchange) {
        exchange.respond(ResponseCode.FORBIDDEN);
    }

    @Override
    public void handleDELETE(CoapExchange exchange) {
        Map<String, String> p = StreamCoapResource.getFourStreamParameters(exchange, true);
        if(p == null) return;
        long userId = Long.parseLong(p.get("user"));
        String resourceName = p.get("resource");
        String streamName = p.get("stream");
        String key = p.get("key");
        
        logger.info("Deleting stream!:"+streamName);
        User user = storage.findUserById(userId);
        Resource resource = Utils.findResourceByIdName(storage,resourceName);
        Stream stream =	    Utils.findStreamByIdName(storage,streamName);
        Utils.checkHierarchy(storage,user,resource);
        if (!user.isAuthorised(key) && !resource.isAuthorised(key) && !stream.isAuthorised(key)) {
            exchange.respond(ResponseCode.FORBIDDEN, "Error: Not authorised to DELETE stream");
        }
        Utils.deleteStream(storage,stream);
	exchange.respond(ResponseCode.DELETED, "Stream deleted");
    }
}
