package com.github.yoshiyoshifujii.akka.sample.asynchronousTesting

import akka.actor.testkit.typed.scaladsl.{ LogCapturing, ManualTime, ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class ManualTimerExampleSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  val manualTime: ManualTime = ManualTime()

  "A timer" must {
    "schedule non-repeated tickets" in {
      case object Tick
      case object Tock

      val probe = TestProbe[Tock.type]()
      val behavior = Behaviors.withTimers[Tick.type] { timer =>
        timer.startSingleTimer(Tick, 10.millis)
        Behaviors.receiveMessage { _ =>
          probe.ref ! Tock
          Behaviors.same
        }
      }

      spawn(behavior)
      manualTime.expectNoMessageFor(0.millis, probe)
      manualTime.timePasses(2.millis)
      probe.expectMessage(Tock)

      manualTime.expectNoMessageFor(10.seconds, probe)
    }
  }

}
