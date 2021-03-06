package com.github.theon.uri

import com.github.theon.uri.Uri._
import com.github.theon.uri.Encoders.{NoopEncoder, PercentEncoder, encode}

case class Uri (
  protocol: Option[String] = None,
  user: Option[String] = None,
  password: Option[String] = None,
  host: Option[String] = None,
  port: Option[Int] = None,
  pathParts: Seq[PathPart] = Seq.empty,
  query: QueryString = QueryString(),
  fragment: Option[String] = None
) {

  lazy val hostParts: Seq[String] =
    host.map(h => h.split('.').toVector).getOrElse(Vector.empty)

  def subdomain = hostParts.headOption

  def pathPart(name: String) =
    pathParts.find(_.part == name)

  def matrixParams =
    pathParts.last match {
      case MatrixParams(_, p) => p
      case _ => Seq.empty
    }

  def matrixParam(pp: String, k: String, v: String) = copy (
    pathParts = pathParts.map {
      case p: PathPart if(p.part == pp) => p.addParam(k -> v)
      case x => x
    }
  )

  def matrixParam(k: String, v: String) = copy (
    pathParts = pathParts.dropRight(1) :+ pathParts.last.addParam(k -> v)
  )

  /**
   * Adds a new Query String parameter key-value pair. If the value for the Query String parmeter is None, then this
   * Query String parameter will not be rendered in calls to toString or toStringRaw
   * @param kv Tuple2 representing the querystring parameter
   * @return A new Uri with the new Query String parameter
   */
  def param(kv: (String, Any)) = kv match {
    case (_, None) => this
    case (k, Some(v)) => copy(query = query.addParam(k, v.toString))
    case (k, v) => copy(query = query.addParam(k, v.toString))
  }

  def params(kvs: Seq[(String, Any)]) = {
    val cleanKvs = kvs.filterNot(_._2 == None).map {
      case (k, Some(v)) => (k, v.toString)
      case (k, v) => (k, v.toString)
    }
    copy(query = query.addParams(cleanKvs))
  }

  def scheme = protocol

  /**
   * Copies this Uri but with the scheme set as the given value.
   *
   * @param scheme the new scheme to set
   * @return a new Uri with the specified scheme
   */
  def scheme(scheme: String): Uri = copy(protocol = Option(scheme))

  /**
   * Copies this Uri but with the host set as the given value.
   *
   * @param host the new host to set
   * @return a new Uri with the specified host
   */
  def host(host: String): Uri = copy(host = Option(host))

  /**
   * Copies this Uri but with the user set as the given value.
   *
   * @param user the new user to set
   * @return a new Uri with the specified user
   */
  def user(user: String): Uri = copy(user = Option(user))

  /**
   * Copies this Uri but with the password set as the given value.
   *
   * @param password the new password to set
   * @return a new Uri with the specified password
   */
  def password(password: String): Uri = copy(password = Option(password))

  /**
   * Copies this Uri but with the port set as the given value.
   *
   * @param port the new host to set
   * @return a new Uri with the specified port
   */
  def port(port: Int): Uri = copy(port = Option(port))

  /**
   * Adds a new Query String parameter key-value pair. If the value for the Query String parameter is None, then this
   * Query String parameter will not be rendered in calls to toString or toStringRaw
   * @param kv Tuple2 representing the query string parameter
   * @return A new Uri with the new Query String parameter
   */
  def ?(kv: (String, Any)) = param(kv)

  /**
   * Adds a new Query String parameter key-value pair. If the value for the Query String parameter is None, then this
   * Query String parameter will not be rendered in calls to toString or toStringRaw
   * @param kv Tuple2 representing the query string parameter
   * @return A new Uri with the new Query String parameter
   */
  def &(kv: (String, Any)) = param(kv)

  /**
   * Adds a fragment to the end of the uri
   * @param fragment String representing the fragment
   * @return A new Uri with this fragment
   */
  def `#`(fragment: String) = copy(fragment = Some(fragment))

  def /(pp: String) = copy(pathParts = pathParts :+ StringPathPart(pp))

  /**
   * Returns the path with no encoding taking place (e.g. non ASCII characters will not be percent encoded)
   * @return String containing the raw path for this Uri
   */
  def pathRaw = path("UTF-8", NoopEncoder)

  /**
   * Returns the encoded path. By default non ASCII characters in the path are percent encoded.
   * @return String containing the path for this Uri
   */
  def path(implicit enc: String = "UTF-8", e: Enc = PercentEncoder) =
    "/" + pathParts.map(_.encoded(e)).mkString("/")

  /**
   * Replaces the all existing Query String parameters with the specified key with a single Query String parameter
   * with the specified value. If the value passed in is None, then all Query String parameters with the specified key
   * are removed
   *
   * @param k Key for the Query String parameter(s) to replace
   * @param v value to replace with
   * @return A new Uri with the result of the replace
   */
  def replaceParams(k: String, v: Any) = {
    v match {
      case valueOpt: Option[_] =>
        copy(query = query.replaceAll(k, valueOpt))
      case _ =>
        copy(query = query.replaceAll(k, Some(v)))
    }
  }

  /**
   * Removes all Query String parameters with the specified key
   * @param k Key for the Query String parameter(s) to remove
   * @return
   */
  def removeParams(k: String) = {
    copy(query = query.removeAll(k))
  }

  override def toString = toString("UTF-8", PercentEncoder)
  def toString(implicit enc: String = "UTF-8", e: Enc = PercentEncoder): String = {
    //If there is no scheme, we use protocol relative
    val schemeStr = scheme.map(_ + "://").getOrElse("//")
    val userInfo = user.map(_ + password.map(":" + _).getOrElse("") + "@").getOrElse("")
    host.map(schemeStr + userInfo + _).getOrElse("") +
      port.map(":" + _).getOrElse("") +
      path(enc, e) +
      query.encoded(e)(enc) +
      fragment.map(f => "#" + encode(f, e, enc)).getOrElse("")
  }

  /**
   * Returns the string representation of this Uri with no encoding taking place
   * (e.g. non ASCII characters will not be percent encoded)
   * @return String containing this Uri in it's raw form
   */
  def toStringRaw(enc: String = "UTF-8"): String = toString(enc, NoopEncoder)
}

