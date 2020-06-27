package com.github.yoshiyoshifujii.akka.sample.asynchronousTesting

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.freespec.AnyFreeSpecLike

class ScalaTestIntegrationExampleSpec extends ScalaTestWithActorTestKit with AnyFreeSpecLike {

  "Something" - {
    "behave correctly" in {
      val pinger = testKit.spawn(Echo(), "ping")
      val probe = testKit.createTestProbe[Echo.Pong]
      pinger ! Echo.Ping("hello", probe.ref)
      probe.expectMessage(Echo.Pong("hello"))
    }
  }

}
