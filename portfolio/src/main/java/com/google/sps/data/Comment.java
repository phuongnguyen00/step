package com.google.sps.data;

/** An item on a todo list. */
public final class Comment {

  private final long id;
  private final String userName;
  private final String text;
  private final String time;


  public Comment(long id, String userName, String text,  String time) {
    this.id = id;
    this.userName = userName;
    this.text = text;
    this.time = time;
  }

}