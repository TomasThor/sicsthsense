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

/* Description: Description: Coap Resource for SicsthSense Users.
 * TODO:
 * */
package se.sics.sicsthsense.resources.coap;

import java.util.List;
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
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;

import se.sics.sicsthsense.core.*;
import se.sics.sicsthsense.jdbi.*;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;

public class UserCoapResource extends CoapResource{
    private final StorageDAO storage;
    private ObjectMapper mapper;
    private final Logger logger = LoggerFactory.getLogger(ResourceCoapResource.class);
    
    public UserCoapResource() {
        this("users");  
    }
    
    // constructor with the system's stoarge and poll system.
    public UserCoapResource(String name) {
        super(name);
        getAttributes().setTitle("Sicsth Sense Users");
        this.storage = DAOFactory.getInstance();
        this.mapper = new ObjectMapper();
    }
     
    private static Map<String, String> getTwoParameters(CoapExchange exchange){
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
        Map<String, String> p = getTwoParameters(exchange);
        if(p == null) return;
        long userId = Long.parseLong(p.get("user"));
        String key = p.get("key");
        User user = storage.findUserById(userId);
        if (!user.isAuthorised(key)) {
            exchange.respond(ResponseCode.FORBIDDEN, "Error: Key does not match! " + key);
        }
        try {   
            exchange.respond(ResponseCode.CONTENT, mapper.writeValueAsString(user));
        } catch (JsonProcessingException ex) {
            java.util.logging.Logger.getLogger(UserCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
    
    @Override
    public void handlePOST(CoapExchange exchange) {
        //logger.info("making a new user: "+user.toString());
        String data = exchange.getRequestText();
        User user = null;
        try {
            user = mapper.readValue(data, User.class);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(UserCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
            return;
        }
        
        if (user.getEmail()==null || user.getEmail()=="") {
            exchange.respond(ResponseCode.BAD_REQUEST, "Error: new User email not set!");
        }
        if (storage.findUserByUsername(user.getUsername())!=null) {
            exchange.respond(ResponseCode.BAD_REQUEST, "Error: Duplicate username: "+user.getUsername()+"!");
        }
        if (storage.findUserByEmail(user.getEmail())!=null) {
            exchange.respond(ResponseCode.BAD_REQUEST, "Error: Duplicate email: "+user.getEmail()+"!");
        }
        User newuser = new User();

        newuser.update(user);
        logger.info("Adding new user: "+newuser.toString());

        storage.insertUser(
                newuser.getUsername(),
                newuser.getEmail(),
                newuser.getFirstName(),
                newuser.getLastName(),
                newuser.getToken(),
                new String(Hex.encodeHex(DigestUtils.md5(newuser.getPassword())))
        );
        try {
            exchange.respond(ResponseCode.CREATED, mapper.writeValueAsString(storage.findUserByUsername(newuser.getUsername())));
        } catch (JsonProcessingException ex) {
            java.util.logging.Logger.getLogger(UserCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
    
    @Override
    public void handlePUT(CoapExchange exchange) {
        Map<String, String> p = getTwoParameters(exchange);
        if(p == null) return;
        long userId = Long.parseLong(p.get("user"));
        String key = p.get("key");
        
        String data = exchange.getRequestText();
        User newuser = null;
        try {
            newuser = mapper.readValue(data, User.class);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(UserCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
            return;
        }
        
        User user = storage.findUserById(userId);
        if (!user.getToken().equals(key)) {throw new WebApplicationException(Status.FORBIDDEN);}

        newuser.setId(userId); // ensure we dont change other others!
        user.update(newuser);
        storage.updateUser(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );
        try {
            exchange.respond(ResponseCode.CHANGED, mapper.writeValueAsString(user));
        } catch (JsonProcessingException ex) {
            java.util.logging.Logger.getLogger(UserCoapResource.class.getName()).log(Level.SEVERE, null, ex);
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void handleDELETE(CoapExchange exchange) {
        exchange.respond(ResponseCode.FORBIDDEN);
    }
}
