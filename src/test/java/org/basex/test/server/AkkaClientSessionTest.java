package org.basex.test.server;

import static org.basex.core.Text.*;
import static org.basex.query.func.Function.*;

import java.io.*;

import org.basex.*;
import org.basex.core.*;
import org.basex.io.in.*;
import org.basex.server.*;
import org.basex.util.*;
import org.basex.util.list.*;
import org.junit.*;

/**
 * This class tests the blocking client/server session API using the akka
 * framework.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 * @author Dirk Kirsten
 */
public class AkkaClientSessionTest extends SessionTest {
  /** Server reference. */
  private static BaseXAServer server;

  /**
   * Starts the server.
   * @throws Exception 
   */
  @BeforeClass
  public static void startServer() throws Exception {
    server = new BaseXAServer();
    server.context.mprop.set(MainProp.DBPATH, sandbox().path());
  }

  /**
   * Stops the server.
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  @AfterClass
  public static void stop() throws IOException {
    // TODO
  }

  /** Starts a session. */
  @Before
  public void startSession() {
    try {
      session = createClient();
      session.setOutputStream(out);
    } catch(final Exception ex) {
      System.err.println(Util.message(ex));
    }
  }

  /**
   * Creates a client instance with default admin user.
   * @return client instance
   * @throws Exception I/O exception
   */
  public static AkkaClientSession createClient() throws Exception {
    return createClient(null, null);
  }
  
  /**
   * Creates a client instance.
   * @param user user name
   * @param password password
   * @return client instance
   * @throws Exception I/O exception
   */
  public static AkkaClientSession createClient(final String user, final String password) throws Exception {
    final String u = user != null ? user : ADMIN;
    final String p = password != null ? password : ADMIN;
    return new AkkaClientSession("127.0.0.1", 1984, u, p);
  }
}
