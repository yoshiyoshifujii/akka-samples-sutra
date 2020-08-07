package com.github.yoshiyoshifujii.akka.samples.cluster.sharding.persistenceExample

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.cluster.ClusterEvent
import akka.cluster.typed.{ Cluster, Join, Subscribe, Unsubscribe }
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }
import akka.testkit.TestDuration
import com.github.yoshiyoshifujii.akka.samples.MultiNodeSpecHelper
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object HelloWorldServiceSpecConfig extends MultiNodeConfig {
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
      |      "${classOf[HelloWorld.Reply].getName}" = jackson-cbor
      |      "${classOf[HelloWorld.Command].getName}" = jackson-cbor
      |      "${classOf[HelloWorld.Event].getName}" = jackson-cbor
      |      "${classOf[HelloWorld.State].getName}" = jackson-cbor
      |    }
      |  }
      |}
      |akka.remote.artery.canonical.port = 0
      |akka.cluster.roles = [compute]
      |""".stripMargin).withFallback(EventSourcedBehaviorTestKit.config).withFallback(ConfigFactory.load())
  )
}

class HelloWorldServiceSpecMultiJvmNode1 extends HelloWorldServiceSpec
class HelloWorldServiceSpecMultiJvmNode2 extends HelloWorldServiceSpec
class HelloWorldServiceSpecMultiJvmNode3 extends HelloWorldServiceSpec

class HelloWorldServiceSpec
    extends MultiNodeSpec(HelloWorldServiceSpecConfig)
    with MultiNodeSpecHelper
    with ScalaFutures {
  import HelloWorldServiceSpecConfig._
  override def initialParticipants: Int          = roles.size
  override protected def beforeAll(): Unit       = multiNodeSpecBeforeAll()
  override protected def afterAll(): Unit        = multiNodeSpecAfterAll()
  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped
  private val dilated: FiniteDuration            = 15.seconds.dilated

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(2, Seconds))

  "HelloWorldService multi-jvm spec" must {

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

    var node1Service: HelloWorldService = null
    var node2Service: HelloWorldService = null
    var node3Service: HelloWorldService = null

    "init Cluster Sharding" in within(dilated) {
      runOn(node1) {
        node1Service = new HelloWorldService(typedSystem)
      }
      runOn(node2) {
        node2Service = new HelloWorldService(typedSystem)
      }
      runOn(node3) {
        node3Service = new HelloWorldService(typedSystem)
      }
      enterBarrier("all-cluster-sharding-up")
    }

    "greet" in within(dilated) {
      val worldId1 = "worldId1"
      val worldId2 = "worldId2"

      runOn(node1) {
        assert(node1Service.greet(worldId1, "abe").futureValue === 1)

        enterBarrier("greet-1")
      }

      runOn(node2) {
        enterBarrier("greet-1")

        assert(node2Service.greet(worldId1, "ino").futureValue === 2)
      }

      runOn(node3) {
        enterBarrier("greet-1")

        assert(node3Service.greet(worldId2, "abe").futureValue === 1)
      }

    }

  }
}
