package aia.stream

import scala.concurrent.{ ExecutionContext, Future }
import akka.NotUsed
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.JsonFraming
import akka.stream.Materializer
import akka.stream.scaladsl.Source

import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._

import akka.util.ByteString
import spray.json._

import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.unmarshalling.Unmarshaller._

object EventUnmarshaller extends EventMarshalling {

  val supported = Set[ContentTypeRange](
    ContentTypes.`text/plain(UTF-8)`, 
    ContentTypes.`application/json`
  )

  def create(maxLine: Int, maxJsonObject: Int) = {
    // 将 HttpEntity 反序列化为 Source[Event,_]
    // ContentNegLogsApi 中的 postRoute 会用到
    new Unmarshaller[HttpEntity, Source[Event, _]] {
      def apply(entity: HttpEntity)(implicit ec: ExecutionContext, 
        materializer: Materializer): Future[Source[Event, _]] = {

        val future = entity.contentType match {
          case ContentTypes.`text/plain(UTF-8)` => 
            Future.successful(LogJson.textInFlow(maxLine)) // 创建一个flow, 然后通过Future.successful创建一个完成的future
          case ContentTypes.`application/json` =>
            Future.successful(LogJson.jsonInFlow(maxJsonObject))
          case other => 
            Future.failed(
              new UnsupportedContentTypeException(supported)
            )
        }
        // 对entity 应用 future中的flow
        future.map(flow => entity.dataBytes.via(flow))(ec)
      } 
    }.forContentTypes(supported.toList:_*) // 指定接受的内容形式
  }
}

