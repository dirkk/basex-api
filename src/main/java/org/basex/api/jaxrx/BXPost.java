package org.basex.api.jaxrx;

import static org.basex.api.jaxrx.BXUtil.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.basex.core.proc.Add;
import org.basex.core.proc.Open;
import org.basex.server.ClientSession;
import org.jaxrx.interfaces.IPost;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class offers an implementation of the JAX-RX 'post' operation.
 * 
 * @author Workgroup DBIS, University of Konstanz 2005-10, ISC License
 * @author Lukas Lewandowski
 * @author Christian Gruen
 */
public final class BXPost implements IPost {

  // ...next method could be split up in the interface?
  
  @Override
  public Response postResource(final String resource, final Object input,
      final boolean isQuery) {

    StreamingOutput so = null;
    if(isQuery) {
      so = postResource(resource, (Document) input);
    } else {
      postResource(resource, (InputStream) input);
    }
    return Response.ok(so).build();
  }

  /**
   * Inserts a new document to a database.
   * @param resource the resource to be requested
   * @param in input stream
   */
  public void postResource(final String resource, final InputStream in) {
    final ClientSession cs = session();
    run(cs, new Code() {
      @Override
      public void run() throws IOException {
        // open database
        if(!cs.execute(new Open(resource))) notFound(cs.info());
        // add cached file to the database
        final File file = cache(in);
        final boolean ok = cs.execute(new Add(file.toString()));
        file.delete();
        // return exception if process failed
        if(!ok) badRequest(cs.info());
      }
    });
  }

  /**
   * Returns a query result.
   * @param resource the resource to be requested
   * @param doc the query input
   * @return output stream
   */
  public StreamingOutput postResource(final String resource,
      final Document doc) {

    return new StreamingOutput() {
      @Override
      public void write(final OutputStream out) {
        final Map<String, String> p = getParams(doc);
        query(resource, out, p.get("query"), p.get("wrap"),
            p.get("output"), p.get("start"), p.get("count"));
      }
    };
  }
  
  /**
   * This method extracts and returns query parameters from a document.
   * @param doc The XML {@link Document} containing the XQuery XML post
   *          request.
   * @return The XQuery expression as {@link String} and optional properties.
   * @throws WebApplicationException The exception occurred.
   */
  protected Map<String, String> getParams(final Document doc) {
    final Map<String, String> params = new HashMap<String, String>();

    params.put("query",
        doc.getElementsByTagName("text").item(0).getTextContent());

    final NodeList props = doc.getElementsByTagName("property");
    for(int i = 0; i < props.getLength(); i++) {
      final Node prop = props.item(i);
      params.put(
          prop.getAttributes().getNamedItem("name").getTextContent(),
          prop.getAttributes().getNamedItem("value").getTextContent());
    }
    return params;
  }
}