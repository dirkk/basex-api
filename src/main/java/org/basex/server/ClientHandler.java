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
      new Writer()
        .writeString(ts)
        .send(getSender(), getSelf());
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
        new Writer().writeTerminator().send(getSender(), getSelf());
        
        // change incoming message processing
        getContext().become(authenticated());
      } else {
        log.info("Access denied for user {}", dbContext.user);
        
        // send authentication denied message with 1s delay
        new Writer()
          .writeSuccess(false)
          .sendDelayed(getSender(), getSelf(), getContext().system(), Duration.create(1, TimeUnit.SECONDS));
      }
    } else if (msg instanceof ConnectionClosed) {
      connectionClosed((ConnectionClosed) msg);
    }
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
              new Writer().writeTerminator().writeTerminator()
                .send(getSender(), getSelf());
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
   * @param name event name
   */
  protected void watch(final String name) {
    if(!addressSend) {
      new Writer()
        .writeString(Integer.toString(dbContext.mprop.num(dbContext.mprop.EVENTPORT)))
        .writeString(getSelf().path().name())
        .send(getSender(), getSelf());
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
    
    new Writer().writeString(message).writeSuccess(ok).send(getSender(), getSelf());
  }

  /**
   * Unwatches an event.
   * @param name event name
   */
  protected void unwatch(final String name) {
    final Sessions s = dbContext.events.get(name);
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
    
    new Writer().writeString(message + name).writeSuccess(ok).send(getSender(), getSelf());
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
      new Writer().writeString(cmd.info()).writeSuccess(true).send(getSender(), getSelf());
    } catch(final BaseXException ex) {
      di.flush();

      new Writer().writeString(ex.getMessage()).writeSuccess(false).send(getSender(), getSelf());
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
      
      new Writer().writeTerminator().writeString(msg).writeSuccess(false)
        .send(getSender(), getSelf());
      return;
    }

    // execute command and send {RESULT}
    String info;
    Writer w = new Writer();
    try {
      // run command
      command.execute(dbContext, new EncodingOutput(w.getOutputStream()));
      info = command.info();

      w.writeTerminator().writeString(info).writeSuccess(true);
    } catch(final BaseXException ex) {
      info = ex.getMessage();
      if(info.startsWith(INTERRUPTED)) info = TIMEOUT_EXCEEDED;

      w.writeTerminator().writeString(info).writeSuccess(false);
    }
    w.send(getSender(), getSelf());
  }
  
  /**
   * The TCP socket connection was closed.
   * @param msg closing message
   */
  protected void connectionClosed(final ConnectionClosed msg) {
    log.info("TCP connection was closed. Error Cause: {}", msg.getErrorCause());
    getContext().stop(getSelf());
  }
}
