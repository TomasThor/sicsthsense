/*
 * Copyright (c) 2013, Swedish Institute of Computer Science
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of The Swedish Institute of Computer Science nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
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
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/* Description:
 * TODO:
 * */

package controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import logic.FileSystem;
import models.User;
import models.Vfile;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import views.html.filesPage;

public class CtrlFile extends Controller {

	@Security.Authenticated(Secured.class)
	public static Result getFiles() {
		User currentUser = Secured.getCurrentUser();
		List<Vfile> vfiles = FileSystem.listFiles(currentUser);
		Map<String, String> fileTree = new HashMap<String, String>(vfiles.size()+1);
		for(Vfile vf : vfiles) {
			vf.getPath();
		}

		return TODO;
	}

	/** Generates a path listing
	 * @param path: the folder to explore
	 * @param full: whether to generate the full html page or only the table body
	 * @returns: an html page representing the result
	 * */
	@Security.Authenticated(Secured.class)
	public static Result browse(String path) {
		User currentUser = Secured.getCurrentUser();
		path = Utils.decodePath(path);
		if( path.trim().equalsIgnoreCase("") || path.trim().equalsIgnoreCase("/")) {
			return ok(filesPage.render(FileSystem.lsDir(currentUser, "/"), "/",
					""));
		}

		Vfile vfile = FileSystem.readFile(currentUser, path);
		if(vfile == null) {
			return notFound(filesPage.render(FileSystem.lsDir(currentUser, "/"), "/",
					"Not found!"));
			}
		if (vfile.isFile() && vfile.getLink() != null) {
			return Application.viewStream(vfile.getLink().id);
		} else if (vfile.isDir()) {
			return ok(filesPage.render(FileSystem.lsDir(currentUser, path), path,
					""));
		} else {
			return notFound(filesPage.render(FileSystem.lsDir(currentUser, "/"), "/",
					"Not found!"));
		}
	}

	//gives partial page for ajax requests
	@Security.Authenticated(Secured.class)
	public static Result miniBrowse(String path) {
		User currentUser = Secured.getCurrentUser();
		path = Utils.decodePath(path);

		if( path.trim().equalsIgnoreCase("") || path.trim().equalsIgnoreCase("/")) {
			return ok(views.html.filesUtils.listDir.render(FileSystem.lsDir(currentUser, "/"), "/"));
		}
		Vfile vfile = FileSystem.readFile(currentUser, path);
		if(vfile == null) {
			return notFound(views.html.filesUtils.listDir.render(FileSystem.lsDir(currentUser,"/"), "/"));
		}
		if(vfile.isFile() && vfile.getLink() != null) {
			return Application.ajaxViewStream(vfile.getLink().id);
		} else if(vfile.isDir()) {
	    return ok(views.html.filesUtils.listDir.render(FileSystem.lsDir(currentUser,path), path));
		} else {
			return notFound(views.html.filesUtils.listDir.render(FileSystem.lsDir(currentUser,"/"), "/"));
		}
	}

	@Security.Authenticated(Secured.class)
	public static Result delete(String path) {
		User currentUser = Secured.getCurrentUser();
		path = Utils.decodePath(path);
		boolean success = FileSystem.deleteFile(currentUser, path);
		if (success)
			return ok("true");
		else
			return notFound("false");
	}

	@Security.Authenticated(Secured.class)
	public static Result move(String path, String newPath) {
		User currentUser = Secured.getCurrentUser();
		path = Utils.decodePath(path);
		boolean success = FileSystem.moveFile(currentUser, path, newPath);
		if (success)
			return ok(views.html.filesUtils.listDir.render(FileSystem.lsDir(
					currentUser, Vfile.extractUpperLevelPath(newPath)), Vfile.extractUpperLevelPath(newPath)));
		else
			return notFound(views.html.filesUtils.listDir.render(FileSystem.lsDir(
					currentUser, Vfile.extractUpperLevelPath(path)), Vfile.extractUpperLevelPath(path)));
	}

	@Security.Authenticated(Secured.class)
	public static Result createDir(String path) {
		User currentUser = Secured.getCurrentUser();
		path = Utils.decodePath(path);
		boolean success = (FileSystem.addDirectory(currentUser, path) != null);
		if (success)
			return ok(views.html.filesUtils.listDir.render(FileSystem.lsDir(
					currentUser, Vfile.extractUpperLevelPath(path)),Vfile.extractUpperLevelPath(path)));
		else
			return notFound(views.html.filesUtils.listDir.render(FileSystem.lsDir(
					currentUser, "/"), "/"));
	}

	@Security.Authenticated(Secured.class)
	public static Result createFile(String path) {
		User currentUser = Secured.getCurrentUser();
		path = Utils.decodePath(path);
		boolean success = false;
		Vfile f = null;
		if (!FileSystem.fileExists(currentUser, path)) {
			f = FileSystem.addFile(currentUser, path);
			success = ( f != null );
		}
		if (success)
			return ok(views.html.filesUtils.listDir.render(FileSystem.lsDir(
					currentUser, f.getParentPath()), f.getParentPath()));
		else
			return notFound(views.html.filesUtils.listDir.render(FileSystem.lsDir(currentUser,"/"), "/"));
	}

	private static void parsePath(Vfile vfile) {
		String path = vfile.getPath();
		User user = vfile.getOwner();
		int i=0;
		int sep=2;
		while ( (sep=path.indexOf('/',sep)) != -1 ) { // for each subdir into path
			String ancestors = path.substring(0, sep);
			if (FileSystem.isDir(user, ancestors)) { // if parent is a dir
				//create dir
				FileSystem.addDirectory(user, ancestors);
			} else if (FileSystem.isFile(user, path)) { // if it is a file
				// complain
				Logger.info("Path already exists as a file: "+path);
			} else if (FileSystem.isFile(user, ancestors)) {
				// complain
				Logger.error("Subpath already exists as a file: "+ancestors);
				//return null;
			}
		}
	}



}
