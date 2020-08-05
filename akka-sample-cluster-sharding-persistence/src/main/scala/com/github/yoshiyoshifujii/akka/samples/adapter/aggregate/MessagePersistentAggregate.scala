package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.scaladsl.Effect
import com.github.yoshiyoshifujii.akka.samples.domain.model._

object MessagePersistentAggregate {

  sealed trait Reply

  sealed trait ReplyCreateMessage                                    extends Reply
  final case class ReplyCreateMessageSucceeded(messageId: MessageId) extends ReplyCreateMessage
  final case class ReplyCreateMessageFailed(error: String)           extends ReplyCreateMessage

  sealed trait ReplyEditMessage                                    extends Reply
  final case class ReplyEditMessageSucceeded(messageId: MessageId) extends ReplyEditMessage
  final case class ReplyEditMessageFailed(error: String)           extends ReplyEditMessage

  sealed trait ReplyDeleteMessage                                    extends Reply
  final case class ReplyDeleteMessageSucceeded(messageId: MessageId) extends ReplyDeleteMessage
  final case class ReplyDeleteMessageFailed(error: String)           extends ReplyDeleteMessage

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

  sealed trait State extends StateBase[Command, Event, State]

  case object EmptyState extends State {

    override protected def applyCommandPartial: PartialFunction[Command, CommandEffect] = {
      case command @ CommandCreateMessage(messageId, threadId, senderId, body, replyTo) =>
        if (Message.canCreate(messageId))
          Effect
            .persist(EventMessageCreated(messageId, threadId, senderId, body))
            .thenReply(replyTo) { _ =>
              ReplyCreateMessageSucceeded(messageId)
            }
        else
          Effect.reply(replyTo)(ReplyCreateMessageFailed(s"$command"))
    }

    override protected def applyEventPartial: PartialFunction[Event, State] = {
      case EventMessageCreated(messageId, threadId, senderId, body) =>
        JustState(Message(messageId, threadId, senderId, body))
    }
  }

  final case class JustState(message: Message) extends State {

    override protected def applyCommandPartial: PartialFunction[Command, CommandEffect] = {
      case command @ CommandEditMessage(messageId, threadId, senderId, body, replyTo) if message.id == messageId =>
        if (message.canEdit(messageId, threadId, senderId))
          Effect
            .persist(EventMessageEdited(messageId, threadId, senderId, body))
            .thenReply(replyTo) { _ =>
              ReplyEditMessageSucceeded(messageId)
            }
        else
          Effect.reply(replyTo)(ReplyEditMessageFailed(s"$command"))
      case command @ CommandDeleteMessage(messageId, threadId, senderId, replyTo) if message.id == messageId =>
        if (message.canDelete(messageId, threadId, senderId))
          Effect
            .persist(EventMessageDeleted(messageId, threadId, senderId))
            .thenReply(replyTo) { _ =>
              ReplyDeleteMessageSucceeded(messageId)
            }
        else
          Effect.reply(replyTo)(ReplyDeleteMessageFailed(s"$command"))

    }

    override protected def applyEventPartial: PartialFunction[Event, State] = {
      case EventMessageEdited(messageId, _, _, body) if message.id == messageId =>
        JustState(message.edit(body))

      case EventMessageDeleted(messageId, _, _) if message.id == messageId =>
        DeletedState(message)
    }

  }

  final case class DeletedState(message: Message) extends State {
    override protected def applyCommandPartial: PartialFunction[Command, CommandEffect] = PartialFunction.empty
    override protected def applyEventPartial: PartialFunction[Event, State]             = PartialFunction.empty
  }

  def apply(id: MessageId): Behavior[Command] = AggregateGenerator[Command, Event, State](EmptyState)(id)

}
