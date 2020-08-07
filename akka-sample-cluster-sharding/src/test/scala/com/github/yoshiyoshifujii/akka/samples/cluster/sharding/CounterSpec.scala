package com.github.yoshiyoshifujii.akka.samples.cluster.sharding

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpecLike

class CounterSpec
    extends ScalaTestWithActorTestKit(ConfigFactory.parseString("""
    |
    |""".stripMargin))
    with AnyFreeSpecLike {

  "Counter" - {

    "success" in {

      val entityId = "counter-1"
      val ref      = testKit.spawn(Counter(entityId))
      val probe    = testKit.createTestProbe[Int]
      ref ! Counter.GetValue(entityId, probe.ref)
      assert(probe.expectMessageType[Int] === 0)
      ref ! Counter.Increment(entityId)
      ref ! Counter.GetValue(entityId, probe.ref)
      assert(probe.expectMessageType[Int] === 1)
    }

  }
}
