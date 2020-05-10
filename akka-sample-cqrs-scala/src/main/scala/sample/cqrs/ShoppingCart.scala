package sample.cqrs

import java.time.Instant

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}

import scala.concurrent.duration._

object ShoppingCart {

  // State

  final case class ItemId(value: String) extends CborSerializable

  final case class Quantity(value: Int) extends CborSerializable {
    def <=(i: Quantity): Boolean = value <= i.value
  }
  object Quantity {
    val zero: Quantity = Quantity(0)
  }

  final case class State(items: Map[ItemId, Quantity], checkoutDate: Option[Instant]) extends CborSerializable {

    def isCheckOut: Boolean = checkoutDate.isDefined

    def hasItem(itemId: ItemId): Boolean = items.contains(itemId)

    def isEmpty: Boolean = items.isEmpty

    def updateItem(itemId: ItemId, quantity: Quantity): State = {
      quantity match {
        case Quantity.`zero` => copy(items = items - itemId)
        case _ => copy(items + (itemId -> quantity))
      }
    }

    def removeItem(itemId: ItemId): State = copy(items = items - itemId)

    def checkout(now: Instant): State = copy(checkoutDate = Some(now))

    def toSummary: Summary = Summary(items, isCheckOut)
  }
  object State {
    val empty: State = State(items = Map.empty, checkoutDate = None)
  }

  final case class Summary(items: Map[ItemId, Quantity], checkedOut: Boolean) extends CborSerializable

  // Command

  sealed trait Command extends CborSerializable
  final case class AddItem(itemId: ItemId, quantity: Quantity, replyTo: ActorRef[Confirmation]) extends Command
  final case class RemoveItem(itemId: ItemId, replyTo: ActorRef[Confirmation]) extends Command
  final case class AdjustItemQuantity(itemId: ItemId, quantity: Quantity, replyTo: ActorRef[Confirmation]) extends Command
  final case class Checkout(replyTo: ActorRef[Confirmation]) extends Command
  final case class Get(replyTo: ActorRef[Summary]) extends Command

  // Confirmation

  sealed trait Confirmation extends CborSerializable
  final case class Accepted(summary: Summary) extends Confirmation
  final case class Rejected(reason: String) extends Confirmation

  // Event

  final case class CartId(value: String) extends CborSerializable

  sealed trait Event extends CborSerializable {
    def cartId: CartId
  }

  final case class ItemAdded(cartId: CartId, itemId: ItemId, quantity: Quantity) extends Event
  final case class ItemRemoved(cartId: CartId, itemId: ItemId) extends Event
  final case class ItemQuantityAdjusted(cartId: CartId, itemId: ItemId, newQuantity: Quantity) extends Event
  final case class CheckedOut(cartId: CartId, eventTime: Instant) extends Event

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShoppingCart")

  private def checkedOutShoppingCart(cartId: CartId, state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)
      case cmd: AddItem =>
        Effect.reply(cmd.replyTo)(Rejected("Can't add an item an already checked out shopping cart"))
      case cmd: RemoveItem =>
        Effect.reply(cmd.replyTo)(Rejected("Can't remove an item from an already checked out shopping cart"))
      case cmd: AdjustItemQuantity =>
        Effect.reply(cmd.replyTo)(Rejected("Can't adjust item on an already checked out shopping cart"))
      case cmd: Checkout =>
        Effect.reply(cmd.replyTo)(Rejected("Can't checkout already checked out shopping cart"))
    }

  private def openShoppingCart(cartId: CartId, state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case AddItem(itemId, quantity, replyTo) =>
        if (state.hasItem(itemId))
          Effect.reply(replyTo)(Rejected(s"Item '$itemId' was already added to this shopping cart'"))
        else if (quantity <= Quantity.zero)
          Effect.reply(replyTo)(Rejected("Quantity must be than zero"))
        else
          Effect.persist(ItemAdded(cartId, itemId, quantity))
          .thenReply(replyTo)(updatedCart => Accepted(updatedCart.toSummary))

      case RemoveItem(itemId, replyTo) =>
        if (state.hasItem(itemId))
          Effect.persist(ItemRemoved(cartId, itemId))
            .thenReply(replyTo)(updatedCart => Accepted(updatedCart.toSummary))
        else
          Effect.reply(replyTo)(Accepted(state.toSummary))

      case AdjustItemQuantity(itemId, quantity, replyTo) =>
        if (quantity <= Quantity.zero)
          Effect.reply(replyTo)(Rejected("Quantity must be generator than zero"))
        else if (state.hasItem(itemId))
          Effect.persist(ItemQuantityAdjusted(cartId, itemId, quantity))
            .thenReply(replyTo)(updatedCart => Accepted(updatedCart.toSummary))
        else
          Effect.reply(replyTo)(Rejected(s"Cannot adjust quantity for item '$itemId'. Item not present on cart'"))

      case Checkout(replyTo) =>
        if (state.isEmpty)
          Effect.reply(replyTo)(Rejected("Cannot checkout an empty shopping cart"))
        else
          Effect.persist(CheckedOut(cartId, Instant.now))
            .thenReply(replyTo)(updateCart => Accepted(updateCart.toSummary))

      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)
    }

  private def handleEvent(state: State, event: Event): State =
    event match {
      case ItemAdded(_, itemId, quantity) => state.updateItem(itemId, quantity)
      case ItemRemoved(_, itemId) => state.removeItem(itemId)
      case ItemQuantityAdjusted(_, itemId, quantity) => state.updateItem(itemId, quantity)
      case CheckedOut(_, eventTime) => state.checkout(eventTime)
    }

  def apply(cartId: CartId, eventProcessorTags: Set[String]): Behavior[Command] =
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      persistenceId = PersistenceId(EntityKey.name, cartId.value),
      emptyState = State.empty,
      commandHandler = (state, command) =>
        if (state.isCheckOut) checkedOutShoppingCart(cartId, state, command)
        else openShoppingCart(cartId, state, command),
      eventHandler = (state, event) => handleEvent(state, event)
    ).withTagger(_ => eventProcessorTags)
    .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
    .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))

  def init(system: ActorSystem[_], eventProcessorSettings: EventProcessorSettings): Unit = {
    ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
      val n = math.abs(entityContext.entityId.hashCode % eventProcessorSettings.parallelism)
      val eventProcessorTag = eventProcessorSettings.tagPrefix + "-" + n
      ShoppingCart(CartId(entityContext.entityId), Set(eventProcessorTag))
    })
  }

}

