import java.sql.PreparedStatement

import anorm.{Column, ToStatement}
import ch.japanimpact.api.events.events.Visibility

package object models {

  case class EventAttribute(eventId: Int, eventAttributeOwner: Int, eventAttributeKey: String,
                            eventAttributeValue: String) {

    /**
     * Is the key private (meaning it cannot be read by public users)
     */
    val isPrivate: Boolean = eventAttributeKey.startsWith("_")

    /**
     * Is the key app private (meaning it cannot be read by other apps nor public users)
     */
    val isAppPrivate: Boolean = eventAttributeKey.startsWith("__")
  }


  implicit val visibilityStatement: ToStatement[Visibility.Value] = (s: PreparedStatement, index: Int, v: Visibility.Value) =>
    s.setString(index, v.toString)

  implicit def visibilityColumn: Column[Visibility.Value] =
    Column.columnToString.map(s => Visibility.withName(s))
}
