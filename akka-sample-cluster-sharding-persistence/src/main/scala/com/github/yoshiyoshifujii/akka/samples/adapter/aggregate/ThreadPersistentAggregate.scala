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

  type AggregateRootId = ThreadId

  sealed trait Reply

  sealed trait ReplyCreateThread                                         extends Reply
  final case class ReplyCreateThreadSucceeded(threadId: AggregateRootId) extends ReplyCreateThread
  final case class ReplyCreateThreadFailed(error: String)                extends ReplyCreateThread

  sealed trait ReplyDeleteThread                                         extends Reply
  final case class ReplyDeleteThreadSucceeded(threadId: AggregateRootId) extends ReplyDeleteThread
  final case class ReplyDeleteThreadFailed(error: String)                extends ReplyDeleteThread

  sealed trait ReplyAddMembers                                         extends Reply
  final case class ReplyAddMembersSucceeded(threadId: AggregateRootId) extends ReplyAddMembers
  final case class ReplyAddMembersFailed(error: String)                extends ReplyAddMembers

  sealed trait ReplyPostMessage                                                               extends Reply
  final case class ReplyPostMessageSucceeded(threadId: AggregateRootId, messageId: MessageId) extends ReplyPostMessage
  final case class ReplyPostMessageFailed(error: String)                                      extends ReplyPostMessage
  case object ReplyPostMessageAlreadyExists                                                   extends ReplyPostMessage
  case object ReplyPostMessageAlreadyDeleted                                                  extends ReplyPostMessage

  sealed trait ReplyEditMessage                                                               extends Reply
  final case class ReplyEditMessageSucceeded(threadId: AggregateRootId, messageId: MessageId) extends ReplyEditMessage
  final case class ReplyEditMessageFailed(error: String)                                      extends ReplyEditMessage
  case object ReplyEditMessageNotFound                                                        extends ReplyEditMessage
  case object ReplyEditMessageAlreadyDeleted                                                  extends ReplyEditMessage

  sealed trait ReplyDeleteMessage extends Reply

  final case class ReplyDeleteMessageSucceeded(threadId: AggregateRootId, messageId: MessageId)
      extends ReplyDeleteMessage
  final case class ReplyDeleteMessageFailed(error: String) extends ReplyDeleteMessage
  case object ReplyDeleteMessageNotFound                   extends ReplyDeleteMessage
  case object ReplyDeleteMessageAlreadyDeleted             extends ReplyDeleteMessage

  sealed trait Command extends BaseCommand[AggregateRootId]

  case object Idle extends BaseIdle[AggregateRootId] with Command
  case object Stop extends BaseStop[AggregateRootId] with Command

  final case class CommandCreateThread(
      id: AggregateRootId,
      threadName: ThreadName,
      creatorId: AccountId,
      replyTo: ActorRef[ReplyCreateThread]
  ) extends Command

  final case class CommandDeleteThread(
      id: AggregateRootId,
      replyTo: ActorRef[ReplyDeleteThread]
  ) extends Command

  final case class CommandAddMembers(
      id: AggregateRootId,
      members: Members,
      replyTo: ActorRef[ReplyAddMembers]
  ) extends Command

  final case class CommandPostMessage(
      id: AggregateRootId,
      messageId: MessageId,
      senderId: AccountId,
      body: MessageBody,
      replyTo: ActorRef[ReplyPostMessage]
  ) extends Command

  final case class CommandEditMessage(
      id: AggregateRootId,
      messageId: MessageId,
      senderId: AccountId,
      body: MessageBody,
      replyTo: ActorRef[ReplyEditMessage]
  ) extends Command

  final case class CommandDeleteMessage(
      id: AggregateRootId,
      messageId: MessageId,
      senderId: AccountId,
      replyTo: ActorRef[ReplyDeleteMessage]
  ) extends Command

  private sealed trait InternalCommand extends Command with UnsupportedId[AggregateRootId]

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
      threadId: AggregateRootId,
      threadName: ThreadName,
      creatorId: AccountId
  ) extends Event

  final case class EventThreadDeleted(
      threadId: AggregateRootId
  ) extends Event

  final case class EventMembersAdded(
      threadId: AggregateRootId,
      members: Members
  ) extends Event

  sealed trait State extends BaseState[Command, Event, State]

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
    if (Thread.canCreate(command.id, command.threadName, command.creatorId))
      Effect
        .persist(EventThreadCreated(command.id, command.threadName, command.creatorId))
        .thenReply(command.replyTo) { _ =>
          ReplyCreateThreadSucceeded(command.id)
        }
    else
      Effect.reply(command.replyTo)(ReplyCreateThreadFailed(s"$command"))

  private def deleteThread(thread: Thread, command: CommandDeleteThread): CommandEffect =
    if (thread.canDelete(command.id))
      Effect
        .persist(EventThreadDeleted(command.id))
        .thenReply(command.replyTo) { _ =>
          ReplyDeleteThreadSucceeded(command.id)
        }
    else
      Effect.reply(command.replyTo)(ReplyDeleteThreadFailed(s"$command"))

  private def addMembers(thread: Thread, command: CommandAddMembers): CommandEffect =
    if (thread.canAddMembers(command.members))
      Effect
        .persist(EventMembersAdded(command.id, command.members))
        .thenReply(command.replyTo) { _ =>
          ReplyAddMembersSucceeded(command.id)
        }
    else
      Effect.reply(command.replyTo)(ReplyAddMembersFailed(s"$command"))

  private def getOrCreateChildActor(
      messageId: MessageId,
      childActorNameF: MessageId => String,
      childBehaviorF: MessageId => Behavior[MessagePersistentAggregate.Command]
  )(implicit context: ActorContext[Command]): ActorRef[MessagePersistentAggregate.Command] = {
    val childActorName = childActorNameF(messageId)
    context.child(childActorName) match {
      case None =>
        context.log.debug(s"Child $messageId => Behavior[[MessagePersistentAggregate.Command]] spawn.")
        context.spawn(childBehaviorF(messageId), childActorName)
      case Some(ref) => ref.asInstanceOf[ActorRef[MessagePersistentAggregate.Command]]
    }
  }

  private def generateReplyCreateMessageRef(
      command: CommandPostMessage
  )(implicit context: ActorContext[Command]): ActorRef[MessagePersistentAggregate.ReplyCreateMessage] =
    context.messageAdapter[MessagePersistentAggregate.ReplyCreateMessage] {
      case MessagePersistentAggregate.ReplyCreateMessageSucceeded(repMessageId) =>
        InternalCommandPostMessage(
          command.replyTo,
          ReplyPostMessageSucceeded(command.id, repMessageId)
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

  private def generateReplyEditMessageRef(
      command: CommandEditMessage
  )(implicit context: ActorContext[Command]): ActorRef[MessagePersistentAggregate.ReplyEditMessage] =
    context.messageAdapter[MessagePersistentAggregate.ReplyEditMessage] {
      case MessagePersistentAggregate.ReplyEditMessageSucceeded(repMessageId) =>
        InternalCommandEditMessage(
          command.replyTo,
          ReplyEditMessageSucceeded(command.id, repMessageId)
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

  private def generateReplyDeleteMessageRef(
      command: CommandDeleteMessage
  )(implicit context: ActorContext[Command]): ActorRef[MessagePersistentAggregate.ReplyDeleteMessage] =
    context.messageAdapter[MessagePersistentAggregate.ReplyDeleteMessage] {
      case MessagePersistentAggregate.ReplyDeleteMessageSucceeded(repMessageId) =>
        InternalCommandDeleteMessage(
          command.replyTo,
          ReplyDeleteMessageSucceeded(command.id, repMessageId)
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

  private def postMessage(
      thread: Thread,
      command: CommandPostMessage,
      actorRef: ActorRef[MessagePersistentAggregate.Command],
      replyRef: ActorRef[MessagePersistentAggregate.ReplyCreateMessage]
  ): CommandEffect =
    if (thread.canPostMessage(command.senderId)) {
      actorRef ! MessagePersistentAggregate.CommandCreateMessage(
        command.messageId,
        command.id,
        command.senderId,
        command.body,
        replyRef
      )

      Effect.noReply
    } else {
      Effect.reply(command.replyTo)(ReplyPostMessageFailed(s"$command"))
    }

  private def editMessage(
      thread: Thread,
      command: CommandEditMessage,
      actorRef: ActorRef[MessagePersistentAggregate.Command],
      replyRef: ActorRef[MessagePersistentAggregate.ReplyEditMessage]
  ): CommandEffect =
    if (thread.canEditMessage(command.senderId)) {
      actorRef ! MessagePersistentAggregate.CommandEditMessage(
        command.messageId,
        command.id,
        command.senderId,
        command.body,
        replyRef
      )

      Effect.noReply
    } else {
      Effect.reply(command.replyTo)(ReplyEditMessageFailed(s"$command"))
    }

  private def deleteMessage(
      thread: Thread,
      command: CommandDeleteMessage,
      actorRef: ActorRef[MessagePersistentAggregate.Command],
      replyRef: ActorRef[MessagePersistentAggregate.ReplyDeleteMessage]
  ): CommandEffect =
    if (thread.canDeleteMessage(command.senderId)) {
      actorRef ! MessagePersistentAggregate.CommandDeleteMessage(
        command.messageId,
        command.id,
        command.senderId,
        replyRef
      )

      Effect.noReply
    } else {
      Effect.reply(command.replyTo)(ReplyDeleteMessageFailed(s"$command"))
    }

  def apply(
      id: AggregateRootId,
      childActorNameF: MessageId => String,
      childBehaviorF: MessageId => Behavior[MessagePersistentAggregate.Command]
  ): Behavior[Command] =
    Behaviors.setup { implicit context =>
      AggregateActorGenerator[Command, Event, State](id, EmptyState) { (state, command) =>
        state match {
          case EmptyState =>
            command match {
              case c: CommandCreateThread => createThread(c)
              case _                      => throwIllegalStateException[Command, Event, State, CommandEffect](state, command)
            }
          case JustState(thread) =>
            command match {
              case d: CommandDeleteThread if thread.id == d.id => deleteThread(thread, d)
              case a: CommandAddMembers if thread.id == a.id   => addMembers(thread, a)
              case p: CommandPostMessage if thread.id == p.id =>
                postMessage(
                  thread,
                  p,
                  getOrCreateChildActor(p.messageId, childActorNameF, childBehaviorF),
                  generateReplyCreateMessageRef(p)
                )
              case e: CommandEditMessage if thread.id == e.id =>
                editMessage(
                  thread,
                  e,
                  getOrCreateChildActor(e.messageId, childActorNameF, childBehaviorF),
                  generateReplyEditMessageRef(e)
                )
              case d: CommandDeleteMessage if thread.id == d.id =>
                deleteMessage(
                  thread,
                  d,
                  getOrCreateChildActor(d.messageId, childActorNameF, childBehaviorF),
                  generateReplyDeleteMessageRef(d)
                )
              case InternalCommandPostMessage(replyTo, replyPostMessage) => Effect.reply(replyTo)(replyPostMessage)
              case InternalCommandEditMessage(replyTo, replyEditMessage) => Effect.reply(replyTo)(replyEditMessage)
              case InternalCommandDeleteMessage(replyTo, replyDeleteMessage) =>
                Effect.reply(replyTo)(replyDeleteMessage)
            }
          case _: DeletedState => throwIllegalStateException[Command, Event, State, CommandEffect](state, command)
        }
      }
    }

}
