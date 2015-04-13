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

/* Description: Description: Coap Resource for SicsthSense Resources.
 * TODO:
 * */
package se.sics.sicsthsense.resources.coap;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

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
import se.sics.sicsthsense.model.*;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;

public class ResourceCoapResource extends CoapResource{
    private final StorageDAO storage;
    private final AtomicLong counter;
    private PollSystem pollSystem;
    private ObjectMapper mapper;
    private final Logger logger = LoggerFactory.getLogger(ResourceCoapResource.class);
    public ParseData parseData;
    List<Parser> parsers;
    
    public ResourceCoapResource() {
        this("resources");  
    }
    
    // constructor with the system's stoarge and poll system.
    public ResourceCoapResource(String name) {
        super(name);
        getAttributes().setTitle("Sicsth Sense Resources");
        add(new ResourceDataCoapResource());
        this.storage = DAOFactory.getInstance();
        this.pollSystem = PollSystem.getInstance();
        this.counter = new AtomicLong();
        this.parseData = new ParseData(storage);
        this.mapper = new ObjectMapper();
    }
    
    public static Map<String, String> getThreeResourceParameters(CoapExchange exchange){
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
    
    public static Map<String, String> getTwoResourceParameters(CoapExchange exchange){
        List<String> queryList = exchange.getRequestOptions().getUriQuery();
        Map<String, String> parameters = new LinkedHashMap<String, String>();
        for(String query: queryList){
            int idx = query.indexOf("=");
            parameters.put(query.substring(0, idx), query.substring(idx + 1));
        }
        if(parameters.containsKey("user") && parameters.containsKey("key")){
            try {
                Long.parseLong(parameters.get("user"));
            } catch(Exception e) {
                exchange.respond(ResponseCode.BAD_OPTION, "Error: User parameter is not a number!");
                return null;
            }
        } else {
            exchange.respond(ResponseCode.BAD_OPTION, "Error: One or more parameter wrong!");
            return null;
        }
        if(parameters.size() != 2){
            exchange.respond(ResponseCode.BAD_OPTION, "Error: Not correct number of parameters!");
            return null;
        }
        return parameters;
    }
    
    @Override
    public void handleGET(CoapExchange exchange) {
        int parameterCount = exchange.getRequestOptions().getUriQuery().size();
        if (parameterCount == 3){
            Map<String, String> p = getThreeResourceParameters(exchange);
            if(p == null) return;
            long userId = Long.parseLong(p.get("user"));
            String resourceName = p.get("resource");
            String key = p.get("key");
            
            logger.info("Getting user/resource: "+userId+"/"+resourceName);
            Utils.checkHierarchy(storage,userId);
            Resource resource = Utils.findResourceByIdName(storage,resourceName,userId);
            if (resource == null) { 
                exchange.respond(ResponseCode.NOT_FOUND, "Error: Resource " + resourceName + " does not exist!"); 
            }
            if (resource.getOwner_id() != userId) { 
                exchange.respond(ResponseCode.NOT_FOUND, "Error: User "+userId+" does not own resource " + resourceName); 
            }
            User user = storage.findUserById(userId);
            if (user==null) {
                throw new WebApplicationException(Status.NOT_FOUND);
            }
            if (!user.isAuthorised(key) && !resource.isAuthorised(key)) { 
                exchange.respond(ResponseCode.FORBIDDEN, "Error: Key does not match! " + key); 
            }
            
            try {
                exchange.respond(ResponseCode.CONTENT, mapper.writeValueAsString(resource), MediaTypeRegistry.APPLICATION_JSON);
            } catch (JsonProcessingException ex) {
                java.util.logging.Logger.getLogger(ResourceCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        } else if(parameterCount == 2){
            Map<String, String> p = getTwoResourceParameters(exchange);
            if(p == null) return;
            long userId = Long.parseLong(p.get("user"));
            String key = p.get("key");
            
            //logger.info("Getting all user "+userId+" resources for visitor "+visitor.toString());
            Utils.checkHierarchy(storage,userId);
            User user = storage.findUserById(userId);
            if (user==null) {
                    exchange.respond(ResponseCode.NOT_FOUND, "Error: No userId match.");
            }
            List<Resource> resources = storage.findResourcesByOwnerId(userId);
            if (!user.isAuthorised(key)) {
                    exchange.respond(ResponseCode.FORBIDDEN, "Error: Key does not match! " + key);
                    /*
                    Iterator<Resource> it = resources.iterator();
                    while (it.hasNext()) {
                            Resource r = it.next();
                            if (r.) {it.remove();}
                    }*/
            }
            
            try {
                exchange.respond(ResponseCode.CONTENT, mapper.writeValueAsString(resources), MediaTypeRegistry.APPLICATION_JSON);
            } catch (JsonProcessingException ex) {
                java.util.logging.Logger.getLogger(ResourceCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        } else {
            exchange.respond(ResponseCode.BAD_OPTION, "Error: Not correct number of parameters!");
            return;
        }
    }
    
    @Override
    public void handlePOST(CoapExchange exchange) {
        Map<String, String> p = getTwoResourceParameters(exchange);
        if(p == null) return;
        long userId = Long.parseLong(p.get("user"));
        String key = p.get("key");
        String data = exchange.getRequestText();
        Resource resource = null;
        try {
            resource = mapper.readValue(data, Resource.class);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(ResourceCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
            return;
        }
        
        logger.info("Adding user/resource:"+resource.getLabel());
        Utils.checkHierarchy(storage,userId);
        long resourceId = -1;
        User user = storage.findUserById(userId);
        if (user==null) {throw new WebApplicationException(Status.NOT_FOUND);}
        if (!user.isAuthorised(key)) { 
            exchange.respond(ResponseCode.FORBIDDEN, "Error: Key does not match! " + key);
        }

        /*
        // no label duplication allowed
        if (storage.findResourceByLabel(resource.getLabel())!=null) {
            exchange.respond(ResponseCode.BAD_REQUEST , "Error: that resource label already exists!");
        }*/

        resource.setOwner_id(userId); // should know the owner
        try {
            resourceId = Utils.insertResource(storage,resource);
            ResourceLog rl = new ResourceLog(resource);
            rl.setResourceId(resourceId); // for the foreign key constraint
            Utils.insertResourceLog(storage,rl);
        } catch (Exception e) {
            exchange.respond(ResponseCode.BAD_REQUEST , "Error: storing the new resource, are the attributes correct?");
        }
        if (resource.getPolling_period() > 0) {
                // remake pollers with updated Resource attribtues
                pollSystem.rebuildResourcePoller(resourceId);
        }
        resource = storage.findResourceById(resourceId);
        try {
            exchange.respond(ResponseCode.CREATED, mapper.writeValueAsString(resource), MediaTypeRegistry.APPLICATION_JSON);
        } catch (JsonProcessingException ex) {
            java.util.logging.Logger.getLogger(ResourceCoapResource.class.getName()).log(Level.SEVERE, null, ex);
        }  
    }
    
    @Override
    public void handlePUT(CoapExchange exchange) {
        Map<String, String> p = getThreeResourceParameters(exchange);
        if(p == null) return;
        long userId = Long.parseLong(p.get("user"));
        String resourceName = p.get("resource");
        String key = p.get("key");
        String data = exchange.getRequestText();
        Resource resource = null;
        try {
            resource = mapper.readValue(data, Resource.class);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(ResourceCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
            return;
        }
        
        logger.info("Updating resourceName:"+resourceName);
        User user = storage.findUserById(userId);
        Resource oldresource = Utils.findResourceByIdName(storage,resourceName,userId);
        Utils.checkHierarchy(storage,user,oldresource);
        if (!user.isAuthorised(key) && !resource.isAuthorised(key)) {
            exchange.respond(ResponseCode.FORBIDDEN, "Error: Key does not match! " + key);
        }
        Utils.updateResource(storage,oldresource.getId(), resource);
        exchange.respond(ResponseCode.CHANGED);
    }

    @Override
    public void handleDELETE(CoapExchange exchange) {
        Map<String, String> p = getThreeResourceParameters(exchange);
        if(p == null) return;
        long userId = Long.parseLong(p.get("user"));
        String resourceName = p.get("resource");
        String key = p.get("key");
        
        logger.warn("Deleting resourceName:"+resourceName);
        User user = storage.findUserById(userId);
        Resource resource = Utils.findResourceByIdName(storage, resourceName,userId);
        Utils.checkHierarchy(storage,user,resource);
        if (!user.isAuthorised(key) && !resource.isAuthorised(key)) {
            exchange.respond(ResponseCode.FORBIDDEN, "Error: Key does not match! " + key + " Only User key deletes a Resource");
        }
        Utils.deleteResource(storage,resource);
        exchange.respond(ResponseCode.DELETED);
    }
}
