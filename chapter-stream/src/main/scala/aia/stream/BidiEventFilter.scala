package aia.stream

import java.nio.file.{ Path, Paths }
import java.nio.file.StandardOpenOption
import java.nio.file.StandardOpenOption._


import scala.concurrent.Future

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, IOResult }
import akka.stream.scaladsl._
import akka.stream.scaladsl.JsonFraming
import akka.util.ByteString

import spray.json._
import com.typesafe.config.{ Config, ConfigFactory }

/**
  * flow的组合 双向flow
  */
object BidiEventFilter extends App with EventMarshalling {
  val config = ConfigFactory.load() 
  val maxLine = config.getInt("log-stream-processor.max-line")
  val maxJsonObject = config.getInt("log-stream-processor.max-json-object")

  if(args.length != 5) {
    System.err.println("Provide args: input-format output-format input-file output-file state")
    System.exit(1)
  }

  val inputFile = FileArg.shellExpanded(args(2))
  val outputFile = FileArg.shellExpanded(args(3))
  val filterState = args(4) match {
    case State(state) => state
    case unknown => 
      System.err.println(s"Unknown state $unknown, exiting.") 
      System.exit(1)
  }

  /**
    * 将byteString => Event
    * 如果以json形式提供，则解码之后再解析成json形式，然后转成Event
    * 否则通过分隔符来进行切分转化
    */
  val inFlow: Flow[ByteString, Event, NotUsed] = 
    if(args(0).toLowerCase == "json") {
      JsonFraming.objectScanner(maxJsonObject)
      .map(_.decodeString("UTF8").parseJson.convertTo[Event])
    } else {
      Framing.delimiter(ByteString("\n"), maxLine)
        .map(_.decodeString("UTF8"))
        .map(LogStreamProcessor.parseLineEx)
        .collect { case Some(event) => event }
    }

  /**
    * 将 Event => ByteString
    * 如果要序列化成json,调用现有库直接转化
    * 否则
    */
  val outFlow: Flow[Event, ByteString, NotUsed] = 
    if(args(1).toLowerCase == "json") {
      Flow[Event].map(event => ByteString(event.toJson.compactPrint))
    } else {
      Flow[Event].map{ event => 
        ByteString(LogStreamProcessor.logLine(event))
      }
    }
  val bidiFlow = BidiFlow.fromFlows(inFlow, outFlow)

  val source: Source[ByteString, Future[IOResult]] =
    FileIO.fromPath(inputFile)

  val sink: Sink[ByteString, Future[IOResult]] = 
    FileIO.toPath(outputFile, Set(CREATE, WRITE, APPEND))
  

  val filter: Flow[Event, Event, NotUsed] =   
    Flow[Event].filter(_.state == filterState)
  // 组合
  val flow = bidiFlow.join(filter)

  // 构成graph
  val runnableGraph: RunnableGraph[Future[IOResult]] = 
    source.via(flow).toMat(sink)(Keep.right)

  implicit val system = ActorSystem() 
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  runnableGraph.run().foreach { result =>
    println(s"Wrote ${result.count} bytes to '$outputFile'.")
    system.terminate()
  }  
}
