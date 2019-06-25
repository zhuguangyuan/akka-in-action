package aia.testdriven

import akka.testkit.TestKit
import akka.actor.{ Actor, Props, ActorRef, ActorSystem }
import org.scalatest.{MustMatchers, WordSpecLike }

class FilteringActorTest extends TestKit(ActorSystem("testsystem"))
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {
  "A Filtering Actor" must {

    "filter out particular messages" in {
      import FilteringActor._
      val props = FilteringActor.props(testActor, 5)
      val filter = system.actorOf(props, "filter-1")
      filter ! Event(1)
      filter ! Event(2)
      filter ! Event(1) // filter ! Event(6)
      filter ! Event(3)
      filter ! Event(1)
      filter ! Event(4)
      filter ! Event(5)
      filter ! Event(5)
      filter ! Event(6)

      // testActor 不断接收消息，直到遇到不满足条件的消息，退出循环
      // 所以若此处将第三条消息由 1 改为 6 则最终的eventIds 只能得到(1,2)
      val eventIds = receiveWhile() {
        case Event(id) if id <= 5 => id
      }
      eventIds must be(List(1, 2, 3, 4, 5))

      // Event(6) 其实也由 FilteringActor 发送过来给testActor了
      // 所以此处验证testActor已经接收到 Event(6) 是通过的
      expectMsg(Event(6))
    }


    "filter out particular messages using expectNoMsg" in {
      import FilteringActor._
      val props = FilteringActor.props(testActor, 5)
      val filter = system.actorOf(props, "filter-2")
      filter ! Event(1)
      filter ! Event(2)
      expectMsg(Event(1))
      expectMsg(Event(2))
      // 再次往 filter 发送 Event(1)的消息，filter发现 1 重复，所以没往 testActor 发送 msg
      // 所以此处 expectNoMsg 是可以通过的
      filter ! Event(1)
      expectNoMsg

      // 给filter 发送消息3 filter验证消息3通过，于是发送回来给testActor,所以此处应该能收到消息3
      // 验证应该通过
      filter ! Event(3)
      expectMsg(Event(3))

      // 再次往 filter 发送 Event(1)的消息，filter发现 1 重复，所以没往 testActor 发送 msg
      // 所以此处 expectNoMsg 是可以通过的
      filter ! Event(1)
      expectNoMsg

      filter ! Event(4)
      filter ! Event(5)
      filter ! Event(5)
      expectMsg(Event(4))
      expectMsg(Event(5))
      expectNoMsg()
    }

  }
}

object FilteringActor {
  def props(nextActor: ActorRef, bufferSize: Int) =
    Props(new FilteringActor(nextActor, bufferSize))
  case class Event(id: Long)
}

class FilteringActor(nextActor: ActorRef,
                     bufferSize: Int) extends Actor {
  import FilteringActor._
  var lastMessages = Vector[Event]()

  // 对于收到的消息，如果本身保存的 Vector 中没有相应的值，则将他发送给下一个actor，此处即testActor
  // 同时检查缓存的size是否超过初始范围，超过则丢弃最旧的值
  def receive = {
    case msg: Event =>
      if (!lastMessages.contains(msg)) {
        lastMessages = lastMessages :+ msg
        nextActor ! msg
        if (lastMessages.size > bufferSize) {
          // discard the oldest
          lastMessages = lastMessages.tail
        }
      }
  }
}


