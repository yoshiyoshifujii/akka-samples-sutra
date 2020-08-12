package com.github.yoshiyoshifujii.akka.samples.workpulling

import java.nio.charset.StandardCharsets
import java.util.UUID

import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpecLike

class WorkPullingSpec extends ScalaTestWithActorTestKit(ConfigFactory.parseString("""""")) with AnyFreeSpecLike {

  "WorkPulling" - {

    "success" in {

      testKit.spawn(ImageConverter(), "worker-1")

      val producer = testKit.spawn(ImageWorkManager(), "producer")

      (1 to 2).foreach { i =>
        producer ! ImageWorkManager.Convert("from", "to", s"Image-$i".getBytes(StandardCharsets.UTF_8))
        Thread.sleep(500)
      }

      val probe = TestProbe[Option[Array[Byte]]]
      producer ! ImageWorkManager.GetResult(UUID.randomUUID(), probe.ref)

    }

  }

}
