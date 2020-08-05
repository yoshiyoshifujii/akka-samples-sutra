package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.scaladsl.Effect
import com.github.yoshiyoshifujii.akka.samples.domain.model.{ AccountId, Members, Thread, ThreadId, ThreadName }

object ThreadPersistentAggregate {

  sealed trait Reply

  sealed trait ReplyCreateThread                                  extends Reply
  final case class ReplyCreateThreadSucceeded(threadId: ThreadId) extends ReplyCreateThread
  final case class ReplyCreateThreadFailed(error: String)         extends ReplyCreateThread

  sealed trait ReplyDeleteThread                                  extends Reply
  final case class ReplyDeleteThreadSucceeded(threadId: ThreadId) extends ReplyDeleteThread
  final case class ReplyDeleteThreadFailed(error: String)         extends ReplyDeleteThread

  sealed trait ReplyAddMembers                                  extends Reply
  final case class ReplyAddMembersSucceeded(threadId: ThreadId) extends ReplyAddMembers
  final case class ReplyAddMembersFailed(error: String)         extends ReplyAddMembers

  sealed trait Command

  final case class CommandCreateThread(
      threadId: ThreadId,
      threadName: ThreadName,
      creatorId: AccountId,
      replyTo: ActorRef[ReplyCreateThread]
  ) extends Command

  final case class CommandDeleteThread(
      threadId: ThreadId,
      replyTo: ActorRef[ReplyDeleteThread]
  ) extends Command

  final case class CommandAddMembers(
      threadId: ThreadId,
      members: Members,
      replyTo: ActorRef[ReplyAddMembers]
  ) extends Command

  sealed trait Event

  final case class EventThreadCreated(
      threadId: ThreadId,
      threadName: ThreadName,
      creatorId: AccountId
  ) extends Event

  final case class EventThreadDeleted(
      threadId: ThreadId
  ) extends Event

  final case class EventMembersAdded(
      threadId: ThreadId,
      members: Members
  ) extends Event

  sealed trait State extends StateBase[Command, Event, State]

  case object EmptyState extends State {

    override protected def applyCommandPartial: PartialFunction[Command, CommandEffect] = {
      case command @ CommandCreateThread(threadId, threadName, creatorId, replyTo) =>
        if (Thread.canCreate(threadId, threadName, creatorId))
          Effect
            .persist(EventThreadCreated(threadId, threadName, creatorId))
            .thenReply(replyTo) { _ =>
              ReplyCreateThreadSucceeded(threadId)
            }
        else
          Effect.reply(replyTo)(ReplyCreateThreadFailed(s"$command"))
    }

    override protected def applyEventPartial: PartialFunction[Event, State] = {
      case EventThreadCreated(threadId, threadName, creatorId) =>
        JustState(
          Thread(
            threadId,
            threadName,
            Members.create(creatorId)
          )
        )
    }

  }

  final case class JustState(thread: Thread) extends State {

    override protected def applyCommandPartial: PartialFunction[Command, CommandEffect] = {
      case command @ CommandDeleteThread(threadId, replyTo) if thread.id == threadId =>
        if (thread.canDelete(threadId))
          Effect
            .persist(EventThreadDeleted(threadId))
            .thenReply(replyTo) { _ =>
              ReplyDeleteThreadSucceeded(threadId)
            }
        else
          Effect.reply(replyTo)(ReplyDeleteThreadFailed(s"$command"))

      case command @ CommandAddMembers(threadId, members, replyTo) if thread.id == threadId =>
        if (thread.canAddMembers(threadId))
          Effect
            .persist(EventMembersAdded(threadId, members))
            .thenReply(replyTo) { _ =>
              ReplyAddMembersSucceeded(threadId)
            }
        else
          Effect.reply(replyTo)(ReplyAddMembersFailed(s"$command"))

    }

    override protected def applyEventPartial: PartialFunction[Event, State] = {
      case EventThreadDeleted(threadId) if thread.id == threadId =>
        DeletedState(thread)
      case EventMembersAdded(threadId, members) if thread.id == threadId =>
        JustState(thread.addMembers(members))
    }

  }

  final case class DeletedState(thread: Thread) extends State {
    override protected def applyCommandPartial: PartialFunction[Command, CommandEffect] = PartialFunction.empty
    override protected def applyEventPartial: PartialFunction[Event, State]             = PartialFunction.empty
  }

  def apply(id: ThreadId): Behavior[Command] = AggregateGenerator[Command, Event, State](EmptyState)(id)

}
