package com.github.yoshiyoshifujii.akka.sample.persistence

import akka.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import akka.persistence.testkit.PersistenceTestKitSnapshotPlugin
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.RetentionCriteria
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpecLike

class Sample1Spec
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
         |""".stripMargin).withFallback(PersistenceTestKitSnapshotPlugin.config).withFallback(
          EventSourcedBehaviorTestKit.config
        )
    )
    with AnyFreeSpecLike
    with LogCapturing {

  "SnapshotCompletedのSignalからCommandを発火してEventがどのように動くかそこからStateを変更するとどうなるかを試す" - {
    import Sample1._

    "success" in {

      val persistenceId = PersistenceId.ofUniqueId("your-persistence-id-1")
      val criteria      = RetentionCriteria.snapshotEvery(numberOfEvents = 3, keepNSnapshots = 2)
      val eventSourcedBehaviorTestKit =
        EventSourcedBehaviorTestKit[Command, Event, State](system, Sample1(persistenceId, criteria))

      val result1 = eventSourcedBehaviorTestKit.runCommand(FirstCommand("first"))
      assert(result1.eventOfType[EventFirst].value === "first")
      assert(result1.stateOfType[JustState].value === "first")

      val result2 = eventSourcedBehaviorTestKit.runCommand(SecondCommand("second"))
      assert(result2.eventOfType[EventSecond].value === "second")
      assert(result2.stateOfType[JustState].value === "firstsecond")

      val result3 = eventSourcedBehaviorTestKit.runCommand(SecondCommand("second-2"))
      assert(result3.eventOfType[EventSecond].value === "second-2")
      assert(result3.stateOfType[JustState].value === "firstsecondsecond-2")

      val result4 = eventSourcedBehaviorTestKit.runCommand(FirstCommand("first-2"))
      assert(result4.stateOfType[JustState].value === "first-2")

      println("restart")

      val restarted = eventSourcedBehaviorTestKit.restart()
      assert(restarted.state.asInstanceOf[JustState].value === "first-2")

    }

  }

}
