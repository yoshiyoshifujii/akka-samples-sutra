package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{ Cluster, Join, Subscribe, Unsubscribe }
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }
import akka.testkit.TestDuration
import com.github.yoshiyoshifujii.akka.samples.MultiNodeSpecHelper
import com.github.yoshiyoshifujii.akka.samples.domain.model._
import com.github.yoshiyoshifujii.akka.samples.infrastructure.ulid.ULID
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }

import scala.concurrent.duration._

object ShardedThreadAggregateSpecConfig extends MultiNodeConfig {
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
       |      "${classOf[ThreadPersistentAggregate.Reply].getName}" = jackson-cbor
       |      "${classOf[ThreadPersistentAggregate.Command].getName}" = jackson-cbor
       |      "${classOf[ThreadPersistentAggregate.Event].getName}" = jackson-cbor
       |      "${classOf[ThreadPersistentAggregate.State].getName}" = jackson-cbor
       |      "${classOf[MessagePersistentAggregate.Reply].getName}" = jackson-cbor
       |      "${classOf[MessagePersistentAggregate.Command].getName}" = jackson-cbor
       |      "${classOf[MessagePersistentAggregate.Event].getName}" = jackson-cbor
       |      "${classOf[MessagePersistentAggregate.State].getName}" = jackson-cbor
       |    }
       |  }
       |}
       |akka.remote.artery.canonical.port = 0
       |akka.cluster.roles = [compute]
       |""".stripMargin).withFallback(EventSourcedBehaviorTestKit.config).withFallback(ConfigFactory.load())
  )
}

class ShardedThreadAggregateSpecMultiJvmNode1 extends ShardedThreadAggregateSpec
class ShardedThreadAggregateSpecMultiJvmNode2 extends ShardedThreadAggregateSpec
class ShardedThreadAggregateSpecMultiJvmNode3 extends ShardedThreadAggregateSpec

class ShardedThreadAggregateSpec
    extends MultiNodeSpec(ShardedThreadAggregateSpecConfig)
    with MultiNodeSpecHelper
    with ScalaFutures {
  import ShardedThreadAggregateSpecConfig._

  override def initialParticipants: Int          = roles.size
  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped
  private val dilated: FiniteDuration            = 15.seconds.dilated

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(2, Seconds))

  "ShardedThreadAggregate multi-jvm spec" must {

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

    var node1ClusterSharding: ClusterSharding = null
    var node2ClusterSharding: ClusterSharding = null
    var node3ClusterSharding: ClusterSharding = null

    "init cluster sharding" in within(dilated) {
      runOn(node1) {
        node1ClusterSharding = initClusterSharding
      }
      runOn(node2) {
        node2ClusterSharding = initClusterSharding
      }
      runOn(node3) {
        node3ClusterSharding = initClusterSharding
      }
      enterBarrier("all-cluster-sharding-up")
    }

    "create thread and post message" in within(dilated) {
      // If this value is not fixed, it will be a different value for each node.
      val threadId  = ThreadId(ULID.parseFromString("01EFG1XKANJ1132QFKK2CYT0YN").get)
      val messageId = MessageId(ULID.parseFromString("01EFG1XKANJ1132QFKK2CYT0YZ").get)
      val accountId = AccountId(ULID.parseFromString("01EFG1XKANJ1132QFKK2CYT0YA").get)

      runOn(node1) {
        val threadRef = system.spawn(
          ShardedThreadAggregate.ofProxy(node1ClusterSharding),
          ShardedThreadAggregate.name
        )

        enterBarrier("spawned")

        val probe = TestProbe[ThreadPersistentAggregate.ReplyCreateThread]
        threadRef ! ThreadPersistentAggregate.CommandCreateThread(
          threadId,
          ThreadName("first thread name"),
          creatorId = accountId,
          replyTo = probe.ref
        )

        assert(probe.expectMessageType[ThreadPersistentAggregate.ReplyCreateThreadSucceeded].threadId === threadId)

        enterBarrier("created-thread-1")
        enterBarrier("posted-message-1")
      }

      runOn(node2) {
        val threadRef = system.spawn(
          ShardedThreadAggregate.ofProxy(node2ClusterSharding),
          ShardedThreadAggregate.name
        )

        enterBarrier("spawned")

        enterBarrier("created-thread-1")

        val probe = TestProbe[ThreadPersistentAggregate.ReplyPostMessage]
        threadRef ! ThreadPersistentAggregate.CommandPostMessage(
          threadId,
          messageId,
          senderId = accountId,
          MessageBody("first message"),
          replyTo = probe.ref
        )

        assert(probe.expectMessageType[ThreadPersistentAggregate.ReplyPostMessageSucceeded].messageId === messageId)

        enterBarrier("posted-message-1")
      }

      runOn(node3) {
        val threadRef = system.spawn(
          ShardedThreadAggregate.ofProxy(node3ClusterSharding),
          ShardedThreadAggregate.name
        )

        enterBarrier("spawned")

        enterBarrier("created-thread-1")
        enterBarrier("posted-message-1")

        val probe = TestProbe[ThreadPersistentAggregate.ReplyEditMessage]
        threadRef ! ThreadPersistentAggregate.CommandEditMessage(
          threadId,
          messageId,
          senderId = accountId,
          MessageBody("first message edition"),
          replyTo = probe.ref
        )

        assert(probe.expectMessageType[ThreadPersistentAggregate.ReplyEditMessageSucceeded].messageId === messageId)
      }
    }
  }

  private def initClusterSharding: ClusterSharding = {
    val clusterSharding = ClusterSharding(typedSystem)
    val childBehavior =
      ThreadAggregates(_.asString)(id => ThreadPersistentAggregate(id, _.asString, MessagePersistentAggregate.apply))
    ShardedThreadAggregate.initClusterSharding(
      clusterSharding,
      childBehavior,
      15.seconds
    )
    clusterSharding
  }
}
