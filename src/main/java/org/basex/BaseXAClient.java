package org.basex;

import static org.basex.core.Text.*;

import java.io.*;
import java.util.concurrent.*;

import org.basex.core.*;
import org.basex.server.*;
import org.basex.util.*;

/**
 * This is the starter class for the client console mode using the event-based
 * server edition.
 * All input is sent to the server instance.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 * @author Dirk Kirsten
 */
public final class BaseXAClient extends BaseX {
  /**
   * Main method of the database client, launching a client instance.
   * Command-line arguments are listed with the {@code -h} argument.
   * @param args command-line arguments
   */
  public static void main(final String... args) {
    try {
      new BaseXAClient(args);
    } catch(final IOException ex) {
      Util.errln(ex);
      System.exit(1);
    }
  }

  /**
   * Constructor.
   * @param args command-line arguments
   * @throws IOException I/O exception
   */
  public BaseXAClient(final String... args) throws IOException {
    super(args);
  }

  @Override
  protected boolean sa() {
    return false;
  }

  @Override
  protected Session session() throws IOException {
    if(session == null) {
      // user/password input
      String user = context.mprop.get(MainProp.USER);
      String pass = context.mprop.get(MainProp.PASSWORD);
      while(user.isEmpty()) {
        Util.out(USERNAME + COLS);
        user = Util.input();
      }
      while(pass.isEmpty()) {
        Util.out(PASSWORD + COLS);
        pass = Util.password();
      }
      try {
        session = new AkkaClientSession(context, user, pass, out);
      } catch(Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return session;
  }
  
  @Override
  protected void quit() throws IOException {
    super.quit();
    session.close();
  }

}
