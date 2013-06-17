package org.basex;

import static org.basex.core.Text.*;

import java.io.*;
import java.net.*;
import java.util.*;

import org.basex.core.*;
import org.basex.server.*;
import org.basex.util.*;
import org.basex.util.list.*;

import scala.concurrent.*;
import scala.concurrent.duration.*;

import com.typesafe.config.*;

import akka.actor.*;
import akka.pattern.*;
import akka.util.*;

/**
 * This is the mostly unblocking server version of BaseX. It uses akka
 * and an event-based approach to handle connections and should be able to
 * serve a high number of concurrent connections.
 * 
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 * @author Andreas Weiler
 * @author Dirk Kirsten
 */
public final class BaseXServer extends Main {
  /** Actor system. This is a heavy-weight structure. */
  private ActorSystem system;
  /** Initial commands. */
  private StringList commands;
  /** Start as daemon. */
  private boolean service;

  /**
   * Main method, launching the server process.
   * Command-line arguments are listed with the {@code -h} argument.
   * @param args command-line arguments
   */
  public static void main(final String[] args) {
    try {
      new BaseXServer(args);
    } catch(final Exception ex) {
      Util.errln(ex);
      System.exit(1);
    }
  }

  /**
   * Constructor.
   * @param args command-line arguments
   * @throws IOException I/O exception
   */
  public BaseXServer(final String... args) throws IOException {
    this(null, args);
  }

  /**
   * Constructor.
   * @param ctx database context
   * @param args command-line arguments
   * @throws IOException I/O exception
   */
  public BaseXServer(final Context ctx, final String... args) throws IOException {
    super(args, ctx);
    
    final MainProp mprop = context.mprop;
    
    // parse and set all config options from the BaseX settings
    Map<String, Object> configObjects = new HashMap<String, Object>();
    int port = mprop.num(MainProp.SERVERPORT);
    if (port != 0)
      configObjects.put("akka.remote.netty.tcp.port", port);
    String host = mprop.get(MainProp.SERVERHOST);
    if (!host.isEmpty())
      configObjects.put("akka.remote.netty.tcp.hostname", host);
    Config parseConfig = ConfigFactory.parseMap(configObjects);
    
    // parse the Akka configuration and merge both configs, letting BaseX
    // specific config win
    Config regularConfig = ConfigFactory.load().getConfig("server");
    
    if(service) {
      Util.start(BaseXServer.class, args);
      Util.outln(SRV_STARTED_PORT_X, port);
      Performance.sleep(1000);
      return;
    }

    try {
      // execute command-line arguments
      for(final String c : commands) execute(c);

      // set up actor system
      system = ActorSystem.create("BaseXServer", parseConfig.withFallback(regularConfig));
      ActorRef server = system.actorOf(ServerActor.mkProps(new InetSocketAddress(host, port), context), "server");
      
      // wait for socket to be bound
      Timeout timeout = new Timeout(Duration.create(5, "seconds"));
      Future<Object> future = Patterns.ask(server, "bound", timeout);
      Await.result(future, timeout.duration());
      
      if(console) {
        console();
        quit();
      }
    } catch(final Exception ex) {
      context.log.writeError(ex);
      throw new IOException(ex);
    }
  }

  @Override
  protected synchronized void quit() throws IOException {
    
  }

  @Override
  protected Session session() {
    if(session == null) session = new LocalSession(context, out);
    return session;
  }

  @Override
  protected void parseArguments(final String... args) throws IOException {
    final Args arg = new Args(args, this, SERVERINFO, Util.info(CONSOLE, SERVERMODE));
    commands = new StringList();

    while(arg.more()) {
      if(arg.dash()) {
        switch(arg.next()) {
          case 'c': // send database commands
            commands.add(arg.string());
            break;
          case 'd': // activate debug mode
            Prop.debug = true;
            break;
          case 'e': // parse event port
            context.mprop.set(MainProp.EVENTPORT, arg.number());
            break;
          case 'i': // activate interactive mode
            console = true;
            break;
          case 'n': // parse host the server is bound to
            context.mprop.set(MainProp.SERVERHOST, arg.string());
            break;
          case 'p': // parse server port
            context.mprop.set(MainProp.SERVERPORT, arg.number());
            break;
          case 'S': // set service flag
            service = true;
            break;
          case 'z': // suppress logging
            context.mprop.set(MainProp.LOG, false);
            break;
          default:
            arg.usage();
        }
      } else {
        arg.usage();
      }
    }
  }
  
  /**
   * Stops the server of this instance.
   */
  public void stop() {
    system.shutdown();
    system.awaitTermination();
  }
}
