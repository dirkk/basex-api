import java.io._ 
import java.net._ 
import java.security._ 
import java.util._ 

/**
 * Scala client for BaseX.
 * Works with BaseX 6.3.1 and later
 * Documentation: http://docs.basex.org/wiki/Clients
 *
 * (C) BaseX Team 2005-11, BSD License
 */

/**
 * Session constructor.
 * @param host server name
 * @param port server port
 * @param usern user name
 * @param pw password
 * @throws IOException I/O exception
 */
class BaseXClient(host: String, port: Int, usern: String, pw: String) {
  var inf = ""
  val socket = new Socket
  socket.connect(new InetSocketAddress(host, port), 5000)
  val in = new BufferedInputStream(socket.getInputStream)
  val out = socket.getOutputStream

  val ts = receive
  send(usern)
  send(md5(md5(pw) + ts))
  if(!ok) throw new IOException("Access denied.")

  /**
   * Executes a command and serializes the result to an output stream.
   * @param cmd command
   * @param os output stream
   * @throws IOException I/O Exception
   */
  def execute(cmd: String, os: OutputStream) {
    send(cmd)
    receive(in, os)
    inf = receive
    if(!ok) throw new IOException(inf)
  }

  /**
   * Executes a command and returns the result.
   * @param cmd command
   * @return result
   * @throws IOException I/O Exception
   */
  def execute(cmd: String) : String = {
    val os = new ByteArrayOutputStream
    execute(cmd, os)
    os.toString("UTF-8")
  }

  /**
   * Creates a query object.
   * @param query query string
   * @return query
   * @throws IOException I/O Exception
   */
  def query(query: String) : Query = {
    new Query(query)
  }

  /**
   * Creates a database.
   * @param name name of database
   * @param input xml input
   * @throws IOException I/O exception
   */
  def create(name: String, input: InputStream) {
    out.write(8)
    send(name)
    send(input)
  }

  /**
   * Adds a database.
   * @param name name of document
   * @param target target path
   * @param input xml input
   * @throws IOException I/O exception
   */
  def add(name: String, target: String, input: InputStream) {
    out.write(9)
    send(name)
    send(target)
    send(input)
  }

  /**
   * Returns command information.
   * @return string info
   */
  def info() : String = {
    inf
  }

  /**
   * Closes the session.
   * @throws IOException I/O Exception
   */
  def close() {
    send("exit")
    out.flush
    socket.close
  }

  /**
   * Sends an input stream to the server.
   * @param input xml input
   * @throws IOException I/O exception
   */
  private def send(input: InputStream) {
    val is = new BufferedInputStream(input)
    val os = new BufferedOutputStream(out)
    var b = 0
    while({ b = is.read; b != -1 }) os.write(b)
    os.write(0)
    os.flush
    inf = receive
    if(!ok) throw new IOException(inf)
  }

  /**
   * Checks the next success flag.
   * @return value of check
   * @throws IOException I/O Exception
   */
  private def ok() : Boolean = {
    out.flush
    in.read == 0
  }

  /**
   * Returns the next received string.
   * @return String result or info
   * @throws IOException I/O exception
   */
  private def receive() : String = {
    val os = new ByteArrayOutputStream
    receive(in, os)
    os.toString("UTF-8")
  }

  /**
   * Sends a string to the server.
   * @param s string to be sent
   * @throws IOException I/O exception
   */
  private def send(s: String) {
    out.write((s + '\0').getBytes("UTF8"))
  }

  /**
   * Receives a string and writes it to the specified output stream.
   * @param bis input stream
   * @param o output stream
   * @throws IOException I/O exception
   */
  private def receive(is: InputStream, os: OutputStream) {
    var b = 0
    while({ b = is.read; b != 0 && b != -1 }) os.write(b)
  }

  /**
   * Returns an MD5 hash.
   * @param pw String
   * @return String
   */
  private def md5(pw: String) : String = {
    val sb = new StringBuilder
    try {
      val md = MessageDigest.getInstance("MD5")
      md.update(pw.getBytes)
      for(b <- md.digest) {
        val s = Integer.toHexString(b & 0xFF)
        if(s.length == 1) sb.append('0')
        sb.append(s)
      }
    } catch {
      case ex: NoSuchAlgorithmException => ex.printStackTrace
      case ex : Exception => throw ex
    }
    sb.toString
  }

  /**
   * Query constructor.
   * @param query query string
   * @throws IOException I/O exception
   */
  class Query(query: String) {
    val id = exec(0, query)
    var nxt = ""

    /**
     * Initializes the query.
     * @return result header
     * @throws IOException I/O exception
     */
    def init() : String = {
      exec(4, id)
    }

    /**
     * Binds a variable.
     * @param name name of variable
     * @param value value
     * @throws IOException I/O exception
     */
    def bind(name: String, value: String) {
      exec(3, id + '\0' + name + '\0' + value + '\0')
    }

    /**
     * Checks for the next item.
     * @return result of check
     * @throws IOException I/O exception
     */
    def more() : Boolean = {
      nxt = exec(1, id)
      nxt.length != 0
    }

    /**
     * Returns the next item.
     * @return item string
     */
    def next() : String = {
      nxt
    }

    /**
     * Returns the whole result of the query.
     * @return query result
     * @throws IOException I/O Exception
     */
    def execute() : String = {
      exec(5, id)
    }

    /**
     * Returns query info as a string, regardless of whether an output stream
     * was specified.
     * @return query info
     * @throws IOException I/O exception
     */
    def info() : String = {
      exec(6, id)
    }

    /**
     * Closes the query.
     * @return result footer
     * @throws IOException I/O exception
     */
    def close() : String = {
      val s = exec(2, id)
      out.flush
      s
    }

    /**
     * Executes the specified command.
     * @param cmd command
     * @param arg argument
     * @return resulting string
     * @throws IOException I/O exception
     */
    private def exec(cmd: Int, arg: String) : String = {
      out.write(cmd)
      send(arg)
      val s = receive
      if(!ok) throw new IOException(receive)
      s
    }
  }
}
