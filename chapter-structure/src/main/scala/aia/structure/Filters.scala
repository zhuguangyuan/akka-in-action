package aia.structure

import akka.actor.{ Actor, ActorRef }

/**
  * 定义要处理的 图片
  * @param license
  * @param speed
  */
case class Photo(license: String, speed: Int)

/**
  * 过滤 speed 的actor
  * @param minSpeed
  * @param pipe
  */
class SpeedFilter(minSpeed: Int, pipe: ActorRef) extends Actor {
  def receive = {
    case msg: Photo =>
      if (msg.speed > minSpeed)
        pipe ! msg
  }
}

/**
  * 过滤 license 的actor
  * @param pipe
  */
class LicenseFilter(pipe: ActorRef) extends Actor {
  def receive = {
    case msg: Photo =>
      if (!msg.license.isEmpty)
        pipe ! msg
  }
}
