package sample.cqrs

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.concurrent.Future

object ShoppingCartRoutes {
  final case class AddItem(cartId: String, itemId: String, quantity:Int)
  final case class UpdateItem(cartId: String, itemId: String, quantity: Int)
  final case class Summary(items: Map[String, Int], checkedOut: Boolean)
  object Summary {
    def of(summary: ShoppingCart.Summary): Summary =
      new Summary(
        items = summary.items.map {
          case (ShoppingCart.ItemId(itemId), ShoppingCart.Quantity(quantity)) => itemId -> quantity
        },
        checkedOut = summary.checkedOut
      )
  }
}

class ShoppingCartRoutes()(implicit system: ActorSystem[_]) {

  implicit private val timeout: Timeout = Timeout.create(system.settings.config.getDuration("shopping.askTimeout"))
  private val sharding = ClusterSharding(system)

  import ShoppingCartRoutes._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.server.Directives._
  import JsonFormats._

  val shopping: Route =
    pathPrefix("shopping") {
      pathPrefix("carts") {
        concat(
          post {
            entity(as[AddItem]) { data =>
              // FIXME validate data
              val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, data.cartId)
              val reply: Future[ShoppingCart.Confirmation] =
                entityRef.ask(ShoppingCart.AddItem(ShoppingCart.ItemId(data.itemId), ShoppingCart.Quantity(data.quantity), _))
              onSuccess(reply) {
                case ShoppingCart.Accepted(summary) =>
                  complete(StatusCodes.OK -> Summary.of(summary))
                case ShoppingCart.Rejected(reason) =>
                  complete(StatusCodes.BadRequest -> reason)
              }
            }
          },
          put {
            entity(as[UpdateItem]) { data =>
              val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, data.cartId)

              def command(replyTo: ActorRef[ShoppingCart.Confirmation]) =
                if (data.quantity == 0) ShoppingCart.RemoveItem(ShoppingCart.ItemId(data.itemId), replyTo)
                else
                  ShoppingCart.AdjustItemQuantity(ShoppingCart.ItemId(data.itemId), ShoppingCart.Quantity(data.quantity), replyTo)

              val reply: Future[ShoppingCart.Confirmation] = entityRef.ask(command)
              onSuccess(reply) {
                case ShoppingCart.Accepted(summary) =>
                  complete(StatusCodes.OK -> Summary.of(summary))
                case ShoppingCart.Rejected(reason) =>
                  complete(StatusCodes.BadRequest -> reason)
              }

            }
          },
          pathPrefix(Segment) { cartId =>
            concat(get {
              val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
              onSuccess(entityRef.ask(ShoppingCart.Get)) { summary =>
                if (summary.items.isEmpty) complete(StatusCodes.NotFound)
                else complete(Summary.of(summary))
              }
            }, path("checkout") {
              post {
                val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
                val reply: Future[ShoppingCart.Confirmation] = entityRef.ask(ShoppingCart.Checkout)
                onSuccess(reply) {
                  case ShoppingCart.Accepted(summary) =>
                    complete(StatusCodes.OK -> Summary.of(summary))
                  case ShoppingCart.Rejected(reason) =>
                    complete(StatusCodes.BadRequest -> reason)
                }
              }
            })
          })
      }
    }

}
