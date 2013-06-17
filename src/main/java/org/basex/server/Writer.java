package org.basex.server;

import java.io.*;

import scala.concurrent.duration.*;

import akka.actor.*;
import akka.io.*;
import akka.util.*;

/**
 * A writer helper class for the BaseX client/server architecture. The data
 * is built using a {@link ByteStringBuilder}, so it uses {@link ByteString}
 * and avoids data copies.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class Writer {
  /** Message builder. */
  private ByteStringBuilder builder;
  
  /**
   * Constructor.
   */
  public Writer() {
    builder = new ByteStringBuilder();
  }
  
  /**
   * Write a string as UTF-8 encoded byte array. The string is followed
   * by a 0 byte to signal the end of the string.
   * @param text text to send
   */
  public void writeString(final String text) {
    builder.append(ByteString.fromString(text));
    writeTerminator();
  }
  
  /**
   * Returns an {@link OutputStream}, which can now be used to write data to
   * the message
   * @return output stream
   */
  public OutputStream getOutputStream() {
    return builder.asOutputStream();
  }
  
  /**
   * In case of successful execution write a 0 byte, otherwise write a
   * 1 byte.
   * @param success execution was successful
   */
  public void writeSuccess(final boolean success) {
    builder.putByte((byte) (success ? 0 : 1));
  }
  
  /**
   * Write the termination sign, which is currently a 0 byte.
   */
  public void writeTerminator() {
    builder.putByte((byte) 0);
  }
  
  /**
   * Send the whole message. This is done asynchronously.
   * 
   * @param dest destination actor to send to
   * @param sender sending actor
   */
  public void send(final ActorRef dest, final ActorRef sender) {
    dest.tell(TcpMessage.write(builder.result()), sender);
  }
  
  /**
   * Send a message once to another actor with a certain amount of delay before
   * sending. This is done asynchronously.
   *
   * @param dest destination actor to send to
   * @param sender sending actor
   * @param sys actor system
   * @param duration time to wait before sending
   */
  public void sendDelayed(final ActorRef dest, final ActorRef sender,
      final ActorSystem sys, FiniteDuration duration) {
    sys.scheduler().scheduleOnce(
        duration,
        dest,
        builder.result(),
        sys.dispatcher(),
        sender
    );
  }
}
