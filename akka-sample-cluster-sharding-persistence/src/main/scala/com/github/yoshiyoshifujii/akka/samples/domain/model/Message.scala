package com.github.yoshiyoshifujii.akka.samples.domain.model

case class Message(
    id: MessageId,
    threadId: ThreadId,
    senderId: AccountId,
    body: MessageBody
) {

  def canEdit(messageId: MessageId, threadId: ThreadId, senderId: AccountId): Boolean =
    this.id == messageId && this.threadId == threadId && this.senderId == senderId

  def canDelete(messageId: MessageId, threadId: ThreadId, senderId: AccountId): Boolean =
    this.id == messageId && this.threadId == threadId && this.senderId == senderId

  def edit(body: MessageBody): Message = this.copy(body = body)

}

object Message {
  def canCreate(messageId: MessageId): Boolean = true
}
