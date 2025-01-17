package au.com.dius.pact.server

import java.net.URI
import java.util.zip.GZIPInputStream

import au.com.dius.pact.core.model.{OptionalBody, Request, Response}
import com.typesafe.scalalogging.StrictLogging
import io.netty.handler.codec.http.{HttpResponse => NHttpResponse}
import unfiltered.netty.ReceivedMessage
import unfiltered.request.HttpRequest
import unfiltered.response._

import scala.collection.JavaConverters._

object Conversions extends StrictLogging {

  case class Headers(headers: java.util.Map[String, java.util.List[String]]) extends unfiltered.response.Responder[Any] {
    def respond(res: HttpResponse[Any]) {
      if (headers != null) {
        headers.asScala.foreach { case (key, value) => res.header(key, value.asScala.mkString(", ")) }
      }
    }
  }

  implicit def pactToUnfilteredResponse(response: Response): ResponseFunction[NHttpResponse] = {
    val headers = response.getHeaders
    if (response.getBody.isPresent) {
      Status(response.getStatus) ~> Headers(headers) ~> ResponseString(response.getBody.valueAsString)
    } else Status(response.getStatus) ~> Headers(headers)
  }

  def toHeaders(request: HttpRequest[ReceivedMessage]): java.util.Map[String, java.util.List[String]] = {
    request.headerNames.map(name => name -> request.headers(name).toList.asJava).toMap.asJava
  }

  def toQuery(request: HttpRequest[ReceivedMessage]): java.util.Map[String, java.util.List[String]] = {
    request.parameterNames.map(name => name -> request.parameterValues(name).asJava).toMap.asJava
  }

  def toPath(uri: String) = new URI(uri).getPath

  def toBody(request: HttpRequest[ReceivedMessage], charset: String = "UTF-8") = {
    val is = if (request.headers(ContentEncoding.GZip.name).contains("gzip")) {
      new GZIPInputStream(request.inputStream)
    } else {
      request.inputStream
    }
    if(is == null) "" else scala.io.Source.fromInputStream(is).mkString
  }

  implicit def unfilteredRequestToPactRequest(request: HttpRequest[ReceivedMessage]): Request = {
    new Request(request.method, toPath(request.uri), toQuery(request), toHeaders(request),
      OptionalBody.body(toBody(request).getBytes))
  }
}
