package org.basex.server;

import java.io.*;

import org.basex.core.*;
import org.basex.server.messages.auth.*;
import org.basex.server.messages.command.*;

import scala.concurrent.*;

import com.typesafe.config.*;

import akka.actor.*;
import akka.event.*;
import akka.pattern.*;
import akka.util.*;

/**
 * This class offers methods to execute database commands via the
 * client/server architecture. Commands are sent to the server instance via
 * remote message passing using Akka.
 * 
 * This client is blocking.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 * @author Dirk Kirsten
 */
public class AkkaClientSession extends Session {
  /** Actor system. Heavy-weight structure, started at start-up. */
  private final ActorSystem system;
  /** Actor for executing database commands. */
  private final ActorRef commandActor;
  /** Default timeout. */
  private final Timeout timeout;
  /** Logging instance. */
  private final LoggingAdapter log;
  
  /**
   * Constructor, specifying login data.
   * @param context database context
   * @param user user name
   * @param pass password
   * @throws Exception timeout when trying to connect or incorrect login
   */
  public AkkaClientSession(final Context context, final String user, final String pass) throws Exception {
    this(context, user, pass, null);
  }

  /**
   * Constructor, specifying login data and an output stream.
   * @param context database context
   * @param user user name
   * @param pass password
   * @param output client output; if set to {@code null}, results will
   * be returned as strings.
   * @throws Exception timeout when trying to connect or incorrect login
   */
  public AkkaClientSession(final Context context, final String user, final String pass,
      final OutputStream output) throws Exception {
    this(context.mprop.get(MainProp.HOST), context.mprop.num(MainProp.PORT),
        user, pass, output);
  }

  /**
   * Constructor, specifying the server host:port combination and login data.
   * @param host server name
   * @param port server port
   * @param user user name
   * @param pass password
   * @throws Exception timeout when trying to connect or incorrect login
   */
  public AkkaClientSession(final String host, final int port, final String user,
      final String pass) throws Exception {
    this(host, port, user, pass, null);
  }

  /**
   * Constructor, specifying the server host:port combination, login data and
   * an output stream.
   * @param host server name
   * @param port server port
   * @param user user name
   * @param pass password
   * @param output client output; if set to {@code null}, results will
   * be returned as strings.
   * @throws IOException timeout when trying to connect or incorrect login
   */
  public AkkaClientSession(final String host, final int port, final String user,
      final String pass, final OutputStream output) throws IOException {
    super(output); 
    // get configuration from Akka configuration files
    Config regularConfig = ConfigFactory.load().getConfig("client");
    
    // create system, heavy-weight operation
    system = ActorSystem.create("BaseXClient", regularConfig);
    
    // create logger
    log = Logging.getLogger(system, this);
    // create standard timeout of 5s
    timeout = new Timeout(5000);
    out = output;

    // create a local actor for login
    ActorRef loginActor = system.actorOf(ClientActor.mkProps(user, pass, host, port), "clientListener");
    
    // do the login and block until a response came in
    Future<Object> future = Patterns.ask(loginActor, new LoginMessage(), timeout);
    Object result;
    try {
      result = Await.result(future, timeout.duration());
    } catch(Exception ex) {
      throw new BaseXException(ex);
    }

    // If successful an ActorRef to the remote normal operation actor is returned
    if (!(result instanceof ActorRef)) {
      log.error("Authentification failed");
      throw new LoginException();
    }
    
    log.info("Authentification successful.");
    
    commandActor = (ActorRef) result;
  }
  
  /**
   * Read the whole input stream until end of stream and build a ByteString.
   * @param input input stream
   * @return ByteString
   * @throws IOException I/O exception
   */
  private ByteString buildFromInputStream(final InputStream input) throws IOException {
    ByteStringBuilder builder = new ByteStringBuilder();
    byte b;
    while ((b = (byte) input.read()) != -1) {
      builder.putByte(b);
    }
    return builder.result();
  }

  @Override
  public void create(final String name, final InputStream input) throws IOException {
    sendAndWait(new CreateMessage(name, buildFromInputStream(input)));
  }

  @Override
  public void add(final String path, final InputStream input) throws IOException {
    sendAndWait(new AddMessage(path, buildFromInputStream(input)));
  }

  @Override
  public void replace(final String path, final InputStream input) throws IOException {
    sendAndWait(new ReplaceMessage(path, buildFromInputStream(input)));
  }

  @Override
  public void store(final String path, final InputStream input) throws IOException {
    sendAndWait(new StoreMessage(path, buildFromInputStream(input)));
  }

  @Override
  public AkkaClientQuery query(final String query) throws IOException {
    return new AkkaClientQuery(query, this, out);
  }

  /**
   * Close this session and block until it is shutdown.
   */
  @Override
  public synchronized void close() throws IOException {
    system.shutdown();
    system.awaitTermination();
  }

  @Override
  protected void execute(final String cmd, final OutputStream os) throws IOException {
    ResultMessage result = (ResultMessage) sendAndWait(new CommandMessage(cmd));
    
    // output the result to the output stream
    PrintWriter pw = new PrintWriter(os);
    pw.write(result.getResult());
    pw.flush();
  }

  @Override
  protected void execute(final Command cmd, final OutputStream os) throws IOException {
    execute(cmd.toString(), os);
  }

  @Override
  public String toString() {
    Address addr = commandActor.path().address();
     return addr.host().get() + ":" + addr.port().get();
  }
  
  /**
   * Send a message to the remote processing actor and wait for the result.
   * 
   * Blocks until a result arrived or the timeout was reached.
   * 
   * @param msg message to send
   * @return result
   * @throws IOException I/O exception
   */
  private Object sendAndWait(Object msg) throws IOException{
    Future<Object> future = Patterns.ask(commandActor, msg, timeout);
    Object result;
    try {
      result = Await.result(future, timeout.duration());    
      if (result instanceof ResultMessage)
        info = ((ResultMessage) result).getInfo();
    } catch(Exception e) {
      throw new BaseXException(e);
    }
    if (result instanceof BaseXException)
      throw (BaseXException) result;
    
    return result;
  }
  
  /**
   * Get the command actor
   * @return command actor
   */
  public ActorRef getCommandActor() {
    return commandActor;
  }
}
