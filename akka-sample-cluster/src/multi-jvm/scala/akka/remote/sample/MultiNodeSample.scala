package akka.remote.sample

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.github.yoshiyoshifujii.akka.sample.cluster.serialization.JsonSerializer
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

object MultiNodeSampleConfig extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")
  val node3: RoleName = role("node3")

  commonConfig(
    ConfigFactory
      .parseString("""
      |akka.actor.provider = cluster
      |akka.actor.serialization-bindings {
      |  "com.github.yoshiyoshifujii.akka.sample.cluster.serialization.JsonSerializer" = jackson-cbor
      |}
      |akka.remote.artery.canonical.port = 0
      |akka.cluster.roles = [compute]
      |""".stripMargin).withFallback(ConfigFactory.load())
  )
}

object Ping {
  val PingServiceKey: ServiceKey[Ping.Command] = ServiceKey[Command]("pingService")

  case class Command(ping: String, replyTo: ActorRef[String]) extends JsonSerializer

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Command("ping", replyTo) =>
          replyTo ! "pong"
          Behaviors.same
        case _ => Behaviors.stopped
      }
    }
}

class MultiNodeSampleSpecMultiJvmNode1 extends MultiNodeSample
class MultiNodeSampleSpecMultiJvmNode2 extends MultiNodeSample
class MultiNodeSampleSpecMultiJvmNode3 extends MultiNodeSample

class MultiNodeSample
    extends MultiNodeSpec(MultiNodeSampleConfig)
    with Matchers
    with STMultiNodeSpec
    with ImplicitSender
    with ScalaFutures {

  import MultiNodeSampleConfig._

  override def initialParticipants: Int = roles.size

  override protected def beforeAll(): Unit = multiNodeSpecBeforeAll()

  override protected def afterAll(): Unit = multiNodeSpecAfterAll()

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped

  "A MultiNodeSample" must {

    "illustrate how to startup cluster" in within(15.seconds) {
      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      val node1Address = node(node1).address
      val node2Address = node(node2).address
      val node3Address = node(node3).address

      Cluster(system).join(node1Address)

      receiveN(3).collect {
        case MemberUp(m) => m.address
      }.toSet should be(
        Set(node1Address, node2Address, node3Address)
      )

      Cluster(system).unsubscribe(testActor)

      testConductor.enter("all-up")
    }

    "show usage of the statsService from one node" in within(15.seconds) {
      runOn(node1, node2) {
        val pinger = system.spawn(Ping(), "pinger")
        typedSystem.receptionist ! Receptionist.Register(Ping.PingServiceKey, pinger)
      }
      runOn(node3) {
        awaitAssert {
          val probe = TestProbe[Receptionist.Listing]()
          typedSystem.receptionist ! Receptionist.Subscribe(Ping.PingServiceKey, probe.ref)
          val Ping.PingServiceKey.Listing(actors) = probe.expectMessageType[Receptionist.Listing]
          actors should not be empty

          val probe2 = TestProbe[String]()
          actors.head ! Ping.Command("ping", probe2.ref)
          probe2.expectMessageType[String] should be("pong")
        }
      }
      testConductor.enter("done-2")
    }

  }

}
