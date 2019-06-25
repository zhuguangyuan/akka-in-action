package aia.testdriven

import org.scalatest.{WordSpecLike, MustMatchers}
import akka.testkit.TestKit
import akka.actor._

//This test is ignored in the BookBuild, it's added to the defaultExcludedNames

class SilentActor01Test extends TestKit(ActorSystem("testsystem"))
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {
  // Commented to make the travis build pass, this is the original test in the book
  "A Silent Actor" must {
    "change state when it receives a message, single threaded" in {
      //Write the test, first fail
      fail("not implemented yet")
      // import SilentActor._

      // val silentActor = TestActorRef[SilentActor] // 找不到 TestActorRef???
      // silentActor ! SilentMessage("whisper")
      // silentActor.underlyingActor.state must (contain("whisper"))
    }
    "change state when it receives a message, multi-threaded" in {
      //Write the test, first fail
      // fail("not implemented yet")
      import SilentActor._

      val silentActor = system.actorOf(Props[SilentActor],"s3")
      silentActor ! SilentMessage("whisper1")
      silentActor ! SilentMessage("whisper2")
      silentActor ! GetState(testActor)

      expectMsg(Vector("whisper1", "whisper2","__"))
    }
  }
  // "A Silent Actor" must {
  //   "change state when it receives a message, single threaded" ignore {
  //     //Write the test, first fail
  //     fail("not implemented yet")
  //   }
  //   "change state when it receives a message, multi-threaded" ignore {
  //     //Write the test, first fail
  //     fail("not implemented yet")
  //   }
  // }

}



// class SilentActor extends Actor {
//   def receive = {
//     case msg =>
//   }
// }

object SilentActor {
  case class SilentMessage(data : String)
  case class GetState(receiver : ActorRef)
}

class SilentActor extends Actor {
  import SilentActor._
  var internalState = Vector[String]()

  def receive = {
    case SilentMessage(data) => 
        internalState = internalState :+ data
    case GetState(receiver) =>
        receiver ! internalState :+ "__"
  }

  def state = internalState
}









