package com.github.yoshiyoshifujii.akka.sample.persistence.testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import akka.persistence.testkit._
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

class PersistenceTestKitSampleSpecWithPolicy
    extends ScalaTestWithActorTestKit(
      PersistenceTestKitPlugin.config.withFallback(ConfigFactory.defaultApplication())
    )
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  val persistenceTestKit: PersistenceTestKit = PersistenceTestKit(system)

  override protected def beforeEach(): Unit = {
    persistenceTestKit.clearAll()
    persistenceTestKit.resetPolicy()
  }

  "TestKit policy" should {
    import SampleBehavior._

    "fail all operations with custom exception" in {
      val policy = new EventStorage.JournalPolicies.PolicyType {

        class CustomFailure extends RuntimeException

        override def tryProcess(persistenceId: String, processingUnit: JournalOperation): ProcessingResult =
          processingUnit match {
            case WriteEvents(_) => StorageFailure(new CustomFailure)
            case _              => ProcessingSuccess
          }
      }
      persistenceTestKit.withPolicy(policy)

      val persistenceId   = PersistenceId.ofUniqueId("your-persistence-id-1")
      val persistentActor = spawn(SampleBehavior(persistenceId))

      persistentActor ! Cmd("data")

      persistenceTestKit.expectNothingPersisted(persistenceId.id)
    }

  }

}
