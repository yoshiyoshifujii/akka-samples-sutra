package com.github.yoshiyoshifujii.akka.sample.behaviorsAsFiniteStateMachines

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.time.{ Seconds, Span }

import scala.concurrent.ExecutionContext

class BuncherSpec extends AnyFreeSpec with BeforeAndAfterAll with ScalaFutures with Eventually {

  private val testKit = ActorTestKit()

  override protected def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(60, Seconds)), interval = scaled(Span(1, Seconds)))

  "Buncher" - {

    "success" in {
      val buncherRef = testKit.spawn(Buncher())

      implicit val ex: ExecutionContext = testKit.system.executionContext

      val probe = testKit.createTestProbe[Buncher.Batch]

      buncherRef ! Buncher.Queue("ouch!!")

      buncherRef ! Buncher.SetTarget(probe.ref)
      buncherRef ! Buncher.Queue("obj-1")
      buncherRef ! Buncher.Flush
      probe.expectMessage(Buncher.Batch(Seq("obj-1")))

      buncherRef ! Buncher.Queue("obj-2")
      buncherRef ! Buncher.Queue("obj-3")
      probe.expectMessage(Buncher.Batch(Seq("obj-2", "obj-3")))
    }

  }
}
