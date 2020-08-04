package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.github.yoshiyoshifujii.akka.samples.domain.model.{
  AccountId,
  Member,
  MemberRole,
  Members,
  Thread,
  ThreadId,
  ThreadName
}
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers

class ThreadPersistentAggregateSpec
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString(s"""
         |akka {
         |  actor {
         |    serializers {
         |      jackson-cbor = "akka.serialization.jackson.JacksonCborSerializer"
         |    }
         |    serialization-bindings {
         |      "${classOf[ThreadPersistentAggregate.Reply].getName}" = jackson-cbor
         |      "${classOf[ThreadPersistentAggregate.Command].getName}" = jackson-cbor
         |      "${classOf[ThreadPersistentAggregate.Event].getName}" = jackson-cbor
         |      "${classOf[ThreadPersistentAggregate.State].getName}" = jackson-cbor
         |    }
         |  }
         |}
         |""".stripMargin).withFallback(
          EventSourcedBehaviorTestKit.config
        )
    )
    with AnyFreeSpecLike
    with Matchers {

  import ThreadPersistentAggregate._

  "ThreadPersistentAggregate" - {

    "Threadを作成し削除するまでの一連のコマンドとイベントが成功することを確認する" in {
      val threadId   = ThreadId()
      val behavior   = ThreadPersistentAggregate(threadId)
      val esbTestKit = EventSourcedBehaviorTestKit[Command, Event, State](system, behavior)
      val thread1    = createThread(threadId, esbTestKit)
      val thread2    = addMembers(esbTestKit, thread1)
      deleteThread(esbTestKit, thread2)
    }

  }

  private def deleteThread(esbTestKit: EventSourcedBehaviorTestKit[Command, Event, State], thread: Thread) = {
    val probe  = TestProbe[ReplyDeleteThread]
    val result = esbTestKit.runCommand(CommandDeleteThread(thread.id, probe.ref))
    result.eventOfType[EventThreadDeleted].threadId should be(thread.id)
    val thread2 = result.stateOfType[DeletedState].thread
    thread2 should be(thread)
    probe.expectMessageType[ReplyDeleteThreadSucceeded].threadId should be(thread.id)
    thread2
  }

  private def addMembers(esbTestKit: EventSourcedBehaviorTestKit[Command, Event, State], thread: Thread): Thread = {
    val member  = Member(AccountId(), MemberRole.Read)
    val members = Members(member)
    val probe   = TestProbe[ReplyAddMembers]
    val result  = esbTestKit.runCommand(CommandAddMembers(thread.id, members, probe.ref))
    val event   = result.eventOfType[EventMembersAdded]
    event.threadId should be(thread.id)
    event.members should be(members)
    val thread2 = result.stateOfType[JustState].thread
    thread2.id should be(thread.id)
    thread2.name should be(thread.name)
    thread2.members.value.tail should contain(member)
    probe.expectMessageType[ReplyAddMembersSucceeded].threadId should be(thread.id)
    thread2
  }

  private def createThread(
      threadId: ThreadId,
      esbTestKit: EventSourcedBehaviorTestKit[Command, Event, State]
  ): Thread = {
    val threadName = ThreadName("thread-name-1")
    val accountId  = AccountId()

    val probe  = TestProbe[ReplyCreateThread]()
    val result = esbTestKit.runCommand(CommandCreateThread(threadId, threadName, accountId, probe.ref))
    val event  = result.eventOfType[EventThreadCreated]
    event.threadId should be(threadId)
    event.threadName should be(threadName)
    event.creatorId should be(accountId)
    val thread = result.stateOfType[JustState].thread
    thread.id should be(threadId)
    thread.name should be(threadName)
    thread.members.value.head.accountId should be(accountId)
    thread.members.value.head.role should be(MemberRole.Admin)
    probe.expectMessageType[ReplyCreateThreadSucceeded].threadId should be(threadId)

    thread
  }
}
