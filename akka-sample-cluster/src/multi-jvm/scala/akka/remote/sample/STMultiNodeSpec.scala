package akka.remote.sample

import akka.remote.testkit.{ MultiNodeSpec, MultiNodeSpecCallbacks }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

trait STMultiNodeSpec extends MultiNodeSpecCallbacks with AnyWordSpecLike with BeforeAndAfterAll {
  self: MultiNodeSpec =>
  override protected def beforeAll(): Unit = multiNodeSpecBeforeAll()
  override protected def afterAll(): Unit  = multiNodeSpecAfterAll()

  override protected implicit def convertToWordSpecStringWrapper(s: String): WordSpecStringWrapper =
    new WordSpecStringWrapper(s"$s (on node '${self.myself.name}', $getClass)")
}
