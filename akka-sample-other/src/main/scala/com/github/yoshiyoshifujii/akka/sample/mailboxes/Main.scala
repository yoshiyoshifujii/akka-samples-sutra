package com.github.yoshiyoshifujii.akka.sample.mailboxes

import akka.actor.typed.{ ActorSystem, Behavior, MailboxSelector }
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

object Main extends App {

  object Guardian {

    def childBehavior: Behavior[AnyRef] =
      Behaviors.setup[AnyRef] { context =>
        Behaviors.empty
      }

    def apply(): Behavior[AnyRef] =
      Behaviors.setup[AnyRef] { context =>
        context.spawn(childBehavior, "bounded-mailbox-child", MailboxSelector.bounded(100))

        val props = MailboxSelector.fromConfig("my-app.my-special-mailbox")
        context.spawn(childBehavior, "from-config-mailbox-child", props)
        Behaviors.empty
      }

  }

  val system = ActorSystem(
    Guardian(),
    "mailboxes",
    ConfigFactory.parseString("""
      |my-app {
      |  my-special-mailbox {
      |    mailbox-type = "akka.dispatch.SingleConsumerOnlyUnboundedMailbox"
      |  }
      |}
      |""".stripMargin)
  )

  system.terminate()
}
