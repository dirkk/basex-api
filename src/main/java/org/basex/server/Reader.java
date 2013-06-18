package org.basex.server;

import java.io.*;

import akka.util.*;

/**
 * A helper class to parse incoming data packets from the client side
 * using the client/server binary protocol for BaseX.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class Reader {
  /** Incoming data packet. */
  private final ByteString data;
  /** Reader Index. */
  private Integer readerIndex = 0;
  
  /**
   * Constructor.
   * @param d data package
   */
  public Reader(final ByteString d) {
    data = d;
  }
  
  /**
   * Returns the next {@link ByteString}, which is terminated by a 0 byte.
   * @return byte string
   */
  public ByteString getNext() {
    int start, end;
    synchronized(readerIndex) {
      start = readerIndex;
      end = data.indexOf((byte) 0, start);
      readerIndex = end + 1;
    }
    return data.slice(start, end);
  }
  
  /**
   * Get the rest of the data packet as input stream.
   * @return input stream
   */
  public InputStream getInputStream() {
    return data.drop(readerIndex).iterator().asInputStream();
  }
  
  /**
   * Returns the next String terminated by a 0 byte.
   * @return UTF-8 encoded string
   */
  public String getString() {
    return getNext().utf8String();
  }
  
  /**
   * Returns the next byte.
   * @return single byte
   */
  public byte getByte() {
    synchronized(readerIndex) {
      return data.slice(readerIndex, ++readerIndex).toArray()[0];
    }
  }
  
  /**
   * Decrease the position of the reader index by one.
   */
  public void decreaseReader() {
    synchronized(readerIndex) {
      --readerIndex;
    }
  }
}
