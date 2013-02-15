package actions;

import models.Resource;
import models.Stream;
import models.User;
import play.Logger;
import play.mvc.Action;
import play.mvc.Result;
import play.mvc.Http.Context;

public class CheckPermissionsAction extends Action<CheckPermissions> {

//	public boolean accessResource(Context ctx, Long resourceId) {
//    	String id = ctx.session().get("id");
//    	return canAccessResource(id, resourceId); 
//    }
//	
//	@Override
//	public Result call(Context ctx) throws Throwable {
//	//		/streams/:id	
//		if(configuration.type() == Stream.class) {
//			long resID;
//			String[] id = ctx.request().queryString().get("id");
//			if(id != null ) {
//				resID = Long.parseLong(id[0]);
//				Logger.info("ctx.request().queryString().get(id) = " + id[0]);
//				return ( accessResource(ctx, resID) ? delegate.call(ctx) : onUnauthorized(ctx));
//			}
//		} 
//			Logger.info("CheckPermissionsAction called for an object that is not a stream: " + ctx);
//			return delegate.call(ctx);
//	}
//
//	public static boolean ownsEndPoint(String idStr, Long endPointId) {
//	  return Long.parseLong(idStr) == Resource.get(endPointId).getUser().id;
//	}
//
//	public static boolean ownsResource(String idStr, Long resourceId) {
//	  return Long.parseLong(idStr) == Stream.get(resourceId).getUser().id;
//	}
//	
//	public static boolean canAccessResource(String idStr, Long resourceId) {
//		Stream stream = Stream.get(resourceId);
//		if(stream != null && stream.isPublicAccess()) {
//			return true;
//		} else if(idStr != null ){
//			long userID = Long.parseLong(idStr);
//			User user = User.get(userID);
//			return ( 
//    			( user != null && 
//    			(ownsResource(idStr, resourceId) || stream.isShare( user )) 
//    			));
//		} else { 
//    	return false;
//		}
//	}
//	
//	public Result onUnauthorized(Context ctx) {
//		//String id = ctx.request().username();
//		return unauthorized(views.html.errorPage.render("Unauthorized! This is not shared with you!"));
//		//return unauthorized("## You can not read or modify a stream that is not shared with you!");
//	}
//	
//	public static Result onUnauthorized() {		
//		//Context.current() will return the context associated with the 
//		//current thread
//		return (new CheckPermissionsAction().onUnauthorized(Context.current()));
//	}
//	
//    public static String getUsername(Context ctx) {
//        String id = ctx.session().get("id");
//        if(id != null && User.exists(Long.parseLong(id)))    return id;
//        else                                                 return null;
//    }
//    public static String getUsername() {
//        return getUsername(Context.current());
//    }

}