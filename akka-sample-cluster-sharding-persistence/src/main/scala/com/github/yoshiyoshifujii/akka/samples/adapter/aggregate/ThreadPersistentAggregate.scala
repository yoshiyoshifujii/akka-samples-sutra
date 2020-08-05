package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.scaladsl.{ Effect, ReplyEffect }
import com.github.yoshiyoshifujii.akka.samples.domain.model.{
  AccountId,
  Members,
  MessageBody,
  MessageId,
  Thread,
  ThreadId,
  ThreadName
}

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

  sealed trait ReplyPostMessage                                                        extends Reply
  final case class ReplyPostMessageSucceeded(threadId: ThreadId, messageId: MessageId) extends ReplyPostMessage
  final case class ReplyPostMessageFailed(error: String)                               extends ReplyPostMessage
  case object ReplyPostMessageAlreadyExists                                            extends ReplyPostMessage
  case object ReplyPostMessageAlreadyDeleted                                           extends ReplyPostMessage

  sealed trait ReplyEditMessage                                                        extends Reply
  final case class ReplyEditMessageSucceeded(threadId: ThreadId, messageId: MessageId) extends ReplyEditMessage
  final case class ReplyEditMessageFailed(error: String)                               extends ReplyEditMessage
  case object ReplyEditMessageNotFound                                                 extends ReplyEditMessage
  case object ReplyEditMessageAlreadyDeleted                                           extends ReplyEditMessage

  sealed trait ReplyDeleteMessage                                                        extends Reply
  final case class ReplyDeleteMessageSucceeded(threadId: ThreadId, messageId: MessageId) extends ReplyDeleteMessage
  final case class ReplyDeleteMessageFailed(error: String)                               extends ReplyDeleteMessage
  case object ReplyDeleteMessageNotFound                                                 extends ReplyDeleteMessage
  case object ReplyDeleteMessageAlreadyDeleted                                           extends ReplyDeleteMessage

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

  final case class CommandPostMessage(
      threadId: ThreadId,
      messageId: MessageId,
      senderId: AccountId,
      body: MessageBody,
      replyTo: ActorRef[ReplyPostMessage]
  ) extends Command

  final case class CommandEditMessage(
      threadId: ThreadId,
      messageId: MessageId,
      senderId: AccountId,
      body: MessageBody,
      replyTo: ActorRef[ReplyEditMessage]
  ) extends Command

  final case class CommandDeleteMessage(
      threadId: ThreadId,
      messageId: MessageId,
      senderId: AccountId,
      replyTo: ActorRef[ReplyDeleteMessage]
  ) extends Command

  private sealed trait InternalCommand extends Command

  private final case class InternalCommandPostMessage(
      replyTo: ActorRef[ReplyPostMessage],
      replyPostMessage: ReplyPostMessage
  ) extends InternalCommand

  private final case class InternalCommandEditMessage(
      replyTo: ActorRef[ReplyEditMessage],
      replyEditMessage: ReplyEditMessage
  ) extends InternalCommand

  private final case class InternalCommandDeleteMessage(
      replyTo: ActorRef[ReplyDeleteMessage],
      replyDeleteMessage: ReplyDeleteMessage
  ) extends InternalCommand

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

    override protected def applyEventPartial: PartialFunction[Event, State] = {
      case EventThreadDeleted(threadId) if thread.id == threadId         => DeletedState(thread)
      case EventMembersAdded(threadId, members) if thread.id == threadId => JustState(thread.addMembers(members))
    }
  }

  final case class DeletedState(thread: Thread) extends State {
    override protected def applyEventPartial: PartialFunction[Event, State] = PartialFunction.empty
  }

  type CommandEffect = ReplyEffect[Event, State]

  private def createThread(command: CommandCreateThread): CommandEffect =
    if (Thread.canCreate(command.threadId, command.threadName, command.creatorId))
      Effect
        .persist(EventThreadCreated(command.threadId, command.threadName, command.creatorId))
        .thenReply(command.replyTo) { _ =>
          ReplyCreateThreadSucceeded(command.threadId)
        }
    else
      Effect.reply(command.replyTo)(ReplyCreateThreadFailed(s"$command"))

  private def deleteThread(thread: Thread, command: CommandDeleteThread): CommandEffect =
    if (thread.canDelete(command.threadId))
      Effect
        .persist(EventThreadDeleted(command.threadId))
        .thenReply(command.replyTo) { _ =>
          ReplyDeleteThreadSucceeded(command.threadId)
        }
    else
      Effect.reply(command.replyTo)(ReplyDeleteThreadFailed(s"$command"))

  private def addMembers(thread: Thread, command: CommandAddMembers): CommandEffect =
    if (thread.canAddMembers(command.members))
      Effect
        .persist(EventMembersAdded(command.threadId, command.members))
        .thenReply(command.replyTo) { _ =>
          ReplyAddMembersSucceeded(command.threadId)
        }
    else
      Effect.reply(command.replyTo)(ReplyAddMembersFailed(s"$command"))

  private def getOrCreateChildActor(
      messageId: MessageId
  )(implicit context: ActorContext[Command]): ActorRef[MessagePersistentAggregate.Command] = {
    val childActorName = messageId.asString
    context.child(childActorName) match {
      case None      => context.spawn(MessagePersistentAggregate(messageId), childActorName)
      case Some(ref) => ref.asInstanceOf[ActorRef[MessagePersistentAggregate.Command]]
    }
  }

  private def postMessage(thread: Thread, command: CommandPostMessage)(implicit
      context: ActorContext[Command]
  ): CommandEffect =
    if (thread.canPostMessage(command.senderId)) {
      val actorRef = getOrCreateChildActor(command.messageId)
      val replyRef = context.messageAdapter[MessagePersistentAggregate.ReplyCreateMessage] {
        case MessagePersistentAggregate.ReplyCreateMessageSucceeded(repMessageId) =>
          InternalCommandPostMessage(
            command.replyTo,
            ReplyPostMessageSucceeded(command.threadId, repMessageId)
          )
        case MessagePersistentAggregate.ReplyCreateMessageFailed(error) =>
          InternalCommandPostMessage(
            command.replyTo,
            ReplyPostMessageFailed(error)
          )
        case MessagePersistentAggregate.ReplyCreateMessageAlreadyExists =>
          InternalCommandPostMessage(
            command.replyTo,
            ReplyPostMessageAlreadyExists
          )
        case MessagePersistentAggregate.ReplyCreateMessageAlreadyDeleted =>
          InternalCommandPostMessage(
            command.replyTo,
            ReplyPostMessageAlreadyDeleted
          )
      }
      actorRef ! MessagePersistentAggregate.CommandCreateMessage(
        command.messageId,
        command.threadId,
        command.senderId,
        command.body,
        replyRef
      )

      Effect.noReply
    } else {
      Effect.reply(command.replyTo)(ReplyPostMessageFailed(s"$command"))
    }

  private def editMessage(thread: Thread, command: CommandEditMessage)(implicit
      context: ActorContext[Command]
  ): CommandEffect =
    if (thread.canEditMessage(command.senderId)) {
      val actorRef = getOrCreateChildActor(command.messageId)
      val replyRef = context.messageAdapter[MessagePersistentAggregate.ReplyEditMessage] {
        case MessagePersistentAggregate.ReplyEditMessageSucceeded(repMessageId) =>
          InternalCommandEditMessage(
            command.replyTo,
            ReplyEditMessageSucceeded(command.threadId, repMessageId)
          )
        case MessagePersistentAggregate.ReplyEditMessageFailed(error) =>
          InternalCommandEditMessage(
            command.replyTo,
            ReplyEditMessageFailed(error)
          )
        case MessagePersistentAggregate.ReplyEditMessageNotFound =>
          InternalCommandEditMessage(
            command.replyTo,
            ReplyEditMessageNotFound
          )
        case MessagePersistentAggregate.ReplyEditMessageAlreadyDeleted =>
          InternalCommandEditMessage(
            command.replyTo,
            ReplyEditMessageAlreadyDeleted
          )
      }
      actorRef ! MessagePersistentAggregate.CommandEditMessage(
        command.messageId,
        command.threadId,
        command.senderId,
        command.body,
        replyRef
      )

      Effect.noReply
    } else {
      Effect.reply(command.replyTo)(ReplyEditMessageFailed(s"$command"))
    }

  private def deleteMessage(thread: Thread, command: CommandDeleteMessage)(implicit
      context: ActorContext[Command]
  ): CommandEffect =
    if (thread.canDeleteMessage(command.senderId)) {
      val actorRef = getOrCreateChildActor(command.messageId)
      val replyRef = context.messageAdapter[MessagePersistentAggregate.ReplyDeleteMessage] {
        case MessagePersistentAggregate.ReplyDeleteMessageSucceeded(repMessageId) =>
          InternalCommandDeleteMessage(
            command.replyTo,
            ReplyDeleteMessageSucceeded(command.threadId, repMessageId)
          )
        case MessagePersistentAggregate.ReplyDeleteMessageFailed(error) =>
          InternalCommandDeleteMessage(
            command.replyTo,
            ReplyDeleteMessageFailed(error)
          )
        case MessagePersistentAggregate.ReplyDeleteMessageNotFound =>
          InternalCommandDeleteMessage(
            command.replyTo,
            ReplyDeleteMessageNotFound
          )
        case MessagePersistentAggregate.ReplyDeleteMessageAlreadyDeleted =>
          InternalCommandDeleteMessage(
            command.replyTo,
            ReplyDeleteMessageAlreadyDeleted
          )
      }
      actorRef ! MessagePersistentAggregate.CommandDeleteMessage(
        command.messageId,
        command.threadId,
        command.senderId,
        replyRef
      )

      Effect.noReply
    } else {
      Effect.reply(command.replyTo)(ReplyDeleteMessageFailed(s"$command"))
    }

  private def commandHandler(implicit context: ActorContext[Command]): (State, Command) => CommandEffect =
    (state, command) =>
      state match {
        case EmptyState =>
          command match {
            case c: CommandCreateThread => createThread(c)
            case _                      => Effect.unhandled.thenNoReply()
          }
        case JustState(thread) =>
          command match {
            case d: CommandDeleteThread if thread.id == d.threadId         => deleteThread(thread, d)
            case a: CommandAddMembers if thread.id == a.threadId           => addMembers(thread, a)
            case p: CommandPostMessage if thread.id == p.threadId          => postMessage(thread, p)
            case e: CommandEditMessage if thread.id == e.threadId          => editMessage(thread, e)
            case d: CommandDeleteMessage if thread.id == d.threadId        => deleteMessage(thread, d)
            case InternalCommandPostMessage(replyTo, replyPostMessage)     => Effect.reply(replyTo)(replyPostMessage)
            case InternalCommandEditMessage(replyTo, replyEditMessage)     => Effect.reply(replyTo)(replyEditMessage)
            case InternalCommandDeleteMessage(replyTo, replyDeleteMessage) => Effect.reply(replyTo)(replyDeleteMessage)
            case _                                                         => Effect.unhandled.thenNoReply()
          }
        case _: DeletedState =>
          Effect.unhandled.thenNoReply()
      }

  def apply(id: ThreadId): Behavior[Command] =
    Behaviors.setup { implicit context =>
      AggregateActorGenerator[Command, Event, State](EmptyState)(commandHandler)(id)
    }

}
