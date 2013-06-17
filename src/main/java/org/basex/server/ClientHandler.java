package org.basex.server;

import java.io.*;
import java.util.*;
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
  /** Query id counter. */
  private Integer id = 0;
  /** Active queries. */
  protected final HashMap<Integer, ActorRef> queries =
    new HashMap<Integer, ActorRef>();
  /** Event Socket address already sent? */
  private boolean addressSend = false;
  
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
    dbContext = new Context(ctx, null);
  }
  
  @Override
  public void onReceive(Object msg) throws Exception {
    // client is not authenticated
    if (msg instanceof String && ((String) msg).equalsIgnoreCase("connect")) {
      ts = Long.toString(System.nanoTime());
      ByteStringBuilder bb = new ByteStringBuilder();
      bb.append(ByteString.fromString(ts));
      // success
      bb.putByte((byte) 0);
      getSender().tell(TcpMessage.write(bb.result()), getSelf());
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
            create(reader.getString(), reader.getInputStream());
          } else if (sc == ServerCmd.ADD) {
            add(reader.getString(), reader.getInputStream());
          } else if (sc == ServerCmd.REPLACE) {
            replace(reader.getString(), reader.getInputStream());
          } else if (sc == ServerCmd.STORE) {
            store(reader.getString(), reader.getInputStream());
          } else if (sc == ServerCmd.WATCH) {
            watch(reader.getString());
          } else if (sc == ServerCmd.UNWATCH) {
            unwatch(reader.getString());
          } else if (sc == ServerCmd.QUERY) {
            newQuery(msg);
          } else if (sc == ServerCmd.CLOSE) {
            int queryId = Integer.decode(reader.getString());
            if (queries.containsKey(queryId)) {
              ActorRef query = queries.get(queryId);
              query.forward(msg, getContext());
              queries.remove(queryId);
            } else {
              ByteStringBuilder bb = new ByteStringBuilder();
              bb.putByte((byte) 0);
              bb.putByte((byte) 0);
              getSender().tell(TcpMessage.write(bb.result()), getSelf());
            }
          } else if (sc == ServerCmd.BIND || sc == ServerCmd.CONTEXT ||
              sc == ServerCmd.ITER || sc == ServerCmd.EXEC || sc == ServerCmd.FULL ||
              sc == ServerCmd.INFO || sc == ServerCmd.OPTIONS || sc == ServerCmd.UPDATING) {
            int queryId = Integer.decode(reader.getString());
            if (queries.containsKey(queryId)) {
              ActorRef query = queries.get(queryId);
              query.forward(msg, getContext());
            } else {
              throw new IOException("Unknown Query ID: " + queryId);
            }
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
   * @param name database name
   * @param input input stream
   * @throws IOException I/O exception
   */
  protected void create(final String name, final InputStream input) throws IOException {
    execute(new CreateDB(name), input);
  }

  /**
   * Adds a document to a database.
   * @param path document path
   * @param input input stream
   * @throws IOException I/O exception
   */
  protected void add(final String path, final InputStream input) throws IOException {
    execute(new Add(path), input);
  }

  /**
   * Replace a document in a database.
   * @param path document path
   * @param input input stream
   * @throws IOException I/O exception
   */
  protected void replace(final String path, final InputStream input) throws IOException {
    execute(new Replace(path), input);
  }

  /**
   * Stores raw data in a database.
   * @param path document path
   * @param input input stream
   * @throws IOException I/O exception
   */
  protected void store(final String path, final InputStream input) throws IOException {
    execute(new Store(path), input);
  }
  
  /**
   * Watches an event.
   * @throws IOException I/O exception
   */
  private void watch(Object msg) throws IOException {
    ActorRef e;
    if (getContext().child("events") == null) {
      e = getContext().actorOf(EventActor.mkProps(), "events");
    } else {
      e = getContext().getChild("events");
    }
    e.forward(msg, getContext());
    
    if(!addressSend) {
      ByteStringBuilder bb = new ByteStringBuilder();
      bb.append(ByteString.fromString(Integer.toString(addr.getPort())));
      bb.putByte((byte) 0);
      bb.append(ByteString.fromString(Long.toString(0)));
      bb.putByte((byte) 0);

      getSender().tell(TcpMessage.write(bb.result()), getSelf());
      addressSend = true;
    }

    final Sessions s = dbContext.events.get(name);
    final boolean ok = s != null && !s.contains(this);
    final String message;
    if(ok) {
      s.add(null);
      message = WATCHING_EVENT_X;
    } else if(s == null) {
      message = EVENT_UNKNOWN_X;
    } else {
      message = EVENT_WATCHED_X;
    }
    
    ByteStringBuilder bb = new ByteStringBuilder();
    bb.append(ByteString.fromString(message));
    bb.putByte((byte) 0);
    bb.putByte((byte) (ok ? 0 : 1));
    getSender().tell(TcpMessage.write(bb.result()), getSelf());
  }

  /**
   * Unwatches an event.
   * @throws IOException I/O exception
   */
  private void unwatch() throws IOException {
    final String name = in.readString();

    final Sessions s = context.events.get(name);
    final boolean ok = s != null && s.contains(this);
    final String message;
    if(ok) {
      s.remove(this);
      message = UNWATCHING_EVENT_X;
    } else if(s == null) {
      message = EVENT_UNKNOWN_X;
    } else {
      message = EVENT_NOT_WATCHED_X;
    }
    info(Util.info(message, name), ok);
    out.flush();
  }
  
  /**
   * Creates a new query.
   * @param msg message
   */
  protected void newQuery(final Object msg) {
    int newId;
    synchronized (id) {
      newId = id++;
    }
    ActorRef query = getContext().actorOf(QueryHandler.mkProps(dbContext, newId));
    queries.put(newId, query);
    query.forward(msg, getContext());
  }
  
  /**
   * Executes the specified command.
   * @param cmd command to be executed
   * @param input encoded input stream
   * @throws IOException I/O exception
   */
  protected void execute(final Command cmd, final InputStream input) throws IOException {
    log.info("Executed command: {}", cmd);
    final DecodingInput di = new DecodingInput(input);
    try {
      cmd.setInput(di);
      cmd.execute(dbContext);
      getSender().tell(TcpMessage.write(buildInfo(cmd.info(), true)), getSelf());
    } catch(final BaseXException ex) {
      di.flush();
      getSender().tell(TcpMessage.write(buildInfo(ex.getMessage(), false)), getSelf());
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
      log.info("Query failed: {}, Error message: {}", cmd, msg);
      
      ByteStringBuilder bb = new ByteStringBuilder();
      bb.putByte((byte) 0);
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
  
  /**
   * Build an outgoing info message.
   * Is in the format:
   * <ul>
   *   <li> info string </li>
   *   <li> 0 byte </li>
   *   <li> success: 0 byte, failed: 1 byte</li>
   * </ul>
   * @param info info string
   * @param success success or failed
   * @return composed message
   */
  protected ByteString buildInfo(final String info, final boolean success) {
    ByteStringBuilder bb = new ByteStringBuilder();
    bb.append(ByteString.fromString(info));
    bb.putByte((byte) 0);
    bb.putByte((byte) (success ? 0 : 1));
    return bb.result();
  }
}
