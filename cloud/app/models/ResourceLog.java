/*
 * Copyright (c) 2013, Swedish Institute of Computer Science All rights reserved. Redistribution and
 * use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met: * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer. * Redistributions in binary form
 * must reproduce the above copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the distribution. * Neither the name of
 * The Swedish Institute of Computer Science nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE SWEDISH INSTITUTE OF
 * COMPUTER SCIENCE BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 */

/*
 * Description: TODO:
 */

package models;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

import play.Logger;
import play.db.ebean.Model;
import play.mvc.Http.HeaderNames;
import protocol.Request;
import protocol.Response;

import com.avaje.ebean.Ebean;

import controllers.Utils;

@Entity
@Table(name = "resource_log", uniqueConstraints = {@UniqueConstraint(columnNames = {"resource_id",
        "is_poll"})})
public class ResourceLog extends Model {
    private static final int MAX_LENGTH = 4 * 1024, BODY_MAX_LENGTH = 8 * 1024;
    /**
	 * 
	 */
    private static final long serialVersionUID = -5437709421347005124L;

    @Id
    public Long id;

    @ManyToOne
    @Column(nullable = false)
    public Resource resource;

    public Long creationTimestamp;

    public Long responseTimestamp;

    public Boolean parsedSuccessfully = false;

    public boolean isPoll = false;

    @Column(length = BODY_MAX_LENGTH)
    public String body = "";

    public String method = "";

    @Column(name = "host_name")
    public String host = "";

    public String uri = "";

    @Column(length = MAX_LENGTH)
    public String headers = "";

    @Column(length = MAX_LENGTH)
    public String message = "";

    @Version
    // for concurrency protection
    private int version;

    @Transient
    public String timestamp = "";

    // Poll roundtrip time
    @Transient
    public String responseTime = "";

    public static Model.Finder<Long, ResourceLog> find = new Model.Finder<Long, ResourceLog>(
            Long.class, ResourceLog.class);

    public ResourceLog(long id, Resource resource, long creationTimestamp, long responseTimestamp,
            Boolean parsedSuccessfully, Boolean isPoll, String body, String method, String host,
            String uri, String headers, String message) {
        super();
        this.id = id;
        this.resource = resource;
        this.parsedSuccessfully = parsedSuccessfully;
        this.isPoll = isPoll;
        this.body = body;
        this.method = method;
        this.host = host;
        this.uri = uri;
        this.headers = headers;
        this.message = message;
        setCreationTimestamp(creationTimestamp);
        setResponseTimestamp(responseTimestamp);
    }

    public ResourceLog() {}

    public static ResourceLog fromRequest(Resource resource, Request request, long creationTimestamp) {
        ResourceLog log = new ResourceLog();
        
        try {
            log.resource = resource;
            log.isPoll = false;
            
            if (request != null) {
                log.body = request.body();
                log.method = request.method();
                log.host = request.header("Host");
                log.uri = request.uri().toString();
                log.headers =
                        HeaderNames.CONTENT_TYPE + " " + request.header(HeaderNames.CONTENT_TYPE)
                                + "\n" + HeaderNames.CONTENT_ENCODING + " "
                                + request.header(HeaderNames.CONTENT_ENCODING) + "\n"
                                + HeaderNames.CONTENT_LENGTH + " "
                                + request.header(HeaderNames.CONTENT_LENGTH) + "\n";
            }
            
            log.setCreationTimestamp(creationTimestamp);
        } catch (Exception e) {
            Logger.error(e.getMessage() + e.getStackTrace()[0].toString() + e.toString());
        }
        
        return log;
    }

