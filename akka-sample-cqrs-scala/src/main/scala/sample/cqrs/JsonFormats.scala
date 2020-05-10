package sample.cqrs

import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._

object JsonFormats {

  implicit val ItemIdFormat: RootJsonFormat[ShoppingCart.ItemId] = jsonFormat1(ShoppingCart.ItemId)
  implicit val QuantityFormat: RootJsonFormat[ShoppingCart.Quantity] = jsonFormat1(ShoppingCart.Quantity.apply)
  implicit val summaryFormat: RootJsonFormat[ShoppingCart.Summary] = jsonFormat2(ShoppingCart.Summary)
  implicit val addItemFormat: RootJsonFormat[ShoppingCartRoutes.AddItem] = jsonFormat3(ShoppingCartRoutes.AddItem)
  implicit val updateItemFormat: RootJsonFormat[ShoppingCartRoutes.UpdateItem] = jsonFormat3(ShoppingCartRoutes.UpdateItem)

}
