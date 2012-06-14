package controllers;

//import models.Project;
//import models.Task;
//import models.User;
import java.util.HashMap;

import models.EndPoint;

import play.*;

import play.core.Router.Routes;
import play.libs.F.*;
import play.libs.*;
import play.mvc.*;

import views.html.*;
import models.*;

@Security.Authenticated(Secured.class)
public class Application extends Controller {
  
  public static Result home() {
    return ok(home.render());
  }
  
  public static Result login() {
    return redirect(routes.Application.home());
  }
  
  public static Result manage() {
    return ok(manage.render(EndPoint.getByUser(CtrlUser.getUser())));
  }
    
}
