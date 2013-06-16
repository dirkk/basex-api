package org.basex.server;

import java.io.*;

import akka.util.*;

public class Reader {
  /** Incoming data packet. */
  private final ByteString data;
  /** Reader Index. */
  private Integer readerIndex = 0;
  
  /**
   * Constructor
   * @param d data package
   */
  public Reader(final ByteString d) {
    data = d;
  }
  
  public ByteString getNext() {
    int start, end;
    synchronized(readerIndex) {
      start = readerIndex;
      end = data.indexOf((byte) 0, start);
      readerIndex = end + 1;
    }
    return data.slice(start, end);
  }
  
  public InputStream getInputStream() {
    return data.drop(readerIndex).iterator().asInputStream();
  }
  
  public String getString() {
    return getNext().utf8String();
  }
  
  public byte getByte() {
    synchronized(readerIndex) {
      return data.slice(readerIndex, ++readerIndex).toArray()[0];
    }
  }
  
  public void decreaseReader() {
    synchronized(readerIndex) {
      --readerIndex;
    }
  }
}
