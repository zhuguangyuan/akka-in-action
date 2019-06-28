package aia.structure

import java.util.Date
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer

import akka.actor._
import java.text.SimpleDateFormat

/**
  * 待处理的照片 message
  * 将 photo 中的信息解析出来 放到 creationTime、speed 两个字段
  * @param id
  * @param photo // 照片内容 参考 createPhotoString
  * @param creationTime
  * @param speed
  */
case class PhotoMessage(id: String,
                        photo: String,
                        creationTime: Option[Date] = None,
                        speed: Option[Int] = None)

/**
  * 照片 处理tools 获取时间、速度、license、创建String形式的photo内容
  */
object ImageProcessing {
  val dateFormat = new SimpleDateFormat("ddMMyyyy HH:mm:ss.SSS")
  def getSpeed(image: String): Option[Int] = {
    val attributes = image.split('|')
    if (attributes.size == 3)
      Some(attributes(1).toInt)
    else
      None
  }
  def getTime(image: String): Option[Date] = {
    val attributes = image.split('|')
    if (attributes.size == 3)
      Some(dateFormat.parse(attributes(0)))
    else
      None
  }
  def getLicense(image: String): Option[String] = {
    val attributes = image.split('|')
    if (attributes.size == 3)
      Some(attributes(2))
    else
      None
  }
  def createPhotoString(date: Date, speed: Int): String = {
    createPhotoString(date, speed, " ")
  }

  private def createPhotoString(date: Date,
                        speed: Int,
                        license: String): String = {
    "%s|%s|%s".format(dateFormat.format(date), speed, license)
  }
}

/**
  * 过滤速度的 filter
  * @param pipe
  */
class GetSpeed(pipe: ActorRef) extends Actor {
  def receive = {
    case msg: PhotoMessage => {
      pipe ! msg.copy(
        speed = ImageProcessing.getSpeed(msg.photo))
    }
  }
}

/**
  * 过滤时间的 filter
  * @param pipe
  */
class GetTime(pipe: ActorRef) extends Actor {
  def receive = {
    case msg: PhotoMessage => {
      pipe ! msg.copy(creationTime =
        ImageProcessing.getTime(msg.photo))
    }
  }
}


/**
  * 接收要处理的 photo消息的 actor ，分发给并行的多个 filter
  * @param recipientList
  */
class RecipientList(recipientList: Seq[ActorRef]) extends Actor {
  def receive = {
    case msg: AnyRef => recipientList.foreach(_ ! msg)
  }
}

/**
  * 用于 Aggregator 接收到某个filter的处理结果之后 超时没有收到另一个 filter 的结果的情况
  * @param msg
  */
case class TimeoutMessage(msg: PhotoMessage)

/**
  * 合并并行filter的处理结果 的actor
  * 正常情况下，aggregator会等拿到两个filter的处理结果的时候才聚合然后发送给后续actor
  * 但是只收到一个filter的结果，另一个filter宕机之后，会造成aggregator一直在等待其结果的情况
  * 为了处理这种情况，加入了超时机制。即受到一个filter-result之后，启动定时器，超时之后给aggregator发送一条超时消息
  * aggregator 收到超时消息后，检查对应消息id的消息是否存在，存在则将其发往后续actor,不存在则说明已经正常处理了，忽略超时消息即可
  * @param timeout
  * @param pipe
  */
class Aggregator(timeout: FiniteDuration, pipe: ActorRef) extends Actor {
  val messages = new ListBuffer[PhotoMessage]
  implicit val ec = context.system.dispatcher

  /**
    * 重启前将已收到的消息发给自己(的mailbox)，重启的时候再处理
    * @param reason
    * @param message
    */
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    messages.foreach(self ! _)
    messages.clear()
  }

  def receive = {
    case rcvMsg: PhotoMessage => {
      messages.find(_.id == rcvMsg.id) match {
        case Some(alreadyRcvMsg) => {
          val newCombinedMsg = new PhotoMessage(
            rcvMsg.id,
            rcvMsg.photo,
            rcvMsg.creationTime.orElse(alreadyRcvMsg.creationTime),
            rcvMsg.speed.orElse(alreadyRcvMsg.speed))
          pipe ! newCombinedMsg
          //cleanup message
          messages -= alreadyRcvMsg
        }
        case None => {
          messages += rcvMsg
          // 开启定时器
          context.system.scheduler.scheduleOnce(
            timeout,
            self,
            new TimeoutMessage(rcvMsg))
        }
      }
    }
    // 收到 rcvMsg 等待另一部分filter的处理结果 超时的消息
    // 则不再等待，将已获得的rsvMsg发送给下游
    case TimeoutMessage(rcvMsg) => {
      messages.find(_.id == rcvMsg.id) match {
        case Some(alreadyRcvMsg) => {
          pipe ! alreadyRcvMsg
          messages -= alreadyRcvMsg
        }
        case None => //message is already processed
      }
    }
    // 收到异常消息，抛出异常，从而让本actor重启
    // $todo 这个是啥机制？
    case ex: Exception => throw ex
  }
}
