package com.goticks

import scala.concurrent.Future

import akka.actor._
import akka.util.Timeout

/**
  * 定义 boxOffice 能够发送和接收的消息
  */
object BoxOffice {
  // 注意此处的 timeout 由外部注入，此处是在 BackendMain中通过读取配置文件的值进行注入
  def props(implicit timeout: Timeout) = Props(new BoxOffice)
  def name = "boxOffice"

  case class CreateEvent(name: String, tickets: Int)
  case class GetEvent(name: String)
  case object GetEvents // 获取所有事件。由于没有参数，所以建议用 case object 而不是 case class
  case class GetTickets(event: String, tickets: Int)
  case class CancelEvent(name: String)

  case class Event(name: String, tickets: Int)
  case class Events(events: Vector[Event])

  // 事件回应。sealed 表示所有的子类都在此文件下列出了，此处只有EventCreated/EventExists
  sealed trait EventResponse
  case class EventCreated(event: Event) extends EventResponse
  case object EventExists extends EventResponse
}

/**
  * actor-BoxOffice
  * @param timeout
  */
class BoxOffice(implicit timeout: Timeout) extends Actor with ActorLogging {
  import BoxOffice._
  import context._

  /**
    * 定义创建 ticketSeller 的方法
    * @param name
    * @return
    */
  def createTicketSeller(name: String) =
    context.actorOf(TicketSeller.props(name), name)

  /**
    * 定义收到消息的处理方法
    * @return
    */
  def receive = {
    /**
      * 创建event,包含名字和票量
      */
    case CreateEvent(name, tickets) =>
      def create() = {
        val eventTickets = createTicketSeller(name)
        val newTickets = (1 to tickets).map { ticketId =>
          TicketSeller.Ticket(ticketId)
        }.toVector
        eventTickets ! TicketSeller.Add(newTickets)
        sender() ! EventCreated(Event(name, tickets))
      }

      // context.child(name) 返回一个Option[Actor]
      // 若Option[Actor]为空则调用第一个函数create(),否则调用第二个函数
      context.child(name)
        .fold(create())(_ => sender() ! EventExists)

    /**
      * 购买ticket，返回一个TicketSeller.Tickets
      */
    case GetTickets(event, tickets) =>
      def notFound() =
        sender() ! TicketSeller.Tickets(event)

      // 向找到的child即ticketseller发送 TicketSeller.Buy(tickets) 消息
      // 注意ticketSeller收到消息之后校验合理会将 Tickets发回来，
      // $todo 但是发回到哪？Future？？本actor如何将Tickets信息返回给上层调用者？？
      def buy(child: ActorRef) =
        child.forward(TicketSeller.Buy(tickets))

      // context.child(name) 返回一个Option[Actor]
      // 若Option[Actor]为空则调用第一个函数notFound(),否则调用第二个函数buy
      context.child(event)
        .fold(notFound())(buy)

    /**
      * 获取 event 的信息
      */
    case GetEvent(event) =>
      def notFound() =
        sender() ! None
      def getEvent(child: ActorRef) =
        child forward TicketSeller.GetEvent

      context.child(event)
        .fold(notFound())(getEvent)

    /**
      * 获取所有事件
      */
    case GetEvents =>
      import akka.pattern.{ ask, pipe }

      // 将旗下的 actor-ticketSeller => Future[Option[Event]]
      // 结果为 Seq[Future[Option[Event]]]
      def getEvents =
        context.children.map { child =>
          self.ask(GetEvent(child.path.name)).mapTo[Option[Event]]
        }
      def convertToEvents(f: Future[Iterable[Option[Event]]]) =
        f.map(_.flatten).map(l=> Events(l.toVector))

      // Future.sequence 将 Seq[Future[Option[Event]]] 转换为 Future[Seq[Option[Event]]],供convertToEvents处理
      // 转换完成之后为 Future[BoxOffice.Events], 然后发给sender
      pipe(convertToEvents(Future.sequence(getEvents))) to sender()

    case CancelEvent(event) =>
      def notFound() =
        sender() ! None
      def cancelEvent(child: ActorRef) =
        child forward TicketSeller.Cancel

      context.child(event)
        .fold(notFound())(cancelEvent)
  }
}

