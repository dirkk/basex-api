package org.basex.http;

import static javax.servlet.http.HttpServletResponse.*;
import static org.basex.http.HTTPText.*;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.basex.core.*;
import org.basex.query.*;
import org.basex.server.*;
import org.basex.util.*;

/**
 * <p>Base class for all servlets.</p>
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public abstract class BaseXServlet extends HttpServlet {
  /** Servlet-specific user. */
  protected String user;
  /** Servlet-specific password. */
  protected String pass;

  @Override
  public final void init(final ServletConfig config) throws ServletException {
    try {
      HTTPContext.init(config.getServletContext());
      final Enumeration<String> en = config.getInitParameterNames();
      while(en.hasMoreElements()) {
        String key = en.nextElement().toLowerCase();
        final String val = config.getInitParameter(key);
        if(key.startsWith(Prop.DBPREFIX)) key = key.substring(Prop.DBPREFIX.length());
        if(key.equalsIgnoreCase(MainProp.USER[0].toString())) {
          user = val;
        } else if(key.equalsIgnoreCase(MainProp.PASSWORD[0].toString())) {
          pass = val;
        }
      }
    } catch(final Exception ex) {
      throw new ServletException(ex);
    }
  }

  @Override
  public final void service(final HttpServletRequest req, final HttpServletResponse res)
      throws IOException {

    final HTTPContext http = new HTTPContext(req, res, this);
    try {
      run(http);
      http.log("", SC_OK);
    } catch(final HTTPException ex) {
      http.status(ex.getStatus(), Util.message(ex));
    } catch(final LoginException ex) {
      http.status(SC_UNAUTHORIZED, Util.message(ex));
    } catch(final IOException ex) {
      http.status(SC_BAD_REQUEST, Util.message(ex));
    } catch(final QueryException ex) {
      http.status(SC_BAD_REQUEST, Util.message(ex));
    } catch(final Exception ex) {
      final String msg = Util.bug(ex);
      Util.errln(msg);
      http.status(SC_INTERNAL_SERVER_ERROR, Util.info(UNEXPECTED, msg));
    } finally {
      if(Prop.debug) {
        Util.outln("_ REQUEST _________________________________" + Prop.NL + req);
        final Enumeration<String> en = req.getHeaderNames();
        while(en.hasMoreElements()) {
          final String key = en.nextElement();
          Util.outln(Text.LI + key + Text.COLS + req.getHeader(key));
        }
        Util.out("_ RESPONSE ________________________________" + Prop.NL + res);
      }
      http.close();
    }
  }

  /**
   * Runs the code.
   * @param http HTTP context
   * @throws Exception any exception
   */
  protected abstract void run(final HTTPContext http) throws Exception;
}
