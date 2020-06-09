// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.


package com.google.sps.servlets;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.gson.Gson;
import com.google.sps.data.Comment;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet that returns some example content. TODO: modify this file to handle comments data */
@WebServlet("/data")
public class DataServlet extends HttpServlet {
  private ArrayList<Comment> comments;
  //the default number of comments to be displayed when the user loads the page
  public static final int MAX_COMMENTS_NUM = 20;
  
  @Override
  public void init() {
    comments = new ArrayList<Comment>();
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    Query query = new Query("Comment").addSort("timestamp", SortDirection.DESCENDING);

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery results = datastore.prepare(query);

    int commentsNum = Integer.parseInt(request.getParameter("comments-num"));

    List<Comment> comments = new ArrayList<>();
    for (Entity entity : results.asIterable(FetchOptions.Builder.withLimit(commentsNum))) {
      long id = entity.getKey().getId();
      String text = (String) entity.getProperty("comment-text");
      String email = (String) entity.getProperty("email");
      long timestamp = (long) entity.getProperty("timestamp");

      Comment comment = new Comment(id, email, text, timestamp);
      comments.add(comment);
    }

    Gson gson = new Gson();
    response.setContentType("application/json;");
    response.getWriter().println(gson.toJson(comments));
  }

  /**
   * Converts a ServerStats instance into a JSON string using the Gson library. Note: We first added
   * the Gson library dependency to pom.xml.*/
   
  private String convertToJsonUsingGson(ArrayList array) {
    Gson gson = new Gson();
    String json = gson.toJson(array);
    return json;
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Only logged-in users can post messages
    UserService userService = UserServiceFactory.getUserService();
    if (!userService.isUserLoggedIn()) {
      response.sendRedirect("contact-form.html#comment-header");
      return;
    }

    // Get the input from the form.
    String commentsNum = request.getParameter("comments-num");
    String text = getParameter(request, "comment-input", "");

    long timestamp = System.currentTimeMillis();
    String email = userService.getCurrentUser().getEmail();
    String userName = getUserName(email);
    //find the userName from the email

    // Create a new entity
    Entity cmtEntity = new Entity("Comment");
    cmtEntity.setProperty("comment-text", text);
    cmtEntity.setProperty("timestamp", timestamp);
    cmtEntity.setProperty("user-name", userName);
    long id = cmtEntity.getKey().getId();

    comments.add(new Comment(id, userName, text, timestamp));

    //create a datastore to store those entities (each of them has content and a timestamp)
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    datastore.put(cmtEntity);

    // Respond with the result.
    response.setContentType("text/html;");
    response.getWriter().println(text);
    response.sendRedirect("/contact-form.html#comment-header");
  }

  /**
   * @return the request parameter, or the default value if the parameter
   *         was not specified by the client
   */
  private String getParameter(HttpServletRequest request, String name, String defaultValue) {
    String value = request.getParameter(name);
    if (value == null) {
      return defaultValue;
    }
    return value;
  }

  private String getUserName(String email){
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    Query query =
        new Query("UserInfo")
            .setFilter(new Query.FilterPredicate("email", Query.FilterOperator.EQUAL, email));
    PreparedQuery results = datastore.prepare(query);
    Entity entity = results.asSingleEntity();
    if (entity == null) {
      return "";
    }
    String userName = (String) entity.getProperty("user-name");
    return userName;
  }

}
