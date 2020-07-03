package com.github.yoshiyoshifujii.akka.sample.persistence.eventSourcing

import akka.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

class MyPersistentBehaviorSpec
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString("""
         |akka {
         |  actor {
         |    serializers {
         |      jackson-cbor = "akka.serialization.jackson.JacksonCborSerializer"
         |    }
         |    serialization-bindings {
         |      "com.github.yoshiyoshifujii.akka.sample.persistence.serialization.CborSerializable" = jackson-cbor
         |    }
         |  }
         |}
         |""".stripMargin).withFallback(EventSourcedBehaviorTestKit.config)
    )
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with LogCapturing {

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[MyPersistentBehavior.Command, MyPersistentBehavior.Event, MyPersistentBehavior.State](
      system,
      MyPersistentBehavior("id-1")
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "MyPersistentBehavior" must {

    "Add" in {
      val result = eventSourcedTestKit.runCommand(MyPersistentBehavior.Add("data-1"))
      result.stateOfType[MyPersistentBehavior.State].history should (contain("data-1") and have length 1)
    }

    "Clear" in {
      val result1 = eventSourcedTestKit.runCommand(MyPersistentBehavior.Add("data-1"))
      result1.stateOfType[MyPersistentBehavior.State].history should (contain("data-1") and have length 1)
      val result2 = eventSourcedTestKit.runCommand(MyPersistentBehavior.Clear)
      result2.stateOfType[MyPersistentBehavior.State].history shouldBe Nil
    }

    "EffectsNone" in {
      val result1 = eventSourcedTestKit.runCommand(MyPersistentBehavior.EffectsNone)
      result1.stateOfType[MyPersistentBehavior.State].history.isEmpty shouldBe true
      val result2 = eventSourcedTestKit.runCommand(MyPersistentBehavior.Add("data-1"))
      result2.stateOfType[MyPersistentBehavior.State].history should (contain("data-1") and have length 1)
      eventSourcedTestKit.restart()
      val result3 = eventSourcedTestKit.runCommand(MyPersistentBehavior.EffectsNone)
      result3.stateOfType[MyPersistentBehavior.State].history should (contain("data-1") and have length 1)
    }

    "EffectsUnHandled" in {
      val result1 = eventSourcedTestKit.runCommand(MyPersistentBehavior.EffectsUnhandled)
      result1.stateOfType[MyPersistentBehavior.State].history.isEmpty shouldBe true
      val result2 = eventSourcedTestKit.runCommand(MyPersistentBehavior.Add("data-1"))
      result2.stateOfType[MyPersistentBehavior.State].history should (contain("data-1") and have length 1)
      eventSourcedTestKit.restart()
      val result3 = eventSourcedTestKit.runCommand(MyPersistentBehavior.EffectsUnhandled)
      result3.stateOfType[MyPersistentBehavior.State].history should (contain("data-1") and have length 1)
    }

    "EffectsStop" in {
      val actorRef = testKit.spawn(MyPersistentBehavior("id-2"))
      actorRef ! MyPersistentBehavior.EffectsStop
    }

    "EffectsStash and EffectsUnStashAll" in {
      val result1 = eventSourcedTestKit.runCommand(MyPersistentBehavior.Add("data-1"))
      result1.stateOfType[MyPersistentBehavior.State].history should (contain("data-1") and have length 1)
      val result2 = eventSourcedTestKit.runCommand(MyPersistentBehavior.EffectsStash)
      result2.stateOfType[MyPersistentBehavior.State].history should (contain("data-1") and have length 1)
      val result3 = eventSourcedTestKit.runCommand(MyPersistentBehavior.Add("data-2"))
      result3.stateOfType[MyPersistentBehavior.State].history should (contain("data-2") and have length 2)
      val result4 = eventSourcedTestKit.runCommand(MyPersistentBehavior.EffectsUnStashAll)
      result4.stateOfType[MyPersistentBehavior.State].history should (contain("data-2") and have length 2)
    }

  }
}
