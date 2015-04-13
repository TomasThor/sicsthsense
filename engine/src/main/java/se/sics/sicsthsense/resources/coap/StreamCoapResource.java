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

/* Description: Description: Coap Resource for SicsthSense Stream.
 * TODO:
 * */
package se.sics.sicsthsense.resources.coap;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;

import se.sics.sicsthsense.Utils;
import se.sics.sicsthsense.core.*;
import se.sics.sicsthsense.jdbi.*;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;

public class StreamCoapResource extends CoapResource{
    private StorageDAO storage;
    private final Logger logger = LoggerFactory.getLogger(StreamCoapResource.class);
    private ObjectMapper mapper;

    public StreamCoapResource() {
        this("streams");  
    }
    
    public StreamCoapResource(String name) {
        super(name);
        getAttributes().setTitle("Sicsth Sense Streams");
        add(new StreamDataCoapResource());
        this.storage = DAOFactory.getInstance();
        this.mapper = new ObjectMapper();
    }
    
    public static Map<String, String> getFourStreamParameters(CoapExchange exchange, boolean sizeCheck){
        List<String> queryList = exchange.getRequestOptions().getUriQuery();
        Map<String, String> parameters = new LinkedHashMap<String, String>();
        for(String query: queryList){
            int idx = query.indexOf("=");
            parameters.put(query.substring(0, idx), query.substring(idx + 1));
        }
        if(parameters.containsKey("user") && parameters.containsKey("resource") && parameters.containsKey("stream") && parameters.containsKey("key")){
            try {
                Long.parseLong(parameters.get("user"));
            } catch(Exception e) {
                exchange.respond(ResponseCode.BAD_OPTION, "Error: User parameter is not a number!");
                return null;
            }
            try {
                Long.parseLong(parameters.get("resource"));
            } catch(Exception e) {
                exchange.respond(ResponseCode.BAD_OPTION, "Error: Resouce parameter is not a number!");
                return null;
            }
            try {
                Long.parseLong(parameters.get("stream"));
            } catch(Exception e) {
                exchange.respond(ResponseCode.BAD_OPTION, "Error: Stream parameter is not a number!");
                return null;
            }
        } else {
            exchange.respond(ResponseCode.BAD_OPTION, "Error: Missing one or more parameter!");
            return null;
        }
        if(sizeCheck && parameters.size() != 4){
            exchange.respond(ResponseCode.BAD_OPTION, "Error: Not correct number of parameters!");
            return null;
        }
        return parameters;
    }
    
    public static Map<String, String> getThreeStreamParameters(CoapExchange exchange){
        List<String> queryList = exchange.getRequestOptions().getUriQuery();
        Map<String, String> parameters = new LinkedHashMap<String, String>();
        for(String query: queryList){
            int idx = query.indexOf("=");
            parameters.put(query.substring(0, idx), query.substring(idx + 1));
        }
        if(parameters.containsKey("user") && parameters.containsKey("resource") && parameters.containsKey("key")){
            try {
                Long.parseLong(parameters.get("user"));
            } catch(Exception e) {
                exchange.respond(ResponseCode.BAD_OPTION, "Error: User parameter is not a number!");
                return null;
            }
            try {
                Long.parseLong(parameters.get("resource"));
            } catch(Exception e) {
                exchange.respond(ResponseCode.BAD_OPTION, "Error: Resouce parameter is not a number!");
                return null;
            }
        } else {
            exchange.respond(ResponseCode.BAD_OPTION, "Error: One or more parameter wrong!");
            return null;
        }
        if(parameters.size() != 3){
            exchange.respond(ResponseCode.BAD_OPTION, "Error: Not correct number of parameters!");
            return null;
        }
        return parameters;
    }
    
