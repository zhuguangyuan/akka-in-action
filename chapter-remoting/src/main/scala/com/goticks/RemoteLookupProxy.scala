package com.goticks

import akka.actor._
import akka.actor.ActorIdentity
import akka.actor.Identify

import scala.concurrent.duration._

class RemoteLookupProxy(path: String)
  extends Actor with ActorLogging {

  context.setReceiveTimeout(3 seconds)
  sendIdentifyRequest()

  def sendIdentifyRequest(): Unit = {
    // 根据path 选出local或者remote的 actor,可以对他们发送消息
    val selection = context.actorSelection(path)

    // 给被选中的actor发送Identify消息，对方收到消息后，会返回 akka.actor.ActorIdentity
    // akka.actor.ActorIdentity中包含了 correlationId 和 Option[ActorRef]
    selection ! Identify(path)
  }

  /**
    * 收到消息后 转 identify进行处理
    * @return
    */
  def receive = identify

  /**
    * 本 actor 在 identify 状态时收到消息的处理方法
    * 消息来源可能是远端 actor 也可能是 actor-ApiActor
    * @return
    */
  def identify: Receive = {
    // 如果收到 ActorIdentity 且包含 actor,则远程是active的actor
    // 本actor 进入active状态，并监视远程actor
    case ActorIdentity(`path`, Some(actor)) =>
      context.setReceiveTimeout(Duration.Undefined)
      log.info("switching to active state")
      context.become(active(actor))
      context.watch(actor)

    // 如果收到 ActorIdentity 且不包含 actor 则远程actor不可用
    case ActorIdentity(`path`, None) =>
      log.error(s"Remote actor with path $path is not available.")

    // 超时未收到远端actor的回复，重新寻找远程actor
    case ReceiveTimeout =>
      sendIdentifyRequest()

    // 收到来自 actor-RestApi 的msg,但是本actor还没准备好
    case msg: Any =>
      log.error(s"Ignoring message $msg, remote actor is not ready yet.")
  }

  /**
    * 本 actor 在 active 状态时收到消息的处理方法
    * 消息来源可能是远端 actor 也可能是 actor-ApiActor
    * @param actor 远程的actor
    * @return
    */
  def active(actor: ActorRef): Receive = {
    // 由于之前已经开启了对目标actorRef的监控，则当其终止时，本actor会收到 actorRef 终止的消息
    // 转入 identify 状态，再发送重新选择actor的请求
    case Terminated(actorRef) =>
      log.info(s"Actor $actorRef terminated.")
      log.info("switching to identify state")
      context.become(identify)
      context.setReceiveTimeout(3 seconds)
      sendIdentifyRequest()

    // 收到actor-ApiActor的消息，则发送给actor
    case msg: Any => actor forward msg
  }
}
