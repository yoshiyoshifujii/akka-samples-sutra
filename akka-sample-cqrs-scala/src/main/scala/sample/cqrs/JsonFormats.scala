package sample.cqrs

import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._

object JsonFormats {

  implicit val summaryFormat: RootJsonFormat[ShoppingCartRoutes.Summary] = jsonFormat2(ShoppingCartRoutes.Summary.apply)
  implicit val addItemFormat: RootJsonFormat[ShoppingCartRoutes.AddItem] = jsonFormat3(ShoppingCartRoutes.AddItem)
  implicit val updateItemFormat: RootJsonFormat[ShoppingCartRoutes.UpdateItem] = jsonFormat3(ShoppingCartRoutes.UpdateItem)

}
