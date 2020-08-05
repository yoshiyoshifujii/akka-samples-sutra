package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.github.yoshiyoshifujii.akka.samples.domain.model._
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers

class MessagePersistentAggregateSpec
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString(s"""
        |akka {
        |  actor {
        |    serializers {
        |      jackson-cbor = "akka.serialization.jackson.JacksonCborSerializer"
        |    }
        |    serialization-bindings {
        |      "${classOf[MessagePersistentAggregate.Reply].getName}" = jackson-cbor
        |      "${classOf[MessagePersistentAggregate.Command].getName}" = jackson-cbor
        |      "${classOf[MessagePersistentAggregate.Event].getName}" = jackson-cbor
        |      "${classOf[MessagePersistentAggregate.State].getName}" = jackson-cbor
        |    }
        |  }
        |}
        |""".stripMargin).withFallback(EventSourcedBehaviorTestKit.config)
    )
    with AnyFreeSpecLike
    with Matchers {

  import MessagePersistentAggregate._

  "MessagePersistentAggregate" - {

    "Messageを作成し削除するまでの一連のコマンドとイベントが成功することを確認する" in {
      val messageId         = MessageId()
      val behavior          = MessagePersistentAggregate(messageId)
      val esbTestKit        = EventSourcedBehaviorTestKit[Command, Event, State](system, behavior)
      val message1: Message = createMessage(messageId, esbTestKit)
      val message2: Message = editMessage(esbTestKit, message1)
      val message3: Message = deleteMessage(esbTestKit, message2)
    }

  }

  private def deleteMessage(
      esbTestKit: EventSourcedBehaviorTestKit[Command, Event, State],
      message: Message
  ): Message = {
    val result =
      esbTestKit.runCommand[ReplyDeleteMessage](CommandDeleteMessage(message.id, message.threadId, message.senderId, _))
    val event = result.eventOfType[EventMessageDeleted]
    event.messageId should be(message.id)
    event.threadId should be(message.threadId)
    event.senderId should be(message.senderId)
    val message2 = result.stateOfType[DeletedState].message
    result.replyOfType[ReplyDeleteMessageSucceeded].messageId should be(message.id)
    message2
  }

  private def editMessage(
      esbTestKit: EventSourcedBehaviorTestKit[Command, Event, State],
      message: Message
  ): Message = {
    val body = MessageBody("message body second edition")
    val result = esbTestKit.runCommand[ReplyEditMessage](
      CommandEditMessage(message.id, message.threadId, message.senderId, body, _)
    )
    val event = result.eventOfType[EventMessageEdited]
    event.messageId should be(message.id)
    event.threadId should be(message.threadId)
    event.senderId should be(message.senderId)
    event.body should be(body)
    val message2 = result.stateOfType[JustState].message
    result.replyOfType[ReplyEditMessageSucceeded].messageId should be(message.id)
    message2
  }

  private def createMessage(
      messageId: MessageId,
      esbTestKit: EventSourcedBehaviorTestKit[Command, Event, State]
  ): Message = {
    val threadId = ThreadId()
    val senderId = AccountId()
    val body     = MessageBody("message body")
    val result   = esbTestKit.runCommand[ReplyCreateMessage](CommandCreateMessage(messageId, threadId, senderId, body, _))
    val event    = result.eventOfType[EventMessageCreated]
    event.messageId should be(messageId)
    event.threadId should be(threadId)
    event.senderId should be(senderId)
    event.body should be(body)
    val message = result.stateOfType[JustState].message
    result.replyOfType[ReplyCreateMessageSucceeded].messageId should be(messageId)
    message
  }

}