    @Override
    public void handleGET(CoapExchange exchange) {
        int parameterCount = exchange.getRequestOptions().getUriQuery().size();
        if (parameterCount == 4){
            
            Map<String, String> p = getFourStreamParameters(exchange, false);
            if(p == null) return;
            long userId = Long.parseLong(p.get("user"));
            String resourceName = p.get("resource");
            String streamName = p.get("stream");
            String key = p.get("key");
            
            logger.info("Getting user/resource/stream: "+userId+"/"+resourceName+"/"+streamName);
            User user = storage.findUserById(userId);
            Resource resource = Utils.findResourceByIdName(storage,resourceName);
            Stream stream     = Utils.findStreamByIdName(storage,streamName);
            Utils.checkHierarchy(storage,user,resource,stream);
            if (!user.isAuthorised(key) && !resource.isAuthorised(key) && !stream.isAuthorised(key)) {
                exchange.respond(ResponseCode.FORBIDDEN, "Error: Not authorised to get stream");
            }

            // add back in the antecedents
            List<Long> antecedents = storage.findAntecedents(stream.getId());
            for(Long antId: antecedents) {
                    Stream antStream = storage.findStreamById(antId);
                    //logger.info("Antecedent: "+antId);
                    if (antStream==null) {continue;}
                    // Check ability to access antecedent!
                    if (antStream.isReadable(storage,key)) {
                            stream.antecedents.add(antId);
                    }
            }

            // and triggers
            stream.triggers = storage.findTriggersByStreamId(stream.getId());
            stream.setLabel(storage.findPathByStreamId(stream.getId()));
            
            try {
                exchange.respond(ResponseCode.CONTENT, mapper.writeValueAsString(stream));
            } catch (JsonProcessingException ex) {
                java.util.logging.Logger.getLogger(StreamCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        } else if (parameterCount == 3){
            
            Map<String, String> p = getThreeStreamParameters(exchange);
            if(p == null) return;
            long userId = Long.parseLong(p.get("user"));
            String resourceName = p.get("resource");
            String key = p.get("key");
            
            logger.info("Getting user/resource/streams "+userId+" "+resourceName);
            User user = storage.findUserById(userId);
            Resource resource = Utils.findResourceByIdName(storage,resourceName);
            Utils.checkHierarchy(storage,user,resource);
            if (!user.isAuthorised(key) && !resource.isAuthorised(key)) {
                exchange.respond(ResponseCode.FORBIDDEN, "Error: Not authorised to get streams");
            }

            List<Stream> streams = storage.findStreamsByResourceId(resource.getId());
            for (Stream stream: streams) {
                stream.triggers = storage.findTriggersByStreamId(stream.getId());
                stream.setLabel(storage.findPathByStreamId(stream.getId()));
            }
            
            try {
                exchange.respond(ResponseCode.CONTENT, mapper.writeValueAsString(streams));
            } catch (JsonProcessingException ex) {
                java.util.logging.Logger.getLogger(StreamCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        } else {
            exchange.respond(ResponseCode.BAD_OPTION, "Error: Not correct number of parameters!");
            return;
        }
    }
    
    @Override
    public void handlePOST(CoapExchange exchange) {
        Map<String, String> p = getThreeStreamParameters(exchange);
        if(p == null) return;
        long userId = Long.parseLong(p.get("user"));
        String resourceName = p.get("resource");
        String key = p.get("key");
        
        String data = exchange.getRequestText();
        Stream stream = null;
        try {
            stream = mapper.readValue(data, Stream.class);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(StreamCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
            return;
        }
        
        logger.info("Creating stream!:"+stream);
        User user         = storage.findUserById(userId);
        Resource resource = Utils.findResourceByIdName(storage,resourceName);
        Utils.checkHierarchy(storage,user,resource);
        if (!user.isAuthorised(key) && !resource.isAuthorised(key)) {
            exchange.respond(ResponseCode.FORBIDDEN, "Error: Not authorised to POST to stream");
        }
        long streamId=-1;

        // initialise the stream correctly
        stream.setResource_id(resource.getId());
        stream.setOwner_id(userId);
        streamId = Utils.insertStream(storage,stream);

        //create antecedant streams correctly!
        if (stream.antecedents !=null) {
                logger.info("Antecedant streams: ");
                for(Long antId: stream.antecedents) {
                        logger.info("Antecedent: "+antId);
                        // check ability to access antecedent!
                        // XXX
                        if (antId==null) {
                            exchange.respond(ResponseCode.BAD_REQUEST, "Error: Antecedent Stream ID is not valid!");
                        }
                        Utils.insertDependent(storage,antId.longValue(),streamId);
                }
        }
        if (stream.triggers!=null) {
                logger.info("Trigger processing..");
                for(Trigger t: stream.triggers) {
                        Utils.insertTrigger(storage, streamId, t.getUrl(), t.getOperator(), t.getOperand(), t.getPayload());
                }
        }
        stream = storage.findStreamById(streamId); // need fresh DB version
        try {
            exchange.respond(ResponseCode.CREATED, mapper.writeValueAsString(stream));
        } catch (JsonProcessingException ex) {
            java.util.logging.Logger.getLogger(StreamCoapResource.class.getName()).log(Level.SEVERE, null, ex);
        }  
    }
    
    @Override
    public void handlePUT(CoapExchange exchange) {
        exchange.respond(ResponseCode.FORBIDDEN);
    }

    @Override
    public void handleDELETE(CoapExchange exchange) {
        exchange.respond(ResponseCode.FORBIDDEN);
    }
}
