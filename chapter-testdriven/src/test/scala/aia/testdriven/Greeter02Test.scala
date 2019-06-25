package aia.testdriven

import akka.testkit.{ TestKit }
import org.scalatest.WordSpecLike
import akka.actor._



class Greeter02Test extends TestKit(ActorSystem("testsystem"))
  with WordSpecLike
  with StopSystemAfterAll {

  "The Greeter" must {
    "say Hello World! when a Greeting(\"World\") is sent to it" in {
      val props = Greeter02.props(Some(testActor))
      val greeter = system.actorOf(props, "greeter02-1")
      greeter ! Greeting("World")
      expectMsg("Hello World!")
    }
    "say something else and see what happens" in {
      val props = Greeter02.props(Some(testActor))
      val greeter = system.actorOf(props, "greeter02-2")

      system.eventStream.subscribe(testActor, classOf[UnhandledMessage])
      
      greeter ! "hello everybody"
      expectMsg(UnhandledMessage("hello everybody", system.deadLetters, greeter))

      greeter ! "hello everybody111"
      expectMsg(UnhandledMessage("hello everybody111", system.deadLetters, greeter))

      greeter ! "hello everybody222"
      expectMsg(UnhandledMessage("hello everybody222", system.deadLetters, greeter))

      greeter ! "hello everybody333"
      expectMsg(UnhandledMessage("hello everybody333", system.deadLetters, greeter))
    }
  }
}


object Greeter02 {
  def props(listener: Option[ActorRef] = None) =
    Props(new Greeter02(listener))
}
class Greeter02(listener: Option[ActorRef])
  extends Actor with ActorLogging {
  def receive = {
    case Greeting(who) =>
      val message = "Hello " + who + "!"
      log.info(message)
      listener.foreach(_ ! message)
  }
}

