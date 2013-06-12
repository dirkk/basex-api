package org.basex.server;

import org.basex.core.*;
import org.jboss.netty.util.*;

import akka.actor.*;
import akka.event.*;
import akka.io.Tcp.ConnectionClosed;
import akka.io.Tcp.Received;
import akka.io.*;
import akka.util.*;
import static org.basex.util.Token.md5;

/**
 * Actor class to handle a connected client at the server-side. This class
 * is non-blocking.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class ClientHandler extends UntypedActor {
  /** Logging adapter. */
  private final LoggingAdapter log;
  /** Authentification timestamp. */
  private String ts;
  /** Database context. */
  private Context dbContext;
  
  /**
   * Create Props for the client handler actor.
   * @param ctx database context
   * @return Props for creating this actor, can be further configured
   */
  public static Props mkProps(final Context ctx) {
    return Props.create(ClientHandler.class, ctx);
  }
  
  /**
   * Constructor.
   * @param ctx database context
   */
  public ClientHandler(final Context ctx) {
    log = Logging.getLogger(getContext().system(), this);
    dbContext = ctx;
  }
  
  @Override
  public void onReceive(Object msg) throws Exception {
    // client is not authenticated
    if (msg instanceof String && ((String) msg).equalsIgnoreCase("connect")) {
      ts = Long.toString(System.nanoTime());
      getSender().tell(TcpMessage.write(buildMessage(ts, true)), getSelf());
    } else if (msg instanceof Received) {
      // client sends username + hashed passsword
      final ByteString data = ((Received) msg).data();
      int reader = 0;
      int end = data.indexOf((byte) 0, reader);
      final String user = data.slice(reader, end).utf8String();
      reader = end + 1;
      end = data.indexOf((byte) 0, reader);
      final String hashedPw = data.slice(reader, end).utf8String();
      
      dbContext.user = dbContext.users.get(user);
      if (md5(dbContext.user.password + ts).equals(hashedPw)) {
        log.info("Success");
      } else {
        log.info("Authentification failed.");
      }
      
      log.info(data.decodeString(CharsetUtil.UTF_8.name()));
      getSender().tell(TcpMessage.write(data), getSelf());
    } else if (msg instanceof ConnectionClosed) {
      getContext().stop(getSelf());
    }
  }
  
  /**
   * Build a message to send over the wire, consisting of a string, followed
   * by either a 0 byte (success) or 1 byte (failed)
   * @param out string to write
   * @param success success flag
   * @return byte string
   */
  private ByteString buildMessage(final String out, final boolean success) {
    ByteStringBuilder bb = new ByteStringBuilder();
    bb.append(ByteString.fromString(out));
    bb.putByte((byte) (success ? 0 : 1));
    return bb.result();
  }
  
}
