package com.google.sps.data;

/** An item on a todo list. */
public final class Comment {

  private final long id;
  private final String email;
  private final String text;
  private final long timestamp;


  public Comment(long id, String email, String text, long timestamp) {
    this.id = id;
    this.email = email;
    this.text = text;
    this.timestamp = timestamp;
  }

}