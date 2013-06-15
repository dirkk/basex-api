package org.basex.server;

import java.io.*;
import java.util.concurrent.*;

import org.basex.core.*;
import org.basex.core.cmd.*;
import org.basex.core.parse.*;
import org.basex.io.in.*;
import org.basex.io.out.*;
import org.basex.query.*;

import scala.concurrent.duration.*;

import akka.actor.*;
import akka.event.*;
import akka.io.Tcp.ConnectionClosed;
import akka.io.Tcp.Received;
import akka.io.*;
import akka.japi.*;
import akka.util.*;

import static org.basex.util.Token.md5;
import static org.basex.core.Text.*;

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
      ByteString data = ((Received) msg).data();
      Reader r = new Reader(data);
      final String user = r.getString();
      final String hashedPw = r.getString();
      
      dbContext.user = dbContext.users.get(user);
      if (md5(dbContext.user.password + ts).equals(hashedPw)) {
        log.info("Authentification successful for user {}", dbContext.user);

        // send successful response message
        ByteStringBuilder bb = new ByteStringBuilder();
        bb.putByte((byte) 0);
        getSender().tell(TcpMessage.write(bb.result()), getSelf());
        
        // change incoming message processing
        getContext().become(authenticated());
      } else {
        log.info("Access denied for user {}", dbContext.user);
        
        // send authentication denied message with 1s delay
        ByteStringBuilder bb = new ByteStringBuilder();
        bb.putByte((byte) 1);
        sendDelayed(TcpMessage.write(bb.result()), getSender(), Duration.create(1, TimeUnit.SECONDS));
      }
    } else if (msg instanceof ConnectionClosed) {
      connectionClosed((ConnectionClosed) msg);
    }
  }
  
  /**
   * Send a message once to another actor with a certain amount of delay before
   * sending.
   * @param msg message
   * @param dest destination actor to send to
   * @param duration time to wait before sending
   */
  protected void sendDelayed(Object msg, ActorRef dest, FiniteDuration duration) {
    getContext().system().scheduler().scheduleOnce(
        duration,
        dest,
        msg,
        getContext().system().dispatcher(),
        getSelf()
    );
  }
  
  /**
   * Message handling procedure when already authenticated, i.e. normal
   * operational mode.
   * @return normal authenticated procedure
   */
  private Procedure<Object> authenticated() {
    return new Procedure<Object>() {
      @Override
      public void apply(Object msg) throws Exception {
        if (msg instanceof Received) {
          Received recv = (Received) msg;
          Reader reader = new Reader(recv.data());
          
          // get the command byte
          ServerCmd sc = ServerCmd.get(reader.getByte());
          if (sc == ServerCmd.CREATE) {
            create(reader);
          } else if (sc == ServerCmd.COMMAND) {
            reader.decreaseReader();
            command(reader);
          }
        } else if (msg instanceof ConnectionClosed) {
          connectionClosed((ConnectionClosed) msg);
        }
      }
    };
  }
  
  /**
   * Creates a database.
   * @throws IOException I/O exception
   */
  protected void create(final Reader reader) throws IOException {
    execute(new CreateDB(reader.getString()),
        reader.getNext().iterator().asInputStream());
  }
  
  /**
   * Executes the specified command.
   * @param cmd command to be executed
   * @throws IOException I/O exception
   */
  protected void execute(final Command cmd, final InputStream input) throws IOException {
    log.info("Executed command: {}", cmd);
    final DecodingInput di = new DecodingInput(input);
    try {
      cmd.setInput(di);
      cmd.execute(dbContext);
      getSender().tell(TcpMessage.write(buildMessage(cmd.info(), true)), getSelf());
    } catch(final BaseXException ex) {
      di.flush();
      getSender().tell(TcpMessage.write(buildMessage(ex.getMessage(), true)), getSelf());
    }
  }
  
  protected void command(final Reader reader) {
    Command command;
    String cmd = reader.getString();
    
    // parse input and create command instance
    try {
      command = new CommandParser(cmd, dbContext).parseSingle();
      log.info(command.toString());
    } catch(final QueryException ex) {
      // log invalid command
      final String msg = ex.getMessage();
      log.info(cmd);
      log.info(msg);
      ByteStringBuilder bb = new ByteStringBuilder();
      bb.append(buildInfo(msg, false));
      getSender().tell(TcpMessage.write(bb.result()), getSelf());
      return;
    }

    // execute command and send {RESULT}
    String info;
    ByteStringBuilder bb = new ByteStringBuilder();
    try {
      // run command
      command.execute(dbContext, new EncodingOutput(bb.asOutputStream()));
      info = command.info();

      bb.putByte((byte) 0);
      bb.append(buildInfo(info, true));
    } catch(final BaseXException ex) {
      info = ex.getMessage();
      if(info.startsWith(INTERRUPTED)) info = TIMEOUT_EXCEEDED;

      bb.putByte((byte) 0);
      bb.append(buildInfo(info, false));
    }
    getSender().tell(TcpMessage.write(bb.result()), getSelf());
  }
  
  /**
   * The TCP socket connection was closed.
   * @param msg closing message
   */
  protected void connectionClosed(final ConnectionClosed msg) {
    getContext().stop(getSelf());
  }
  
  protected ByteString buildInfo(final String info, final boolean success) {
    ByteStringBuilder bb = new ByteStringBuilder();
    bb.append(ByteString.fromString(info));
    bb.putByte((byte) 0);
    bb.putByte((byte) (success ? 0 : 1));
    return bb.result();
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
