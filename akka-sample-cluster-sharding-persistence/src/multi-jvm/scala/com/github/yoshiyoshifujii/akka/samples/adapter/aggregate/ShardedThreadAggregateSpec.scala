package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.{ Cluster, ClusterEvent }
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }
import akka.testkit.{ ImplicitSender, TestDuration }
import com.github.yoshiyoshifujii.akka.samples.MultiNodeSpecHelper
import com.github.yoshiyoshifujii.akka.samples.domain.model._
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers

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
    with Matchers
    with ImplicitSender
    with ScalaFutures {
  import ShardedThreadAggregateSpecConfig._

  override def initialParticipants: Int = roles.size

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped

  "ShardedThreadAggregate multi-jvm spec" must {

    "illustrate how to startup cluster" in within(15.seconds.dilated) {
      enterBarrier("begin-cluster")
      Cluster(system).subscribe(testActor, classOf[ClusterEvent.MemberUp])
      expectMsgClass(classOf[ClusterEvent.CurrentClusterState])

      val node1Address = node(node1).address
      val node2Address = node(node2).address
      val node3Address = node(node3).address

      Cluster(system).join(node1Address)

      receiveN(3).collect {
        case ClusterEvent.MemberUp(m) => m.address
      }.toSet should be(
        Set(node1Address, node2Address, node3Address)
      )

      Cluster(system).unsubscribe(testActor)

      testConductor.enter("all-up")
    }

    val threadId = ThreadId()

    "create thread and post message" in within(15.seconds.dilated) {

      runOn(node1) {
        val clusterSharding = initClusterSharding
        val threadRef = system.spawn(
          ShardedThreadAggregate.ofProxy(clusterSharding),
          ShardedThreadAggregate.name
        )

        val probe = TestProbe[ThreadPersistentAggregate.ReplyCreateThread]
        threadRef ! ThreadPersistentAggregate.CommandCreateThread(
          threadId,
          ThreadName("first thread name"),
          AccountId(),
          probe.ref
        )

        probe.expectMessageType[ThreadPersistentAggregate.ReplyCreateThreadSucceeded].threadId should be(threadId)
      }

      runOn(node2) {
        val clusterSharding = initClusterSharding
        val threadRef = system.spawn(
          ShardedThreadAggregate.ofProxy(clusterSharding),
          ShardedThreadAggregate.name
        )
        val messageId = MessageId()
        val probe     = TestProbe[ThreadPersistentAggregate.ReplyPostMessage]
        threadRef ! ThreadPersistentAggregate.CommandPostMessage(
          threadId,
          messageId,
          AccountId(),
          MessageBody("first message"),
          probe.ref
        )

        probe.expectMessageType[ThreadPersistentAggregate.ReplyPostMessageSucceeded].messageId should be(messageId)
      }

      runOn(node3) {
        val clusterSharding = initClusterSharding
        val threadRef = system.spawn(
          ShardedThreadAggregate.ofProxy(clusterSharding),
          ShardedThreadAggregate.name
        )
      }
    }
  }

  private def initClusterSharding: ClusterSharding = {
    val clusterSharding = ClusterSharding(system.toTyped)
    val childBehavior =
      ThreadAggregates(_.asString)(id => ThreadPersistentAggregate(id, _.asString, MessagePersistentAggregate.apply))
    val shardRegion: ActorRef[ShardingEnvelope[ThreadPersistentAggregate.Command]] =
      ShardedThreadAggregate.initClusterSharding(
        clusterSharding,
        childBehavior,
        15.seconds
      )
    clusterSharding
  }
}
