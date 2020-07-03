package com.github.yoshiyoshifujii.akka.sample.asynchronousTesting

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec

import scala.util.Success

class ObservingMockedBehaviorSpec extends AnyFreeSpec with BeforeAndAfterAll {

  val testKit: ActorTestKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "ObservingMockedBehavior" - {
    import com.github.yoshiyoshifujii.akka.sample.asynchronousTesting.ObservingMockedBehavior._

    "success" in {

      import testKit._
      implicit val scheduler: Scheduler = testKit.scheduler

      val mockedBehavior = Behaviors.receiveMessage[Message] { msg =>
        msg.replyTo ! Success(msg.i)
        Behaviors.same
      }
      val probe           = testKit.createTestProbe[Message]
      val mockedPublisher = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))

      val producer = new Producer(mockedPublisher)
      val messages = 3
      producer.produce(messages)

      for (i <- 0 until messages) {
        val msg = probe.expectMessageType[Message]
        assert(msg.i === i)
      }
    }

  }

}
