package models;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.*;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;

import play.*;
import play.core.Router.Routes;
import play.Logger;
import play.data.validation.Constraints;
import play.db.ebean.*;
import play.mvc.*;
import controllers.*;
import play.data.Form;

import play.mvc.Http.Request;

import models.*;
import views.html.*;

@Entity
@Table(name = "sources")
public class Source extends Operator {

    @Id
    public Long id;

    @ManyToOne//(cascade = CascadeType.ALL) 
    public User owner;

    @ManyToOne//(cascade = CascadeType.ALL) 
		public List<Stream> outputStreams;

	/**
	 * The serialization runtime associates with each serializable class a version
	 * number, called a serialVersionUID
	 */
	// @Transient

	public String label = "NewSource";
  public Long pollingPeriod = 0L;
  public Long lastPolled = 0L;
	public String pollingUrl = null;
	public String pollingAuthenticationKey = null;

	@OneToMany(mappedBy = "source", cascade = CascadeType.ALL)
	public List<StreamParser> streamParsers = new ArrayList<StreamParser>();

	/** Secret token for authenticating posts coming from outside */
	private String token;


	public static Model.Finder<Long, Source> find = new Model.Finder<Long, Source>(
			Long.class, Source.class);

	/** Call to create, or update an access token */
	private String updateToken() {
		String newtoken = UUID.randomUUID().toString();
		token = newtoken;
		if(id > 0) {
			this.update();
		}
		return token;
	}
	
	public Source(User owner, String label, Long pollingPeriod,
			 String pollingUrl, String pollingAuthenticationKey) {
		super();
		this.label = label;
		this.owner = owner;
		this.pollingPeriod = pollingPeriod;
		this.lastPolled = 0L;
		this.pollingUrl = pollingUrl;
		this.pollingAuthenticationKey = pollingAuthenticationKey;
	}

	public Source(String label, Long pollingPeriod,
			 String pollingUrl, String pollingAuthenticationKey) {
		this(null, label, pollingPeriod, pollingUrl, pollingAuthenticationKey);
	}
	
	protected String getToken() {
		return token;
	}

	public void updateSource(Source source) {
		this.label = source.label;
		this.pollingPeriod = source.pollingPeriod;
		this.lastPolled = source.lastPolled;
		this.pollingUrl = source.pollingUrl;
		this.pollingAuthenticationKey = source.pollingAuthenticationKey;
		if(token == null || "".equalsIgnoreCase(token)) {
			updateToken();
		}
		update();
	}

	/**
	public void performRequest() {

      WSRequestHolder request = WS.url(pollingUrl);
      request.get().map(
        new Function<WS.Response, Boolean>() {
          public Boolean apply(WS.Response response) {
        	System.out.println("type " + response.getHeader("Content-type"));
        	JsonNode jsonBody = null;
        	String textBody = null;
        	String strBody = null;
        	switch(response.getHeader("Content-type")) {
        		case "application/json":
        			jsonBody = response.asJson();
        			strBody = jsonBody.asText();
        			break;
        		default:
        			textBody = response.getBody();
        			strBody = textBody.length() + " bytes";
        			break;
        	}
            //Logger.info("[Streams] polling response for: " + resource.fullPath() + ", content type: " + response.getHeader("Content-Type") + ", payload: " + strBody);
            //parseResponse(endPoint, jsonBody, textBody, resource);
            return true;
          }
        }
      );
      //lastPolled = current;
	}
*/

	public HttpURLConnection probe() {
		Logger.warn("probe(): "+pollingUrl);
		HttpURLConnection connection = null;  
		PrintWriter outWriter = null;  
		BufferedReader serverResponse = null;  
		StringBuffer returnBuffer = new StringBuffer();  
		String line;  
		User currentUser = Secured.getCurrentUser();

		try { 
			connection = ( HttpURLConnection ) new URL( pollingUrl ).openConnection();  
			connection.setRequestMethod( "GET" );  
			//connection.setDoOutput( true );  
			/*	
			//CREATE A WRITER FOR OUTPUT  
			outWriter = new PrintWriter( connection.getOutputStream() );  
			//PARAMETERS  
			buff.append( "param1=" );   
			buff.append( URLEncoder.encode( "Param 1 Value", "UTF-8" ) );  
			buff.append( "&" );  
			buff.append( "param2=" );   
			buff.append( URLEncoder.encode( "Param 2 Value", "UTF-8" ) );  
			//SEND PARAMETERS  
			outWriter.println( buff.toString() );  
			outWriter.flush();  
			outWriter.close();  
			*/	
			return connection;

		} catch (MalformedURLException mue) {  
			Logger.error(mue.toString() + " Stack trace:\n" + mue.getStackTrace().toString() );  
		  //return badRequest("Malformed URL");
		} catch (IOException ioe) {  
			Logger.error(ioe.toString() + " Stack trace:\n" + ioe.getStackTrace().toString() );
		  //return badRequest("IO Exception on probe()");
		} finally {  
			if (connection!=null) connection.disconnect();  	
			if (serverResponse!=null) { try {serverResponse.close();} catch (Exception ex) {}  }  
		}  
		return null;
	}

	public boolean poll() {
		// perform a poll() if it is time
		long time = System.currentTimeMillis() / 1000L;
		if ( (lastPolled+pollingPeriod) < time) {
			return false; // dont poll yet
		}
		Logger.info("Poll() happening!");
		lastPolled = time;
		return true;
	}
	
	public Boolean checkToken(String token) {
		return token == this.token;
	}

	public static Source get(Long id, String key) {
		Source source = find.byId(id);
		if (source != null && source.checkToken(key))
			return source;
		return null;
	}

	public static Source get(Long id, User user) {
		Source source = find.byId(id);
		if ( source != null && source.owner.equals(user) )
			return source;
		return null;
	}

	public Source(User user) {
		this.owner = user;
	}

	public Source() {
		super();
	}

	public static Source create(User user) {
		if (user != null) {
			Source source = new Source(user);
			source.save();
			return source;
		}
		return null;
	}
	
	public static Source create(Source source) {
		if (source.owner != null) {
			source.save();
			source.updateToken();
			return source;
		}
		return null;
	}

	public void setPeriod(Long period) {
		this.pollingPeriod = period;
	}

	public boolean parseAndPost(Request req) {
		boolean result = false;
		/*
		for (StreamParser sp : streamParsers) {
			if (sp != null) {
				result |= sp.parseResponse(req);
			}
		}*/
		return result;
	}

}
