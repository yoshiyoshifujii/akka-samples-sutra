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
      result.stateOfType[MyPersistentBehavior.State].history should contain("data-1")
      result.stateOfType[MyPersistentBehavior.State].history.size shouldBe 1
    }

    "Clear" in {
      val result1 = eventSourcedTestKit.runCommand(MyPersistentBehavior.Add("data-1"))
      result1.stateOfType[MyPersistentBehavior.State].history should contain("data-1")
      result1.stateOfType[MyPersistentBehavior.State].history.size shouldBe 1
      val result2 = eventSourcedTestKit.runCommand(MyPersistentBehavior.Clear)
      result2.stateOfType[MyPersistentBehavior.State].history shouldBe Nil
    }

  }
}
