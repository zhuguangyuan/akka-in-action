package com.goticks

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}

/**
  * 定义 TicketSeller 能够发送和接收的消息
  */
object TicketSeller {
  def props(event: String) = Props(new TicketSeller(event))

  case class Add(tickets: Vector[Ticket])
  case class Buy(tickets: Int)
  case class Ticket(id: Int)
  case class Tickets(event: String,
                     entries: Vector[Ticket] = Vector.empty[Ticket])
  case object GetEvent
  case object Cancel
}

/**
  * actor-ticketSeller, 因为此project下 ticketSeller都是由actor-boxoffice创建的，所以都是其child
  * @param event
  */
class TicketSeller(event: String) extends Actor with ActorLogging{
  import TicketSeller._

  // 用于保存旗下的ticket数据
  var tickets = Vector.empty[Ticket]

  def receive = {
    /**
      * 注意 newTickets 在BoxOffice上创建了
      */
    case Add(newTickets) =>
      tickets = tickets ++ newTickets

    case Buy(nrOfTickets) =>
      // 获取 tickets中，前nrOfTickets个元素组成的新Vector
      log.info(s"=====111小伙子，你要买 ${nrOfTickets} 件麒麟胸甲吗")
      val entries = tickets.take(nrOfTickets).toVector
      log.info(s"=====222小伙子，你要买 ${nrOfTickets} 件麒麟胸甲吗")

      //
      if(entries.size >= nrOfTickets) {
        log.info(s"=====333小伙子" + entries)

        sender() ! Tickets(event, entries) // 这里将 Tickets(event, entries) 返回给 BoxOffice，
        tickets = tickets.drop(nrOfTickets)
        log.info(s"=====444小伙子" + tickets)

      } else {
        log.info(s"=====5555小伙子，你的购买需求不合理哦" + event)

        sender() ! Tickets(event)
      }

    case GetEvent =>
      sender() ! Some(BoxOffice.Event(event, tickets.size))

    case Cancel =>
      sender() ! Some(BoxOffice.Event(event, tickets.size))
      self ! PoisonPill
  }
}

