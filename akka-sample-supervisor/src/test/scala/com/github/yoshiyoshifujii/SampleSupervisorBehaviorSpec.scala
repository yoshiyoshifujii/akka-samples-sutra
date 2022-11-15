package com.github.yoshiyoshifujii

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.freespec.AnyFreeSpecLike

class SampleSupervisorBehaviorSpec extends ScalaTestWithActorTestKit() with AnyFreeSpecLike {

  "Sample" in {
    val sut = spawn(SampleSupervisorBehavior())

    {
      val probe = createTestProbe[SampleSupervisorBehavior.ChildActorThrowsExceptionReply]

      sut ! SampleSupervisorBehavior.ChildActorThrowsException(probe.ref)

//      probe.receiveMessage() shouldBe SampleSupervisorBehavior.ChildActorThrowsExceptionFailed
    }
    {
      val probe = createTestProbe[SampleSupervisorBehavior.PingReply]

      sut ! SampleSupervisorBehavior.Ping(probe.ref)

      probe.receiveMessage() shouldBe SampleSupervisorBehavior.PingReply("pong!")
    }
  }

}
