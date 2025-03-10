package aia.faulttolerance

import aia.faulttolerance.LifeCycleHooks.{ForceRestart, ForceRestartException}
import akka.actor._

object LifeCycleHooks {
  object SampleMessage
  object ForceRestart
  private class ForceRestartException extends IllegalArgumentException("force restart")
}

class LifeCycleHooks extends Actor with ActorLogging {
  log.info("\n====Constructor")

  override def preStart(): Unit = {
    log.info("\n====preStart")
  }

  override def postStop(): Unit = {
    log.info("\n====postStop")
  }

  override def preRestart(reason: Throwable,
                          message: Option[Any]): Unit = {
    log.info(s"\n====preRestart. Reason: $reason when handling message: $message")
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    log.info("\n====postRestart")
    super.postRestart(reason)
  }

  def receive = {
    case ForceRestart =>
      throw new ForceRestartException
    // case msg: AnyRef =>
    //   log.info(s"\n====Received: '$msg'. Sending back")
    //   sender() ! msg
  }
}
