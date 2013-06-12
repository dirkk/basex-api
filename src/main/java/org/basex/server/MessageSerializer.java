package org.basex.server;

import org.basex.server.messages.*;

import akka.serialization.*;

/**
 * This serializes all messages implementing {@link Message} to be
 * send to remote nodes.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class MessageSerializer extends JSerializer {
  // This is whether "fromBinary" requires a "clazz" or not
  @Override
  public boolean includeManifest() {
    return false;
  }
 
  /**
   * A unique identifier for this BaseX specific message serializer.
   */
  @Override
  public int identifier() {
    return 49900;
  }
 
  /**
   * This function serializes the given object into a
   * byte array to be send over the wire.
   */
  @Override
  public byte[] toBinary(Object obj) {
    byte[] b = new byte[1];
    b[0] = 0x01;
    return b;
  }
 
  /**
   * This deserializes the given byte array into an object.
   */
  @Override
  public Object fromBinaryJava(byte[] bytes, Class<?> clazz) {
    return new String("TEST");
  }
}
