package com.github.yoshiyoshifujii.akka.sample.persistence.styleGuide

import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import com.github.yoshiyoshifujii.akka.sample.persistence.serialization.CborSerializable

object AccountEntity2 {

  // Reply
  sealed trait CommandReply                            extends CborSerializable
  sealed trait OperationResult                         extends CommandReply
  case object Confirmed                                extends OperationResult
  final case class Rejected(reason: String)            extends OperationResult
  final case class CurrentBalance(balance: BigDecimal) extends CommandReply

  // Command
  sealed trait Command                                                              extends CborSerializable
  final case class CreateAccount(replyTo: ActorRef[OperationResult])                extends Command
  final case class Deposit(amount: BigDecimal, replyTo: ActorRef[OperationResult])  extends Command
  final case class Withdraw(amount: BigDecimal, replyTo: ActorRef[OperationResult]) extends Command
  final case class GetBalance(replyTo: ActorRef[CurrentBalance])                    extends Command
  final case class CloseAccount(replyTo: ActorRef[OperationResult])                 extends Command

  // Event
  sealed trait Event                       extends CborSerializable
  case object AccountCreated               extends Event
  case class Deposited(amount: BigDecimal) extends Event
  case class Withdrawn(amount: BigDecimal) extends Event
  case object AccountClosed                extends Event

  val Zero = BigDecimal(0)

  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[Event, Account]

  // State
  sealed trait Account extends CborSerializable {
    def applyCommand(cmd: Command): ReplyEffect
    def applyEvent(event: Event): Account
  }

  case object EmptyAccount extends Account {

    override def applyCommand(cmd: Command): ReplyEffect =
      cmd match {
        case c: CreateAccount => Effect.persist(AccountCreated).thenReply(c.replyTo)(_ => Confirmed)
        case _                => Effect.unhandled.thenNoReply()
      }

    override def applyEvent(event: Event): Account =
      event match {
        case AccountCreated => OpenedAccount(Zero)
        case _              => throw new IllegalStateException(s"unexpected event [$event] in state [EmptyAccount]")
      }
  }

  case class OpenedAccount(balance: BigDecimal) extends Account {
    require(balance >= Zero, "Account balance can't be negative")

    override def applyCommand(cmd: Command): ReplyEffect =
      cmd match {
        case Deposit(amount, replyTo) =>
          Effect.persist(Deposited(amount)).thenReply(replyTo)(_ => Confirmed)

        case Withdraw(amount, replyTo) =>
          if (canWithdraw(amount))
            Effect.persist(Withdrawn(amount)).thenReply(replyTo)(_ => Confirmed)
          else
            Effect.reply(replyTo)(Rejected(s"Insufficient balance $balance to be able to withdraw $amount"))

        case GetBalance(replyTo) =>
          Effect.reply(replyTo)(CurrentBalance(balance))

        case CloseAccount(replyTo) =>
          if (balance == Zero)
            Effect.persist(AccountClosed).thenReply(replyTo)(_ => Confirmed)
          else
            Effect.reply(replyTo)(Rejected("Can't close account with non-zero balance"))

        case c: CreateAccount =>
          Effect.reply(c.replyTo)(Rejected("Account is already created"))
      }

    override def applyEvent(event: Event): Account =
      event match {
        case Deposited(amount) => this.copy(balance = balance + amount)
        case Withdrawn(amount) => this.copy(balance = balance - amount)
        case AccountClosed     => ClosedAccount
        case AccountCreated    => throw new IllegalStateException(s"unexpected event [$event] in state [OpenedAccount]")
      }

    def canWithdraw(amount: BigDecimal): Boolean = balance - amount > Zero
  }

  case object ClosedAccount extends Account {

    private def replyClosed(replyTo: ActorRef[OperationResult]): ReplyEffect =
      Effect.reply(replyTo)(Rejected(s"Account is closed"))

    override def applyCommand(cmd: Command): ReplyEffect =
      cmd match {
        case c: Deposit             => replyClosed(c.replyTo)
        case c: Withdraw            => replyClosed(c.replyTo)
        case GetBalance(replyTo)    => Effect.reply(replyTo)(CurrentBalance(Zero))
        case CloseAccount(replyTo)  => replyClosed(replyTo)
        case CreateAccount(replyTo) => replyClosed(replyTo)
      }

    override def applyEvent(event: Event): Account =
      throw new IllegalStateException(s"unexpected event [$event] in state [ClosedAccount]")
  }

  //  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Account")

  def apply(accountNumber: String, persistenceId: PersistenceId): Behavior[Command] =
    EventSourcedBehavior.withEnforcedReplies[Command, Event, Account](
      persistenceId,
      EmptyAccount,
      (state, cmd) => state.applyCommand(cmd),
      (state, event) => state.applyEvent(event)
    )
}
