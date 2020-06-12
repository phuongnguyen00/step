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

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
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

import java.util.TimeZone;
import java.text.SimpleDateFormat;  
import java.util.Date; 
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
    ArrayList<Comment> comments = new ArrayList<Comment>();
    int commentsNum = Integer.parseInt(request.getParameter("comments-num"));
    String sortingOrder = request.getParameter("sorting-cmt");
    String languageCode = request.getParameter("translation-cmt");
    
    if (sortingOrder.equals("new")) {
        comments = getCommentsSorted("new", commentsNum, languageCode);
    } else if (sortingOrder.equals("old")) {
        comments = getCommentsSorted("old", commentsNum, languageCode);
    } else { //sort by userName
        comments = getCommentsSortedUserNames(commentsNum, languageCode);
    }
    Gson gson = new Gson();
    response.setContentType("application/json;");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().println(gson.toJson(comments));
  }

  /** Return an ArrayList of sorted comments based on newest to oldest or oldest to newest
  */
  private ArrayList<Comment> getCommentsSorted(String sortingOrder, int commentsNum, String languageCode){
    Query query = new Query("Comment");
    if (sortingOrder.equals("new")) {
       query.addSort("timestamp", SortDirection.DESCENDING);
    } else {
       query.addSort("timestamp", SortDirection.ASCENDING);
    }

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery results = datastore.prepare(query);

    ArrayList<Comment> comments = new ArrayList<>();

    // Do the translation.
    Translate translator = TranslateOptions.getDefaultInstance().getService();

    // For converting into Date-Time from timestamp
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    simpleDateFormat.setTimeZone(TimeZone.getTimeZone("PST"));

    for (Entity entity : results.asIterable(FetchOptions.Builder.withLimit(commentsNum))) {
      long id = entity.getKey().getId();
      String email = (String) entity.getProperty("email");

      // Translate the text based on users' request
      String originalText = (String) entity.getProperty("comment-text");
      String text = getTranslatedContent(translator, originalText, languageCode);

      // Convert timestamp into datetime format
      long timestamp = (long) entity.getProperty("timestamp");
      String time = simpleDateFormat.format(new Date(timestamp));

      String userName = (String) getUserName(email);

      Comment comment = new Comment(id, userName, text, time);
      comments.add(comment);
    }
    return comments;
  }

  /** @return an ArrayList of sorted comments based on user names
  */
  private ArrayList<Comment> getCommentsSortedUserNames(int commentsNum, String languageCode) {
    
    Query queryUserName = new Query("UserInfo").addSort("user-name", SortDirection.ASCENDING);
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery queryUser = datastore.prepare(queryUserName);
    int count = 0;

    ArrayList<Comment> comments = new ArrayList<Comment>();

    // Get a translator.
    Translate translator = TranslateOptions.getDefaultInstance().getService();

    // For converting into Date-Time from timestamp
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    simpleDateFormat.setTimeZone(TimeZone.getTimeZone("PST"));

    // Iterate over a query of UserInfo entities
    for (Entity userEntity : queryUser.asIterable()) {
        // Get the user email from the user name to access a query of 
        String email = (String) userEntity.getProperty("email");

        // If same usernames, then sort by time descending
        Query queryCommentUnprepared =
        new Query("Comment")
            .setFilter(new Query.FilterPredicate("email", Query.FilterOperator.EQUAL, email)).addSort("timestamp", SortDirection.DESCENDING);
        PreparedQuery queryComment = datastore.prepare(queryCommentUnprepared);

        // Iterate over a query of Comment entities based on the email address, with the order of usernames
        for (Entity entity: queryComment.asIterable()) {
            long id = entity.getKey().getId();
            
            // Translate the text based on users' request
            String originalText = (String) entity.getProperty("comment-text");
            String text = getTranslatedContent(translator, originalText, languageCode);

            // Get the time based on the timestamp
            long timestamp = (long) entity.getProperty("timestamp");
            String time = simpleDateFormat.format(new Date(timestamp));
            
            String userName = (String) getUserName(email);

            Comment comment = new Comment(id, userName, text, time);
            count += 1;
            comments.add(comment);
            if (count >= commentsNum) {break;}
        }
        if (count >= commentsNum) {break;}
    }
    return comments;
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

    // Create a new entity. Store email in entity, but store user-name in object Comment
    Entity cmtEntity = new Entity("Comment");
    cmtEntity.setProperty("comment-text", text);
    cmtEntity.setProperty("timestamp", timestamp);
    cmtEntity.setProperty("email", email);
    long id = cmtEntity.getKey().getId();

    //create a datastore to store those entities (each of them has content and a timestamp)
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    datastore.put(cmtEntity);

    // Respond with the result.
    response.setContentType("text/html;");
    response.getWriter().println(text);
    response.sendRedirect("/contact-form.html#comment-header");
  }

  /**
  * @return translated text into a language with languageCode
  */
  private String getTranslatedContent(Translate translator, String originalText, String languageCode){
      Translation trasnlatedText = translator.translate(originalText, Translate.TranslateOption.targetLanguage(languageCode));
      return trasnlatedText.getTranslatedText();
  }

    /**
   * Converts a ServerStats instance into a JSON string using the Gson library. Note: We first added
   * the Gson library dependency to pom.xml.*/
   
  private String convertToJsonUsingGson(ArrayList array) {
    Gson gson = new Gson();
    String json = gson.toJson(array);
    return json;
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

  /** 
   * @return username based on email address
   */
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
