package com.github.yoshiyoshifujii.akka.samples.cluster.sharding.persistenceExample

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpecLike

class HelloWorldSpec
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString(s"""
         |akka {
         |  actor {
         |    serializers {
         |      jackson-cbor = "akka.serialization.jackson.JacksonCborSerializer"
         |    }
         |    serialization-bindings {
         |      "${classOf[HelloWorld.Reply].getName}" = jackson-cbor
         |      "${classOf[HelloWorld.Command].getName}" = jackson-cbor
         |      "${classOf[HelloWorld.Event].getName}" = jackson-cbor
         |      "${classOf[HelloWorld.State].getName}" = jackson-cbor
         |    }
         |  }
         |}
         |""".stripMargin).withFallback(EventSourcedBehaviorTestKit.config)
    )
    with AnyFreeSpecLike {

  "HelloWorld" - {

    "success" in {

      val entityId   = "hello1"
      val behavior   = HelloWorld(PersistenceId.of(HelloWorld.name, entityId, "-"))
      val esbTestKit = EventSourcedBehaviorTestKit(system, behavior)
      val result     = esbTestKit.runCommand[HelloWorld.ReplyGreeting](HelloWorld.CommandGreet("whom-1"))
      assert(result.replyOfType[HelloWorld.ReplyGreeting].whom === "whom-1")
      assert(result.replyOfType[HelloWorld.ReplyGreeting].numberOfPeople === 1)

    }

  }

}
