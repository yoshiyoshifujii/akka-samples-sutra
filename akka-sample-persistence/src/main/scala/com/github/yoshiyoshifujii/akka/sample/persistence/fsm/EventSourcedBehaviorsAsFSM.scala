package com.github.yoshiyoshifujii.akka.sample.persistence.fsm

import akka.actor.FSM.StateTimeout
import akka.actor.typed.scaladsl.{ Behaviors, TimerScheduler }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import com.github.yoshiyoshifujii.akka.sample.persistence.serialization.CborSerializable

import scala.concurrent.duration._

object EventSourcedBehaviorsAsFSM {

  case class Item()

  case class ShoppingCart(items: Vector[Item]) {
    def addItem(item: Item): ShoppingCart = this.copy(items = items :+ item)
  }

  object ShoppingCart {
    val empty: ShoppingCart = ShoppingCart(Vector.empty)
  }

  sealed trait Command                                             extends CborSerializable
  final case class AddItem(item: Item)                             extends Command
  case object Buy                                                  extends Command
  case object Leave                                                extends Command
  final case class GetCurrentCart(replyTo: ActorRef[ShoppingCart]) extends Command
  private case object Timeout                                      extends Command

  sealed trait Event                     extends CborSerializable
  final case class ItemAdded(item: Item) extends Event
  case object OrderExecuted              extends Event
  case object OrderDiscarded             extends Event
  private case object CustomerInactive   extends Event

  sealed trait State extends CborSerializable {
    def applyCommand(cmd: Command)(implicit timers: TimerScheduler[Command]): Effect[Event, State]
    def applyEvent(event: Event): State
  }

  object State {
    def empty: State = LookingAround(ShoppingCart.empty)
  }

  final case class LookingAround(cart: ShoppingCart) extends State {

    override def applyCommand(cmd: Command)(implicit timers: TimerScheduler[Command]): Effect[Event, State] =
      cmd match {
        case AddItem(item) =>
          Effect.persist(ItemAdded(item)).thenRun(_ => timers.startSingleTimer(StateTimeout, Timeout, 1.second))

        case GetCurrentCart(replyTo) =>
          replyTo ! cart
          Effect.none

        case _ =>
          Effect.none
      }

    override def applyEvent(event: Event): State =
      event match {
        case ItemAdded(item) => Shopping(cart.addItem(item))
        case _               => this
      }
  }

  final case class Shopping(cart: ShoppingCart) extends State {

    override def applyCommand(cmd: Command)(implicit timers: TimerScheduler[Command]): Effect[Event, State] =
      cmd match {
        case AddItem(item) =>
          Effect.persist(ItemAdded(item)).thenRun(_ => timers.startSingleTimer(StateTimeout, Timeout, 1.second))

        case Buy =>
          Effect.persist(OrderExecuted).thenRun(_ => timers.cancel(StateTimeout))

        case Leave =>
          Effect.persist(OrderDiscarded).thenStop()

        case GetCurrentCart(replyTo) =>
          replyTo ! cart
          Effect.none

        case Timeout =>
          Effect.persist(CustomerInactive)

        case _ =>
          Effect.none
      }

    override def applyEvent(event: Event): State =
      event match {
        case ItemAdded(item)  => Shopping(cart.addItem(item))
        case OrderExecuted    => Paid(cart)
        case OrderDiscarded   => this // will be stopped
        case CustomerInactive => Inactive(cart)
        case _                => this
      }
  }

  final case class Inactive(cart: ShoppingCart) extends State {

    override def applyCommand(cmd: Command)(implicit timers: TimerScheduler[Command]): Effect[Event, State] =
      cmd match {
        case AddItem(item) =>
          Effect.persist(ItemAdded(item)).thenRun(_ => timers.startSingleTimer(StateTimeout, Timeout, 1.second))

        case Timeout =>
          Effect.persist(OrderDiscarded)

        case _ =>
          Effect.none
      }

    override def applyEvent(event: Event): State =
      event match {
        case ItemAdded(item) => Shopping(cart.addItem(item))
        case OrderDiscarded  => this // will be stopped
        case _               => this
      }
  }

  final case class Paid(cart: ShoppingCart) extends State {

    override def applyCommand(cmd: Command)(implicit timers: TimerScheduler[Command]): Effect[Event, State] =
      cmd match {
        case Leave =>
          Effect.stop()

        case GetCurrentCart(replyTo) =>
          replyTo ! cart
          Effect.none

        case _ =>
          Effect.none
      }

    override def applyEvent(event: Event): State = this // no events after paid

  }

  def apply(persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.withTimers { implicit timers =>
        EventSourcedBehavior[Command, Event, State](
          persistenceId,
          emptyState = State.empty,
          commandHandler = (state, command) => state.applyCommand(command),
          eventHandler = (state, event) => state.applyEvent(event)
        )
      }
    }

}
