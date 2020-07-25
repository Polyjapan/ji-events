package ch.japanimpact.api.events

import ch.japanimpact.api.APIError
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[HttpEventsService])
trait EventsService {
  /**
   * Get all the events on the server. This function makes an HTTP request to the event server.
   * <br>An access token is used, make sure the configuration contains a `jiauth.clientSecret`
   * <br>Only events visible to the current app can be returned. Visibility depends on the permissions.
   *
   * Public events can be read by anyone (no permission)
   * Public test events require the permission `events/read/test`
   * Internal events require the permission `events/read/internal`
   * Other visibilities require the permission `events/read/<wildcard>`
   *
   * @param visibility   filter events by visiblity status
   *                     set to None to return all events, without considering visibility
   * @param isTest       filter events by test status
   *                     set to None to return all events without considering test status
   * @param isMainstream filter events according to mainstream state. A mainstream event is a Japan Impact event.
   *                     set to None to return all events without considering mainstream status.
   * @return a list of events or an error
   */
  def getEvents(visibility: Option[events.Visibility.Value] = None, isTest: Option[Boolean] = None, isMainstream: Option[Boolean] = None): Future[Either[APIError, Iterable[events.Event]]]

  /**
   * Get the event with the earliest start date. This function makes an HTTP request to the event server.
   * <br>An access token is used, make sure the configuration contains a `jiauth.clientSecret`
   * <br>Only events visible to the current app can be returned. Visibility depends on the permissions.
   *
   * Public events can be read by anyone (no permission)
   * Public test events require the permission `events/read/test`
   * Internal events require the permission `events/read/internal`
   * Other visibilities require the permission `events/read/<wildcard>`
   *
   * @param visibility   filter events by visiblity status
   *                     set to None to return all events, without considering visibility
   * @param isTest       filter events by test status
   *                     set to None to return all events without considering test status
   * @param isMainstream filter events according to mainstream state. A mainstream event is a Japan Impact event.
   *                     set to None to return all events without considering mainstream status.
   * @return a list of events or an error
   */
  def getCurrentEvent(visibility: Option[events.Visibility.Value] = None, isTest: Option[Boolean] = None, isMainstream: Option[Boolean] = None): Future[Either[APIError, events.Event]]

  /**
   * Returns the cached events, or make a request to get all the events from the server if the cache is empty.
   * @return The list of all events, or empty if an error happens
   */
  def getCachedEvents: Future[Iterable[events.Event]]

  /**
   * Gets an event by its ID
   * @param id the id of the event to get
   * @return the event or an error
   */
  def getEvent(id: Int): Future[Either[APIError, events.Event]]

  /**
   * Get the event set to visibility Public with the lowest start date (likely the earliest upcoming public event)
   * @return
   */
  def getCurrentPublicEvent: Future[Either[APIError, events.Event]]

  /**
   * Get the event set to visibility Public and which is mainstream with the lower start date.
   * This is very likely to be the next JI edition.
   * @return
   */
  def getCurrentMainstreamEvent: Future[Either[APIError, events.Event]]

  def getCachedMainstreamEvent: Future[Option[events.Event]]

  def createEvent(event: events.SimpleEvent): Future[Either[APIError, events.SimpleEvent]]

  def updateEvent(id: Int, event: events.SimpleEvent): Future[Either[APIError, Boolean]]

  /**
   *
   * @param id
   * @param key
   * start with `_` to make it invisible to unlogged people
   * start with `__` to make it visible only to your app
   * @param value
   */
  def setAttribute(id: Int, key: String, value: String): Future[Either[APIError, Boolean]]

  def deleteAttribute(id: Int, key: String): Future[Either[APIError, Boolean]]
}