case class QueryString(parameters: Seq[(String, String)] = Seq.empty) extends Parameters[QueryString] {

  def separator = "&"
  def withParams(params: Seq[(String, String)]) = copy(parameters = params)

  def params(key: String) = parameters.collect {
    case (k, v) if k == key => v
  }

  def param(key: String) = parameters.find(_._1 == key)

  /**
   * Adds a new Query String parameter key-value pair. If the value for the Query String parmeter is None, then this
   * Query String parameter will not be rendered in calls to toString or toStringRaw
   *
   * @return A new Query String with the new Query String parameter
   */
  def addParam(k: String, v: String) =
    withParams(parameters :+ (k -> v.toString))

  def addParams(kvs: Seq[(String, String)]) =
    withParams(parameters ++ kvs)

  def encoded(e: Enc)(implicit enc: String = "UTF-8"): String = {
    if(parameters.isEmpty) {
      ""
    } else {
      "?" + paramsEncoded(e)
    }
  }
}

trait Parameters[+Self] {
  this: Self =>

  def separator: String
  def parameters: Seq[(String,String)]
  def withParams(params: Seq[(String,String)]): Self

  def add(kv: (String, String)) = withParams(parameters :+ kv)

  /**
   * Replaces the all existing Query String parameters with the specified key with a single Query String parameter
   * with the specified value. If the value passed in is None, then all Query String parameters with the specified key
   * are removed
   *
   * @param k Key for the Query String parameter(s) to replace
   * @param v value to replace with
   * @return A new QueryString with the result of the replace
   */
  def replaceAll(k: String, v: Option[Any]) = {
    v match {
      case Some(v) => withParams(parameters.filterNot(_._1 == k) :+ (k -> v.toString))
      case None => removeAll(k)
    }
  }

  /**
   * Removes all Query String parameters with the specified key
   * @param k Key for the Query String parameter(s) to remove
   * @return
   */
  def removeAll(k: String) = {
    withParams(parameters.filterNot(_._1 == k))
  }

  def paramsEncoded(e: Enc)(implicit enc: String = "UTF-8") =
    parameters.map(kv => {
      val (k,v) = kv
      encode(k, e, enc) + "=" + encode(v, e, enc)
    }).mkString(separator)

  /**
   * Returns the string representation of this QueryString with no encoding taking place
   * (e.g. non ASCII characters will not be percent encoded)
   * @return String containing this QueryString in it's raw form
   */
  def paramsRaw(): String = paramsEncoded(NoopEncoder)
}

case class StringPathPart(part: String) extends AnyVal with PathPart {
  def parameters = Vector.empty

  def addParam(kv: (String,String)) =
    MatrixParams(part, Vector(kv))

  def encoded(e: Enc)(implicit enc: String = "UTF-8") =
    partEncoded(e)(enc)
}

case class MatrixParams(part: String, parameters: Seq[(String, String)]) extends PathPart with Parameters[MatrixParams] {
  def separator = ";"
  def withParams(params: Seq[(String, String)]) = copy(parameters = params)

  def encoded(e: Enc)(implicit enc: String = "UTF-8") =
    partEncoded(e) + ";" + paramsEncoded(e)

  def addParam(kv: (String, String)) =
    copy(parameters = parameters :+ kv)
}

trait PathPart extends Any {

  /**
   * The non-parameter part of this pathPart
   *
   * @return
   */
  def part: String

  /**
   * Adds a matrix parameter to the end of this path part
   *
   * @param kv
   */
  def addParam(kv: (String, String)): PathPart

  def parameters: Seq[(String, String)]

  def partEncoded(e: Enc)(implicit enc: String = "UTF-8") = {
    encode(part, e, enc)
  }

  def encoded(e: Enc)(implicit enc: String = "UTF-8"): String
}

object PathPart {
  def apply(path: String, matrixParams: Seq[(String,String)] = Seq.empty) =
    if(matrixParams.isEmpty) new StringPathPart(path) else MatrixParams(path, matrixParams)
}

object Uri {
  type Enc = UriEncoder

  implicit def stringToUri(s: String)(implicit d: UriDecoder = PercentDecoder) = parseUri(s)
  implicit def uriToString(uri: Uri)(implicit enc: String = "UTF-8", e: UriEncoder = PercentEncoder): String = uri.toString(enc, e)
  implicit def encoderToChainerEncoder(enc: UriEncoder) = ChainedUriEncoder(enc :: Nil)

  def parseUri(s: CharSequence)(implicit d: UriDecoder = PercentDecoder): Uri =
    UriParser.parse(s.toString, d)
}
