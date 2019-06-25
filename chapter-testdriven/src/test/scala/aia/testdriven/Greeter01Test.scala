package aia.testdriven
import akka.testkit.{ CallingThreadDispatcher, EventFilter, TestKit }
import akka.actor.{ Props, ActorSystem }
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpecLike


import Greeter01Test._

class Greeter01Test extends TestKit(testSystem)
  with WordSpecLike
  with StopSystemAfterAll {

  "The Greeter" must {
    "say Hello World! when a Greeting(\"World\") is sent to it" in {
      val dispatcherId = CallingThreadDispatcher.Id
      val props = Props[Greeter].withDispatcher(dispatcherId)
      val greeter = system.actorOf(props)

      EventFilter.info(message = "Hello brucezhu!",
        occurrences = 1).intercept {
          // 这里向 greeter 发送一个消息，"brucezhu"，greeter会问候，打印"Hello brucezhu!"
          // TestEventListener 会记录这条打印
          // EventFilter 会从中查找 Hello brucezhu! 的记录，若出现，则测试通过
          greeter ! Greeting("brucezhu") 

        }
    }
  }
}

object Greeter01Test {
  val testSystem = {
    val config = ConfigFactory.parseString(
      """
         akka.loggers = [akka.testkit.TestEventListener]
      """)
    ActorSystem("testsystem", config)
  }
}


