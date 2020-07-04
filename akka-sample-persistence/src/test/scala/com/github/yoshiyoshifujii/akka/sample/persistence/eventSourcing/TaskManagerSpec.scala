package com.github.yoshiyoshifujii.akka.sample.persistence.eventSourcing

import akka.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

class TaskManagerSpec
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
    with LogCapturing {

  "TaskManager" must {
    import TaskManager._

    "success" in {
      val eventSourcedTestKit = EventSourcedBehaviorTestKit[Command, Event, State](
        system,
        TaskManager(PersistenceId.ofUniqueId("persistence-id-1"))
      )

      val taskId1 = "task-id-1"
      val taskId2 = "task-id-2"
      val taskId3 = "task-id-3"

      val result1 = eventSourcedTestKit.runCommand(StartTask(taskId1))
      result1.eventOfType[TaskStarted].taskId shouldBe taskId1
      result1.state.taskIdInProgress shouldBe Some(taskId1)

      // duplicate, already in progress
      val result2 = eventSourcedTestKit.runCommand(StartTask(taskId1))
      result2.hasNoEvents should be(true)
      result2.state.taskIdInProgress shouldBe Some(taskId1)

      // stash other task in progress
      val result3 = eventSourcedTestKit.runCommand(StartTask(taskId2))
      result3.hasNoEvents should be(true)
      result3.state.taskIdInProgress shouldBe Some(taskId1)

      // next step
      val result4 = eventSourcedTestKit.runCommand(NextStep(taskId1, "instruction-1"))
      result4.eventOfType[TaskStep].instruction shouldBe "instruction-1"
      result4.state.taskIdInProgress shouldBe Some(taskId1)

      // stash other task in progress
      val result5 = eventSourcedTestKit.runCommand(StartTask(taskId3))
      result5.hasNoEvents should be(true)
      result5.state.taskIdInProgress shouldBe Some(taskId1)

      // end task and un stash all
      val result6 = eventSourcedTestKit.runCommand(EndTask(taskId1))
      result6.eventOfType[TaskCompleted].taskId shouldBe taskId1
      result6.state.taskIdInProgress shouldBe Some(taskId2)

      val result7 = eventSourcedTestKit.runCommand(EndTask(taskId2))
      result7.eventOfType[TaskCompleted].taskId shouldBe taskId2
      result7.state.taskIdInProgress shouldBe Some(taskId3)

      val result8 = eventSourcedTestKit.runCommand(EndTask(taskId3))
      result8.eventOfType[TaskCompleted].taskId shouldBe taskId3
      result8.state.taskIdInProgress shouldBe None
    }

  }

}
