package aia.faulttolerance

import akka.actor._
import akka.actor.Terminated

object DbStrategy2 {
  class DbWatcher(dbWriter: ActorRef) extends Actor with ActorLogging {
    context.watch(dbWriter) // 本 actor 监视 dbWriter

    // 当收到被监视的actor终止的消息的时候
    // 打印相关日志
    def receive = {
      case Terminated(actorRef) =>
        log.warning("Actor {} terminated", actorRef)
    }
  }
}