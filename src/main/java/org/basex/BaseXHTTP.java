package org.basex;

import static org.basex.core.Text.*;
import static org.basex.http.HTTPText.*;

import java.io.*;
import java.net.*;

import org.basex.core.*;
import org.basex.http.*;
import org.basex.io.*;
import org.basex.server.*;
import org.basex.util.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.nio.*;
import org.eclipse.jetty.webapp.*;
import org.eclipse.jetty.xml.*;

/**
 * This is the main class for the starting the database HTTP services.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 * @author Dirk Kirsten
 */
public final class BaseXHTTP {
  /** Database context. */
  final Context context = HTTPContext.init();
  /** HTTP port. */
  int httpPort;
  /** HTTP server. */
  private final Server jetty;
  /** Start as daemon. */
  private boolean service;
  /** Stopped flag. */
  private boolean stopped;

  /**
   * Main method, launching the HTTP services.
   * Command-line arguments are listed with the {@code -h} argument.
   * @param args command-line arguments
   */
  public static void main(final String... args) {
    try {
      new BaseXHTTP(args);
    } catch(final Exception ex) {
      Util.errln(ex);
      System.exit(1);
    }
  }

  /**
   * Constructor.
   * @param args command-line arguments
   * @throws Exception exception
   */
  public BaseXHTTP(final String... args) throws Exception {
    parseArguments(args);

    // create jetty instance and set default context to HTTP path
    final MainProp mprop = context.mprop;
    final String webapp = mprop.get(MainProp.WEBPATH);
    final WebAppContext wac = new WebAppContext(webapp, "/");
    jetty = (Server) new XmlConfiguration(initJetty(webapp).inputStream()).configure();
    jetty.setHandler(wac);

    // retrieve jetty port
    for(final Connector c : jetty.getConnectors()) {
      if(c instanceof SelectChannelConnector) {
        if(httpPort == 0) httpPort = c.getPort();
        else c.setPort(httpPort);
      }
    }

    // stop server
    if(stopped) {
      stop();
      Util.outln(HTTP + ' ' + SRV_STOPPED_PORT_X, httpPort);
      // temporary console windows: keep the message visible for a while
      Performance.sleep(1000);
      return;
    }

    // start web server in a new process
    if(service) {
      start(httpPort, args);
      Util.outln(HTTP + ' ' + SRV_STARTED_PORT_X, httpPort);
      // temporary console windows: keep the message visible for a while
      Performance.sleep(1000);
      return;
    }

    // request password on command line if only the user was specified
    if(!AProp.getSystem(MainProp.USER).isEmpty()) {
      while(AProp.getSystem(MainProp.PASSWORD).isEmpty()) {
        Util.out(PASSWORD + COLS);
        AProp.setSystem(MainProp.PASSWORD, Util.password());
      }
    }

    // start web server
    jetty.start();
    Util.outln(HTTP + ' ' + SRV_STARTED_PORT_X, httpPort);

    // initialize web.xml settings, assign system properties and run database server
    // if not done so already. this must be called after starting jetty
    HTTPContext.init(wac.getServletContext());

    // start daemon for stopping web server
    final int stop = mprop.num(MainProp.STOPPORT);
    if(stop >= 0) new StopServer(mprop.get(MainProp.SERVERHOST), stop).start();

    // show info when HTTP server is aborted
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        Util.outln(HTTP + ' ' + SRV_STOPPED_PORT_X, httpPort);
        final Log l = context.log;
        if(l != null) l.writeServer(OK, HTTP + ' ' + SRV_STOPPED_PORT_X, httpPort);
        context.close();
      }
    });

    // log server start at very end (logging flag could have been updated by web.xml)
    context.log.writeServer(OK, HTTP + ' ' + SRV_STARTED_PORT_X, httpPort);
  }

  /**
   * Stops the server.
   * @throws Exception exception
   */
  public void stop() throws Exception {
    // notify the jetty monitor to stop
    final MainProp mprop = context.mprop;
    final int stop = num(MainProp.STOPPORT, mprop);
    if(stop >= 0) stop(stop);

    // server has been started in a separate process and needs to be stopped
    if(!bool(MainProp.HTTPLOCAL, mprop)) {
      final int port = num(MainProp.SERVERPORT, mprop);
      final int eport = num(MainProp.EVENTPORT, mprop);
      // TODO BaseXServer.stop(port, eport);
    }
  }

  /**
   * Returns a numeric value for the specified option.
   * @param option option to be retrieved
   * @param mprop main properties
   * @return numeric value
   */
  private static int num(final Object[] option, final MainProp mprop) {
    final String val = AProp.getSystem(option);
    return val.isEmpty() ? mprop.num(option) : Token.toInt(val);
  }

  /**
   * Returns a boolean value for the specified option.
   * @param option option to be retrieved
   * @param mprop main properties
   * @return boolean value
   */
  private static boolean bool(final Object[] option, final MainProp mprop) {
    final String val = AProp.getSystem(option);
    return val.isEmpty() ? mprop.is(option) : Boolean.parseBoolean(val);
  }

  /**
   * Returns a reference to the Jetty configuration file.
   * @param root target root directory
   * @return input stream
   * @throws IOException I/O exception
   */
  private static IOFile initJetty(final String root) throws IOException {
    locate(WEBCONF, root);
    return locate(JETTYCONF, root);
  }

  /**
   * Locates the specified configuration file.
   * @param file file to be copied
   * @param root target root directory
   * @return reference to created file
   * @throws IOException I/O exception
   */
  private static IOFile locate(final String file, final String root) throws IOException {
    final IOFile trg = new IOFile(root + '/' + file);
    final boolean create = !trg.exists();

    // try to locate file from development branch
    final IO in = new IOFile("src/main/webapp/" + file);
    final byte[] data;
    if(in.exists()) {
      data = in.read();
      // check if resource path exists
      IOFile res = new IOFile("src/main/resources/");
      if(res.exists()) {
        res = new IOFile(res, file);
        // update file in resource path if it has changed
        if(!res.exists() || !Token.eq(data, res.read())) {
          Util.errln("Updating " +  res);
          res.dir().md();
          res.write(in.read());
        }
      }
    } else if(create) {
      // try to locate file from resource path
      final InputStream is = BaseXHTTP.class.getResourceAsStream('/' + file);
      if(is == null) throw new BaseXException(in + " not found.");
      data = new IOStream(is).read();
    } else {
      return trg;
    }

    if(create) {
      // create configuration file
      Util.errln("Creating " +  trg);
      trg.dir().md();
      trg.write(data);
    }
    return trg;
  }

  /**
   * Parses the command-line arguments, specified by the user.
   * @param args command-line arguments
   * @throws IOException I/O exception
   */
  private void parseArguments(final String[] args) throws IOException {
    /* command-line properties not be stored in system properties (instead of
     * context.mprop). this way, they will not be overwritten by web.xml settings. */
    final Args arg = new Args(args, this, HTTPINFO, Util.info(CONSOLE, HTTP));
    boolean daemon = false;
    while(arg.more()) {
      if(arg.dash()) {
        switch(arg.next()) {
          case 'd': // activate debug mode
            AProp.setSystem(MainProp.DEBUG, true);
            Prop.debug = true;
            break;
          case 'D': // hidden flag: daemon mode
            daemon = true;
            break;
          case 'e': // parse event port
            AProp.setSystem(MainProp.EVENTPORT, arg.number());
            break;
          case 'h': // parse HTTP port
            httpPort = arg.number();
            break;
          case 'l': // use local mode
            AProp.setSystem(MainProp.HTTPLOCAL, true);
            break;
          case 'n': // parse host name
            AProp.setSystem(MainProp.HOST, arg.string());
            break;
          case 'p': // parse server port
            final int p = arg.number();
            AProp.setSystem(MainProp.PORT, p);
            AProp.setSystem(MainProp.SERVERPORT, p);
            break;
          case 'P': // specify password
            AProp.setSystem(MainProp.PASSWORD, arg.string());
            break;
          case 's': // parse stop port
            AProp.setSystem(MainProp.STOPPORT, arg.number());
            break;
          case 'S': // set service flag
            service = !daemon;
            break;
          case 'U': // specify user name
            AProp.setSystem(MainProp.USER, arg.string());
            break;
          case 'z': // suppress logging
            AProp.setSystem(MainProp.LOG, false);
            break;
          default:
            arg.usage();
        }
      } else {
        if(!arg.string().equalsIgnoreCase("stop")) arg.usage();
        stopped = true;
      }
    }
  }

  // STATIC METHODS ===========================================================

  /**
   * Starts the HTTP server in a separate process.
   * @param port server port
   * @param args command-line arguments
   * @throws BaseXException database exception
   */
  private static void start(final int port, final String... args) throws BaseXException {
    Util.start(BaseXHTTP.class, args);
    // try to connect to the new server instance
    for(int c = 1; c < 10; ++c) {
      if(ping(LOCALHOST, port)) return;
      Performance.sleep(c * 100);
    }
    throw new BaseXException(CONNECTION_ERROR);
  }

  /**
   * Generates a stop file for the specified port.
   * @param port server port
   * @return stop file
   */
  private static File stopFile(final int port) {
    return new File(Prop.TMP, Util.name(BaseXHTTP.class) + port);
  }

  /**
   * Stops the server.
   * @param port server port
   * @throws IOException I/O exception
   */
  private static void stop(final int port) throws IOException {
    final File stop = stopFile(port);
    try {
      stop.createNewFile();
      new Socket(LOCALHOST, port).close();
      // give the notified process some time to quit
      Performance.sleep(100);
    } catch(final IOException ex) {
      stop.delete();
      throw ex;
    }
  }

  /**
   * Checks if a server is running.
   * @param host host
   * @param port server port
   * @return boolean success
   */
  private static boolean ping(final String host, final int port) {
    try {
      // create connection
      final URL url = new URL("http://" + host + ':' + port);
      url.openConnection().getInputStream();
      return true;
    } catch(final IOException ex) {
      // if page is not found, server is running
      return ex instanceof FileNotFoundException;
    }
  }

  /** Monitor for stopping the Jetty server. */
  @SuppressWarnings("synthetic-access")
  private final class StopServer extends Thread {
    /** Server socket. */
    private final ServerSocket ss;
    /** Stop file. */
    private final File stop;

    /**
     * Constructor.
     * @param host host address
     * @param port stop port
     * @throws IOException I/O exception
     */
    StopServer(final String host, final int port) throws IOException {
      final InetAddress addr = host.isEmpty() ? null : InetAddress.getByName(host);
      ss = new ServerSocket();
      ss.setReuseAddress(true);
      ss.bind(new InetSocketAddress(addr, port));
      stop = stopFile(port);
      setDaemon(true);
    }

    @Override
    public void run() {
      try {
        while(true) {
          ss.accept().close();
          if(stop.exists()) {
            ss.close();
            stop.delete();
            jetty.stop();
            break;
          }
        }
      } catch(final Exception ex) {
        Util.errln(ex);
      }
    }
  }
}
