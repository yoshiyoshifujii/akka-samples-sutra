package sample.cluster.stats

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

object StatsSampleSpecConfig extends MultiNodeConfig {

  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(ConfigFactory.parseString(
    """
      |akka.actor.provider = cluster
      |akka.cluster.roles = [compute]
      |""".stripMargin).withFallback(ConfigFactory.load()))
}

class StatsSampleSPecMultiJvmNode1 extends StatsSampleSpec
class StatsSampleSPecMultiJvmNode2 extends StatsSampleSpec
class StatsSampleSPecMultiJvmNode3 extends StatsSampleSpec

abstract class StatsSampleSpec extends MultiNodeSpec(StatsSampleSpecConfig)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  import StatsSampleSpecConfig._

  override def initialParticipants: Int = roles.size

  override def beforeAll(): Unit = multiNodeSpecBeforeAll()

  override def afterAll(): Unit = multiNodeSpecAfterAll()

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped

  "The stats sample" must {

    "illustrate how to startup cluster" in within(15.seconds) {
      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      val firstAddress = node(first).address
      val secondAddress = node(second).address
      val thirdAddress = node(third).address

      Cluster(system).join(firstAddress)

      receiveN(3).collect {
        case MemberUp(m) => m.address
      }.toSet should be(
        Set(firstAddress, secondAddress, thirdAddress)
      )

      Cluster(system).unsubscribe(testActor)

      testConductor.enter("all-up")
    }

    "show usage of the statsService from one node" in within(15.seconds) {
      runOn(first, second) {
        val worker = system.spawn(StatsWorker(), "StatsWorker")
        val service = system.spawn(StatsService(worker), "StatsService")
        typedSystem.receptionist ! Receptionist.Register(Main.StatsServiceKey, service)
      }
      runOn(third) {
        assertServiceOk()
      }

      testConductor.enter("done-2")
    }

    def assertServiceOk(): Unit = {
      awaitAssert {
        val probe = TestProbe[AnyRef]()
        typedSystem.receptionist ! Receptionist.Find(Main.StatsServiceKey, probe.ref)
        val Main.StatsServiceKey.Listing(actors) = probe.expectMessageType[Receptionist.Listing]
        actors should not be empty

        actors.head ! StatsService.ProcessText("this is the text that will be analyzed", probe.ref)
        probe.expectMessageType[StatsService.JobResult].meanWordLength should be(3.875 +- 0.001)
      }
    }

    "show usage of the statsService from all nodes" in within(15.seconds) {
      assertServiceOk()
      testConductor.enter("done-3")
    }
  }


}
