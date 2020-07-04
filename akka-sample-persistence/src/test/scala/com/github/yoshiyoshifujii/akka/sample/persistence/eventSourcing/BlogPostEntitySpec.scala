package com.github.yoshiyoshifujii.akka.sample.persistence.eventSourcing

import akka.Done
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

    "success" in {
      val postId           = "post-id-1"
      val content          = PostContent(postId, "title-1", "body-1")
      val addPostDoneProbe = testKit.createTestProbe[AddPostDone]

      val result1 = eventSourcedTestKit.runCommand(AddPost(content, addPostDoneProbe.ref))
      result1.eventOfType[PostAdded].postId shouldBe content.postId
      result1.eventOfType[PostAdded].content shouldBe content
      result1.stateOfType[DraftState].content shouldBe content
      addPostDoneProbe.expectMessageType[AddPostDone].postId shouldBe content.postId

      val content2 = PostContent("post-id-2", "title-2", "body-2")

      val result2 = eventSourcedTestKit.runCommand(AddPost(content2, addPostDoneProbe.ref))
      result2.hasNoEvents should be(true)
      result2.stateOfType[DraftState].content shouldBe content
      addPostDoneProbe.stop()

      val doneProbe = testKit.createTestProbe[Done]

      val newBody1   = "new-body-2"
      val newContent = content.copy(body = newBody1)

      val result3 = eventSourcedTestKit.runCommand(ChangeBody(newBody1, doneProbe.ref))
      result3.eventOfType[BodyChanged].newBody shouldBe newBody1
      result3.eventOfType[BodyChanged].postId shouldBe postId
      result3.stateOfType[DraftState].content shouldBe newContent
      doneProbe.expectMessageType[Done]

      val postContentProbe = testKit.createTestProbe[PostContent]

      val result4 = eventSourcedTestKit.runCommand(GetPost(postContentProbe.ref))
      result4.hasNoEvents should be(true)
      result4.stateOfType[DraftState].content shouldBe newContent
      postContentProbe.expectMessageType[PostContent] shouldBe newContent

      eventSourcedTestKit.restart()

      val doneProbe2 = testKit.createTestProbe[Done]

      val result5 = eventSourcedTestKit.runCommand(Publish(doneProbe2.ref))
      result5.eventOfType[Published].postId shouldBe postId
      result5.stateOfType[PublishedState].content shouldBe newContent
      doneProbe2.expectMessageType[Done]

      val postContentProbe2 = testKit.createTestProbe[PostContent]

      val result6 = eventSourcedTestKit.runCommand(GetPost(postContentProbe2.ref))
      result6.hasNoEvents should be(true)
      result6.stateOfType[PublishedState].content shouldBe newContent
      postContentProbe2.expectMessageType[PostContent] shouldBe newContent

      eventSourcedTestKit.restart()
    }

  }

}
