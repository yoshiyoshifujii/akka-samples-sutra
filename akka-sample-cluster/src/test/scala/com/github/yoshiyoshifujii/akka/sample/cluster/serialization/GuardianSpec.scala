package com.github.yoshiyoshifujii.akka.sample.cluster.serialization

import akka.actor.testkit.typed.scaladsl.{ ActorTestKit, ScalaTestWithActorTestKit }
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpecLike

class GuardianSpec extends AnyFreeSpecLike {

  "Guardian" - {

    "JacksonJsonSerializer" in {

      val testKit = ActorTestKit(ConfigFactory.parseString("""
          |akka {
          |  actor {
          |    serializers {
          |      jackson-json = "akka.serialization.jackson.JacksonJsonSerializer"
          |    }
          |    serialization-bindings {
          |      "com.github.yoshiyoshifujii.akka.sample.cluster.serialization.JsonSerializer" = jackson-json
          |    }
          |  }
          |}
          |""".stripMargin))

      try {
        val probe = testKit.createTestProbe[AnyRef]

        val guardian = testKit.spawn(Guardian())

        val groupChat = GroupChat(
          GroupChatId("A"),
          GroupChatName(""),
          Members(Vector.empty),
          MessageMetas(Vector())
        )
        MemberRole.Admin.entryName

        guardian ! Guardian.Something(groupChat, probe.ref)

        assert(probe.expectMessageType[GroupChat].id.value === "A")
      } finally {
        testKit.shutdownTestKit()
      }

    }

    "Protobuf" in {

      import com.github.yoshiyoshifujii.akka.sample.cluster.serialization.proto.{ domain => Proto }

      val testKit = ActorTestKit(ConfigFactory.parseString("""
          |akka {
          |  actor {
          |    allow-java-serialization = off
          |    warn-about-java-serializer-usage = off
          |    serializers {
          |      proto = "akka.remote.serialization.ProtobufSerializer"
          |    }
          |    serialization-bindings {
          |      "scalapb.GeneratedMessage" = proto
          |    }
          |  }
          |}
          |""".stripMargin))

      try {
        val probe = testKit.createTestProbe[AnyRef]

        val guardian = testKit.spawn(Guardian())

        val groupChat = Proto.GroupChat(
          "A",
          "name",
          Some(Proto.Members(Vector(Proto.Member(accountId = "account-id-1", role = Proto.Member.MemberRole.ADMIN)))),
          Some(Proto.MessageMetas(Vector(Proto.MessageMeta(messageId = "message-id-1", senderId = "account-id-1"))))
        )

        guardian ! Guardian.Something(groupChat, probe.ref)

        assert(probe.expectMessageType[Proto.GroupChat].id === "A")
      } finally {
        testKit.shutdownTestKit()
      }

    }

  }

}
