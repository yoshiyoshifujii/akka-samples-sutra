package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, ReplyEffect }
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

  private sealed trait InternalCommand extends Command

  private final case class InternalCommandPostMessage(
      replyTo: ActorRef[ReplyPostMessage],
      replyPostMessage: ReplyPostMessage
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

  private def postMessage(thread: Thread, command: CommandPostMessage)(implicit
      context: ActorContext[Command]
  ): CommandEffect =
    if (thread.canPostMessage(command.senderId)) {
      val childActorName = command.messageId.asString
      val actorRef = context.child(childActorName) match {
        case None      => context.spawn(MessagePersistentAggregate(command.messageId), childActorName)
        case Some(ref) => ref.asInstanceOf[ActorRef[MessagePersistentAggregate.Command]]
      }
      val replyRef: ActorRef[MessagePersistentAggregate.ReplyCreateMessage] =
        context.messageAdapter[MessagePersistentAggregate.ReplyCreateMessage] {
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

  private def addMembers(thread: Thread, command: CommandAddMembers): CommandEffect =
    if (thread.canAddMembers(command.members))
      Effect
        .persist(EventMembersAdded(command.threadId, command.members))
        .thenReply(command.replyTo) { _ =>
          ReplyAddMembersSucceeded(command.threadId)
        }
    else
      Effect.reply(command.replyTo)(ReplyAddMembersFailed(s"$command"))

  private def deleteThread(thread: Thread, command: CommandDeleteThread): CommandEffect =
    if (thread.canDelete(command.threadId))
      Effect
        .persist(EventThreadDeleted(command.threadId))
        .thenReply(command.replyTo) { _ =>
          ReplyDeleteThreadSucceeded(command.threadId)
        }
    else
      Effect.reply(command.replyTo)(ReplyDeleteThreadFailed(s"$command"))

  private def createThread(command: CommandCreateThread): CommandEffect =
    if (Thread.canCreate(command.threadId, command.threadName, command.creatorId))
      Effect
        .persist(EventThreadCreated(command.threadId, command.threadName, command.creatorId))
        .thenReply(command.replyTo) { _ =>
          ReplyCreateThreadSucceeded(command.threadId)
        }
    else
      Effect.reply(command.replyTo)(ReplyCreateThreadFailed(s"$command"))

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
            case d: CommandDeleteThread if thread.id == d.threadId     => deleteThread(thread, d)
            case a: CommandAddMembers if thread.id == a.threadId       => addMembers(thread, a)
            case p: CommandPostMessage if thread.id == p.threadId      => postMessage(thread, p)
            case InternalCommandPostMessage(replyTo, replyPostMessage) => Effect.reply(replyTo)(replyPostMessage)
            case _                                                     => Effect.unhandled.thenNoReply()
          }
        case _: DeletedState =>
          Effect.unhandled.thenNoReply()
      }

  def apply(id: ThreadId): Behavior[Command] =
    Behaviors.setup { implicit context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.of(id.modelName, id.asString, "-"),
        emptyState = EmptyState,
        commandHandler,
        (state, event) => state.applyEvent(event)
      )
    }

}
