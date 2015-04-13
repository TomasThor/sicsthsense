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

/* Description: Coap Resource for SicsthSense Resources Data.
 * TODO:
 * */
package se.sics.sicsthsense.resources.coap;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;

import se.sics.sicsthsense.Utils;
import se.sics.sicsthsense.core.*;
import se.sics.sicsthsense.jdbi.*;
import se.sics.sicsthsense.model.*;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;

public class ResourceDataCoapResource extends CoapResource{
    private final StorageDAO storage;
    private final Logger logger = LoggerFactory.getLogger(ResourceDataCoapResource.class);
    public ParseData parseData;
    List<Parser> parsers;
    
    
    public ResourceDataCoapResource() {
        this("data");  
    }
    
    // constructor with the system's stoarge and poll system.
    public ResourceDataCoapResource(String name) {
        super(name);
        getAttributes().setTitle("Sicsth Sense Resources Data");
        this.storage = DAOFactory.getInstance();
        this.parseData = new ParseData(storage);
    }
    
    @Override
    public void handleGET(CoapExchange exchange) {
        exchange.respond(ResponseCode.FORBIDDEN, "Error: Only Streams can have data read");
    }
    
    @Override
    public void handlePOST(CoapExchange exchange) {
        
        Map<String, String> p = ResourceCoapResource.getThreeResourceParameters(exchange);
        if(p == null) return;
        long userId = Long.parseLong(p.get("user"));
        String resourceName = p.get("resource");
        String key = p.get("key");
        String data = exchange.getRequestText();
        
        User user = storage.findUserById(userId);
        Resource resource = Utils.findResourceByIdName(storage,resourceName);
        Utils.checkHierarchy(storage, user,resource);
        if (!resource.isAuthorised(key) && !user.isAuthorised(key)) { 
            exchange.respond(ResponseCode.FORBIDDEN, "Error: Key does not match! " + key); 
        }

        long timestamp = java.lang.System.currentTimeMillis();
        //logger.info("Adding data to resource: "+resource.getLabel()+" @ "+timestamp);

        // if parsers are undefined, create them!
        List<Parser> parsers = storage.findParsersByResourceId(resource.getId());
        if (parsers==null || parsers.size()==0) {
                logger.info("No parsers defined! Trying to auto create for: "+resource.getLabel());
                try {
                        // staticness is a mess...
                        parseData.autoCreateJsonParsers(storage,PollSystem.getInstance().mapper, resource, data);
                } catch (Exception e) {
                        exchange.respond(ResponseCode.BAD_REQUEST, "Error: JSON parsing for auto creation failed!");
                }
        }
        //run it through the parsers and update resource log
        Utils.applyParsers(storage, resource, data, timestamp);

        // update Resource last_posted
        storage.postedResource(resource.getId(),timestamp);

        exchange.respond(ResponseCode.CREATED, "Data post successful");
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
