package sample.cqrs

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import sample.cqrs.ShoppingCart._

class ShoppingCartSpec extends ScalaTestWithActorTestKit(
  s"""
     |akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
     |akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
     |akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
     |""".stripMargin) with AnyWordSpecLike {

  private var counter = 0
  def newCartId(): CartId = {
    counter += 1
    CartId(s"cart=$counter")
  }

  "The Shopping Cart" should {

    "add item" in {
      val cart = testKit.spawn(ShoppingCart(newCartId(), Set.empty))
      val probe = testKit.createTestProbe[Confirmation]
      val itemId = ItemId("foo")
      val quantity = Quantity(42)
      cart ! AddItem(itemId, quantity, probe.ref)
      probe.expectMessage(Accepted(Summary(Map(itemId -> quantity), checkedOut = false)))
    }

    "reject already added item" in {
      val cart = testKit.spawn(ShoppingCart(newCartId(), Set.empty))
      val probe = testKit.createTestProbe[Confirmation]
      val itemId = ItemId("foo")
      val quantity = Quantity(42)
      cart ! AddItem(itemId, quantity, probe.ref)
      probe.expectMessageType[Accepted]
      cart ! AddItem(itemId, quantity, probe.ref)
      probe.expectMessageType[Rejected]
    }

    "remove item" in {
      val cart = testKit.spawn(ShoppingCart(newCartId(), Set.empty))
      val probe = testKit.createTestProbe[Confirmation]
      val itemId = ItemId("foo")
      val quantity = Quantity(42)
      cart ! AddItem(itemId, quantity, probe.ref)
      probe.expectMessageType[Accepted]
      cart ! RemoveItem(itemId, probe.ref)
      probe.expectMessage(Accepted(Summary(Map.empty, checkedOut = false)))
    }

    "adjust quantity" in {
      val cart = testKit.spawn(ShoppingCart(newCartId(), Set.empty))
      val probe = testKit.createTestProbe[Confirmation]
      val itemId = ItemId("foo")
      val quantity = Quantity(42)
      cart ! AddItem(itemId, quantity, probe.ref)
      probe.expectMessageType[Accepted]
      cart ! AdjustItemQuantity(itemId, quantity, probe.ref)
      probe.expectMessage(Accepted(Summary(Map(itemId -> quantity), checkedOut = false)))
    }

    "checkout" in {
      val cart = testKit.spawn(ShoppingCart(newCartId(), Set.empty))
      val probe = testKit.createTestProbe[Confirmation]
      val itemId = ItemId("foo")
      val quantity = Quantity(42)
      cart ! AddItem(itemId, quantity, probe.ref)
      probe.expectMessageType[Accepted]
      cart ! Checkout(probe.ref)
      probe.expectMessage(Accepted(Summary(Map(itemId -> quantity), checkedOut = true)))

      cart ! AddItem(ItemId("bar"), Quantity(13), probe.ref)
      probe.expectMessageType[Rejected]
    }

    "keep its state" in {
      val cartId = newCartId()
      val cart = testKit.spawn(ShoppingCart(cartId, Set.empty))
      val probe = testKit.createTestProbe[Confirmation]
      val itemId = ItemId("foo")
      val quantity = Quantity(42)
      cart ! AddItem(itemId, quantity, probe.ref)
      probe.expectMessage(Accepted(Summary(Map(itemId -> quantity), checkedOut = false)))

      testKit.stop(cart)

      val restartedCart = testKit.spawn(ShoppingCart(cartId, Set.empty))
      val stateProbe = testKit.createTestProbe[Summary]
      restartedCart ! Get(stateProbe.ref)
      stateProbe.expectMessage(Summary(Map(itemId -> quantity), checkedOut = false))
    }
  }

}
