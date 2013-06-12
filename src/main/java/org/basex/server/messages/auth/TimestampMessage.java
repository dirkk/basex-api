package org.basex.server.messages.auth;

import java.io.Serializable;

public class TimestampMessage implements Serializable {
  /** Timestamp. */
  private final long ts;
  
  public TimestampMessage() {
    this(System.nanoTime());
  }
  
  public TimestampMessage(final long t) {
    ts = t;
  }
  
  public long getTimestamp() {
    return ts;
  }
}
