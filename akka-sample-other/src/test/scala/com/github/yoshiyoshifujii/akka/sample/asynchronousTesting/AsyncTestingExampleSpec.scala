package com.github.yoshiyoshifujii.akka.sample.asynchronousTesting

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class AsyncTestingExampleSpec extends AnyFreeSpec with BeforeAndAfterAll with Matchers {
  val testKit: ActorTestKit = ActorTestKit()

  override protected def afterAll(): Unit = testKit.shutdownTestKit()

  "AsyncTestingExample" - {

    "The following demonstrates" in {
      val pinger = testKit.spawn(Echo(), "ping")
      val probe  = testKit.createTestProbe[Echo.Pong]

      pinger ! Echo.Ping("hello", probe.ref)
      probe.expectMessage(Echo.Pong("hello"))
    }

    "Stopping actors" in {
      val probe = testKit.createTestProbe[Echo.Pong]

      val pinger1 = testKit.spawn(Echo(), "pinger")
      pinger1 ! Echo.Ping("hello", probe.ref)
      probe.expectMessage(Echo.Pong("hello"))
      testKit.stop(pinger1)

      val pinger2 = testKit.spawn(Echo(), "pinger")
      pinger2 ! Echo.Ping("hello", probe.ref)
      probe.expectMessage(Echo.Pong("hello"))
      testKit.stop(pinger2, 10.seconds)
    }

  }

}
