package com.github.yoshiyoshifujii.akka.samples.domain.model

case class Message(
    id: MessageId,
    threadId: ThreadId,
    senderId: AccountId,
    body: MessageBody
)