    public static ResourceLog fromResponse(Resource resource, Response response, long creationTimestamp,
            long responseTimestamp) {
        ResourceLog log = new ResourceLog();
        
        try {
            log.resource = resource;
            log.isPoll = true;
            
            if (response != null) {
                log.body = response.body();
                log.method = response.request().method();
                log.host = response.request().header("Host");
                log.uri = response.uri().toString();
                log.headers =
                        "Status " + response.statusText() + "\n" + HeaderNames.CONTENT_TYPE + " "
                                + response.contentType() + "\n" + HeaderNames.CONTENT_ENCODING
                                + " " + response.contentEncoding() + "\n"
                                + HeaderNames.CONTENT_LENGTH + " " + response.contentLength()
                                + "\n";
            }
            
            log.setCreationTimestamp(creationTimestamp);
            log.setResponseTimestamp(responseTimestamp);
        } catch (Exception e) {
            Logger.error(e.getMessage() + e.getStackTrace()[0].toString() + e.toString());
        }
        
        return log;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void appendMessage(String message) {
        this.message += message;
    }

    public void setCreationTimestamp(long creationTimestamp) {
        this.creationTimestamp = (creationTimestamp <= 0) ? Utils.currentTime() : creationTimestamp;
        this.timestamp = new Date(creationTimestamp).toString();
    }

    public void setResponseTimestamp(long responseTimestamp) {
        this.responseTimestamp = (responseTimestamp <= 0) ? Utils.currentTime() : responseTimestamp;
        this.responseTime = controllers.Utils.timeStr(this.responseTimestamp - creationTimestamp);
    }

    public String getTimestamp() {
        setCreationTimestamp(this.creationTimestamp);
        return timestamp;
    }

    public String getResponseTime() {
        setResponseTimestamp(this.responseTimestamp);
        return responseTime;
    }

    public void updateParsedSuccessfully(Boolean parsedSuccessfully) {
        this.parsedSuccessfully = parsedSuccessfully;
        if (id != null) {
            this.update();
        }
    }

    public void updateMessages(String msg) {
        this.message = msg;
        if (id != null) {
            this.update();
        }
    }

    public boolean updateResourceLog(ResourceLog rl) {
        this.resource = rl.resource;
        this.creationTimestamp = rl.creationTimestamp;
        this.responseTimestamp = rl.responseTimestamp;
        this.parsedSuccessfully = rl.parsedSuccessfully;
        this.isPoll = rl.isPoll;
        this.body = rl.body;
        this.method = rl.method;
        this.host = rl.host;
        this.uri = rl.uri;
        this.headers = rl.headers;
        this.message = rl.message;
        this.timestamp = rl.timestamp;
        this.responseTime = rl.responseTime;
        if (id != null) {
            this.update();
            return true;
        }
        return false;
    }

    // trim strings longer than maximum length
    public void verify() {
        body = body.trim();
        if (body.length() > BODY_MAX_LENGTH) {
            body = body.substring(0, BODY_MAX_LENGTH - 2) + "";
            // Logger.warn("[ResourceLog] trimming body: " + resource.label + " " +
            // body);
        }

        headers = headers.trim();
        if (headers.length() > MAX_LENGTH) {
            headers = headers.substring(0, MAX_LENGTH - 2) + "";
            // Logger.warn("[ResourceLog] trimming body: " + resource.label + " " +
            // body);
        }

        message = message.trim();
        if (message.length() > MAX_LENGTH) {
            message = message.substring(0, MAX_LENGTH - 2) + "";
            // Logger.warn("[ResourceLog] trimming body: " + resource.label + " " +
            // body);
        }

        uri = uri.trim();
        if (uri.length() > 255) {
            uri = uri.substring(0, 255 - 2) + "";
            // Logger.warn("[ResourceLog] trimming body: " + resource.label + " " +
            // body);
        }

        host = host.trim();
        if (host.length() > 255) {
            host = host.substring(0, 255 - 2) + "";
            // Logger.warn("[ResourceLog] trimming body: " + resource.label + " " +
            // body);
        }

        method = method.trim();
        if (method.length() > 255) {
            body = body.substring(0, 255 - 2) + "";
            // Logger.warn("[ResourceLog] trimming body: " + resource.label + " " +
            // body);
        }

        // trimString(body);
        // trimString();
        // trimString(host);
        // trimString(uri);
        // trimString(headers);
        // trimString(message);
    }

    public void save() {
        verify();
        super.save();
    }

    public void update() {
        verify();
        super.update();
    }


    public static ResourceLog createOrUpdate(ResourceLog resourceLog) {
        try {
            if (resourceLog.resource != null) {
                if (resourceLog.creationTimestamp == null || resourceLog.creationTimestamp == 0L) {
                    resourceLog.creationTimestamp = Utils.currentTime();
                }
                ResourceLog rplCopy = getByResource(resourceLog.resource, resourceLog.isPoll);
                if (rplCopy != null) {
                    rplCopy.updateResourceLog(resourceLog);
                    Logger.info("[ResourceLog] updating existing for " + resourceLog.resource.label
                            + ", id: " + resourceLog.resource.id);
                    return rplCopy;
                } else {
                    resourceLog.save();
                    Logger.warn("[ResourceLog] creating new for " + resourceLog.resource.label
                            + ", id: " + resourceLog.resource.id);
                    return resourceLog;
                }

            } else {
                Logger.warn("[ResourceLog] resource null");
            }
        } catch (Exception e) {
            Logger.error(e.getMessage() + e.getStackTrace()[0].toString() + e.toString());
        }
        return null;
    }

    public static ResourceLog getByResource(Resource resource, boolean isPoll) {
        if (resource == null) {
            Logger.warn("[ResourcePostLog] Could not find one for resource: Null");
            return null;
        }
        ResourceLog rpl =
                find.where().eq("resource_id", resource.id).eq("is_poll", isPoll).findUnique();
        if (rpl == null) {
            Logger.warn("[ResourceLog] Could not find a " + ((isPoll) ? "poll" : "post")
                    + " log for resource: " + resource.id.toString() + ", id: " + resource.label);
        }
        return rpl;
    }

    public static ResourceLog getById(Long id) {
        return find.byId(id);
    }

    public static void delete(Long id) {
        find.ref(id).delete();
    }

    public static void deleteByResource(Resource resource) {
        Ebean.delete(find.where().eq("resource_id", resource.id).findList());
    }

}
