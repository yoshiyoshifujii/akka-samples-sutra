package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.scaladsl.{ Effect, ReplyEffect }
import com.github.yoshiyoshifujii.akka.samples.domain.model._

object MessagePersistentAggregate {

  sealed trait Reply

  sealed trait ReplyCreateMessage                                    extends Reply
  final case class ReplyCreateMessageSucceeded(messageId: MessageId) extends ReplyCreateMessage
  final case class ReplyCreateMessageFailed(error: String)           extends ReplyCreateMessage
  case object ReplyCreateMessageAlreadyExists                        extends ReplyCreateMessage
  case object ReplyCreateMessageAlreadyDeleted                       extends ReplyCreateMessage

  sealed trait ReplyEditMessage                                    extends Reply
  final case class ReplyEditMessageSucceeded(messageId: MessageId) extends ReplyEditMessage
  final case class ReplyEditMessageFailed(error: String)           extends ReplyEditMessage
  case object ReplyEditMessageNotFound                             extends ReplyEditMessage
  case object ReplyEditMessageAlreadyDeleted                       extends ReplyEditMessage

  sealed trait ReplyDeleteMessage                                    extends Reply
  final case class ReplyDeleteMessageSucceeded(messageId: MessageId) extends ReplyDeleteMessage
  final case class ReplyDeleteMessageFailed(error: String)           extends ReplyDeleteMessage
  case object ReplyDeleteMessageNotFound                             extends ReplyDeleteMessage
  case object ReplyDeleteMessageAlreadyDeleted                       extends ReplyDeleteMessage

  sealed trait Command

  final case class CommandCreateMessage(
      messageId: MessageId,
      threadId: ThreadId,
      senderId: AccountId,
      body: MessageBody,
      replyTo: ActorRef[ReplyCreateMessage]
  ) extends Command

  final case class CommandEditMessage(
      messageId: MessageId,
      threadId: ThreadId,
      senderId: AccountId,
      body: MessageBody,
      replyTo: ActorRef[ReplyEditMessage]
  ) extends Command

  final case class CommandDeleteMessage(
      messageId: MessageId,
      threadId: ThreadId,
      senderId: AccountId,
      replyTo: ActorRef[ReplyDeleteMessage]
  ) extends Command

  sealed trait Event

  final case class EventMessageCreated(
      messageId: MessageId,
      threadId: ThreadId,
      senderId: AccountId,
      body: MessageBody
  ) extends Event

  final case class EventMessageEdited(
      messageId: MessageId,
      threadId: ThreadId,
      senderId: AccountId,
      body: MessageBody
  ) extends Event

  final case class EventMessageDeleted(
      messageId: MessageId,
      threadId: ThreadId,
      senderId: AccountId
  ) extends Event

  sealed trait State extends BaseState[Command, Event, State]

  case object EmptyState extends State {

    override protected def applyEventPartial: PartialFunction[Event, State] = {
      case EventMessageCreated(messageId, threadId, senderId, body) =>
        JustState(Message(messageId, threadId, senderId, body))
    }
  }

  final case class JustState(message: Message) extends State {

    override protected def applyEventPartial: PartialFunction[Event, State] = {
      case EventMessageEdited(messageId, _, _, body) if message.id == messageId =>
        JustState(message.edit(body))

      case EventMessageDeleted(messageId, _, _) if message.id == messageId =>
        DeletedState(message)
    }
  }

  final case class DeletedState(message: Message) extends State {
    override protected def applyEventPartial: PartialFunction[Event, State] = PartialFunction.empty
  }

  type CommandEffect = ReplyEffect[Event, State]

  private def deleteMessage(message: Message, command: CommandDeleteMessage): CommandEffect =
    if (message.canDelete(command.messageId, command.threadId, command.senderId))
      Effect
        .persist(EventMessageDeleted(command.messageId, command.threadId, command.senderId))
        .thenReply(command.replyTo) { _ =>
          ReplyDeleteMessageSucceeded(command.messageId)
        }
    else
      Effect.reply(command.replyTo)(ReplyDeleteMessageFailed(s"$command"))

  private def editMessage(message: Message, command: CommandEditMessage): CommandEffect =
    if (message.canEdit(command.messageId, command.threadId, command.senderId))
      Effect
        .persist(EventMessageEdited(command.messageId, command.threadId, command.senderId, command.body))
        .thenReply(command.replyTo) { _ =>
          ReplyEditMessageSucceeded(command.messageId)
        }
    else
      Effect.reply(command.replyTo)(ReplyEditMessageFailed(s"$command"))

  private def createMessage(command: CommandCreateMessage): CommandEffect =
    if (Message.canCreate(command.messageId))
      Effect
        .persist(EventMessageCreated(command.messageId, command.threadId, command.senderId, command.body))
        .thenReply(command.replyTo) { _ =>
          ReplyCreateMessageSucceeded(command.messageId)
        }
    else
      Effect.reply(command.replyTo)(ReplyCreateMessageFailed(s"$command"))

  def apply(id: MessageId): Behavior[Command] =
    Behaviors.setup { implicit context =>
      AggregateActorGenerator[Command, Event, State](id, EmptyState) { (state, command) =>
        state match {
          case EmptyState =>
            command match {
              case c: CommandCreateMessage => createMessage(c)
              case e: CommandEditMessage   => Effect.reply(e.replyTo)(ReplyEditMessageNotFound)
              case d: CommandDeleteMessage => Effect.reply(d.replyTo)(ReplyDeleteMessageNotFound)
              case _                       => throwIllegalStateException[Command, Event, State, CommandEffect](state, command)
            }
          case JustState(message) =>
            command match {
              case c: CommandCreateMessage                              => Effect.reply(c.replyTo)(ReplyCreateMessageAlreadyExists)
              case e: CommandEditMessage if message.id == e.messageId   => editMessage(message, e)
              case d: CommandDeleteMessage if message.id == d.messageId => deleteMessage(message, d)
              case _                                                    => throwIllegalStateException[Command, Event, State, CommandEffect](state, command)
            }
          case _: DeletedState =>
            command match {
              case c: CommandCreateMessage => Effect.reply(c.replyTo)(ReplyCreateMessageAlreadyDeleted)
              case e: CommandEditMessage   => Effect.reply(e.replyTo)(ReplyEditMessageAlreadyDeleted)
              case d: CommandDeleteMessage => Effect.reply(d.replyTo)(ReplyDeleteMessageAlreadyDeleted)
              case _                       => throwIllegalStateException[Command, Event, State, CommandEffect](state, command)
            }
        }
      }
    }

}
