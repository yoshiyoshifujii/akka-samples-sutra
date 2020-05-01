package sample.cqrs

import java.io.File

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.eventstream.EventStream
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.scalatest.wordspec.AnyWordSpecLike
import sample.cqrs.ShoppingCart.{Accepted, AddItem, CartId, Confirmation, Event, ItemAdded, ItemId, Quantity}

class EventProcessorSpec extends ScalaTestWithActorTestKit(ConfigFactory.parseString(
  s"""
     |akka.actor.provider = local
     |
     |akka.persistence.cassandra {
     |  events-by-tag {
     |    eventual-consistency-delay = 200ms
     |  }
     |
     |  query {
     |    refresh-interval = 500ms
     |  }
     |
     |  journal.keyspace-autocreate = on
     |  journal.tables-autocreate = on
     |  snapshot.keyspace-autocreate = on
     |  snapshot.tables-autocreate = on
     |}
     |datastax-java-driver {
     |  basic.contact-points = ["127.0.0.1:19042"]
     |  basic.load-balancing-policy.local-datacenter = "datacenter1"
     |}
     |
     |akka.actor.testkit.typed.single-expect-default = 5s
     |# For LoggingTestKit
     |akka.actor.testkit.typed.filter-leeway = 5s
     |""".stripMargin).withFallback(ConfigFactory.load())
) with AnyWordSpecLike {

  val databaseDirectory = new File("target/cassandra-EventProcessorSpec")

  override protected def beforeAll(): Unit = {
    CassandraLauncher.start(
      databaseDirectory,
      CassandraLauncher.DefaultTestConfigResource,
      clean = true,
      port = 19042,
      CassandraLauncher.classpathForResources("logback-test.xml")
    )

    CassandraHelper.createTables(system)

    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    CassandraLauncher.stop()
    FileUtils.deleteDirectory(databaseDirectory)
  }

  "The events from the Shopping Cart" should {
    "be consumed by the event processor" in {
      val cartId1 = CartId("cart-1")
      val itemId1 = ItemId("foo")
      val quantity1 = Quantity(42)

      val cart1 = testKit.spawn(ShoppingCart(cartId1, Set("tag-0")))
      val probe = testKit.createTestProbe[Confirmation]

      val eventProbe = testKit.createTestProbe[Event]
      testKit.system.eventStream ! EventStream.Subscribe(eventProbe.ref)

      testKit.spawn[Nothing](
        EventProcessor(
          new ShoppingCartEventProcessorStream(system, system.executionContext, "EventProcessor", "tag-0")
        )
      )

      cart1 ! AddItem(itemId1, quantity1, probe.ref)
      probe.expectMessageType[Accepted]
      eventProbe.expectMessage(ItemAdded(cartId1, itemId1, quantity1))
    }

  }
}
