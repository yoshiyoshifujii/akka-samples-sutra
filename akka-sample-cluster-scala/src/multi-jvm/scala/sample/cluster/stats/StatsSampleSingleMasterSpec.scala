package sample.cluster.stats

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.cluster.typed.{ClusterSingleton, ClusterSingletonSettings, SingletonActor}
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

object StatsSampleSingleMasterSpecConfig extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(ConfigFactory.parseString(
    """
      |akka.loglevel = INFO
      |akka.actor.provider = cluster
      |akka.cluster.roles = [compute]
      |""".stripMargin).withFallback(ConfigFactory.load()))
}

class StatsSampleSingleMasterSpecMultiJvmNode1 extends StatsSampleSingleMasterSpec
class StatsSampleSingleMasterSpecMultiJvmNode2 extends StatsSampleSingleMasterSpec
class StatsSampleSingleMasterSpecMultiJvmNode3 extends StatsSampleSingleMasterSpec

abstract class StatsSampleSingleMasterSpec extends MultiNodeSpec(StatsSampleSingleMasterSpecConfig)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  import StatsSampleSingleMasterSpecConfig._

  override def initialParticipants: Int = roles.size

  override def beforeAll(): Unit = multiNodeSpecBeforeAll()

  override def afterAll(): Unit = multiNodeSpecBeforeAll()

  implicit val typedSystem = system.toTyped

  var singletonProxy: ActorRef[StatsService.Command] = _

  "The stats sample with single master" must {

    "illustrate how to startup cluster" in within(15.seconds) {
      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      val firstAddress = node(first).address
      val secondAddress = node(second).address
      val thirdAddress = node(third).address

      Cluster(system) join firstAddress

      receiveN(3).collect {
        case MemberUp(m) => m.address
      }.toSet should be(
        Set(firstAddress, secondAddress, thirdAddress)
      )

      Cluster(system).unsubscribe(testActor)

      val singletonSettings = ClusterSingletonSettings(typedSystem).withRole("compute")
      singletonProxy = ClusterSingleton(typedSystem).init(
        SingletonActor(
          Behaviors.setup[StatsService.Command] { ctx =>
            val workersRouter = ctx.spawn(Routers.pool(2)(StatsWorker()), "WorkersRouter")
            StatsService(workersRouter)
          }, "StatsService"
        ).withSettings(singletonSettings)
      )

      testConductor.enter("all-up")
    }

    "show usage of statsServiceProxy" in within(20.seconds) {
      awaitAssert {
        system.log.info("Trying a request")
        val probe = TestProbe[StatsService.Response]()
        singletonProxy ! StatsService.ProcessText("this is the text that will be analyzed", probe.ref)
        val response = probe.expectMessageType[StatsService.JobResult](3.seconds)
        response.meanWordLength should be(3.875 +- 0.001)
      }

      testConductor.enter("done")
    }

  }
}
