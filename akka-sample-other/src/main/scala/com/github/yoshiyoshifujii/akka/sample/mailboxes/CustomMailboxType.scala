package com.github.yoshiyoshifujii.akka.sample.mailboxes

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.{ ActorRef, ActorSystem }
import akka.dispatch.{ Envelope, MailboxType, MessageQueue, ProducesMessageQueue }
import com.typesafe.config.Config

object CustomMailboxType extends App {

  trait MyUnboundedMessageQueueSemantics

  object MyUnboundedMailbox {

    class MyMessageQueue extends MessageQueue with MyUnboundedMessageQueueSemantics {

      private final val queue = new ConcurrentLinkedQueue[Envelope]()

      override def enqueue(receiver: ActorRef, handle: Envelope): Unit =
        queue.offer(handle)

      override def dequeue(): Envelope = queue.poll()

      override def numberOfMessages: Int = queue.size

      override def hasMessages: Boolean = !queue.isEmpty

      override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit =
        while (hasMessages) {
          deadLetters.enqueue(owner, dequeue())
        }
    }
  }

  class MyUnboundedMailbox extends MailboxType with ProducesMessageQueue[MyUnboundedMailbox.MyMessageQueue] {

    import MyUnboundedMailbox._

    def this(settings: ActorSystem.Settings, config: Config) = this()

    override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new MyMessageQueue()
  }

}
