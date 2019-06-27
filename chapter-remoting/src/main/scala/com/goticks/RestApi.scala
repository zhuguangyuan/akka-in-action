package com.goticks

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor._
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.util.Timeout

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

trait RestApi extends BoxOfficeApi with EventMarshalling {
  import StatusCodes._

  //  The ~ composes routes and/or directives.
  //  You can read this as getOrder or post- Orders, whichever matches.
  // 注意这个参数在 FrontendMain 最后的startup 中作为参数传入
  def routes: Route = eventsRoute ~ eventRoute ~ ticketsRoute

  // 获取所有event
  def eventsRoute =
    pathPrefix("events") {
      pathEndOrSingleSlash { // 无论 /events之后有无斜线，都走{}内流程。 即/events 或者 /events/ 都走
        get {
          // GET /events
          onSuccess(getEvents()) { events =>
            complete(OK, events)
          }
        }
      }
    }

  // event 的创建、查询和删除
  def eventRoute =
    pathPrefix("events" / Segment) { event =>
      pathEndOrSingleSlash {
        post {
          // POST /events/:event
          entity(as[EventDescription]) { ed =>
            onSuccess(createEvent(event, ed.tickets)) {
              case BoxOffice.EventCreated(event) =>
                complete(Created, event)
              case BoxOffice.EventExists =>
                val err = Error(s"$event event exists already.")
                complete(BadRequest, err)
            }
          }
        } ~
        get {
          // GET /events/:event
          onSuccess(getEvent(event)) {
            _.fold(complete(NotFound))(e => complete(OK, e))
          }
        } ~
        delete {
          // DELETE /events/:event
          onSuccess(cancelEvent(event)) {
            _.fold(complete(NotFound))(e => complete(OK, e))
          }
        }
      }
    }

  // 购买事件下的ticket
  def ticketsRoute =
    pathPrefix("events" / Segment / "tickets") { event =>
      post {
        pathEndOrSingleSlash {
          // POST /events/:event/tickets
          entity(as[TicketRequest]) { request =>
            onSuccess(requestTickets(event, request.tickets)) { tickets =>
              if(tickets.entries.isEmpty) {
//                complete(NotFound)
                complete("小伙子，你的购买需求不合理哦")
              } else {
                complete(Created, tickets)
              }
            }
          }
        }
      }
    }
}




/**
  * 这个接口定义了 boxoffice能接受的请求
  */
trait BoxOfficeApi {
  import BoxOffice._
  def log: LoggingAdapter // 因为trait没法继承ActorLogger 所以采用此日志模板？

  // 定义创建 RemoteLookupProxy 的方法，具体实现在 FrontendMain中提供，因为要用到的配置信息它那里才有
  def createBoxOffice(): ActorRef

  // 为future提供异步执行环境，这里只是声明，具体的实现由子类提供
  // 子类比如 FrontendMain/SingleNodeMain
  implicit def executionContext: ExecutionContext
  // 声明超时时间，具体阈值由子类实现
  // 子类比如 FrontendMain/SingleNodeMain
  implicit def requestTimeout: Timeout

  /**
    * 注意这里创建 RemoteLookupProxy，如果采用懒加载，会导致第一个REST请求到来的时候actor-RemoteLookupProxy还没创建
    * 直到引用实例 boxOffice 时候才创建，才进入RemoteLookupProxy流程，其中的 sendIdentifyRequest() 才会被调用，从而去远程寻找boxOffice
    * 再建立和远端actor-BoxOffice的联系，才能正确处理客户端的请求
    */
  val boxOffice = createBoxOffice() //lazy val boxOffice = createBoxOffice()

  /**
    * 下面定义的几个请求方法，都通过 RemoteLookupProxy的实例boxOffice 转发到远端的 actor-BoxOffice 进行处理
    * 返回的Future通过mapTo()映射成期望的结果(所以actor-BoxOffice处理的结果要符合要求,不要返回格式不对的数据)
    * $todo 如果返回了格式不对的数据咋办？？
    */
  def createEvent(event: String, nrOfTickets: Int) = {
    log.info(s"Received new event $event, sending to $boxOffice")
    boxOffice.ask(CreateEvent(event, nrOfTickets))
      .mapTo[EventResponse]
  }

  def getEvents() =
    boxOffice.ask(GetEvents).mapTo[Events]

  def getEvent(event: String) =
    boxOffice.ask(GetEvent(event))
      .mapTo[Option[Event]]

  def cancelEvent(event: String) =
    boxOffice.ask(CancelEvent(event))
      .mapTo[Option[Event]]

  def requestTickets(event: String, tickets: Int) =
    boxOffice.ask(GetTickets(event, tickets))
      .mapTo[TicketSeller.Tickets]
}