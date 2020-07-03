package com.github.yoshiyoshifujii.akka.sample.persistence.eventSourcing

import akka.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

class BlogPostEntitySpec
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
    EventSourcedBehaviorTestKit[BlogPostEntity.Command, BlogPostEntity.Event, BlogPostEntity.State](
      system,
      BlogPostEntity("entity-id-1", PersistenceId.ofUniqueId("persistence-id-1"))
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "BlogPostEntity" must {
    import BlogPostEntity._

    "AddPost" in {
      val content = PostContent("post-id-1", "title-1", "body-1")
      val probe   = testKit.createTestProbe[AddPostDone]

      val result1 = eventSourcedTestKit.runCommand(AddPost(content, probe.ref))
      result1.eventOfType[PostAdded].postId shouldBe content.postId
      result1.eventOfType[PostAdded].content shouldBe content
      result1.stateOfType[DraftState].content shouldBe content
      probe.expectMessageType[AddPostDone].postId shouldBe content.postId

      val content2 = PostContent("post-id-2", "title-2", "body-2")

      val result2 = eventSourcedTestKit.runCommand(AddPost(content2, probe.ref))
      result2.hasNoEvents should be(true)
      result2.stateOfType[DraftState].content shouldBe content
      probe.stop()
    }

  }

}
