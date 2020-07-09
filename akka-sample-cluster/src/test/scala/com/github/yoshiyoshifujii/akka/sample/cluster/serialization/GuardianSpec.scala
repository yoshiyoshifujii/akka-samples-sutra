package com.github.yoshiyoshifujii.akka.sample.cluster.serialization

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpecLike

class GuardianSpec extends ScalaTestWithActorTestKit(ActorTestKit(ConfigFactory.parseString(
  """
    |akka {
    |  actor {
    |    serializers {
    |      jackson-json = "akka.serialization.jackson.JacksonJsonSerializer"
    |    }
    |    serialization-bindings {
    |      "" = jackson-json
    |    }
    |  }
    |}
    |""".stripMargin))) with AnyFreeSpecLike {

  "Guardian" - {

    "success" in {

      val probe = testKit.createTestProbe[AnyRef]

      val guardian = testKit.spawn(Guardian())

      val groupChat = GroupChat(
        GroupChatId("A"),
        GroupChatName(""),
        Members(Vector.empty),
        MessageMetas(Vector())
      )

      guardian ! Guardian.Something(groupChat, probe.ref)

      assert(probe.expectMessageType[GroupChat].id.value === "A")

    }

  }

}
