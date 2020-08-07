package com.github.yoshiyoshifujii.akka.samples.cluster.sharding

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{ Cluster, Join, Subscribe, Unsubscribe }
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }
import akka.testkit.TestDuration
import com.github.yoshiyoshifujii.akka.samples.MultiNodeSpecHelper
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object ShardedCounterSpecConfig extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")
  val node3: RoleName = role("node3")

  commonConfig(
    ConfigFactory
      .parseString(s"""
      |akka.actor.provider = cluster
      |akka {
      |  actor {
      |    serializers {
      |      jackson-cbor = "akka.serialization.jackson.JacksonCborSerializer"
      |    }
      |    serialization-bindings {
      |      "${classOf[Counter.Command].getName}" = jackson-cbor
      |    }
      |  }
      |}
      |akka.remote.artery.canonical.port = 0
      |akka.cluster.roles = [compute]
      |""".stripMargin).withFallback(ConfigFactory.load())
  )
}

class ShardedCounterSpecMultiJvmNode1 extends ShardedCounterSpec
class ShardedCounterSpecMultiJvmNode2 extends ShardedCounterSpec
class ShardedCounterSpecMultiJvmNode3 extends ShardedCounterSpec

class ShardedCounterSpec extends MultiNodeSpec(ShardedCounterSpecConfig) with MultiNodeSpecHelper {
  import ShardedCounterSpecConfig._
  override protected def beforeAll(): Unit = multiNodeSpecBeforeAll()
  override protected def afterAll(): Unit  = multiNodeSpecAfterAll()
  override def initialParticipants: Int    = roles.size

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped

  private val dilated: FiniteDuration = 15.seconds.dilated

  "ShardedCounterSpec multi-jvm spec" must {

    "init Cluster" in within(dilated) {
      val cluster      = Cluster(typedSystem)
      val node1Address = node(node1).address
      val node2Address = node(node2).address
      val node3Address = node(node3).address

      val probe = TestProbe[ClusterEvent.MemberEvent]
      cluster.subscriptions ! Subscribe(probe.ref, classOf[ClusterEvent.MemberUp])

      cluster.manager ! Join(node1Address)

      assert {
        probe
          .receiveMessages(3)
          .collect {
            case ClusterEvent.MemberUp(m) => m.address
          }.toSet === Set(node1Address, node2Address, node3Address)
      }

      cluster.subscriptions ! Unsubscribe(probe.ref)

      enterBarrier("all-up")
    }

    val CounterId1 = "counter-1"

    "init Cluster Sharding" in within(dilated) {
      val clusterSharding = ClusterSharding(typedSystem)
      val shardRegion     = ShardedCounter.init(clusterSharding)
      val probe           = TestProbe[Int]
      shardRegion ! ShardingEnvelope(CounterId1, Counter.GetValue(CounterId1, probe.ref))
      assert(probe.expectMessageType[Int] === 0)

      enterBarrier("all-sharding-up")
    }

    "Count up" in within(dilated) {

      runOn(node1) {
        val clusterSharding = ClusterSharding(typedSystem)
        val proxyBehavior   = ShardedCounter.ofProxy(clusterSharding)
        val ref             = system.spawn(proxyBehavior, "node1-shard-region")
        val probe           = TestProbe[Int]
        ref ! Counter.GetValue(CounterId1, probe.ref)
        assert(probe.expectMessageType[Int] === 0)

        ref ! Counter.Increment(CounterId1)
        ref ! Counter.GetValue(CounterId1, probe.ref)
        assert(probe.expectMessageType[Int] === 1)

        enterBarrier("count-up")
      }

      runOn(node2, node3) {
        enterBarrier("count-up")

        val clusterSharding = ClusterSharding(typedSystem)
        val proxyBehavior   = ShardedCounter.ofProxy(clusterSharding)
        val ref             = system.spawn(proxyBehavior, "node2or3-shard-region")
        val probe           = TestProbe[Int]
        ref ! Counter.GetValue(CounterId1, probe.ref)
        assert(probe.expectMessageType[Int] === 1)
      }

    }

  }

}
